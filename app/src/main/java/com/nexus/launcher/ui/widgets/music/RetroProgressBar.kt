package com.nexus.launcher.ui.widgets.music

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint

/**
 * Where a retro music widget is in the track, drawn in each skin's own language.
 *
 * The amplifier and the cassette both had ways of *hinting* at progress — a VU meter's segments,
 * an odometer counter, the thickness of a spool — and every one of them reads as something else
 * first: level, a tape count, a reel. So neither told you how far through a song you were. These
 * are plain progress bars, dressed as hardware: a tuning scale on the amplifier's faceplate, a
 * length of tape across the cassette's shell. No outer ring — retro styles never draw one.
 */
object RetroProgressBar {

    /** Height the [drawTuning] band wants, including its text line. */
    const val TUNING_BAND_DP = 16f

    /** Height the [drawTape] band wants. */
    const val TAPE_BAND_DP = 8f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isSubpixelText = true
    }
    private val rect = RectF()

    /**
     * The amplifier's dial scale: an inset track with tick marks, an amber fill, and the elapsed
     * and total times at either end — the two strings the expanded faceplate was handed and
     * never drew.
     */
    fun drawTuning(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        dp: Float,
        progress: Float,
        posStr: String,
        durStr: String,
        colors: RetroMusicPalette.AmplifierColors,
    ) {
        if (right - left < 24f * dp) return
        val barH = 5f * dp
        val fraction = progress.coerceIn(0f, 1f)

        rect.set(left, top, right, top + barH)
        trackPaint.color = colors.displayBg
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, trackPaint)

        // Ten marks along the scale, like a tuning dial's.
        tickPaint.color = colors.amberDim
        tickPaint.strokeWidth = 1f * dp
        val step = (right - left) / 10f
        for (i in 1 until 10) {
            val x = left + step * i
            canvas.drawLine(x, top + barH * 0.15f, x, top + barH * 0.85f, tickPaint)
        }

        if (fraction > 0f) {
            rect.set(left, top, left + (right - left) * fraction, top + barH)
            fillPaint.color = colors.amberText
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, fillPaint)
        }
        headPaint.color = colors.chromeFace
        canvas.drawCircle(left + (right - left) * fraction, top + barH / 2f, barH * 0.72f, headPaint)

        if (posStr.isEmpty() && durStr.isEmpty()) return
        timePaint.color = colors.amberDim
        timePaint.textSize = (8f * dp).toInt().toFloat()
        val baseline = (top + barH + 9f * dp).toInt().toFloat()
        timePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(posStr, left, baseline, timePaint)
        timePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(durStr, right, baseline, timePaint)
    }

    /**
     * The cassette's tape: a dark channel with brown tape run into it as far as the track has
     * played, and a chrome capstan at the head.
     */
    fun drawTape(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        dp: Float,
        progress: Float,
        colors: RetroMusicPalette.CassetteColors,
    ) {
        if (right - left < 24f * dp) return
        val barH = 4f * dp
        val fraction = progress.coerceIn(0f, 1f)

        rect.set(left, top, right, top + barH)
        trackPaint.color = colors.spoolWellBg
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, trackPaint)

        if (fraction > 0f) {
            rect.set(left, top, left + (right - left) * fraction, top + barH)
            fillPaint.color = colors.stripeRed
            canvas.drawRoundRect(rect, barH / 2f, barH / 2f, fillPaint)
        }
        headPaint.color = colors.chromeBtn
        canvas.drawCircle(left + (right - left) * fraction, top + barH / 2f, barH * 0.85f, headPaint)
    }

    /** A hairline for sizes with no room for anything else. */
    fun drawSlim(canvas: Canvas, left: Float, right: Float, y: Float, dp: Float, progress: Float, trackColor: Int, fillColor: Int) {
        if (right - left < 16f * dp) return
        val barH = 2.5f * dp
        rect.set(left, y, right, y + barH)
        trackPaint.color = trackColor
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, trackPaint)
        val fraction = progress.coerceIn(0f, 1f)
        if (fraction <= 0f) return
        rect.set(left, y, left + (right - left) * fraction, y + barH)
        fillPaint.color = fillColor
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, fillPaint)
    }

    /** Around a dial or a reel, where a 1x1 widget has no room for a bar. */
    fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float, dp: Float, progress: Float, trackColor: Int, fillColor: Int) {
        val fraction = progress.coerceIn(0f, 1f)
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        arcPaint.strokeWidth = 2.5f * dp
        arcPaint.color = trackColor
        canvas.drawArc(rect, 0f, 360f, false, arcPaint)
        if (fraction <= 0f) return
        arcPaint.color = fillColor
        canvas.drawArc(rect, -90f, 360f * fraction, false, arcPaint)
    }
}
