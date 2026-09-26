package com.nexus.launcher.ui.dock

import android.graphics.RectF
import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.abs

/** Shared painted-layout math for draw, hit-test, hover, and drop insertion. */
internal object DockRenderHelper {

    data class PaintLayout(
        val axisSize: Int,
        val height: Float,
        val maxDockIcons: Int,
        val slotWidth: Float,
        val startOffset: Float,
        val iconSizePx: Float,
        val baseTop: Float,
        val viewHeight: Float,
        val sortedVisible: List<HomeScreenItem>,
        val itemLeft: Map<Int, Float>,
        val itemScale: Map<Int, Float>,
        val visualSlotCount: Int,
        val originalCount: Int,
        val bgLeft: Float,
        val bgWidth: Float,
        val isVertical: Boolean = false
    )

    fun buildLayout(
        axisSize: Int,
        height: Int,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        iconSizePx: Float,
        bottomPaddingPx: Float,
        excludeId: Int?,
        hoveredSlot: Int?,
        isInternalDrag: Boolean,
        draggedOriginalColumn: Int?,
        animX: MutableMap<Int, DockSpringPhysics.Channel>,
        fingerLocalX: Float?,
        density: Float,
        motionIntensity: Float,
        advanceSprings: Boolean = true,
        isVertical: Boolean = false
    ): PaintLayout {
        val safeMax = maxDockIcons.coerceAtLeast(1)
        DockSlotLayout.applyDisplayDensity(density)
        val originalCount = items.count { it.column < safeMax }
        val visibleItems = items
            .filter { it.column < safeMax && it.id != excludeId }
            .sortedBy { it.column }
        val effectiveHover = hoveredSlot?.takeIf { it in 0 until safeMax }
        val hasIncomingGap = effectiveHover != null && !isInternalDrag &&
            originalCount < safeMax
        val shuffleActive = effectiveHover != null || isInternalDrag
        val visualSlotCount = when {
            isInternalDrag -> originalCount.coerceAtLeast(1)
            effectiveHover != null && hasIncomingGap -> (originalCount + 1).coerceAtMost(safeMax)
            else -> visibleItems.size.coerceAtLeast(if (effectiveHover != null) 1 else 0)
        }.coerceAtLeast(1)
        val slotWidth = DockSlotLayout.cappedSlotWidth(axisSize, visualSlotCount, iconSizePx, density)
        val startOffset = DockSlotLayout.centerStartOffset(axisSize, visualSlotCount, slotWidth)
        val viewHeight = height.toFloat()
        val labelExtraPx = if (isVertical) 0f else com.nexus.launcher.ui.dock.DockLabelRenderer.labelExtraHeightPx(density)
        val availableHeight = viewHeight - labelExtraPx
        val baseTop = (availableHeight - iconSizePx) / 2f
        val itemLeft = mutableMapOf<Int, Float>()
        val itemScale = mutableMapOf<Int, Float>()
        for (item in visibleItems) {
            val targetCol = displacedColumn(
                item.column, effectiveHover, isInternalDrag, draggedOriginalColumn, safeMax
            )
            val left = startOffset + targetCol * slotWidth + (slotWidth - iconSizePx) / 2f
            itemLeft[item.id] = left
            itemScale[item.id] = 1f
        }
        val bgWidth = visualSlotCount * slotWidth
        val bgLeft = startOffset
        return PaintLayout(
            axisSize, height.toFloat(), safeMax, slotWidth, startOffset, iconSizePx,
            baseTop, viewHeight, visibleItems, itemLeft, itemScale, visualSlotCount,
            originalCount, bgLeft, bgWidth, isVertical
        )
    }

    fun displacedColumn(
        column: Int,
        hover: Int?,
        isInternalDrag: Boolean,
        draggedOriginalColumn: Int?,
        safeMax: Int
    ): Int {
        var targetCol = column.coerceIn(0, safeMax - 1)
        if (hover == null || hover !in 0 until safeMax) return targetCol
        if (isInternalDrag && draggedOriginalColumn != null) {
            val orig = draggedOriginalColumn
            if (hover < orig && column in hover until orig) {
                targetCol = (column + 1).coerceAtMost(safeMax - 1)
            } else if (hover > orig && column in (orig + 1)..hover) {
                targetCol = (column - 1).coerceAtLeast(0)
            }
        } else if (column >= hover) {
            targetCol = (column + 1).coerceAtMost(safeMax - 1)
        }
        return targetCol
    }

    fun iconBoundsAt(layout: PaintLayout, index: Int): RectF? {
        val item = layout.sortedVisible.getOrNull(index) ?: return null
        val mainPos = layout.itemLeft[item.id] ?: return null
        val scale = layout.itemScale[item.id] ?: 1f
        val size = layout.iconSizePx * scale
        val crossPos = DockSlotLayout.iconTop(
            layout.baseTop,
            item.column.coerceIn(0, layout.maxDockIcons - 1),
            layout.viewHeight
        )
        val inset = (layout.iconSizePx - size) / 2f
        return DockAxis.iconBounds(mainPos + inset, crossPos + inset, size, layout.isVertical)
    }

    fun iconIndexForTouch(layout: PaintLayout, x: Float, y: Float): Int {
        layout.sortedVisible.forEachIndexed { index, item ->
            val bounds = iconBoundsAt(layout, index) ?: return@forEachIndexed
            if (bounds.contains(x, y)) return index
        }
        return -1
    }

    fun insertIndexForX(
        layout: PaintLayout,
        fingerX: Float,
        animX: Map<Int, DockSpringPhysics.Channel>,
        isInternalDrag: Boolean,
        draggedOriginalColumn: Int?,
        allowAppendGap: Boolean
    ): Int = DockHoverResolver.slotForFingerX(
        fingerX,
        layout,
        animX,
        isInternalDrag,
        draggedOriginalColumn,
        allowAppendGap
    )
}
