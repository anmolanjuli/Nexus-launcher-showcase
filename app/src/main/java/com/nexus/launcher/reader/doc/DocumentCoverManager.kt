package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Manages document cover thumbnail generation, caching to disk, and memory LruCache.
 */
class DocumentCoverManager(private val context: Context) {

    private val dp = context.resources.displayMetrics.density
    /** Covers, budgeted in kilobytes: 25 of them is 4MB on one device and 20MB on another. */
    private val memoryCache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024).toInt() / 16).coerceIn(4 * 1024, 24 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.allocationByteCount / 1024).coerceAtLeast(1)

        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            // No recycling: a library card may still be drawing this cover.
        }
    }

    private val coversDir: File by lazy {
        File(context.filesDir, "doc_covers").apply { if (!exists()) mkdirs() }
    }

    fun getMemoryCached(uri: String): Bitmap? = memoryCache.get(uri)

    suspend fun getOrGenerateCover(
        document: DocumentRecord,
        onGenerated: (String) -> Unit
    ): Bitmap? = withContext(Dispatchers.IO) {
        val uri = document.uri
        val cachedMem = memoryCache.get(uri)
        if (cachedMem != null && !cachedMem.isRecycled) return@withContext cachedMem

        // Check disk cache
        val diskFile = document.coverPath?.let { File(it) }
            ?: File(coversDir, "cover_${hashUri(uri)}.png")

        if (diskFile.exists() && diskFile.length() > 0) {
            val bmp = BitmapFactory.decodeFile(diskFile.absolutePath)
            if (bmp != null) {
                memoryCache.put(uri, bmp)
                return@withContext bmp
            }
        }

        // Generate cover for PDF
        if (document.fileType == "pdf") {
            val generated = renderPdfPageOne(Uri.parse(uri))
            if (generated != null) {
                try {
                    FileOutputStream(diskFile).use { out ->
                        generated.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    memoryCache.put(uri, generated)
                    onGenerated(diskFile.absolutePath)
                    return@withContext generated
                } catch (_: Exception) {}
            }
        }

        // Generate cover for EPUB
        if (document.fileType == "epub") {
            val generated = renderEpubCover(Uri.parse(uri))
            if (generated != null) {
                try {
                    FileOutputStream(diskFile).use { out ->
                        generated.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                    memoryCache.put(uri, generated)
                    onGenerated(diskFile.absolutePath)
                    return@withContext generated
                } catch (_: Exception) {}
            }
        }

        null
    }

    private suspend fun renderEpubCover(uri: Uri): Bitmap? {
        return NexusEpubParser.extractCover(context, uri)
    }

    private fun renderPdfPageOne(uri: Uri): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null

            val targetWidth = (120 * dp).toInt().coerceAtLeast(1)

            renderer.openPage(0).use { page ->
                // The page's own proportions. A fixed 120x160 box stretched a landscape or square
                // page to fit, so wide documents had visibly squashed covers.
                val ratio = page.height.toFloat() / page.width.coerceAtLeast(1)
                val targetHeight = (targetWidth * ratio).toInt()
                    .coerceIn((60 * dp).toInt(), (220 * dp).toInt())
                val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        } catch (_: Exception) {
            return null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    private fun hashUri(uri: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5").digest(uri.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            uri.hashCode().toString()
        }
    }
}
