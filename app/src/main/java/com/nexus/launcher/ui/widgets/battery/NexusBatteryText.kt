package com.nexus.launcher.ui.widgets.battery

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

/**
 * Text fitting and the percentage mark shared by every battery style.
 *
 * ## Fit, don't guess
 *
 * The styles used to size text as a fraction of the widget's width or height and then place it
 * at a fixed fraction of the other axis — two independent guesses that were right at the size the
 * style was tuned for and wrong elsewhere. Radial Gauge showed "!00%": at around 100dp tall the
 * percentage's top reached into the ring, and the ring's stroke cut the "1". Every style now gives
 * each piece of text a box that nothing else occupies and [fit]s the text into it, so growing the
 * widget grows the text and shrinking it shrinks the text, and nothing overlaps at any size.
 *
 * ## The percentage
 *
 * [drawPercent] sets the digits at full weight and the "%" sign smaller on the same baseline — the
 * detail that makes a battery reading look designed rather than typed. Every style uses it, so the
 * number reads the same wherever it appears.
 *
 * Readings use tabular figures ("tnum"): every digit the same width, so the number does not shift
 * sideways as the charge changes and nudge whatever is laid out against it. Fonts without the
 * feature simply ignore it.
 */
internal object NexusBatteryText {

    /** Size of the "%" sign relative to the digits. */
    private const val SIGN_SCALE = 0.52f

    /** Gap between the digits and the sign, as a fraction of the digit size. */
    private const val SIGN_GAP = 0.04f

    private const val TABULAR = "tnum"

    private val bounds = Rect()
    private val signPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The largest text size, up to [maxPx], at which [text] fits [maxW] wide and whose ink is at
     * most [maxH] tall.
     *
     * [minPx] is the size a layout is *designed* not to go below — but when even that does not
     * fit, the box wins and the text shrinks further. This used to return [minPx] regardless, which
     * is how text ran into its neighbours on narrow widgets: the fitter picked a size the box could
     * not hold. Layouts that would rather drop or rearrange text than shrink it check [fits] or
     * compare against [minPx] themselves.
     */
    fun fit(paint: Paint, text: String, maxW: Float, maxH: Float, minPx: Float, maxPx: Float): Float {
        if (text.isEmpty() || maxW <= 0f || maxH <= 0f) return minPx
        val (w, h) = inkAt100(paint, text)
        val byWidth = if (w > 0f) maxW / w * 100f else maxPx
        val byHeight = if (h > 0f) maxH / h * 100f else maxPx
        return capped(minOf(byWidth, byHeight, maxPx), minPx)
    }

    /** Whether [text] at the paint's current size fits [maxW] × [maxH]. */
    fun fits(paint: Paint, text: String, maxW: Float, maxH: Float): Boolean {
        if (text.isEmpty()) return true
        paint.getTextBounds(text, 0, text.length, bounds)
        return paint.measureText(text) <= maxW + 0.5f && bounds.height() <= maxH + 0.5f
    }

    /** Fits a percentage reading ([digits] plus the smaller sign) into [maxW] × [maxH]. */
    fun fitPercent(paint: Paint, digits: String, maxW: Float, maxH: Float, minPx: Float, maxPx: Float): Float {
        paint.fontFeatureSettings = TABULAR
        val saved = paint.textSize
        paint.textSize = 100f
        val width = percentWidth(paint, digits)
        paint.getTextBounds(digits, 0, digits.length, bounds)
        val height = bounds.height().toFloat()
        paint.textSize = saved
        val byWidth = if (width > 0f) maxW / width * 100f else maxPx
        val byHeight = if (height > 0f) maxH / height * 100f else maxPx
        return capped(minOf(byWidth, byHeight, maxPx), minPx)
    }

    /** The fitted size, never above what fits; a hair of a floor so nothing measures zero. */
    private fun capped(fitted: Float, @Suppress("UNUSED_PARAMETER") minPx: Float): Float =
        fitted.coerceAtLeast(1f)

    /** Width of a percentage reading at the paint's current size. */
    fun percentWidth(paint: Paint, digits: String): Float {
        val size = paint.textSize
        val digitsW = paint.measureText(digits)
        signPaint.set(paint)
        signPaint.textSize = size * SIGN_SCALE
        return digitsW + size * SIGN_GAP + signPaint.measureText("%")
    }

    /**
     * Draws [digits] then a smaller "%" on the same baseline, positioned by [align] as a single
     * unit — so a centred reading is centred as a whole, not just its digits.
     */
    fun drawPercent(canvas: Canvas, digits: String, x: Float, baseline: Float, paint: Paint, align: Paint.Align) {
        paint.fontFeatureSettings = TABULAR
        val total = percentWidth(paint, digits)
        val start = when (align) {
            Paint.Align.LEFT -> x
            Paint.Align.CENTER -> x - total / 2f
            Paint.Align.RIGHT -> x - total
        }
        val savedAlign = paint.textAlign
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(digits, start, baseline, paint)
        paint.textAlign = savedAlign

        signPaint.set(paint)
        signPaint.textAlign = Paint.Align.LEFT
        signPaint.textSize = paint.textSize * SIGN_SCALE
        canvas.drawText("%", start + paint.measureText(digits) + paint.textSize * SIGN_GAP, baseline, signPaint)
    }

    /** Height of the digits' ink at the paint's current size — what a box must hold. */
    fun inkHeight(paint: Paint, text: String): Float {
        paint.getTextBounds(text, 0, text.length, bounds)
        return bounds.height().toFloat()
    }

    private fun inkAt100(paint: Paint, text: String): Pair<Float, Float> {
        val saved = paint.textSize
        paint.textSize = 100f
        val w = paint.measureText(text)
        paint.getTextBounds(text, 0, text.length, bounds)
        val h = bounds.height().toFloat()
        paint.textSize = saved
        return w to h
    }
}
