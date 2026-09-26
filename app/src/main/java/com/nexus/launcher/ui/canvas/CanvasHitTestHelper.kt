package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.HomeScreenItem

object CanvasHitTestHelper {
    /** Returns (col, row) of the home grid cell containing [x],[y], clamped to grid bounds on [targetPage]. */
    fun getCellAtDrop(view: LauncherCanvasView, x: Float, y: Float, targetPage: Int = view.currentPage): Pair<Int, Int>? {
        val (cx, cy) = SelectionModeTransform.mapTouchIfSelecting(view, x, y, targetPage)
        val gridBottom = view.viewHeight - view.dockBottomReserve
        val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = view.gridAreaWidth.toFloat(),
            availableHeightPx = (gridBottom - view.topInset).toFloat(),
            columns = view.currentGridCols,
            rows = view.currentGridRows,
            paddingLeftRightDp = view.homePaddingLeftRightDp,
            paddingTopBottomDp = view.homePaddingTopBottomDp,
            gapHorizontalDp = view.homeGapHorizontalDp,
            gapVerticalDp = view.homeGapVerticalDp,
            density = view.resources.displayMetrics.density
        )

        val col = ((cx - view.gridAreaLeft - metrics.paddingLeftRightPx) / metrics.cellWidthPx).toInt().coerceIn(0, view.currentGridCols - 1)
        val row = ((cy - view.topInset - metrics.paddingTopBottomPx) / metrics.cellHeightPx).toInt().coerceIn(0, view.currentGridRows - 1)

        return Pair(col, row)
    }

    fun getItemAt(view: LauncherCanvasView, x: Float, y: Float): HomeScreenItem? {
        val cell = getCellAtDrop(view, x, y) ?: return null
        return view.homeScreenItems.firstOrNull {
            val p = view.fractionDerivedPositions[it.id] ?: Triple(it.page, it.column, it.row)
            val hitCol = cell.first >= p.second && cell.first <= p.second + it.spanX - 1
            val hitRow = cell.second >= p.third && cell.second <= p.third + it.spanY - 1
            p.first == view.currentPage && hitCol && hitRow &&
            it.containerId == -1L && (it.itemType == 0 || it.itemType == 1 || it.itemType == 2)
        }
    }
}
