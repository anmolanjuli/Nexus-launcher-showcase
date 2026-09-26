package com.nexus.launcher.ui.dock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Circular drop-target highlight at the hovered dock slot during drag. */
internal object DockHighlightRenderer {

    private const val FILL_ALPHA = 0x33000000
    private const val STROKE_ALPHA = 0xB3000000.toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    fun shouldDraw(): Boolean {
        val slot = DockLayoutRenderer.hoveredSlotIndex ?: return false
        if (slot < 0) return false
        return DockLayoutRenderer.draggedItemId != null || DockLayoutRenderer.isInternalDrag
    }

    fun draw(canvas: Canvas, layout: DockRenderHelper.PaintLayout?, density: Float) {
        if (!shouldDraw() || layout == null) return
        val hoverSlot = DockLayoutRenderer.hoveredSlotIndex ?: return
        if (hoverSlot !in 0 until layout.maxDockIcons) return

        val mainCenter = layout.startOffset + hoverSlot * layout.slotWidth + layout.slotWidth / 2f
        val crossCenter = DockSlotLayout.iconCenterY(
            layout.baseTop,
            layout.iconSizePx,
            hoverSlot,
            layout.viewHeight
        )
        val centerX = DockAxis.x(mainCenter, crossCenter, layout.isVertical)
        val centerY = DockAxis.y(mainCenter, crossCenter, layout.isVertical)
        val radius = layout.iconSizePx / 2f
        val accent = accentArgb()

        fillPaint.color = (accent and 0x00FFFFFF) or FILL_ALPHA
        strokePaint.color = (accent and 0x00FFFFFF) or STROKE_ALPHA
        strokePaint.strokeWidth = 1.5f * density

        canvas.drawCircle(centerX, centerY, radius, fillPaint)
        canvas.drawCircle(centerX, centerY, radius, strokePaint)
    }

    private fun accentArgb(): Int {
        val accent = DockSearchSlot.accentColorArgb
        return if (accent != 0) accent else Color.rgb(0x7E, 0xB8, 0xD4)
    }
}
