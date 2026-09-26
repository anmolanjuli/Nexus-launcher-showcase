package com.nexus.launcher.ui.dock

import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository

internal object DockLayoutPageOps {

    fun itemsOnPage(items: List<HomeScreenItem>, currentPage: Int, maxDockIcons: Int) =
        DockSlotLayout.itemsOnPage(items, currentPage, maxDockIcons)

    fun pageItemCount(items: List<HomeScreenItem>, currentPage: Int, maxDockIcons: Int) =
        itemsOnPage(items, currentPage, maxDockIcons).size

    fun localInsertionIndexAt(
        isLandscape: Boolean,
        localX: Float,
        localY: Float,
        axisSize: Int,
        pageItemCount: Int,
        maxDockIcons: Int
    ): Int {
        val dropPosition = if (isLandscape) localY else localX
        return appendAwareDropSlot(dropPosition, axisSize, maxDockIcons, pageItemCount)
    }

    /**
     * Maps a drop coordinate to a slot index, allowing append at [currentDockOccupancy]
     * (no size - 1 clamp).
     */
    fun appendAwareDropSlot(
        dropCoord: Float,
        axisSize: Int,
        maxDockIcons: Int,
        currentDockOccupancy: Int
    ): Int {
        if (axisSize == 0) return 0
        val safeMax = maxDockIcons.coerceAtLeast(1)
        val slotWidth = axisSize / safeMax.toFloat()
        val totalOccupiedWidth = currentDockOccupancy * slotWidth
        val startOffset = maxOf(0f, (axisSize - totalOccupiedWidth) / 2f)
        val localX = dropCoord - startOffset
        val targetColumn = when {
            localX < 0f -> 0
            localX > totalOccupiedWidth -> currentDockOccupancy
            else -> (localX / slotWidth).toInt()
        }
        return targetColumn.coerceIn(0, currentDockOccupancy).coerceAtMost(safeMax - 1)
    }

    fun dropSlotAt(
        view: View,
        localX: Float,
        localY: Float,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        excludedItem: HomeScreenItem?
    ): Int {
        val isLandscape = com.nexus.launcher.ui.canvas.LayoutProfile.isPhoneLandscape(
            view.resources.configuration
        )
        val dropCoord = if (isLandscape) localY else localX
        val axisSize = if (isLandscape) view.height else view.width
        val occupancy = dockOccupancyExcluding(items, excludedItem)
        return appendAwareDropSlot(dropCoord, axisSize, maxDockIcons, occupancy)
    }

    private fun dockOccupancyExcluding(items: List<HomeScreenItem>, excluded: HomeScreenItem?): Int {
        if (excluded == null) return items.size
        return items.count {
            it.id != excluded.id &&
                !(excluded.id == 0 && it.packageName == excluded.packageName)
        }
    }

    fun globalInsertionIndexAt(currentPage: Int, maxDockIcons: Int, localIndex: Int) =
        currentPage * maxDockIcons + localIndex

    fun hitTestAt(
        event: MotionEvent,
        isLandscape: Boolean,
        width: Int,
        height: Int,
        items: List<HomeScreenItem>,
        maxDockIcons: Int
    ): HomeScreenItem? {
        if (width == 0 || height == 0) return null
        val touchPosition = if (isLandscape) event.y else event.x
        val axisSize = if (isLandscape) height else width
        return DockSlotLayout.hitTestItem(touchPosition, items, axisSize, maxDockIcons)
    }

    fun pageCountFor(itemCount: Int, maxDockIcons: Int): Int =
        if (itemCount == 0) 0 else Math.ceil(itemCount.toDouble() / maxDockIcons.coerceAtLeast(1)).toInt()
}
