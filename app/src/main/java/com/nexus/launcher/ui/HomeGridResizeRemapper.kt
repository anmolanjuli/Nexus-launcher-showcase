package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.ItemPosition
import com.nexus.launcher.data.ShapeAwareHomeScreenDao
import com.nexus.launcher.ui.canvas.DrawEngineLayout
import com.nexus.launcher.ui.canvas.GridOccupancyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Nova / Pixel keep integer cell coordinates when the grid grows:
 * extra columns appear on the right, extra rows are bottom-anchored
 * (shifted so items stay the same distance from the dock).
 * Shrink still clamps / reflows overflow.
 */
object HomeGridResizeRemapper {

    suspend fun remap(
        dao: HomeScreenDao,
        context: Context,
        oldCols: Int,
        oldRows: Int,
        newCols: Int,
        newRows: Int
    ) = withContext(Dispatchers.IO) {
        if (oldCols == newCols && oldRows == newRows) return@withContext
        // The portrait grid is the base shape. Other shapes derive their grid from it (phone
        // landscape swaps it), so their stored positions no longer fit: drop them and let
        // them be filled in again the next time that shape is shown.
        ShapeAwareHomeScreenDao.positionsOf(dao)?.let {
            it.deleteAllForShape(ItemPosition.SHAPE_PHONE_LANDSCAPE)
            it.deleteAllForShape(ItemPosition.SHAPE_LARGE)
        }
        remapBase(ShapeAwareHomeScreenDao.baseOf(dao), context, oldCols, oldRows, newCols, newRows)
    }

    private suspend fun remapBase(
        dao: HomeScreenDao,
        context: Context,
        oldCols: Int,
        oldRows: Int,
        newCols: Int,
        newRows: Int
    ) {
        val allItems = dao.getAllItemsDebug()
        val homeItems = allItems.filter { it.page != HomeScreenViewModel.DOCK_CONTAINER && it.containerId == -1L }
        val rowShift = newRows - oldRows

        for ((page, itemsOnPage) in homeItems.groupBy { it.page }) {
            val occupancy = itemsOnPage.toMutableList()
            for (item in itemsOnPage) {
                val target = resolveCell(context, occupancy, page, item, newCols, newRows, rowShift)
                if (target.first == item.column && target.second == item.row) continue
                occupancy.removeAll { it.id == item.id }
                val (xF, yF) = DrawEngineLayout.cellToFraction(
                    target.first, target.second, newCols, newRows, context, item.spanX, item.spanY
                )
                val updated = item.copy(
                    column = target.first,
                    row = target.second,
                    xFraction = xF,
                    yFraction = yF
                )
                dao.updateItem(updated)
                occupancy.add(updated)
            }
        }
    }

    private fun resolveCell(
        context: Context,
        occupancy: List<HomeScreenItem>,
        page: Int,
        item: HomeScreenItem,
        newCols: Int,
        newRows: Int,
        rowShift: Int
    ): Pair<Int, Int> {
        var col = item.column
        var row = item.row + rowShift
        col = col.coerceIn(0, (newCols - item.spanX).coerceAtLeast(0))
        row = row.coerceIn(0, (newRows - item.spanY).coerceAtLeast(0))
        if (item.column + item.spanX <= newCols &&
            item.row + rowShift >= 0 &&
            item.row + rowShift + item.spanY <= newRows
        ) {
            val shifted = Pair(item.column, item.row + rowShift)
            if (GridOccupancyHelper.findItemAtCell(
                    context, occupancy.filter { it.id != item.id }, page,
                    shifted.first, shifted.second, emptyMap(), emptyList(), item.spanX, item.spanY
                ) == null
            ) {
                return shifted
            }
        }
        val overlap = GridOccupancyHelper.findItemAtCell(
            context, occupancy.filter { it.id != item.id }, page,
            col, row, emptyMap(), emptyList(), item.spanX, item.spanY
        ) != null
        if (!overlap && col + item.spanX <= newCols && row + item.spanY <= newRows) {
            return Pair(col, row)
        }
        val search = GridOccupancyHelper.findNearestEmptySlot(
            context = context,
            desiredCol = col,
            desiredRow = row,
            spanX = item.spanX,
            spanY = item.spanY,
            maxCols = newCols,
            maxRows = newRows,
            items = occupancy.filter { it.id != item.id },
            visualPositions = emptyMap(),
            page = page
        )
        return search ?: Pair(col, row)
    }
}
