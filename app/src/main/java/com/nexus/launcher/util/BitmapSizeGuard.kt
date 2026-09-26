package com.nexus.launcher.util

import android.graphics.Bitmap
import android.util.Log

/**
 * Lightweight logger and guard for Bitmaps before drawing/binding to prevent
 * hardware canvas overflow crashes (>50MB warnings).
 */
object BitmapSizeGuard {
    private const val TAG = "BitmapSizeGuard"
    private const val WARN_THRESHOLD_BYTES = 50 * 1024 * 1024 // 50 MB

    fun guard(source: String, bitmap: Bitmap?): Bitmap? {
        if (bitmap == null) return null
        val bytes = bitmap.byteCount
        val width = bitmap.width
        val height = bitmap.height
        val mb = bytes / (1024.0 * 1024.0)

        if (bytes > WARN_THRESHOLD_BYTES) {
            Log.w(
                TAG,
                "OVERSIZED BITMAP DETECTED! source=$source, size=${width}x${height}, bytes=$bytes (${String.format("%.2f", mb)} MB), config=${bitmap.config}"
            )
        } else {
            Log.d(
                TAG,
                "source=$source, size=${width}x${height}, bytes=$bytes (${String.format("%.2f", mb)} MB), config=${bitmap.config}"
            )
        }
        return bitmap
    }
}
