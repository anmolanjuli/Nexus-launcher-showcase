package com.nexus.launcher.ui.dock

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.typography.SystemFontProvider

/** Dock icon labels — uses dock-specific font size only. */
internal object DockLabelRenderer {

    private val BRIGHT_LABEL = NexusColorTokens.Light.textPrimary
    // Reuses NexusColorTokens.Dark.textPrimary (0xFFE8EEF2) as a proxy for Amoled (since they currently share the same value).
    // If Dark and Amoled textPrimary diverge in future token updates, revisit this to decide if Amoled needs a separate endpoint.
    private val DARK_LABEL = (NexusColorTokens.Dark.textPrimary and 0x00FFFFFF) or (0xE6 shl 24)

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = SystemFontProvider.getTypeface(NexusTypeScale.iconLabel.weight)
        letterSpacing = NexusTypeScale.iconLabel.letterSpacingEm
    }

    @Volatile var showLabels: Boolean = false
    @Volatile var labelFontSizeSp: Int = com.nexus.launcher.ui.dock.settings.DockSettingsRepository.DEFAULT_LABEL_FONT_SIZE_SP

    fun updateTypeface(typeface: android.graphics.Typeface) {
        labelPaint.typeface = typeface
    }

    fun labelExtraHeightPx(density: Float): Float =
        if (!showLabels) 0f else labelFontSizeSp * density + 4f * density

    fun drawLabels(
        canvas: Canvas,
        layout: DockRenderHelper.PaintLayout,
        items: List<HomeScreenItem>,
        density: Float,
        dockBackgroundColor: Int,
        labelResolver: (HomeScreenItem) -> String?
    ) {
        if (!showLabels || layout.isVertical) return
        applyLabelStyle(dockBackgroundColor, density)
        labelPaint.textSize = labelFontSizeSp * density
        val maxLabelWidth = layout.iconSizePx * 1.15f
        val labelGapPx = 4f * density
        for (item in layout.sortedVisible) {
            if (DockSearchSlot.isSearchItem(item)) continue
            val label = labelResolver(item) ?: continue
            val left = layout.itemLeft[item.id] ?: continue
            val targetCol = DockRenderHelper.displacedColumn(
                item.column, DockLayoutRenderer.hoveredSlotIndex,
                DockLayoutRenderer.isInternalDrag, DockLayoutRenderer.draggedItemOriginalColumn,
                layout.maxDockIcons
            )
            val iconTop = DockSlotLayout.iconTop(
                layout.baseTop, targetCol, layout.viewHeight
            )
            val iconBottom = iconTop + layout.iconSizePx
            val centerX = left + layout.iconSizePx / 2f
            val truncated = TextUtils.ellipsize(
                label, labelPaint, maxLabelWidth, TextUtils.TruncateAt.END
            ).toString()
            val baseline = iconBottom + labelGapPx - labelPaint.fontMetrics.ascent
            canvas.drawText(truncated, centerX, baseline, labelPaint)
        }
    }

    private fun applyLabelStyle(backgroundColor: Int, density: Float) {
        val luminance = luminanceOf(backgroundColor)
        val brightBackground = luminance > 0.5f
        labelPaint.color = if (brightBackground) BRIGHT_LABEL else DARK_LABEL
        val shadowArgb = if (brightBackground) {
            Color.argb(102, 255, 255, 255)
        } else {
            Color.argb(102, 0, 0, 0)
        }
        labelPaint.setShadowLayer(density, 0f, 0f, shadowArgb)
    }

    private fun luminanceOf(argb: Int): Float {
        if (Color.alpha(argb) < 16) return 0f
        val r = Color.red(argb) / 255f
        val g = Color.green(argb) / 255f
        val b = Color.blue(argb) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
