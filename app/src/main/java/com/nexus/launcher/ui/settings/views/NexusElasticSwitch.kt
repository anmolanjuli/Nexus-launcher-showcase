package com.nexus.launcher.ui.settings.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.OvershootInterpolator
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Custom elastic pill toggle switch matching modern minimal design system.
 * Features an elastic spring glide animation, rounded capsule track with outline ring, and live theme reactivity.
 */
class NexusElasticSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Paper styling, for the copy of this view that lives inside the Feed's E-Ink Paper Mode.
     *
     * Off by default and set only by the Feed. This used to read E-Ink Paper Mode straight from the
     * global switch, which meant turning on paper for the news feed also squared off and repainted
     * Settings, the context menus and the drawer — every screen built from these shared views. The
     * mode belongs to the Feed and its readers; a view cannot tell where it is, so the Feed says so.
     */
    var paperMode: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }


    private val density = resources.displayMetrics.density

    var isChecked: Boolean = false
        private set

    var onCheckedChange: ((Boolean) -> Unit)? = null

    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var thumbProgress: Float = 0f
    private var animator: ValueAnimator? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val trackRect = RectF()
    private val thumbRect = RectF()

    // Neumorphism only: the knob's soft shadow and highlight. Blur filters are cached by radius.
    private val knobShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val knobHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val knobBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val onTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var shadowBlur: android.graphics.BlurMaskFilter? = null
    private var highlightBlur: android.graphics.BlurMaskFilter? = null

    init {
        isClickable = true
        isFocusable = true
        try {
            currentTokens = if (paperMode) {
                com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(context)
            } else {
                ThemeObserver.currentTokens(context)
            }
        } catch (_: Exception) {}

        setOnClickListener {
            toggle()
        }
    }

    fun setChecked(checked: Boolean, animate: Boolean = true) {
        if (isChecked == checked && animator == null) return
        isChecked = checked
        val target = if (checked) 1f else 0f

        animator?.cancel()
        if (animate && isAttachedToWindow) {
            animator = ValueAnimator.ofFloat(thumbProgress, target).apply {
                duration = 220
                interpolator = OvershootInterpolator(1.35f)
                addUpdateListener {
                    thumbProgress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            thumbProgress = target
            invalidate()
        }
    }

    fun toggle(animate: Boolean = true) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        setChecked(!isChecked, animate)
        onCheckedChange?.invoke(isChecked)
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredW = (54 * density).toInt()
        val desiredH = (30 * density).toInt()
        val w = resolveSize(desiredW, widthMeasureSpec)
        val h = resolveSize(desiredH, heightMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
            drawNeumorphic(canvas)
            return
        }
        val isEInk = paperMode
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val strokeW = if (isEInk) 1f * density else 1.6f * density
        val halfStroke = strokeW / 2f
        val cornerRadius = if (isEInk) 0f else viewH / 2f

        trackRect.set(halfStroke, halfStroke, viewW - halfStroke, viewH - halfStroke)

        val progress = thumbProgress.coerceIn(0f, 1f)

        // Track fill & border
        val offBorder = (currentTokens.textSecondary and 0x00FFFFFF) or (0x4D shl 24)
        val onBorder = currentTokens.textPrimary
        val borderColor = if (isEInk) currentTokens.divider else androidx.core.graphics.ColorUtils.blendARGB(offBorder, onBorder, progress)

        trackPaint.color = currentTokens.surfaceRaised
        borderPaint.color = borderColor
        borderPaint.strokeWidth = strokeW

        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, borderPaint)

        // Thumb calculations
        val pad = 3.5f * density
        val thumbDiameter = viewH - (pad * 2f)
        val minX = pad
        val maxX = viewW - pad - thumbDiameter
        val thumbX = minX + (maxX - minX) * thumbProgress
        val thumbY = pad

        thumbRect.set(thumbX, thumbY, thumbX + thumbDiameter, thumbY + thumbDiameter)

        val offThumb = currentTokens.textSecondary
        val onThumb = currentTokens.textPrimary
        val thumbColor = if (isEInk) currentTokens.textPrimary else androidx.core.graphics.ColorUtils.blendARGB(offThumb, onThumb, progress)

        thumbPaint.color = thumbColor
        val thumbRadius = if (isEInk) 0f else thumbDiameter / 2f
        canvas.drawRoundRect(thumbRect, thumbRadius, thumbRadius, thumbPaint)
    }

    /**
     * The Neumorphism switch: a sunken track with a raised knob sliding along it — the form every
     * neumorphic toggle takes, and the one that matches the raised cards and buttons around it.
     * ON is a tint inside the track in the theme's ink, not the accent, as on the widgets.
     *
     * The knob uses a scaled-down version of [com.nexus.launcher.ui.widgets.NexusNeumorphicDraw]'s
     * raised recipe rather than the recipe itself: that shadow is about 10dp, larger than the
     * knob, and would spill past this view. This one stays inside the switch's own padding.
     */
    private fun drawNeumorphic(canvas: Canvas) {
        val palette = com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(currentTokens)
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val progress = thumbProgress.coerceIn(0f, 1f)

        trackRect.set(0f, 0f, viewW, viewH)
        com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawDebossedWell(canvas, trackRect, viewH / 2f, palette, density)
        if (progress > 0f) {
            onTintPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(
                palette.textPrimary, (progress * (if (palette.isLight) 60 else 90)).toInt(),
            )
            canvas.drawRoundRect(trackRect, viewH / 2f, viewH / 2f, onTintPaint)
        }

        val pad = 3.5f * density
        val d = viewH - pad * 2f
        val x = pad + (viewW - pad * 2f - d) * thumbProgress
        thumbRect.set(x, pad, x + d, pad + d)
        val r = d / 2f
        val cx = thumbRect.centerX()
        val cy = thumbRect.centerY()

        val shadowR = 2.6f * density
        if (shadowBlur == null) {
            shadowBlur = android.graphics.BlurMaskFilter(shadowR, android.graphics.BlurMaskFilter.Blur.NORMAL)
            highlightBlur = android.graphics.BlurMaskFilter(2f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        knobShadowPaint.maskFilter = shadowBlur
        knobShadowPaint.color = palette.shadow
        knobShadowPaint.alpha = if (palette.isLight) 60 else 150
        canvas.drawCircle(cx + 0.6f * density, cy + 1.4f * density, r, knobShadowPaint)
        if (palette.isLight) {
            knobHighlightPaint.maskFilter = highlightBlur
            knobHighlightPaint.color = palette.highlight
            knobHighlightPaint.alpha = 210
            canvas.drawCircle(cx - 0.9f * density, cy - 0.9f * density, r, knobHighlightPaint)
        }

        thumbPaint.color = palette.surfaceLight
        canvas.drawCircle(cx, cy, r, thumbPaint)
        knobBevelPaint.strokeWidth = 1f * density
        knobBevelPaint.color = if (palette.isLight) {
            android.graphics.Color.argb(30, 0, 0, 0)
        } else {
            android.graphics.Color.argb(46, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, r - 0.5f * density, knobBevelPaint)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = isChecked
    }
}
