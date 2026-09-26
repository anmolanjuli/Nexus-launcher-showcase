package com.nexus.launcher.ui.dock

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.view.View
import android.view.ViewOutlineProvider
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository

/** User-configurable dock corner radius — single source of truth for all dock background drawing. */
internal object DockCornerRadius {

    @Volatile var cornerRadiusDp: Int = DockSettingsRepository.DEFAULT_CORNER_RADIUS_DP

    private val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** User-set radius in px; 0dp returns 0f (sharp corners). No height/2 fallback. */
    fun cornerRadiusPx(viewHeight: Int, density: Float): Float = cornerRadiusDp * density

    fun cornerRadiusPx(density: Float): Float = cornerRadiusPx(0, density)

    fun cornerRadiusPx(view: View): Float =
        cornerRadiusPx(view.height, view.resources.displayMetrics.density)

    fun applyClipOutline(
        view: View,
        clipRect: android.graphics.RectF,
        radius: Float
    ) {
        if (view.width <= 0 || view.height <= 0) return
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                val l = clipRect.left.toInt().coerceAtLeast(0)
                val t = clipRect.top.toInt().coerceAtLeast(0)
                val r = clipRect.right.toInt().coerceAtMost(v.width)
                val b = clipRect.bottom.toInt().coerceAtMost(v.height)
                if (r <= l || b <= t) {
                    outline.setEmpty()
                    return
                }
                outline.setRoundRect(l, t, r, b, radius)
            }
        }
        view.clipToOutline = true
        view.invalidateOutline()
    }

    fun applyClipOutline(view: View, clipLeft: Float = 0f, clipRight: Float = view.width.toFloat()) {
        if (view.width <= 0 || view.height <= 0) return
        val density = view.resources.displayMetrics.density
        val radius = cornerRadiusPx(view.height, density)
        applyClipOutline(view, android.graphics.RectF(clipLeft, 0f, clipRight, view.height.toFloat()), radius)
    }

    fun applyClipOutline(dock: View, blurChild: View, clipLeft: Float = 0f, clipRight: Float = dock.width.toFloat()) {
        applyClipOutline(dock, clipLeft, clipRight)
        applyClipOutline(blurChild, clipLeft, clipRight)
    }

    fun applyClipOutline(dock: View, blurChild: View, clipRect: android.graphics.RectF, radius: Float) {
        applyClipOutline(dock, clipRect, radius)
        applyClipOutline(blurChild, clipRect, radius)
    }

    fun drawSolidIfNeeded(
        canvas: Canvas,
        width: Int,
        height: Int,
        density: Float,
        color: Int,
        left: Float = 0f,
        right: Float = width.toFloat()
    ) {
        if (!DockBackgroundRenderer.shouldDrawSolidOnCanvas()) return
        drawSolidBackground(canvas, left, right, height, color, density)
    }

    fun drawSolidRect(canvas: Canvas, rect: android.graphics.RectF, radius: Float, color: Int) {
        if (rect.right <= rect.left || rect.bottom <= rect.top) return
        solidPaint.color = color
        if (radius <= 0f) {
            canvas.drawRect(rect, solidPaint)
        } else {
            canvas.drawRoundRect(rect, radius, radius, solidPaint)
        }
    }

    fun drawSolidBackground(canvas: Canvas, left: Float, right: Float, height: Int, color: Int, density: Float) {
        if (right <= left || height <= 0) return
        val radius = cornerRadiusPx(height, density)
        drawSolidRect(canvas, android.graphics.RectF(left, 0f, right, height.toFloat()), radius, color)
    }
}
