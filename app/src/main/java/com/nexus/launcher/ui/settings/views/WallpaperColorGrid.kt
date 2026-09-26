package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class WallpaperColorGrid @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val swatches = listOf(
        "#0D1117", "#1A2332", "#0A1628", "#121212", "#1C1C1E", "#2C2C2E",
        "#1A1A2E", "#16213E", "#0F3460", "#533483", "#2D132C", "#1B262C",
        "#0D2137", "#071E22", "#1D3354", "#467599", "#E8C1A0", "#F7EDE2",
        "#FFECD2", "#FFD6A5", "#FDFFB6", "#CAFFBF", "#9BF6FF", "#A0C4FF",
        "#BDB2FF", "#FFC6FF", "#FFFFFC", "#EAE4E9", "#FFF1E6", "#F0EFEB"
    )

    private var selectedIndex = 0
    var onColorSelected: ((String) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#7EB8D4")
        strokeWidth = 3f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33FFFFFF")
        strokeWidth = 1f
    }

    private val dp = context.resources.displayMetrics.density
    private val cols = 6
    private val rows = 5
    private val circleRadius = 16f * dp
    private val spacing = 10f * dp

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = ((circleRadius * 2 + spacing) * cols - spacing + paddingLeft + paddingRight).toInt()
        val h = ((circleRadius * 2 + spacing) * rows - spacing + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val stepX = circleRadius * 2 + spacing
        val stepY = circleRadius * 2 + spacing
        swatches.forEachIndexed { i, hex ->
            val col = i % cols
            val row = i / cols
            val cx = paddingLeft + circleRadius + col * stepX
            val cy = paddingTop + circleRadius + row * stepY
            fillPaint.color = Color.parseColor(hex)
            canvas.drawCircle(cx, cy, circleRadius, fillPaint)
            canvas.drawCircle(cx, cy, circleRadius, borderPaint)
            if (i == selectedIndex) {
                canvas.drawCircle(cx, cy, circleRadius + 4 * dp, ringPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val stepX = circleRadius * 2 + spacing
            val stepY = circleRadius * 2 + spacing
            val col = ((event.x - paddingLeft) / stepX).toInt().coerceIn(0, cols - 1)
            val row = ((event.y - paddingTop) / stepY).toInt().coerceIn(0, rows - 1)
            val idx = (row * cols + col).coerceIn(0, swatches.size - 1)
            selectedIndex = idx
            invalidate()
            onColorSelected?.invoke(swatches[idx])
        }
        return true
    }

    fun setSelectedColor(hex: String) {
        val idx = swatches.indexOfFirst { it.equals(hex, ignoreCase = true) }
        selectedIndex = if (idx >= 0) idx else 0
        invalidate()
    }
}
