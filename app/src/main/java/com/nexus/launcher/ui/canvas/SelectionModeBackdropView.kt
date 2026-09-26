package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Full-screen theme/wallpaper layer visible in selection-mode gutters behind the shrunken workspace. */
class SelectionModeBackdropView(
    context: Context,
    private val canvasRenderer: CanvasRenderer
) : View(context) {

    private val dimPaint = Paint().apply {
        color = Color.parseColor("#33000000")
    }

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        canvasRenderer.draw(canvas, width, height)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
    }
}
