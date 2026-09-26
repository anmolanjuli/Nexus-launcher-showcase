package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Secondary helper for [HomeScreenViewModel] housing empty space search, widget adding, and page reordering. */
internal object HomeScreenOpsHelper {

    fun findEmptySpace(
        appContext: Context,
        allItems: List<HomeScreenItem>,
        page: Int,
        maxCols: Int,
        maxRows: Int,
        visualPositions: Map<Int, Triple<Int, Int, Int>>
    ): Pair<Int, Int>? {
        for (row in 0 until maxRows) {
            for (col in 0 until maxCols) {
                if (com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(appContext, allItems, page, col, row, visualPositions) == null) {
                    return Pair(col, row)
                }
            }
        }
        return null
    }

    fun addWidgetToHomeScreen(
        scope: CoroutineScope,
        homeScreenDao: HomeScreenDao,
        appContext: Context,
        appWidgetId: Int,
        providerPackage: String,
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float,
        providerClassName: String? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val (maxCols, maxRows) = com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(appContext)
            val (col, row) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                appContext, xFraction, yFraction, spanX, spanY, "{}", maxCols, maxRows
            )
            homeScreenDao.insertItem(
                HomeScreenItem(
                    packageName = providerPackage, page = page, column = col, row = row,
                    xFraction = xFraction, yFraction = yFraction, itemType = 3,
                    appWidgetId = appWidgetId, spanX = spanX, spanY = spanY,
                    providerClassName = providerClassName
                )
            )
        }
    }

    fun reorderPages(
        scope: CoroutineScope,
        homeScreenDao: HomeScreenDao,
        oldToNew: IntArray,
        onItemsLoaded: (List<HomeScreenItem>) -> Unit,
        onComplete: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            PageReorderHelper.remapItemPages(homeScreenDao, oldToNew)
            val fresh = homeScreenDao.getAllItemsDebug()
            withContext(Dispatchers.Main) {
                onItemsLoaded(fresh)
                onComplete()
            }
        }
    }
}
