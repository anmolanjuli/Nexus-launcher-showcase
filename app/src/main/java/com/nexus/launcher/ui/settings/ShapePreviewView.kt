package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.icons.IconShapePaths

/** Mini preview using the same paths as [com.nexus.launcher.ui.icons.IconShapeMasker]. */
class ShapePreviewView(
    context: Context,
    private val shapeType: Int,
    private val density: Float
) : View(context) {

    private var selected = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.1f * density
    }

    init {
        val defaultColor = try {
            ThemeObserver.currentTokens(context).textPrimary
        } catch (_: Exception) {
            Color.WHITE
        }
        applyColors(defaultColor)
    }

    fun setSelectedBorder(selected: Boolean, color: Int) {
        this.selected = selected
        applyColors(color)
        invalidate()
    }

    private fun applyColors(color: Int) {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        fillPaint.color = Color.argb(if (selected) 0x30 else 0x18, r, g, b)
        strokePaint.color = Color.argb(if (selected) 0xCC else 0x66, r, g, b)
        strokePaint.strokeWidth = if (selected) 1.4f * density else 1f * density
        edgePaint.color = Color.argb(if (selected) 0x55 else 0x40, r, g, b)
    }

    override fun onDraw(canvas: Canvas) {
        val pad = 3.5f * density
        val bounds = RectF(pad, pad, width - pad, height - pad)
        if (shapeType == IconShapeTileRow.FOLLOW_GLOBAL) {
            val outer = bounds.width() * 0.22f
            val inset = 5f * density
            val inner = RectF(
                bounds.left + inset, bounds.top + inset,
                bounds.right - inset, bounds.bottom - inset
            )
            canvas.drawRoundRect(bounds, outer, outer, strokePaint)
            canvas.drawRoundRect(inner, outer * 0.7f, outer * 0.7f, strokePaint)
            return
        }
        if (shapeType == -1) {
            val rr = bounds.width() * 0.22f
            canvas.drawRoundRect(bounds, rr, rr, fillPaint)
            canvas.drawRoundRect(bounds, rr, rr, strokePaint)
            return
        }
        val path = IconShapePaths.build(shapeType, bounds)
        canvas.drawPath(path, fillPaint)
        if (shapeType == 10) drawCubeGuides(canvas, bounds)
        canvas.drawPath(path, strokePaint)
    }

    /** Internal Y of the 3/4 cube — silhouette alone reads as a box, not a hex. */
    private fun drawCubeGuides(canvas: Canvas, bounds: RectF) {
        val w = bounds.width()
        val h = bounds.height()
        val l = bounds.left
        val t = bounds.top
        val fl = l + w * 0.10f
        val fr = l + w * 0.62f
        val br = bounds.right - w * 0.08f
        val topY = t + h * 0.16f
        val frontTop = t + h * 0.34f
        val bot = bounds.bottom - h * 0.10f
        canvas.drawLine(fr, frontTop, fr, bot, edgePaint)
        canvas.drawLine(fl, frontTop, fl + (br - fr), topY, edgePaint)
    }
}
