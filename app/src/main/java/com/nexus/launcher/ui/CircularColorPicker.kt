package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** Circular hue indicator button that opens the expanded color picker on click. */
class CircularColorPicker(context: Context, private val density: Float) : View(context) {
    private var currentHue = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * density
    }

    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(2f * density, 0f, 1f * density, Color.parseColor("#80000000"))
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, thumbPaint)
        val colors = IntArray(360)
        for (i in 0 until 360) {
            colors[i] = Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f))
        }
        ringPaint.shader = SweepGradient(16f * density, 16f * density, colors, null)
    }

    fun setHue(hue: Float) {
        currentHue = hue
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = (32 * density).toInt()
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
        val radius = cx - ringPaint.strokeWidth

        canvas.drawCircle(cx, cy, radius, ringPaint)

        val angleRad = Math.toRadians(currentHue.toDouble())
        val tx = cx + radius * cos(angleRad).toFloat()
        val ty = cy + radius * sin(angleRad).toFloat()

        canvas.drawCircle(tx, ty, 6f * density, thumbPaint)
    }
}
