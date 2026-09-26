package com.nexus.launcher.model

import android.graphics.Point
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem

object GridPlacementEngine {

    fun findFirstEmptySlot(grid: Array<Array<HomeScreenItem?>>): Point? {
        for (row in grid.indices.reversed()) {
            for (col in grid[row].indices) {
                if (grid[row][col] == null) return Point(col, row)
            }
        }
        return null
    }

    suspend fun injectAppIcon(
        app: HomeScreenItem,
        page: Int,
        maxCols: Int,
        maxRows: Int,
        dao: HomeScreenDao,
        currentItems: List<HomeScreenItem>,
        visualPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap(),
        context: android.content.Context
    ): Boolean {
        // Build the 2D grid for the specific page
        val grid = Array(maxRows) { arrayOfNulls<HomeScreenItem>(maxCols) }
        currentItems.filter { it.page == page }.forEach { item ->
            val pos = visualPositions[item.id] ?: Triple(item.page, item.column, item.row)
            val effCol = pos.second
            val effRow = pos.third
            for (yOffset in 0 until item.spanY) {
                for (xOffset in 0 until item.spanX) {
                    val targetRow = effRow + yOffset
                    val targetCol = effCol + xOffset
                    if (targetRow in 0 until maxRows && targetCol in 0 until maxCols) {
                        grid[targetRow][targetCol] = item
                    }
                }
            }
        }

        val emptySlot = findFirstEmptySlot(grid)
        // TEMPORARY — HomeGridPlacementDiag; remove with the diag object.
        com.nexus.launcher.ui.canvas.HomeGridPlacementDiag.logPlace(
            source = "injectAppIcon",
            searchCols = maxCols,
            searchRows = maxRows,
            liveCols = maxCols,
            liveRows = maxRows,
            page = page,
            assignedCol = emptySlot?.x,
            assignedRow = emptySlot?.y
        )
        if (emptySlot == null) return false

        val (xF, yF) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
            emptySlot.x, emptySlot.y, maxCols, maxRows, context, app.spanX, app.spanY
        )

        val injectedItem = app.copy(
            page = page,
            column = emptySlot.x,
            row = emptySlot.y,
            xFraction = xF,
            yFraction = yF,
            containerId = -1L
        )
        dao.insertItem(injectedItem)
        return true
    }
    suspend fun injectShortcut(
        app: HomeScreenItem,
        page: Int,
        maxCols: Int,
        maxRows: Int,
        dao: HomeScreenDao,
        currentItems: List<HomeScreenItem>,
        visualPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap(),
        context: android.content.Context
    ): Boolean {
        return injectAppIcon(app, page, maxCols, maxRows, dao, currentItems, visualPositions, context)
    }
}
