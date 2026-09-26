package com.nexus.launcher.ui.immersive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.nexus.launcher.locale.LocaleDigitUtils

/** Battery bar, ring, and percentage — sized from the pill so the number has room inside. */
internal class ImmersiveStatusBatteryDraw(
    private val context: Context,
    private val density: Float,
    private val textPaint: Paint,
    private val captionPaint: Paint,
    private val shapePaint: Paint,
    private val strokePaint: Paint,
    private val scratch: RectF,
) {
    var iconSize: Float = 15f * density
    var insideMeter: Float = 18f * density
    var alertColor: Int = 0
    var captionColor: Int = 0
    private val clusterGap = 6f * density

    fun draw(
        canvas: Canvas,
        left: Float,
        cy: Float,
        baseline: Float,
        style: String,
        percentStyle: String,
        percent: Int,
        lowPercent: Int,
        charging: Boolean,
        pulse: Float,
    ): Float {
        val ink = textPaint.color
        if (percent in 1..lowPercent) {
            val low = alertColor
            shapePaint.color = low
            strokePaint.color = low
            textPaint.color = low
            captionPaint.color = low
        } else if (charging) {
            shapePaint.alpha = (255 * pulse).toInt().coerceIn(80, 255)
        }
        val edge = drawBody(canvas, left, cy, baseline, style, percentStyle, percent)
        shapePaint.color = ink
        strokePaint.color = ink
        textPaint.color = ink
        captionPaint.color = captionColor
        shapePaint.alpha = 255
        return edge
    }

    fun width(style: String, percentStyle: String, percent: Int): Float {
        val text = LocaleDigitUtils.formatPercent(percent, context)
        if (style == ImmersiveStatusStyle.BATTERY_TEXT) return captionPaint.measureText(text)
        val inside = percentStyle == ImmersiveStatusStyle.PERCENT_INSIDE
        var w = if (style == ImmersiveStatusStyle.BATTERY_RING) {
            if (inside) insideMeter else iconSize
        } else {
            val bodyW = if (inside) insideMeter * 1.55f else iconSize * 1.35f
            bodyW + 2.2f * density
        }
        if (percentStyle == ImmersiveStatusStyle.PERCENT_BESIDE) {
            w += clusterGap + captionPaint.measureText(text)
        }
        return w
    }

    private fun drawBody(
        canvas: Canvas,
        left: Float,
        cy: Float,
        baseline: Float,
        style: String,
        percentStyle: String,
        percent: Int,
    ): Float {
        val text = LocaleDigitUtils.formatPercent(percent, context)
        if (style == ImmersiveStatusStyle.BATTERY_TEXT) {
            canvas.drawText(text, left, baseline, captionPaint)
            return left + captionPaint.measureText(text)
        }
        val inside = percentStyle == ImmersiveStatusStyle.PERCENT_INSIDE
        var right = if (style == ImmersiveStatusStyle.BATTERY_RING) {
            drawRing(canvas, left, cy, percent, inside)
        } else {
            drawBar(canvas, left, cy, percent, inside)
        }
        if (percentStyle == ImmersiveStatusStyle.PERCENT_BESIDE) {
            val textLeft = right + clusterGap
            canvas.drawText(text, textLeft, baseline, captionPaint)
            right = textLeft + captionPaint.measureText(text)
        }
        return right
    }

    private fun drawBar(
        canvas: Canvas, left: Float, cy: Float, percent: Int, inside: Boolean,
    ): Float {
        val bodyW = if (inside) insideMeter * 1.55f else iconSize * 1.35f
        val bodyH = if (inside) (insideMeter * 0.58f).coerceAtMost(iconSize) else iconSize * 0.58f
        scratch.set(left, cy - bodyH / 2f, left + bodyW, cy + bodyH / 2f)
        canvas.drawRoundRect(scratch, 2.4f * density, 2.4f * density, strokePaint)
        val inset = 2.4f * density
        val fillW = (bodyW - inset * 2) * percent / 100f
        scratch.set(left + inset, cy - bodyH / 2f + inset, left + inset + fillW, cy + bodyH / 2f - inset)
        if (!inside) canvas.drawRect(scratch, shapePaint)
        scratch.set(left + bodyW, cy - bodyH / 5f, left + bodyW + 2.2f * density, cy + bodyH / 5f)
        canvas.drawRect(scratch, shapePaint)
        if (inside) {
            val pad = 4.5f * density
            drawInsidePercent(canvas, percent, left + pad, left + bodyW - pad, cy)
        }
        return left + bodyW + 2.2f * density
    }

    private fun drawRing(
        canvas: Canvas, left: Float, cy: Float, percent: Int, inside: Boolean,
    ): Float {
        val size = if (inside) insideMeter else iconSize
        val radius = size / 2f
        val inset = strokePaint.strokeWidth / 2f
        scratch.set(
            left + inset, cy - radius + inset,
            left + size - inset, cy + radius - inset,
        )
        strokePaint.alpha = 80
        canvas.drawArc(scratch, 0f, 360f, false, strokePaint)
        strokePaint.alpha = 255
        canvas.drawArc(scratch, -90f, 360f * percent / 100f, false, strokePaint)
        if (inside) {
            val gutter = inset + 2.2f * density
            drawInsidePercent(canvas, percent, left + gutter, left + size - gutter, cy)
        }
        return left + size
    }

    private fun drawInsidePercent(
        canvas: Canvas, percent: Int, left: Float, right: Float, cy: Float,
    ) {
        val hole = (right - left).coerceAtLeast(1f)
        val text = LocaleDigitUtils.formatPercent(percent, context)
        val full = textPaint.textSize
        var size = hole * 0.46f
        textPaint.textSize = size
        while (size > 8f * density && textPaint.measureText(text) > hole) {
            size -= 0.5f * density
            textPaint.textSize = size
        }
        val w = textPaint.measureText(text)
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, (left + right) / 2f - w / 2f, baseline, textPaint)
        textPaint.textSize = full
    }
}
