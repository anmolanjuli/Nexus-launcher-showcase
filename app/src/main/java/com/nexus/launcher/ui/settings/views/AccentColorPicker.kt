package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * The accent swatches, and a last one for any colour the user wants.
 *
 * ## Why these colours
 *
 * The accent has to sit on a white paper background, a near-black AMOLED one and every Calm
 * palette in between, and it is used as a fill as often as it is used for a line of text. The
 * earlier set was Material's primary ramp — saturated sky blue, hot pink, bright amber — which
 * glared against the muted surfaces everywhere else in the launcher and, in the Feed, turned
 * category pills and links into the loudest thing on a page of reading.
 *
 * These sit around the middle of the lightness range and well below full saturation, so each one
 * holds its contrast both on paper and in the dark instead of being legible in one and shouting in
 * the other.
 */
class AccentColorPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val colors = PRESETS.map { it.first }

    private var selectedIndex = 0
    private var customColor: Int? = null

    var onColorSelected: ((String) -> Unit)? = null

    /** Tapping the last swatch asks for a colour; the current accent comes back as the start point. */
    var onCustomRequested: ((currentHex: String) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }
        color = tokens.textPrimary
        strokeWidth = 3f
    }

    private val dp = context.resources.displayMetrics.density
    private val circleRadius = 18f * dp
    private val spacing = 12f * dp

    /** One more than the presets: the custom swatch sits at the end. */
    private val swatchCount = colors.size + 1

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = ((circleRadius * 2 + spacing) * swatchCount - spacing + paddingLeft + paddingRight + (4 * dp)).toInt()
        val height = (circleRadius * 2 + 8 * dp + paddingTop + paddingBottom + (4 * dp)).toInt()
        setMeasuredDimension(totalWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }
        ringPaint.color = tokens.textPrimary
        val step = circleRadius * 2 + spacing
        for (i in 0 until swatchCount) {
            val cx = paddingLeft + circleRadius + (4 * dp) + i * step
            val cy = paddingTop + circleRadius + 4 * dp
            if (i < colors.size) {
                fillPaint.color = Color.parseColor(colors[i])
                canvas.drawCircle(cx, cy, circleRadius, fillPaint)
            } else {
                drawCustomSwatch(canvas, cx, cy)
            }
            if (i == selectedIndex) {
                canvas.drawCircle(cx, cy, circleRadius + 4 * dp, ringPaint)
            }
        }
    }

    /** The chosen custom colour, or a hue wheel when there is none yet. */
    private fun drawCustomSwatch(canvas: Canvas, cx: Float, cy: Float) {
        val picked = customColor
        if (picked != null) {
            fillPaint.color = picked
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)
            return
        }
        if (wheelPaint.shader == null) {
            val hues = IntArray(HUE_STEPS) { Color.HSVToColor(floatArrayOf(it * (360f / HUE_STEPS), 0.45f, 0.78f)) }
            wheelPaint.shader = SweepGradient(cx, cy, hues, null)
        }
        canvas.drawCircle(cx, cy, circleRadius, wheelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val step = circleRadius * 2 + spacing
            val idx = ((event.x - paddingLeft) / step).toInt().coerceIn(0, swatchCount - 1)
            performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            if (idx == colors.size) {
                // The custom swatch always opens the picker, selected or not: tapping it again is
                // how someone adjusts the colour they already chose.
                onCustomRequested?.invoke(currentHex())
            } else if (selectedIndex != idx) {
                selectedIndex = idx
                invalidate()
                onColorSelected?.invoke(colors[idx])
            }
        }
        return true
    }

    private fun currentHex(): String =
        if (selectedIndex < colors.size) colors[selectedIndex]
        else customColor?.let { String.format("#%06X", it and 0xFFFFFF) } ?: colors.first()

    fun setSelectedColor(hex: String) {
        val idx = colors.indexOfFirst { it.equals(hex, ignoreCase = true) }
        if (idx >= 0) {
            selectedIndex = idx
        } else {
            // Anything not in the row is the custom swatch, which then shows that colour.
            customColor = try { Color.parseColor(hex) } catch (_: Exception) { null }
            selectedIndex = if (customColor != null) colors.size else 0
        }
        invalidate()
    }

    companion object {
        private const val HUE_STEPS = 12

        /** Hex and name, in the order they appear. [DEFAULT_ACCENT] leads. */
        val PRESETS = listOf(
            "#4F8DA6" to "Harbor",
            "#7EB8D4" to "Mist",
            "#6FA083" to "Sage",
            "#C1795E" to "Clay",
            "#C2A063" to "Sand",
            "#C0788A" to "Rose",
            "#8E86C0" to "Lilac",
            "#8A93A3" to "Slate",
        )

        /** The launcher's own accent when the user has not chosen one. */
        const val DEFAULT_ACCENT = "#4F8DA6"

        /** The preset's name in the current language, or null for a colour the user mixed. */
        fun labelFor(context: android.content.Context, hex: String): String? {
            val name = PRESETS.firstOrNull { it.first.equals(hex, ignoreCase = true) }?.second ?: return null
            val res = when (name) {
                "Harbor" -> com.nexus.launcher.R.string.accent_preset_harbor
                "Mist" -> com.nexus.launcher.R.string.accent_preset_mist
                "Sage" -> com.nexus.launcher.R.string.accent_preset_sage
                "Clay" -> com.nexus.launcher.R.string.accent_preset_clay
                "Sand" -> com.nexus.launcher.R.string.accent_preset_sand
                "Rose" -> com.nexus.launcher.R.string.accent_preset_rose
                "Lilac" -> com.nexus.launcher.R.string.accent_preset_lilac
                else -> com.nexus.launcher.R.string.accent_preset_slate
            }
            return context.getString(res)
        }
    }
}
