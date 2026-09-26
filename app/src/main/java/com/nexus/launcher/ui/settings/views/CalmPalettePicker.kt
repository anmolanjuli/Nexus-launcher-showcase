package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.theme.CalmPalette
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Swatch picker for [CalmPalette]: every palette on one row, sized to the width it is given.
 *
 * The original row used a fixed 40dp per swatch, which fitted five palettes and overflowed at
 * eleven. Rather than scroll or wrap, the row now takes its parent's width and divides it,
 * shrinking the swatches to whatever fits — so every palette stays visible and tappable on any
 * screen and the picker never measures wider than the space it has. The radius is capped so a
 * short list does not blow the swatches up to absurd sizes.
 *
 * Swatches are deliberately unlabelled. Names under each dot cost more height than the row
 * itself; the selected palette's name already appears in the "Calm Palette" row label, which is
 * where a single current value belongs.
 */
class CalmPalettePicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val palettes = CalmPalette.entries
    private var selectedIndex = 0
    var onPaletteSelected: ((CalmPalette) -> Unit)? = null

    private val dp = context.resources.displayMetrics.density

    /** Upper bound on swatch size, so a short palette list does not produce huge circles. */
    private val maxCircleRadius = 15f * dp
    private val ringGap = 3f * dp
    private val minCellPadding = 3f * dp

    /** Both are recomputed from the measured width; these defaults keep the view sensible before
     *  it has ever been measured. */
    private var cellWidth = (maxCircleRadius + ringGap + minCellPadding) * 2
    private var circleRadius = maxCircleRadius

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swatchBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
    }

    private var shaders: Array<Shader?> = arrayOfNulls(palettes.size)

    private fun centreX(index: Int) = paddingLeft + cellWidth * index + cellWidth / 2f
    private fun centreY() = paddingTop + circleRadius + ringGap

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val fallback = (maxCircleRadius + ringGap + minCellPadding) * 2 * palettes.size
        val usable = if (available > 0) available.toFloat() else fallback

        cellWidth = usable / palettes.size
        circleRadius = (cellWidth / 2f - ringGap - minCellPadding).coerceIn(1f, maxCircleRadius)

        val width = (usable + paddingLeft + paddingRight).toInt()
        val height = ((circleRadius + ringGap) * 2 + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cy = centreY()
        shaders = Array(palettes.size) { i ->
            val cx = centreX(i)
            LinearGradient(
                cx, cy - circleRadius,
                cx, cy + circleRadius,
                palettes[i].previewTopColor,
                palettes[i].previewBottomColor,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        ringPaint.color = tokens.textPrimary
        swatchBorderPaint.color = (tokens.textPrimary and 0x00FFFFFF) or (0x2A shl 24)

        val cy = centreY()
        for (i in palettes.indices) {
            val cx = centreX(i)

            shaders.getOrNull(i)?.let { fillPaint.shader = it }
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)
            fillPaint.shader = null
            canvas.drawCircle(cx, cy, circleRadius, swatchBorderPaint)

            if (i == selectedIndex) {
                canvas.drawCircle(cx, cy, circleRadius + ringGap, ringPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val idx = ((event.x - paddingLeft) / cellWidth).toInt()
                .coerceIn(0, palettes.size - 1)
            if (selectedIndex != idx) {
                selectedIndex = idx
                performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                invalidate()
                onPaletteSelected?.invoke(palettes[idx])
            }
        }
        return true
    }

    fun setSelectedPalette(palette: CalmPalette) {
        val idx = palettes.indexOf(palette)
        selectedIndex = if (idx >= 0) idx else 0
        invalidate()
    }
}
