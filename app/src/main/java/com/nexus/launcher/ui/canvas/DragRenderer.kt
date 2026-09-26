package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.nexus.launcher.theme.ColorBlindMode
import com.nexus.launcher.ui.NexusDesignSystem

class DragRenderer(private val density: Float) {

    private val invalidDashedEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE)
        style = Paint.Style.FILL
    }

    private val highlightBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(NexusDesignSystem.COLOR_TEXT_PRIMARY)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val invalidFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FF0000")
        style = Paint.Style.FILL
    }

    private val invalidStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFF0000")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private var colorBlindMode: ColorBlindMode = ColorBlindMode.NONE

    /** Spring state for the landing highlight, so it glides between cells instead of teleporting. */
    internal val gridHighlight = DragGridHighlight()

    /** Proximity-lit cell markers shown under a drag. */
    internal val gridField = DragGridField(density)

    /** Scratch — [drawCellHighlight] runs every frame of a drag; never allocate here. */
    private val insetRect = RectF()

    /** Updates fill and border to contrast against the current theme background. */
    fun setTheme(tokens: com.nexus.launcher.theme.NexusColorTokens) {
        highlightBorderPaint.color = tokens.textPrimary
        highlightPaint.color = (tokens.surface and 0x00FFFFFF) or (0x28 shl 24)
        gridField.setTheme(tokens)
    }

    /** Drag highlights are accent-free — neutral glass only. */
    fun setAccentColor(@Suppress("UNUSED_PARAMETER") color: Int) = Unit

    fun setColorBlindMode(mode: ColorBlindMode) {
        colorBlindMode = mode
        if (mode != ColorBlindMode.NONE) {
            val safeColor = mode.safeDangerColor ?: 0xFFFF8C00.toInt()
            invalidStrokePaint.color = safeColor
            invalidStrokePaint.pathEffect = invalidDashedEffect
            invalidFillPaint.color = (safeColor and 0x00FFFFFF) or (0x22 shl 24)
        } else {
            invalidStrokePaint.color = Color.parseColor("#FFFF0000")
            invalidStrokePaint.pathEffect = null
            invalidFillPaint.color = Color.parseColor("#44FF0000")
        }
    }

    fun drawCellHighlight(canvas: Canvas, cellRect: RectF, isValid: Boolean = true) {
        val inset = 8f * density
        insetRect.set(
            cellRect.left + inset,
            cellRect.top + inset,
            cellRect.right - inset,
            cellRect.bottom - inset
        )
        val radius = 12f * density
        val currentFill = if (isValid) highlightPaint else invalidFillPaint
        val currentStroke = if (isValid) highlightBorderPaint else invalidStrokePaint
        canvas.drawRoundRect(insetRect, radius, radius, currentFill)
        canvas.drawRoundRect(insetRect, radius, radius, currentStroke)
    }

    fun drawDragShadow(
        canvas: Canvas,
        icon: Drawable?,
        fingerX: Float,
        fingerY: Float,
        iconSize: Float
    ) {
        if (icon == null) return
        val scaledHalf = iconSize * 1.2f / 2f
        icon.setBounds(
            (fingerX - scaledHalf).toInt(),
            (fingerY - scaledHalf).toInt(),
            (fingerX + scaledHalf).toInt(),
            (fingerY + scaledHalf).toInt()
        )
        icon.draw(canvas)
    }
}
