package com.nexus.launcher.ui.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.view.ViewCompat
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.immersive.ImmersiveStatus

/**
 * Fullscreen transparent overlay displayed during Island size & position calibration.
 * Draws a real-time calibration pill with camera cutout alignment guide over the physical cutout.
 */
@SuppressLint("ViewConstructor")
class IslandCalibrationOverlayView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    var previewWidthDp: Int = 110
        set(value) { field = value; updateBounds(); invalidate() }
    var previewHeightDp: Int = 28
        set(value) { field = value; updateBounds(); invalidate() }
    var previewXOffsetDp: Int = 0
        set(value) { field = value; updateBounds(); invalidate() }
    var previewYOffsetDp: Int = 0
        set(value) { field = value; updateBounds(); invalidate() }
    var isImmersive: Boolean = false

    private var cutoutCenterX: Float = -1f
    private var cutoutCenterY: Float = -1f
    private var cutoutWidth: Float = 0f
    private var cutoutHeight: Float = 0f
    private val pillRect = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val cutoutFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        isClickable = false
        isFocusable = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val band = IslandGeometry.readBand(this)
        cutoutCenterX = band.centerX
        cutoutCenterY = band.centerY
        cutoutWidth = band.cutoutWidthPx
        cutoutHeight = band.cutoutHeightPx
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val updated = IslandGeometry.readBand(this)
            cutoutCenterX = updated.centerX
            cutoutCenterY = updated.centerY
            cutoutWidth = updated.cutoutWidthPx
            cutoutHeight = updated.cutoutHeightPx
            updateBounds()
            invalidate()
            insets
        }
        ViewCompat.requestApplyInsets(this)
        updateBounds()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ImmersiveStatus.setIslandBounds(null, null)
    }

    private fun updateBounds() {
        val cx = if (cutoutCenterX > 0f) cutoutCenterX else (width / 2f).takeIf { it > 0f }
            ?: (resources.displayMetrics.widthPixels / 2f)
        val cy = if (cutoutCenterY > 0f) cutoutCenterY else (24f * density)
        val w = previewWidthDp * density
        val h = previewHeightDp * density
        val x = cx + previewXOffsetDp * density
        val y = cy + previewYOffsetDp * density
        pillRect.set(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
        if (isImmersive) {
            ImmersiveStatus.setIslandBounds(pillRect.left, pillRect.right)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        fillPaint.color = (tokens.surface and 0x00FFFFFF) or 0xF2000000.toInt()
        strokePaint.color = tokens.accent
        cutoutPaint.color = tokens.accent
        cutoutFillPaint.color = tokens.accentMuted
        dashPaint.color = (tokens.textSecondary and 0x00FFFFFF) or 0x88000000.toInt()

        // 1. Draw calibrated capsule
        val radius = pillRect.height() / 2f
        canvas.drawRoundRect(pillRect, radius, radius, fillPaint)
        canvas.drawRoundRect(pillRect, radius, radius, strokePaint)

        // 2. Draw camera cutout indicator hole if detected
        val cx = if (cutoutCenterX > 0f) cutoutCenterX else pillRect.centerX()
        val cy = if (cutoutCenterY > 0f) cutoutCenterY else pillRect.centerY()
        val holeRadius = if (cutoutWidth > 0f) (cutoutWidth / 2f).coerceAtLeast(6f * density) else 8f * density
        canvas.drawCircle(cx, cy, holeRadius, cutoutFillPaint)
        canvas.drawCircle(cx, cy, holeRadius, cutoutPaint)

        // 3. Draw crosshair center lines
        val px = pillRect.centerX()
        val py = pillRect.centerY()
        val crossArm = 6f * density
        canvas.drawLine(px - crossArm, py, px + crossArm, py, dashPaint)
        canvas.drawLine(px, py - crossArm, px, py + crossArm, dashPaint)
    }
}
