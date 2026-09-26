package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Encapsulates Android's native PdfRenderer, memory-safe LruCache, aspect ratio scaling,
 * and background thread rendering with thread synchronization.
 */
class NexusPdfRendererEngine(
    private val context: Context,
    private val uri: Uri
) {
    class PdfPasswordException(message: String) : Exception(message)
    class PdfCorruptedException(message: String) : Exception(message)

    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val renderLock = Mutex()

    /**
      * Rendered pages, budgeted in kilobytes rather than counted.
      *
      * A page is as big as the screen is wide — about 6MB on a phone, more on a tablet and more
      * again at a larger fit width — so "three pages" meant anything from 12MB to well over 60MB
      * depending on the device. An eighth of the heap, within sane bounds, holds the current page
      * and its neighbours on a small device and more on a large one.
      */
    private val pageBitmapCache = object : LruCache<Int, Bitmap>(cacheBudgetKb()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)

        override fun entryRemoved(evicted: Boolean, key: Int?, oldValue: Bitmap?, newValue: Bitmap?) {
            // No recycling: an evicted page may still be on screen in a page view's ImageView.
        }
    }

    val pageCount: Int get() = pdfRenderer?.pageCount ?: 0

    private fun cacheBudgetKb(): Int {
        val heapKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        return (heapKb / 8).coerceIn(12 * 1024, 64 * 1024)
    }

    suspend fun open(): Int = withContext(Dispatchers.IO) {
        renderLock.withLock {
            try {
                fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw PdfCorruptedException("Failed to open file descriptor")
                val fd = fileDescriptor?.fileDescriptor
                    ?: throw PdfCorruptedException("Invalid file descriptor")
                // PdfRenderer takes ownership of what it is given and closes it, so it gets a
                // dup of ours. If it throws — a protected or corrupt file, the common case — that
                // dup is ours to close; it used to leak one file descriptor per failed open.
                val dup = ParcelFileDescriptor.dup(fd)
                try {
                    pdfRenderer = PdfRenderer(dup)
                } catch (e: Throwable) {
                    try { dup.close() } catch (_: Exception) {}
                    throw e
                }
                pdfRenderer?.pageCount ?: 0
            } catch (e: SecurityException) {
                closeInternal()
                throw PdfPasswordException(e.message ?: "Protected PDF")
            } catch (e: Exception) {
                closeInternal()
                throw PdfCorruptedException(e.message ?: "Corrupted PDF")
            }
        }
    }

    suspend fun getPageDimensions(pageIndex: Int, targetWidth: Int): Pair<Int, Int> = withContext(Dispatchers.IO) {
        renderLock.withLock {
            val renderer = pdfRenderer ?: return@withContext Pair(targetWidth, targetWidth)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return@withContext Pair(targetWidth, targetWidth)
            }
            try {
                renderer.openPage(pageIndex).use { page ->
                    val pw = page.width.coerceAtLeast(1)
                    val ph = page.height.coerceAtLeast(1)
                    val targetHeight = ((targetWidth.toFloat() / pw) * ph).toInt().coerceAtLeast(1)
                    Pair(targetWidth, targetHeight)
                }
            } catch (_: Exception) {
                Pair(targetWidth, targetWidth)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cached = pageBitmapCache.get(pageIndex)
        if (cached != null && !cached.isRecycled && cached.width == targetWidth && cached.height == targetHeight) {
            return@withContext cached
        }

        renderLock.withLock {
            val renderer = pdfRenderer ?: return@withContext null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            try {
                drawPage(renderer, pageIndex, targetWidth, targetHeight)
            } catch (_: OutOfMemoryError) {
                // The reader shares the launcher's process, so an OOM here would take the home
                // screen with it. Drop every cached page and try this one once more; a blank page
                // is a far better outcome than a launcher restart.
                pageBitmapCache.evictAll()
                try {
                    drawPage(renderer, pageIndex, targetWidth, targetHeight)
                } catch (_: Throwable) {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun drawPage(renderer: PdfRenderer, pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap =
        renderer.openPage(pageIndex).use { page ->
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            // White paper fill for PDF background
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pageBitmapCache.put(pageIndex, bitmap)
            bitmap
        }

    fun getCached(pageIndex: Int): Bitmap? = pageBitmapCache.get(pageIndex)

    fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        try {
            pdfRenderer?.close()
        } catch (_: Exception) {}
        pdfRenderer = null

        try {
            fileDescriptor?.close()
        } catch (_: Exception) {}
        fileDescriptor = null

        pageBitmapCache.evictAll()
    }
}
