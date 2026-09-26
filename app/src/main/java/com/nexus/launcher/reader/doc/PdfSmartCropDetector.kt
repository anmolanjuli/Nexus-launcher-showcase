package com.nexus.launcher.reader.doc

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans page pixels to detect the content bounding box (trimming empty margins)
 * and crops the bitmap to expand content up to 1.8x.
 * Falls back to full page if content area is < 5% of total page area.
 */
object PdfSmartCropDetector {

    private const val RGB_TOLERANCE_SQ = 15 * 15 // RGB Euclidean distance >= 15
    private const val MIN_CONTENT_AREA_RATIO = 0.05f
    private const val MAX_EXPANSION_RATIO = 1.8f
    private const val DOWNSAMPLE_FACTOR = 4

    suspend fun cropBitmap(source: Bitmap, dp: Float): Bitmap = withContext(Dispatchers.IO) {
        val bounds = detectContentBounds(source, dp) ?: return@withContext source
        try {
            Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width(), bounds.height())
        } catch (_: Exception) {
            source
        }
    }

    suspend fun detectContentBounds(source: Bitmap, dp: Float): Rect? = withContext(Dispatchers.IO) {
        if (source.width <= 10 || source.height <= 10) return@withContext null

        val dw = (source.width / DOWNSAMPLE_FACTOR).coerceAtLeast(1)
        val dh = (source.height / DOWNSAMPLE_FACTOR).coerceAtLeast(1)

        val downscaled = try {
            Bitmap.createScaledBitmap(source, dw, dh, true)
        } catch (_: Exception) {
            return@withContext null
        }

        val pixels = IntArray(dw * dh)
        try {
            downscaled.getPixels(pixels, 0, dw, 0, 0, dw, dh)
        } catch (_: Exception) {
            downscaled.recycle()
            return@withContext null
        }
        downscaled.recycle()

        // 1. Determine dominant edge/corner background color
        val c1 = pixels[0]
        val c2 = pixels[dw - 1]
        val c3 = pixels[(dh - 1) * dw]
        val c4 = pixels[(dh - 1) * dw + (dw - 1)]

        val bgR = (Color.red(c1) + Color.red(c2) + Color.red(c3) + Color.red(c4)) / 4
        val bgG = (Color.green(c1) + Color.green(c2) + Color.green(c3) + Color.green(c4)) / 4
        val bgB = (Color.blue(c1) + Color.blue(c2) + Color.blue(c3) + Color.blue(c4)) / 4

        // 2. Scan from each edge for non-background pixels
        var topY = -1
        for (y in 0 until dh) {
            val rowOffset = y * dw
            for (x in 0 until dw) {
                if (isContentPixel(pixels[rowOffset + x], bgR, bgG, bgB)) {
                    topY = y
                    break
                }
            }
            if (topY != -1) break
        }

        var bottomY = -1
        for (y in dh - 1 downTo 0) {
            val rowOffset = y * dw
            for (x in 0 until dw) {
                if (isContentPixel(pixels[rowOffset + x], bgR, bgG, bgB)) {
                    bottomY = y
                    break
                }
            }
            if (bottomY != -1) break
        }

        var leftX = -1
        for (x in 0 until dw) {
            for (y in 0 until dh) {
                if (isContentPixel(pixels[y * dw + x], bgR, bgG, bgB)) {
                    leftX = x
                    break
                }
            }
            if (leftX != -1) break
        }

        var rightX = -1
        for (x in dw - 1 downTo 0) {
            for (y in 0 until dh) {
                if (isContentPixel(pixels[y * dw + x], bgR, bgG, bgB)) {
                    rightX = x
                    break
                }
            }
            if (rightX != -1) break
        }

        // Check if content was detected
        if (topY == -1 || bottomY == -1 || leftX == -1 || rightX == -1 || leftX >= rightX || topY >= bottomY) {
            return@withContext null
        }

        // 3. Map back to original dimensions
        val scaleX = source.width.toFloat() / dw
        val scaleY = source.height.toFloat() / dh

        var origLeft = (leftX * scaleX).toInt()
        var origTop = (topY * scaleY).toInt()
        var origRight = ((rightX + 1) * scaleX).toInt().coerceAtMost(source.width)
        var origBottom = ((bottomY + 1) * scaleY).toInt().coerceAtMost(source.height)

        // 4. Check 5% area threshold
        val contentArea = (origRight - origLeft).toLong() * (origBottom - origTop).toLong()
        val totalArea = source.width.toLong() * source.height.toLong()
        if (contentArea < totalArea * MIN_CONTENT_AREA_RATIO) {
            return@withContext null
        }

        // 5. Add small breathing margin (4dp)
        val padPx = (4 * dp).toInt()
        origLeft = (origLeft - padPx).coerceAtLeast(0)
        origTop = (origTop - padPx).coerceAtLeast(0)
        origRight = (origRight + padPx).coerceAtMost(source.width)
        origBottom = (origBottom + padPx).coerceAtMost(source.height)

        // 6. Enforce maximum expansion ratio up to 1.8x
        val minCropW = (source.width / MAX_EXPANSION_RATIO).toInt()
        val minCropH = (source.height / MAX_EXPANSION_RATIO).toInt()
        val curW = origRight - origLeft
        val curH = origBottom - origTop

        if (curW < minCropW) {
            val delta = minCropW - curW
            origLeft = (origLeft - delta / 2).coerceAtLeast(0)
            origRight = (origLeft + minCropW).coerceAtMost(source.width)
            if (origRight == source.width) {
                origLeft = (source.width - minCropW).coerceAtLeast(0)
            }
        }
        if (curH < minCropH) {
            val delta = minCropH - curH
            origTop = (origTop - delta / 2).coerceAtLeast(0)
            origBottom = (origTop + minCropH).coerceAtMost(source.height)
            if (origBottom == source.height) {
                origTop = (source.height - minCropH).coerceAtLeast(0)
            }
        }

        Rect(origLeft, origTop, origRight, origBottom)
    }

    private fun isContentPixel(pixel: Int, bgR: Int, bgG: Int, bgB: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val distSq = (r - bgR) * (r - bgR) + (g - bgG) * (g - bgG) + (b - bgB) * (b - bgB)
        return distSq >= RGB_TOLERANCE_SQ
    }
}
