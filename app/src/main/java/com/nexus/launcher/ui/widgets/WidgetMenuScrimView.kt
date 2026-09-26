package com.nexus.launcher.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.view.View

class WidgetMenuScrimView(
    context: Context,
    private var widgetRect: Rect = Rect(),
    private val punchOut: Boolean = true
) : View(context) {

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Matches FolderWindowScrimView / the folder edit sheet's window dim (0.72) — the
        // lighter 0x80 (50%) this used before made widget sheets/menus read as noticeably
        // less frosted than folder equivalents at the same fill alpha.
        color = Color.parseColor("#B8000000")
        style = Paint.Style.FILL
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private var dimAlpha = 0f
    private var dimAnimator: ValueAnimator? = null
    private val density = resources.displayMetrics.density

    private val punchRect = RectF()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun animateDim(target: Float) {
        dimAnimator?.cancel()
        dimAnimator = ValueAnimator.ofFloat(dimAlpha, target).apply {
            duration = 200
            addUpdateListener { anim ->
                dimAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun updateWidgetBounds(newRect: Rect) {
        widgetRect = newRect
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animateDim(1f)
    }

    fun dismiss() {
        animateDim(0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dimAlpha > 0f) {
            dimPaint.color = Color.BLACK
            dimPaint.alpha = (dimAlpha * 180).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

            // Punch out the long-pressed widget only when requested
            if (punchOut && !widgetRect.isEmpty) {
                punchRect.set(widgetRect)
                val cornerRadius = 24f * density
                canvas.drawRoundRect(punchRect, cornerRadius, cornerRadius, clearPaint)
            }
        }
    }
}

