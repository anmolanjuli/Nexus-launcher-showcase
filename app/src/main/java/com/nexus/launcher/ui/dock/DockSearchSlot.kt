package com.nexus.launcher.ui.dock

import android.graphics.Color
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.ui.HomeScreenViewModel

/** Virtual dock search slot — not stored in Room; managed via [DockSettingsRepository]. */
object DockSearchSlot {

    const val PACKAGE = "nexus.launcher.internal.search"
    const val ITEM_ID = Int.MIN_VALUE

    @Volatile var enabled: Boolean = false
    @Volatile var slotIndex: Int = 0
    @Volatile var accentColorArgb: Int = 0

    fun isSearchItem(item: HomeScreenItem?): Boolean =
        item != null && (item.id == ITEM_ID || item.packageName == PACKAGE)

    fun syntheticItem(column: Int): HomeScreenItem = HomeScreenItem(
        id = ITEM_ID,
        packageName = PACKAGE,
        page = HomeScreenViewModel.DOCK_CONTAINER,
        column = column,
        row = 0,
        xFraction = 0.5f,
        yFraction = 0.5f
    )

    /**
     * Merges the virtual search item into dock items for layout/draw/hit-test.
     * DB columns are preserved; layout columns are reassigned 0..n-1.
     */
    fun mergeForLayout(
        dbItems: List<HomeScreenItem>,
        maxDockIcons: Int,
        currentPage: Int
    ): List<HomeScreenItem> {
        val pageItems = DockSlotLayout.itemsOnPage(dbItems, currentPage, maxDockIcons)
        if (!enabled || currentPage != 0) return pageItems.sortedBy { it.column }
        val sorted = pageItems.sortedBy { it.column }
        val maxApps = (maxDockIcons - 1).coerceAtLeast(0)
        val apps = sorted.take(maxApps)
        val insertAt = slotIndex.coerceIn(0, apps.size)
        val merged = apps.toMutableList()
        merged.add(insertAt, syntheticItem(insertAt))
        return merged.take(maxDockIcons).mapIndexed { index, item ->
            item.copy(column = index)
        }
    }

    fun defaultSlotIndex(dbItemCount: Int, maxDockIcons: Int): Int =
        dbItemCount.coerceIn(0, (maxDockIcons - 1).coerceAtLeast(0))
}
