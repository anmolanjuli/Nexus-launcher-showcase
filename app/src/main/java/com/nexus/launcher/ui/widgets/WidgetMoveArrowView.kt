package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

class WidgetMoveArrowView(
    context: Context,
    private val widgetView: View,
    private val page: Int,
    private val pageWidth: Float,
    private val onNudge: (Float, Float) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    
    private val pillWidthH = 88f * density
    private val pillHeightH = 40f * density
    private val pillWidthV = 40f * density
    private val pillHeightV = 88f * density
    private val cornerRadius = 20f * density
    
    private val topPill = RectF()
    private val bottomPill = RectF()
    private val leftPill = RectF()
    private val rightPill = RectF()
    
    private val tokens = try {
        com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        com.nexus.launcher.theme.NexusColorTokens.Dark
    }
    
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tokens.surface
    }
    
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = tokens.divider
    }
    
    private val innerHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.5f * density
        color = tokens.divider
    }
    
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = tokens.divider
        maskFilter = BlurMaskFilter(12f * density, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = tokens.textPrimary
    }
    
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = tokens.divider
    }
    
    enum class ArrowDir { NONE, UP, DOWN, LEFT, RIGHT }
    private var pressedArrow = ArrowDir.NONE
    private var activeRect: RectF? = null
    private var pressScale = 1f
    private var scaleAnimator: android.animation.ValueAnimator? = null
    
    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        val lp = widgetView.layoutParams as android.widget.FrameLayout.LayoutParams
        val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
            lp.leftMargin.toFloat(), page, pageWidth
        ).toInt()
        android.util.Log.d(
            "MoveArrow",
            "Init: absLeft=${lp.leftMargin} screenLeft=$screenLeft top=${lp.topMargin}"
        )
        updateWidgetBounds(screenLeft, lp.topMargin, lp.width, lp.height)
    }

    fun updateWidgetBounds(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int) {
        val cx = newLeft + newWidth / 2f
        val cy = newTop + newHeight / 2f
        
        topPill.set(cx - pillWidthH/2, newTop - pillHeightH/2f, cx + pillWidthH/2, newTop + pillHeightH/2f)
        bottomPill.set(cx - pillWidthH/2, newTop + newHeight - pillHeightH/2f, cx + pillWidthH/2, newTop + newHeight + pillHeightH/2f)
        leftPill.set(newLeft - pillWidthV/2f, cy - pillHeightV/2, newLeft + pillWidthV/2f, cy + pillHeightV/2)
        rightPill.set(newLeft + newWidth - pillWidthV/2f, cy - pillHeightV/2, newLeft + newWidth + pillWidthV/2f, cy + pillHeightV/2)
        
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPill(canvas, topPill, ArrowDir.LEFT, ArrowDir.RIGHT, isHorizontal = true)
        drawPill(canvas, bottomPill, ArrowDir.LEFT, ArrowDir.RIGHT, isHorizontal = true)
        drawPill(canvas, leftPill, ArrowDir.UP, ArrowDir.DOWN, isHorizontal = false)
        drawPill(canvas, rightPill, ArrowDir.UP, ArrowDir.DOWN, isHorizontal = false)
    }
    
    private fun drawPill(canvas: Canvas, rect: RectF, dir1: ArrowDir, dir2: ArrowDir, isHorizontal: Boolean) {
        val isPressed1 = pressedArrow == dir1 && activeRect === rect
        val isPressed2 = pressedArrow == dir2 && activeRect === rect
        val pillPressed = isPressed1 || isPressed2
        
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, glowPaint)
        
        bgPaint.shader = null
        bgPaint.color = if (pillPressed) tokens.surfaceRaised else tokens.surface
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        
        val innerRect = RectF(rect.left + 0.5f*density, rect.top + 0.5f*density, rect.right - 0.5f*density, rect.bottom - 0.5f*density)
        canvas.drawRoundRect(innerRect, cornerRadius, cornerRadius, innerHighlightPaint)
        
        borderPaint.shader = null
        borderPaint.color = tokens.divider
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        
        val pad = 4f * density
        if (isHorizontal) {
            canvas.drawLine(rect.centerX(), rect.top + pad, rect.centerX(), rect.bottom - pad, dividerPaint)
        } else {
            canvas.drawLine(rect.left + pad, rect.centerY(), rect.right - pad, rect.centerY(), dividerPaint)
        }
        
        drawChevron(canvas, getArrowCenter(rect, 1, isHorizontal), dir1, if (isPressed1) pressScale else 1f, isPressed1)
        drawChevron(canvas, getArrowCenter(rect, 2, isHorizontal), dir2, if (isPressed2) pressScale else 1f, isPressed2)
    }
    
    private fun getArrowCenter(rect: RectF, index: Int, isHorizontal: Boolean): PointF {
        if (isHorizontal) {
            return if (index == 1) PointF(rect.left + rect.width()/4, rect.centerY())
                   else PointF(rect.right - rect.width()/4, rect.centerY())
        } else {
            return if (index == 1) PointF(rect.centerX(), rect.top + rect.height()/4)
                   else PointF(rect.centerX(), rect.bottom - rect.height()/4)
        }
    }
    
    private fun drawChevron(canvas: Canvas, center: PointF, dir: ArrowDir, scale: Float, isPressed: Boolean) {
        val path = Path()
        val s = 7f * density * scale
        val off = 3f * density * scale
        
        when (dir) {
            ArrowDir.LEFT -> {
                path.moveTo(center.x + off, center.y - s)
                path.lineTo(center.x - off, center.y)
                path.lineTo(center.x + off, center.y + s)
            }
            ArrowDir.RIGHT -> {
                path.moveTo(center.x - off, center.y - s)
                path.lineTo(center.x + off, center.y)
                path.lineTo(center.x - off, center.y + s)
            }
            ArrowDir.UP -> {
                path.moveTo(center.x - s, center.y + off)
                path.lineTo(center.x, center.y - off)
                path.lineTo(center.x + s, center.y + off)
            }
            ArrowDir.DOWN -> {
                path.moveTo(center.x - s, center.y - off)
                path.lineTo(center.x, center.y + off)
                path.lineTo(center.x + s, center.y - off)
            }
            else -> {}
        }
        
        arrowPaint.color = tokens.textPrimary
        canvas.drawPath(path, arrowPaint)
    }
    
    private fun hitTest(x: Float, y: Float): ArrowDir {
        if (topPill.contains(x, y)) return if (x < topPill.centerX()) ArrowDir.LEFT else ArrowDir.RIGHT
        if (bottomPill.contains(x, y)) return if (x < bottomPill.centerX()) ArrowDir.LEFT else ArrowDir.RIGHT
        if (leftPill.contains(x, y)) return if (y < leftPill.centerY()) ArrowDir.UP else ArrowDir.DOWN
        if (rightPill.contains(x, y)) return if (y < rightPill.centerY()) ArrowDir.UP else ArrowDir.DOWN
        return ArrowDir.NONE
    }
    
    private val repeatRunnable = object : Runnable {
        var count = 0
        override fun run() {
            fireNudge(pressedArrow)
            count++
            val nextDelay = max(20L, 80L - (count * 2L))
            postDelayed(this, nextDelay)
        }
    }
    
    private fun fireNudge(dir: ArrowDir) {
        if (dir == ArrowDir.NONE) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val d = 2f * density
        when (dir) {
            ArrowDir.LEFT -> onNudge(-d, 0f)
            ArrowDir.RIGHT -> onNudge(d, 0f)
            ArrowDir.UP -> onNudge(0f, -d)
            ArrowDir.DOWN -> onNudge(0f, d)
            else -> {}
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val hit = hitTest(event.x, event.y)
                if (hit == ArrowDir.NONE) return false
                
                pressedArrow = hit
                activeRect = when {
                    topPill.contains(event.x, event.y) -> topPill
                    bottomPill.contains(event.x, event.y) -> bottomPill
                    leftPill.contains(event.x, event.y) -> leftPill
                    else -> rightPill
                }
                
                animateScale(1f, 0.85f)
                fireNudge(hit)
                repeatRunnable.count = 0
                postDelayed(repeatRunnable, 400)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressedArrow != ArrowDir.NONE) {
                    removeCallbacks(repeatRunnable)
                    pressedArrow = ArrowDir.NONE
                    activeRect = null
                    animateScale(pressScale, 1f)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun animateScale(from: Float, to: Float) {
        scaleAnimator?.cancel()
        scaleAnimator = android.animation.ValueAnimator.ofFloat(from, to).apply {
            duration = 80
            if (to == 1f) interpolator = android.view.animation.OvershootInterpolator()
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
