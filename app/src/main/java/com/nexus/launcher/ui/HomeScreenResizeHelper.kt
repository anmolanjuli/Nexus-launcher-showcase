package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.DrawEngineLayout
import com.nexus.launcher.ui.canvas.GridOccupancyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared occupancy + persist for home-item resize (folders and icons).
 * Uses [GridOccupancyHelper].
 */
object HomeScreenResizeHelper {

    suspend fun attemptResizeFolder(
        dao: HomeScreenDao,
        appContext: Context,
        item: HomeScreenItem,
        newSpanX: Int,
        newSpanY: Int,
        newCol: Int,
        newRow: Int,
        effCols: Int,
        effRows: Int,
        visualPositions: Map<Int, Triple<Int, Int, Int>>
    ): Boolean = withContext(Dispatchers.IO) {
        if (newSpanX != newSpanY) return@withContext false
        resizeCore(
            dao, appContext, item, newSpanX, newSpanY, newCol, newRow,
            effCols, effRows, visualPositions
        ) { it }
    }

    suspend fun attemptResizeIcon(
        dao: HomeScreenDao,
        appContext: Context,
        item: HomeScreenItem,
        newSpanX: Int,
        newSpanY: Int,
        newCol: Int,
        newRow: Int,
        effCols: Int,
        effRows: Int,
        visualPositions: Map<Int, Triple<Int, Int, Int>>
    ): Boolean = withContext(Dispatchers.IO) {
        // Apps only (itemType 0); reject folders/widgets/shortcuts here
        if (item.itemType != 0) return@withContext false
        resizeCore(
            dao, appContext, item, newSpanX, newSpanY, newCol, newRow,
            effCols, effRows, visualPositions
        ) { it }
    }

    /**
     * Live occupancy probe for resize drag (same AABB as finalize).
     * Returns true when the proposed footprint is free.
     */
    fun canOccupy(
        context: Context,
        items: List<HomeScreenItem>,
        page: Int,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        excludeId: Int,
        visualPositions: Map<Int, Triple<Int, Int, Int>>,
        effCols: Int,
        effRows: Int
    ): Boolean {
        if (col < 0 || row < 0) return false
        if (col + spanX > effCols || row + spanY > effRows) return false
        if (spanX < 1 || spanY < 1) return false
        val others = items.filter { it.id != excludeId }
        return GridOccupancyHelper.findItemAtCell(
            context, others, page, col, row, visualPositions,
            emptyList(), spanX, spanY
        ) == null
    }

    private suspend fun resizeCore(
        dao: HomeScreenDao,
        appContext: Context,
        item: HomeScreenItem,
        newSpanX: Int,
        newSpanY: Int,
        newCol: Int,
        newRow: Int,
        effCols: Int,
        effRows: Int,
        visualPositions: Map<Int, Triple<Int, Int, Int>>,
        transform: (HomeScreenItem) -> HomeScreenItem
    ): Boolean {
        val itemsOnPage = dao.getItemsForPage(item.page)
        val otherItems = itemsOnPage.filter { it.id != item.id }
        val newLeft = newCol
        val newRight = newCol + newSpanX - 1
        val newTop = newRow
        val newBottom = newRow + newSpanY - 1
        if (newLeft < 0 || newRight >= effCols || newTop < 0 || newBottom >= effRows) {
            return false
        }
        val overlap = GridOccupancyHelper.findItemAtCell(
            appContext, otherItems, item.page, newCol, newRow,
            visualPositions, emptyList(), newSpanX, newSpanY
        ) != null
        if (overlap) return false
        val (newXF, newYF) = DrawEngineLayout.cellToFraction(
            newCol, newRow, effCols, effRows, appContext, newSpanX, newSpanY
        )
        val base = item.copy(
            column = newCol,
            row = newRow,
            spanX = newSpanX,
            spanY = newSpanY,
            xFraction = newXF,
            yFraction = newYF
        )
        dao.updateItem(transform(base))
        return true
    }
}
