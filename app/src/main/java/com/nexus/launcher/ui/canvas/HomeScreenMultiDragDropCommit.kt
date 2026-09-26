package com.nexus.launcher.ui.canvas

/** Commits a batch home-screen drop after multi-select drag. */
internal object HomeScreenMultiDragDropCommit {

    fun commit(
        view: LauncherCanvasView,
        handler: HomeScreenMultiDragHandler,
        dropX: Float,
        dropY: Float,
        onDropped: (Int, Int, Float, Float, Int, Int) -> Unit
    ): Boolean {
        val cell = CanvasHitTestHelper.getCellAtDrop(view, dropX, dropY) ?: return false
        val placements = HomeScreenBatchPlacement.commit(
            view, cell.first, cell.second, handler
        ) ?: return false
        if (placements.isEmpty()) return false

        val page = view.currentPage
        val cols = view.currentGridCols
        val rows = view.currentGridRows
        val updatedById = mutableMapOf<Int, com.nexus.launcher.data.HomeScreenItem>()

        for (slot in placements) {
            val item = view.homeScreenItems.firstOrNull { it.id == slot.id } ?: return false
            val (xf, yf) = DrawEngineLayout.cellToFraction(
                slot.col, slot.row, cols, rows, view.context,
                item.spanX.coerceAtLeast(1), item.spanY.coerceAtLeast(1)
            )
            updatedById[slot.id] = item.copy(
                page = page,
                column = slot.col,
                row = slot.row,
                xFraction = xf,
                yFraction = yf
            )
        }

        view.homeScreenItems = view.homeScreenItems.map { updatedById[it.id] ?: it }
        for (slot in placements) {
            val updated = updatedById[slot.id] ?: continue
            onDropped(updated.id, page, updated.xFraction, updated.yFraction, slot.col, slot.row)
        }
        handler.finish()
        return true
    }
}
