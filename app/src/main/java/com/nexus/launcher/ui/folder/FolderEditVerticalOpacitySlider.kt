package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import kotlin.math.roundToInt

/**
 * Vertical opacity control for folder edit live previews.
 * Uses the same token contrast as [com.nexus.launcher.ui.settings.views.NexusSliderRow].
 */
class FolderEditVerticalOpacitySlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onValueChanged: ((Int) -> Unit)? = null
    private var sliderLabel: String = ""

    fun setLabel(text: String) {
        sliderLabel = text
        contentDescription = text
    }

    fun getLabel(): String = sliderLabel

    private val dp = resources.displayMetrics.density
    private var tokens: NexusColorTokens = ThemeObserver.currentTokens(context)
    private var valuePct = 82
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val trackRect = RectF()

    fun applyTokens(newTokens: NexusColorTokens) {
        tokens = newTokens
        invalidate()
    }

    @Deprecated("Use applyTokens — sliders follow theme contrast, not accent.")
    fun applyAccentColor(@Suppress("UNUSED_PARAMETER") color: Int) = Unit

    fun configure(initialPct: Int) {
        valuePct = initialPct.coerceIn(0, 100)
        invalidate()
    }

    fun value(): Int = valuePct

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = (32 * dp).toInt()
        val minH = (60 * dp).toInt()
        val h = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(minH)
            else -> minH
        }
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 8f * dp
        val trackW = 8f * dp
        val thumbR = 8f * dp
        val cx = width / 2f
        trackRect.set(cx - trackW / 2f, inset, cx + trackW / 2f, height - inset)

        trackPaint.color = tokens.surfaceRaised
        canvas.drawRoundRect(trackRect, trackW, trackW, trackPaint)

        val t = valuePct / 100f
        val thumbY = trackRect.bottom - t * trackRect.height()
        fillPaint.color = tokens.textPrimary
        canvas.drawRoundRect(
            trackRect.left,
            thumbY,
            trackRect.right,
            trackRect.bottom,
            trackW,
            trackW,
            fillPaint
        )

        thumbPaint.color = tokens.textPrimary
        canvas.drawCircle(cx, thumbY, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val inset = 10f * dp
                val usable = (height - inset * 2f).coerceAtLeast(1f)
                val y = event.y.coerceIn(inset, height - inset)
                val t = 1f - (y - inset) / usable
                val next = (t * 100f).roundToInt().coerceIn(0, 100)
                if (next != valuePct) {
                    valuePct = next
                    invalidate()
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    onValueChanged?.invoke(valuePct)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
