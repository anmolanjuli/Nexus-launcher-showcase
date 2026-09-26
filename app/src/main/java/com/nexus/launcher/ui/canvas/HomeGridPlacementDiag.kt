package com.nexus.launcher.ui.canvas

import android.util.Log
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.util.NexusDiag

/**
 * TEMPORARY diagnostic for home-grid item placement vs live column count.
 * Remove after the 8-col / 6-col occupancy repro is reviewed.
 * Do not use for product logic.
 */
object HomeGridPlacementDiag {
    const val TAG = "HomeGridPlacementDiag"

    private var lastReflowKey: String? = null
    private var lastPlaceKey: String? = null

    fun logReflow(
        liveHomeColumns: Int,
        liveHomeRows: Int,
        layoutCols: Int,
        layoutRows: Int,
        items: List<HomeScreenItem>,
        derived: Map<Int, Triple<Int, Int, Int>>
    ) {
        if (!NexusDiag.ENABLED) return
        val gridItems = items.filter { it.containerId == -1L && it.page >= 0 }
        val maxDbCol = gridItems.maxOfOrNull { it.column } ?: -1
        val maxVisCol = derived.values.maxOfOrNull { it.second } ?: -1
        val key = "$liveHomeColumns|$layoutCols|$maxDbCol|$maxVisCol|${gridItems.size}|" +
            derived.entries.sortedBy { it.key }
                .joinToString(";") { "${it.key}:${it.value.first},${it.value.second},${it.value.third}" }
        if (key == lastReflowKey) return
        lastReflowKey = key
        val colsUsed = (0 until layoutCols.coerceAtLeast(1)).count { c ->
            derived.values.any { it.second == c } || gridItems.any { it.column == c }
        }
        Log.d(
            TAG,
            "REFLOW defaultCols=${NexusDefaults.HOME_COLUMNS} " +
                "settingsCols=$liveHomeColumns settingsRows=$liveHomeRows " +
                "layoutCols=$layoutCols layoutRows=$layoutRows " +
                "maxDbCol=$maxDbCol maxVisCol=$maxVisCol colsOccupied=$colsUsed " +
                "itemCount=${gridItems.size}"
        )
        val dump = gridItems.joinToString(" ") { item ->
            val vis = derived[item.id]
            "id=${item.id} type=${item.itemType} aw=${item.appWidgetId} " +
                "db=${item.column},${item.row} " +
                "span=${item.spanX}x${item.spanY} " +
                "vis=${vis?.second ?: -1},${vis?.third ?: -1} " +
                "page=${item.page} xf=${"%.2f".format(item.xFraction)}"
        }
        Log.d(TAG, "ITEMS $dump")
    }

    fun logPlace(
        source: String,
        searchCols: Int,
        searchRows: Int,
        liveCols: Int,
        liveRows: Int,
        page: Int,
        assignedCol: Int?,
        assignedRow: Int?
    ) {
        if (!NexusDiag.ENABLED) return
        val key = "$source|$searchCols|$liveCols|$page|$assignedCol|$assignedRow"
        if (key == lastPlaceKey) return
        lastPlaceKey = key
        Log.d(
            TAG,
            "PLACE source=$source searchCols=$searchCols searchRows=$searchRows " +
                "settingsCols=$liveCols settingsRows=$liveRows page=$page " +
                "assigned=${assignedCol},${assignedRow} " +
                "searchColsMatchSettings=${searchCols == liveCols}"
        )
    }
}
