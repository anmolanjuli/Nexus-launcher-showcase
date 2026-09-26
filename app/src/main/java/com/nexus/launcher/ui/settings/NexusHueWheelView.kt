package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class NexusHueWheelView(
    context: Context,
    private val density: Float,
    initialHue: Float,
    private val onHueChanged: (Float) -> Unit
) : View(context) {

    private var currentHue = initialHue

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f * density
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(4f * density, 0f, 2f * density, Color.parseColor("#80000000"))
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, thumbPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (200 * density).toInt()
        setMeasuredDimension(size, size)
        val colors = IntArray(360)
        for (i in 0 until 360) {
            colors[i] = Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
        }
        ringPaint.shader = SweepGradient(size / 2f, size / 2f, colors, null)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = cx - ringPaint.strokeWidth / 2f - (8f * density)

        canvas.drawCircle(cx, cy, radius, ringPaint)

        val angleRad = Math.toRadians(currentHue.toDouble())
        val tx = cx + radius * cos(angleRad).toFloat()
        val ty = cy + radius * sin(angleRad).toFloat()

        // Draw thumb
        canvas.drawCircle(tx, ty, 14f * density, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val cx = width / 2f
                val cy = height / 2f
                var angle = Math.toDegrees(atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())).toFloat()
                if (angle < 0) angle += 360f
                currentHue = angle
                invalidate()
                onHueChanged(currentHue)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
