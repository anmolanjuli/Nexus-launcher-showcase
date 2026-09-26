package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.text.TextPaint
import android.text.TextUtils

/**
 * Single source for app-icon label wrapping (home, drawer, previews).
 * Two-line mode wraps leftover text onto a second line and ellipsizes that line.
 */
object IconLabelText {
    data class Lines(val line1: String, val line2: String?)

    fun bandHeightPx(
        showLabels: Boolean,
        twoLine: Boolean,
        labelSizePx: Float,
        labelGapPx: Float
    ): Float {
        if (!showLabels) return 0f
        val lines = if (twoLine) 2 else 1
        return labelGapPx + labelSizePx * lines
    }

    fun lineStepPx(labelSizePx: Float): Float = labelSizePx

    fun wrap(text: String, paint: TextPaint, maxWidth: Float, twoLine: Boolean): Lines {
        val width = maxWidth.coerceAtLeast(1f)
        if (text.isEmpty()) return Lines("", null)
        if (!twoLine || paint.measureText(text) <= width) {
            return Lines(
                TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString(),
                null
            )
        }
        if (paint.measureText(text, 0, 1) > width) {
            return Lines(
                TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString(),
                null
            )
        }
        val breakAt = findBreakIndex(text, paint, width)
        val line1 = text.substring(0, breakAt).trimEnd()
        val rest = text.substring(breakAt).trimStart()
        if (rest.isEmpty()) return Lines(line1, null)
        val line2 = TextUtils.ellipsize(rest, paint, width, TextUtils.TruncateAt.END).toString()
        return Lines(line1, line2)
    }

    fun drawCentered(
        canvas: Canvas,
        paint: TextPaint,
        centerX: Float,
        firstBaseline: Float,
        line1: String?,
        line2: String?,
        lineStepPx: Float
    ) {
        if (line1.isNullOrEmpty()) return
        canvas.drawText(line1, centerX, firstBaseline, paint)
        if (!line2.isNullOrEmpty()) {
            canvas.drawText(line2, centerX, firstBaseline + lineStepPx, paint)
        }
    }

    fun drawStart(
        canvas: Canvas,
        paint: TextPaint,
        x: Float,
        firstBaseline: Float,
        line1: String,
        line2: String?,
        lineStepPx: Float
    ) {
        canvas.drawText(line1, x, firstBaseline, paint)
        if (!line2.isNullOrEmpty()) {
            canvas.drawText(line2, x, firstBaseline + lineStepPx, paint)
        }
    }

    private fun findBreakIndex(text: String, paint: TextPaint, maxWidth: Float): Int {
        var lo = 1
        var hi = text.length
        var fit = 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (paint.measureText(text, 0, mid) <= maxWidth) {
                fit = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val space = text.lastIndexOf(' ', fit)
        return if (space >= 1) space else fit
    }
}
