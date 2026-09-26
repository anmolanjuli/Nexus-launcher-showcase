package com.nexus.launcher.ui.canvas

import android.content.Context
import com.nexus.launcher.data.HomeScreenItem

object GridOccupancyHelper {
    fun findItemAtCell(
        context: Context,
        items: List<HomeScreenItem>,
        page: Int,
        col: Int,
        row: Int,
        visualPositions: Map<Int, Triple<Int, Int, Int>>,
        excludeIds: List<Int> = emptyList(),
        spanX: Int = 1,
        spanY: Int = 1
    ): HomeScreenItem? {
        val match = items.firstOrNull { item ->
            if (item.id in excludeIds) return@firstOrNull false
            if (item.itemType == 3 && item.appWidgetId == -1) return@firstOrNull false
            if (item.containerId != -1L) return@firstOrNull false
            val p = visualPositions[item.id] ?: run {
                val isFractionBound = (item.itemType == 3 || item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC || item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX) &&
                    !(item.xFraction == 0f && item.yFraction == 0f)
                if (isFractionBound && (item.column < 0 || item.row < 0)) {
                    val (liveCols, liveRows) = HomeGridBounds.liveOrDefault(context)
                    val (c, r) = OverlayFractionGrid.originCellOrFallback(
                        context, item.xFraction, item.yFraction, item.spanX, item.spanY,
                        item.folderConfigJson,
                        liveCols,
                        liveRows
                    )
                    Triple(item.page, c, r)
                } else {
                    Triple(item.page, item.column, item.row)
                }
            }

            if (p.first != page) return@firstOrNull false

            val isWidgetLike = item.itemType == 3 || item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC || item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX
            val canvas = if (isWidgetLike) {
                com.nexus.launcher.ui.folder.FolderBlurCoordinator.findCanvas(context)
            } else null
            val hit: Boolean
            if (isWidgetLike && canvas != null && canvas.viewWidth > 0) {
                hit = OverlayFractionGrid.overlapsDropTarget(canvas, item, col, row, spanX, spanY)
            } else {
                val otherLeft = p.second
                val otherRight = p.second + item.spanX - 1
                val otherTop = p.third
                val otherBottom = p.third + item.spanY - 1
                val thisLeft = col
                val thisRight = col + spanX - 1
                val thisTop = row
                val thisBottom = row + spanY - 1
                hit = thisLeft <= otherRight && thisRight >= otherLeft &&
                    thisTop <= otherBottom && thisBottom >= otherTop
            }

            hit && (item.itemType == 0 || item.itemType == 1 || item.itemType == 2 || item.itemType == 3 || item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC || item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX)
        }
        return match
    }

    /**
     * Performs a spiral (Chebyshev distance) search to find the nearest empty slot.
     * Ideal for relocation scenarios where an item should land near its original/desired position.
     */
    fun findNearestEmptySlot(
        context: Context,
        desiredCol: Int,
        desiredRow: Int,
        spanX: Int,
        spanY: Int,
        maxCols: Int,
        maxRows: Int,
        items: List<HomeScreenItem>,
        visualPositions: Map<Int, Triple<Int, Int, Int>>,
        excludeIds: List<Int> = emptyList(),
        page: Int
    ): Pair<Int, Int>? {
        val maxRadius = maxOf(maxCols, maxRows)
        for (r in 0..maxRadius) {
            val minC = maxOf(0, desiredCol - r)
            val maxC = minOf(maxCols - spanX, desiredCol + r)
            val minR = maxOf(0, desiredRow - r)
            val maxR = minOf(maxRows - spanY, desiredRow + r)

            val ringCells = mutableListOf<Pair<Int, Int>>()
            for (c in minC..maxC) {
                for (row in minR..maxR) {
                    val dist = maxOf(Math.abs(c - desiredCol), Math.abs(row - desiredRow))
                    if (dist == r) {
                        ringCells.add(Pair(c, row))
                    }
                }
            }

            // Sort ring cells by Euclidean distance for better locality
            ringCells.sortBy { (c, row) ->
                (c - desiredCol) * (c - desiredCol) + (row - desiredRow) * (row - desiredRow)
            }

            for ((c, row) in ringCells) {
                if (findItemAtCell(context, items, page, c, row, visualPositions, excludeIds, spanX, spanY) == null) {
                    return Pair(c, row)
                }
            }
        }
        return null
    }
}
