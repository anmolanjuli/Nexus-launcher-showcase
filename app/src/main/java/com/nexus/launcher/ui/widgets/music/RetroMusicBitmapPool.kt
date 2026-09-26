package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap

/**
 * Bitmap reuse pool for Retro and Nexus music widgets.
 * Eliminates garbage collection churn caused by allocating new ARGB_8888 Bitmaps
 * during 15 FPS animation loops.
 *
 * Two bitmaps per widget, used in turn. The one just drawn is handed to the launcher inside a
 * RemoteViews, which reads it when it gets round to it — with a single bitmap the next frame
 * erased and redrew the very pixels being read, which is what a torn or flickering frame is.
 */
object RetroMusicBitmapPool {

    private class Buffers(var front: Bitmap, var back: Bitmap)

    private val widgetBitmaps = mutableMapOf<Int, Buffers>()
    private val lock = Any()

    /**
     * Obtains a reusable [Bitmap] matching [w] and [h] for the specified [appWidgetId].
     * If an existing bitmap exists with identical dimensions, its pixels are cleared to 0
     * and reused without any heap allocation.
     */
    fun acquire(appWidgetId: Int, w: Int, h: Int): Bitmap {
        val width = w.coerceAtLeast(1)
        val height = h.coerceAtLeast(1)

        synchronized(lock) {
            val existing = widgetBitmaps[appWidgetId]
            if (existing != null && existing.back.fits(width, height) && existing.front.fits(width, height)) {
                // Swap, then draw into the one the host is not holding.
                val next = existing.back
                existing.back = existing.front
                existing.front = next
                next.eraseColor(0)
                return next
            }

            // Dimensions changed or a bitmap was recycled — release both and start again.
            existing?.front?.recycle()
            existing?.back?.recycle()
            val front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val back = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            widgetBitmaps[appWidgetId] = Buffers(front, back)
            return front
        }
    }

    /**
     * Releases cached bitmap for a removed widget.
     */
    fun release(appWidgetId: Int) {
        synchronized(lock) {
            val buffers = widgetBitmaps.remove(appWidgetId) ?: return
            buffers.front.recycle()
            buffers.back.recycle()
        }
    }

    private fun Bitmap.fits(width: Int, height: Int): Boolean =
        !isRecycled && this.width == width && this.height == height

    /**
     * Releases all cached bitmaps.
     */
    fun clear() {
        synchronized(lock) {
            for (buffers in widgetBitmaps.values) {
                if (!buffers.front.isRecycled) buffers.front.recycle()
                if (!buffers.back.isRecycled) buffers.back.recycle()
            }
            widgetBitmaps.clear()
        }
    }
}
