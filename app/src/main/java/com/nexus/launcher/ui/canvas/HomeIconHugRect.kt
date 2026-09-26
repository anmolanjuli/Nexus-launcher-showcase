package com.nexus.launcher.ui.canvas

import android.graphics.Rect
import android.graphics.RectF
import com.nexus.launcher.data.HomeScreenItem

/**
 * Context-menu / spotlight bounds for a home grid item.
 *
 * Uses the full span footprint to find the drawn icon centre, then returns a 1×1 canonical-size
 * rect centred there, so a menu hugs the middle of a resized icon rather than the top-left origin
 * cell.
 *
 */
object HomeIconHugRect {

    fun forItem(view: LauncherCanvasView, item: HomeScreenItem): Rect {
        val pos = view.fractionDerivedPositions[item.id]
            ?: Triple(item.page, item.column, item.row)
        val cols = view.currentGridCols
        val rows = view.currentGridRows
        val originCol = pos.second
        val originRow = pos.third
        val cellIndex = originRow * cols + originCol
        if (cellIndex !in view.homeGridCells.indices) return Rect()

        var cell = RectF(view.homeGridCells[cellIndex])
        if (item.spanX > 1 || item.spanY > 1) {
            val endCol = (originCol + item.spanX - 1).coerceAtMost(cols - 1)
            val endRow = (originRow + item.spanY - 1).coerceAtMost(rows - 1)
            val endCellIndex = endRow * cols + endCol
            if (endCellIndex in view.homeGridCells.indices) {
                val endCell = view.homeGridCells[endCellIndex]
                cell = RectF(cell.left, cell.top, endCell.right, endCell.bottom)
            }
        }

        val fullIconRect = view.homeScreenRenderer.getIconRect(item, cell)
        if (fullIconRect.isEmpty) return Rect()

        val canonical = view.homeScreenRenderer.getIconRect(
            item.copy(spanX = 1, spanY = 1),
            RectF(view.homeGridCells[cellIndex])
        )
        val halfW = canonical.width() / 2f
        val halfH = canonical.height() / 2f
        val cx = fullIconRect.exactCenterX()
        val cy = fullIconRect.exactCenterY()
        return Rect(
            (cx - halfW).toInt(),
            (cy - halfH).toInt(),
            (cx + halfW).toInt(),
            (cy + halfH).toInt()
        )
    }
}
