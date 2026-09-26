package com.nexus.launcher.ui.settings

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat

object IconPackBrowseBitmapLoader {

    fun loadDrawable(context: Context, res: Resources, resId: Int, targetSize: Int): Drawable {
        var bitmap: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(res, resId, options)

            if (options.outWidth > 0 && options.outHeight > 0) {
                var sampleSize = 1
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / sampleSize >= targetSize && halfWidth / sampleSize >= targetSize) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                bitmap = BitmapFactory.decodeResource(res, resId, decodeOpts)
            }
        } catch (_: Exception) {}

        if (bitmap != null) {
            return BitmapDrawable(context.resources, bitmap)
        }
        return try {
            ResourcesCompat.getDrawable(res, resId, null) ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
        } catch (_: Exception) {
            ColorDrawable(android.graphics.Color.TRANSPARENT)
        }
    }
}
