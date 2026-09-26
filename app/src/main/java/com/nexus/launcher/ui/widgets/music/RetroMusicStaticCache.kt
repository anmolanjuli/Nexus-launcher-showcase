package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * Caches static hardware chrome, chassis bevels, frames, labels, and album art
 * for music widgets as a reusable Bitmap per [appWidgetId].
 *
 * Only re-renders when the track changes, styling changes, or widget dimensions change.
 * During 15 FPS animation, the static layer is rendered in a single [Canvas.drawBitmap] pass.
 */
object RetroMusicStaticCache {

    private data class CacheEntry(
        var bitmap: Bitmap,
        var cacheKey: String,
        var width: Int,
        var height: Int
    )

    private val cache = mutableMapOf<Int, CacheEntry>()
    private val lock = Any()

    /**
     * Retrieves or draws the static background layer for [appWidgetId].
     *
     * @param appWidgetId The widget instance ID.
     * @param w Target width in pixels.
     * @param h Target height in pixels.
     * @param cacheKey Unique string representing track metadata + style configuration.
     * @param drawStatic Lambda executed ONLY on cache miss to draw the static layer.
     */
    fun getOrRender(
        appWidgetId: Int,
        w: Int,
        h: Int,
        cacheKey: String,
        drawStatic: (Canvas) -> Unit
    ): Bitmap {
        val width = w.coerceAtLeast(1)
        val height = h.coerceAtLeast(1)

        synchronized(lock) {
            val existing = cache[appWidgetId]
            if (existing != null &&
                !existing.bitmap.isRecycled &&
                existing.width == width &&
                existing.height == height &&
                existing.cacheKey == cacheKey
            ) {
                return existing.bitmap
            }

            // Cache miss: reuse bitmap if dimensions match, else allocate
            val bmp = if (existing != null &&
                !existing.bitmap.isRecycled &&
                existing.bitmap.width == width &&
                existing.bitmap.height == height
            ) {
                existing.bitmap.eraseColor(0)
                existing.bitmap
            } else {
                existing?.bitmap?.recycle()
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }

            val canvas = Canvas(bmp)
            drawStatic(canvas)

            cache[appWidgetId] = CacheEntry(
                bitmap = bmp,
                cacheKey = cacheKey,
                width = width,
                height = height
            )
            return bmp
        }
    }

    /**
     * Invalidates static cache for a specific widget.
     */
    fun invalidate(appWidgetId: Int) {
        synchronized(lock) {
            val entry = cache.remove(appWidgetId)
            entry?.bitmap?.recycle()
        }
    }

    /**
     * Releases static cache for a removed widget.
     */
    fun release(appWidgetId: Int) {
        invalidate(appWidgetId)
    }

    /**
     * Clears all cached static bitmaps.
     */
    fun clear() {
        synchronized(lock) {
            for (entry in cache.values) {
                if (!entry.bitmap.isRecycled) {
                    entry.bitmap.recycle()
                }
            }
            cache.clear()
        }
    }
}
