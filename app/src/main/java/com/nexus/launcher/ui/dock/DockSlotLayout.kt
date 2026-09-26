package com.nexus.launcher.ui.dock

import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository

/** Centered dock geometry — shared by draw, hit-test, hover, and fluid drop math. */
object DockSlotLayout {

    const val DEFAULT_MAX_DOCK_ICONS = 5
    const val ICON_GAP_DP = 16f

    /** Live user preference (dp); synced from [DockSettingsRepository] via [DockLayoutSettingsBinder]. */
    @Volatile
    var userIconSizeDp: Int = DockSettingsRepository.DEFAULT_ICON_SIZE_DP
        private set

    /** Display density for gap math when callers omit an explicit value. */
    @Volatile
    var displayDensity: Float = 1f
        private set

    fun applyUserIconSizeDp(dp: Int) {
        userIconSizeDp = dp.coerceIn(
            DockSettingsRepository.MIN_ICON_SIZE_DP,
            DockSettingsRepository.MAX_ICON_SIZE_DP
        )
    }

    fun applyDisplayDensity(density: Float) {
        displayDensity = density.coerceAtLeast(0.5f)
    }

    fun cappedSlotWidth(
        axisSize: Int,
        activeCount: Int,
        iconSizePx: Float,
        density: Float = displayDensity
    ): Float {
        val count = activeCount.coerceAtLeast(1)
        val fullSlotWidth = axisSize / count.toFloat()
        val naturalSlotWidth = iconSizePx + ICON_GAP_DP * density
        return minOf(naturalSlotWidth, fullSlotWidth)
    }

    private fun estimatedIconSizePx(
        fullSlotWidth: Float,
        density: Float,
        iconSizePx: Float?
    ): Float {
        if (iconSizePx != null) return iconSizePx
        val padding = 4f * density
        val slotFit = (fullSlotWidth - padding * 2f).coerceAtLeast(0f)
        return minOf(userIconSizeDp * density, slotFit)
    }

    data class Metrics(
        val startOffset: Float,
        val slotWidth: Float,
        val totalItems: Int,
        val totalOccupiedWidth: Float
    )

    fun centerStartOffset(axisSize: Int, slotCount: Int, slotWidth: Float): Float {
        if (slotCount <= 0) return 0f
        return maxOf(0f, (axisSize - slotCount * slotWidth) / 2f)
    }

    fun metrics(
        axisSize: Int,
        itemCount: Int,
        maxDockIcons: Int = DEFAULT_MAX_DOCK_ICONS,
        includeIncomingDrop: Boolean = false,
        iconSizePx: Float? = null,
        density: Float = displayDensity
    ): Metrics {
        val safeMaxIcons = maxDockIcons.coerceAtLeast(1)
        val currentItemCount = if (includeIncomingDrop) {
            minOf(itemCount + 1, safeMaxIcons)
        } else {
            minOf(itemCount, safeMaxIcons)
        }.coerceAtLeast(1)
        
        val fullSlotWidth = axisSize / currentItemCount.toFloat()
        val estIcon = estimatedIconSizePx(fullSlotWidth, density, iconSizePx)
        val slotWidth = cappedSlotWidth(axisSize, currentItemCount, estIcon, density)
        val totalOccupiedWidth = currentItemCount * slotWidth
        val startOffset = centerStartOffset(axisSize, currentItemCount, slotWidth)
        return Metrics(startOffset, slotWidth, currentItemCount, totalOccupiedWidth)
    }



    fun iconTop(
        baseTop: Float,
        localColumn: Int,
        viewHeight: Float
    ): Float = baseTop

    fun iconCenterY(
        baseTop: Float,
        iconSizePx: Float,
        localColumn: Int,
        viewHeight: Float
    ): Float = iconTop(baseTop, localColumn, viewHeight) + (iconSizePx / 2f)

    fun centerX(column: Int, metrics: Metrics): Float =
        metrics.startOffset + (column * metrics.slotWidth) + (metrics.slotWidth / 2f)

    fun iconLeft(column: Int, metrics: Metrics, iconSizePx: Float): Float =
        centerX(column, metrics) - (iconSizePx / 2f)

    fun dockPageFor(item: HomeScreenItem, maxDockIcons: Int): Int =
        if (maxDockIcons <= 0) 0 else item.column / maxDockIcons

    fun localColumnFor(item: HomeScreenItem, maxDockIcons: Int): Int =
        if (maxDockIcons <= 0) item.column else item.column % maxDockIcons

    fun itemsOnPage(items: List<HomeScreenItem>, page: Int, maxDockIcons: Int): List<HomeScreenItem> =
        items.filter { dockPageFor(it, maxDockIcons) == page }

