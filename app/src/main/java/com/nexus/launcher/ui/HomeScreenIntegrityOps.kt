package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal object HomeScreenIntegrityOps {
    fun runStartupIntegrityChecks(
        scope: CoroutineScope,
        homeScreenDao: HomeScreenDao,
        appContext: Context,
        itemPositionDao: com.nexus.launcher.data.ItemPositionDao? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val dbItems = homeScreenDao.getAllItemsDebug()
            val corruptedWidgets = dbItems.filter { it.appWidgetId != -1 && it.itemType != 3 }
            if (corruptedWidgets.isNotEmpty()) {
                val (maxCols, maxRows) = com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(appContext)
                for (widget in corruptedWidgets) {
                    val (col, row) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                        appContext, widget.xFraction, widget.yFraction, widget.spanX, widget.spanY,
                        widget.folderConfigJson, maxCols, maxRows
                    )
                    homeScreenDao.updateItem(widget.copy(itemType = 3, column = col, row = row))
                }
            }
            LegacyDockMigration.rescueLegacyDockItems(homeScreenDao, appContext)
            LegacyDockMigration.populateDefaultDockIfEmpty(homeScreenDao, appContext)
            
            val allItems = homeScreenDao.getAllItemsDebug()
            // Nothing strands a non-icon item on a page any more — every page is a grid page.
            val strandedItems = emptyList<com.nexus.launcher.data.HomeScreenItem>()
            if (strandedItems.isNotEmpty()) {
                val (maxCols, maxRows) = com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(appContext)
                val page0Items = allItems.filter { it.page == 0 && it.containerId == -1L }.toMutableList()
                
                for (item in strandedItems) {
                    val searchRes = com.nexus.launcher.ui.canvas.GridOccupancyHelper.findNearestEmptySlot(
                        context = appContext, desiredCol = 0, desiredRow = 0,
                        spanX = item.spanX, spanY = item.spanY,
                        maxCols = maxCols, maxRows = maxRows,
                        items = page0Items, visualPositions = emptyMap(), page = 0
                    )
                    val targetCol = searchRes?.first ?: 0
                    val targetRow = searchRes?.second ?: 0
                    val (xF, yF) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                        targetCol, targetRow, maxCols, maxRows, appContext, item.spanX, item.spanY
                    )
                    val recoveredItem = item.copy(page = 0, column = targetCol, row = targetRow, xFraction = xF, yFraction = yF)
                    homeScreenDao.updateItem(recoveredItem)
                    page0Items.add(recoveredItem)
                    android.util.Log.w("HomeScreenIntegrity", "Recovered stranded item ${item.id} to page 0")
                }
            }
            
            // Type-3 rows with appWidgetId=-1 never bind or render (layout skips them → vis=-1,-1)
            // but still occupy DB cells. Mosaic parents (type 4) legitimately use appWidgetId=-1.
            // Backup restore writes type-3 rows at -1 with a provider class and sets
            // pending_widget_rebind; those must survive until AppWidgetRestoredRebinder runs.
            val pendingRebind = appContext
                .getSharedPreferences("nexus_prefs", android.content.Context.MODE_PRIVATE)
                .getBoolean("pending_widget_rebind", false)
            val ghostWidgets = allItems.filter { item ->
                if (item.itemType != 3 || item.appWidgetId != -1) return@filter false
                if (pendingRebind && !item.providerClassName.isNullOrBlank()) return@filter false
                true
            }
            for (ghost in ghostWidgets) {
                android.util.Log.w(
                    "WidgetGhostCleanup",
                    "Startup purge of unbound ghost widget id=${ghost.id} " +
                        "appWidgetId=${ghost.appWidgetId} pkg=${ghost.packageName} " +
                        "provider=${ghost.providerClassName} " +
                        "pos=(page=${ghost.page}, col=${ghost.column}, row=${ghost.row}) " +
                        "span=${ghost.spanX}x${ghost.spanY}"
                )
                homeScreenDao.removeItemById(ghost.id)
            }
            val remainingWidgets = homeScreenDao.getAllItemsDebug().filter { it.itemType == 3 }
            android.util.Log.w(
                "WidgetGhostCleanup",
                "After purge type3 remaining=${remainingWidgets.size} " +
                    remainingWidgets.joinToString(" ") {
                        "id=${it.id} aw=${it.appWidgetId} cell=${it.column},${it.row} " +
                            "span=${it.spanX}x${it.spanY}"
                    }.ifBlank { "(none)" }
            )

            val orphanCount = homeScreenDao.countOrphanedFolderMembers()
            android.util.Log.w(
                "HomeScreenIntegrity",
                "Orphaned folder members in DB: $orphanCount (report-only; purge not run)"
            )

            val posDao = itemPositionDao ?: runCatching {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    appContext.applicationContext,
                    com.nexus.launcher.di.DaoEntryPoint::class.java
                ).itemPositionDao()
            }.getOrNull()
            posDao?.cleanupOrphans()
        }
    }
}
