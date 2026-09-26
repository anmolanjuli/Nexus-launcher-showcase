package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Secondary helper for [HomeScreenDropHandler] housing widget bounds, pinning, and page deletion operations.
 * Extracted to ensure [HomeScreenDropHandler] stays safely under the 400-line limit.
 */
internal object HomeScreenDropOperations {

    fun updateWidgetBounds(
        id: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float,
        context: Context?,
        getAllItems: () -> List<HomeScreenItem>,
        updateAllItems: (List<HomeScreenItem>) -> Unit,
        homeScreenDao: HomeScreenDao,
        scope: CoroutineScope
    ) {
        val currentList = getAllItems().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return
        val ctx = context ?: return
        val (fCols, fRows) = com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(ctx)
        val (newCol, newRow) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
            ctx, xFraction, yFraction, spanX, spanY,
            currentList[index].folderConfigJson, fCols, fRows
        )
        currentList[index] = currentList[index].copy(
            spanX = spanX, spanY = spanY, xFraction = xFraction, yFraction = yFraction, column = newCol, row = newRow
        )
        updateAllItems(currentList)
        scope.launch(Dispatchers.IO) {
            homeScreenDao.updateWidgetBounds(id, spanX, spanY, xFraction, yFraction, newCol, newRow)
        }
    }

    fun pinToHome(
        packageName: String,
        page: Int,
        col: Int,
        row: Int,
        context: Context?,
        homeScreenDao: HomeScreenDao,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.IO) {
            val allItems = homeScreenDao.getAllItemsDebug()
            val isOccupied = context != null && com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                context, allItems, page, col, row, emptyMap()
            ) != null
            if (!isOccupied) {
                var xF = 0f
                var yF = 0f
                if (context != null) {
                    val (fracX, fracY) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                        col, row, com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(context).first,
                        com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(context).second, context, 1, 1
                    )
                    xF = fracX
                    yF = fracY
                }
                homeScreenDao.insertItem(
                    HomeScreenItem(packageName = packageName, page = page, column = col, row = row, xFraction = xF, yFraction = yF)
                )
            }
        }
    }

    fun removeFromHomeScreen(
        packageName: String,
        page: Int,
        context: Context?,
        homeScreenDao: HomeScreenDao,
        scope: CoroutineScope
    ) {
        scope.launch(Dispatchers.IO) {
            val id = homeScreenDao.findItemId(packageName, page) ?: return@launch
            val item = homeScreenDao.getItemById(id)
            if (item != null) HomeScreenDeletion.deleteItem(homeScreenDao, item, context)
            else homeScreenDao.removeItemById(id)
        }
    }

    fun deletePage(
        page: Int,
        context: Context?,
        homeScreenDao: HomeScreenDao,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            HomeScreenDeletion.deletePage(homeScreenDao, context, page)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }
}