    fun slotIndex(localPosition: Float, metrics: Metrics): Int {
        if (metrics.totalItems == 0) return 0
        val localX = localPosition - metrics.startOffset
        if (localX < 0f || localX > metrics.totalOccupiedWidth) return -1
        return (localX / metrics.slotWidth).toInt().coerceIn(0, metrics.totalItems - 1)
    }

    /**
     * Single-page hit test — mirrors [DockLayoutRenderer.drawIcons] exactly.
     * Ignores scroll offset and pagination; only columns `0..maxDockIcons-1` are hittable.
     */
    fun hitTestItem(
        localPosition: Float,
        items: List<HomeScreenItem>,
        axisSize: Int,
        maxDockIcons: Int,
        iconSizePx: Float? = null,
        density: Float = displayDensity
    ): HomeScreenItem? {
        if (axisSize == 0) return null
        val visibleItems = items
            .filter { it.column < maxDockIcons }
            .sortedBy { it.column }
        if (visibleItems.isEmpty()) return null
        val safeMax = maxDockIcons.coerceAtLeast(1)
        val metrics = metrics(
            axisSize, visibleItems.size, safeMax,
            iconSizePx = iconSizePx, density = density
        )
        val localX = localPosition - metrics.startOffset
        if (localX < 0f || localX > metrics.totalOccupiedWidth) return null
        val slotIndex = (localX / metrics.slotWidth).toInt().coerceIn(0, visibleItems.size - 1)
        return visibleItems.find { it.column == slotIndex }
    }

    fun insertionIndex(dropPosition: Float, metrics: Metrics, itemCount: Int): Int {
        val slotSize = metrics.slotWidth
        val startOffset = metrics.startOffset
        var targetIndex = itemCount
        for (i in 0 until itemCount) {
            val itemCenter = startOffset + (i * slotSize) + (slotSize / 2f)
            if (dropPosition < itemCenter) {
                targetIndex = i
                break
            }
        }
        return targetIndex.coerceIn(0, itemCount)
    }

    fun slotCenterX(column: Int, metrics: Metrics): Float = centerX(column, metrics)

    /** Icon size strictly bounded by user setting, slot width, and dock pill height so icons NEVER clip borders. */
    fun dynamicIconSizePx(
        slotWidth: Float,
        density: Float,
        maxHeightPx: Float,
        showLabels: Boolean = DockLabelRenderer.showLabels,
        labelFontSizeSp: Int = DockLabelRenderer.labelFontSizeSp
    ): Float {
        val userSizePx = userIconSizeDp * density
        val labelExtraPx = if (showLabels) (labelFontSizeSp + 6f) * density else 0f
        val verticalPaddingPx = 16f * density // 8dp top + 8dp bottom padding inside dock pill
        val maxIconHeight = (maxHeightPx - labelExtraPx - verticalPaddingPx).coerceAtLeast(16f * density)
        val horizontalPaddingPx = 8f * density
        val maxIconWidth = (slotWidth - horizontalPaddingPx).coerceAtLeast(16f * density)
        return minOf(userSizePx, maxIconHeight, maxIconWidth)
    }

    fun iconHitRectFor(
        item: HomeScreenItem,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        axisSize: Int,
        viewHeight: Float,
        iconSizePx: Float,
        bottomPaddingPx: Float,
        density: Float,
        isVertical: Boolean = false
    ): android.graphics.Rect {
        val maxIcons = maxDockIcons.coerceAtLeast(1)
        val visibleCount = items.count { it.column < maxIcons }
        val localColumn = item.column.coerceIn(0, maxIcons - 1)
        val metrics = metrics(axisSize, visibleCount, maxIcons, iconSizePx = iconSizePx, density = density)
        val mainCenter = centerX(localColumn, metrics)
        val labelExtraPx = if (isVertical) 0f else com.nexus.launcher.ui.dock.DockLabelRenderer.labelExtraHeightPx(density)
        val availableHeight = viewHeight - labelExtraPx
        val baseTop = (availableHeight - iconSizePx) / 2f
        val crossCenter = iconCenterY(baseTop, iconSizePx, localColumn, viewHeight)
        val cx = DockAxis.x(mainCenter, crossCenter, isVertical)
        val cy = DockAxis.y(mainCenter, crossCenter, isVertical)
        val hitRadius = (28 * density).toInt()
        return android.graphics.Rect(
            (cx - hitRadius).toInt(), (cy - hitRadius).toInt(),
            (cx + hitRadius).toInt(), (cy + hitRadius).toInt()
        )
    }
}
