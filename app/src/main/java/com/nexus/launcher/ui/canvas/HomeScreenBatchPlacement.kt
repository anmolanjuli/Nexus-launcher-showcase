package com.nexus.launcher.ui.canvas

/** Batch placement for multi-select drag — packs selected items as a tight group at the drop point. */
internal object HomeScreenBatchPlacement {

    data class Slot(val id: Int, val col: Int, val row: Int)

    fun preview(
        view: LauncherCanvasView,
        dropCol: Int,
        dropRow: Int,
        handler: HomeScreenMultiDragHandler
    ): List<Slot>? = compute(view, dropCol, dropRow, handler)

    fun commit(
        view: LauncherCanvasView,
        dropCol: Int,
        dropRow: Int,
        handler: HomeScreenMultiDragHandler
    ): List<Slot>? = compute(view, dropCol, dropRow, handler)

    private data class RectSpan(val col: Int, val row: Int, val spanX: Int, val spanY: Int)

    private fun compute(
        view: LauncherCanvasView,
        dropCol: Int,
        dropRow: Int,
        handler: HomeScreenMultiDragHandler
    ): List<Slot>? {
        if (!handler.isActive) return null
        val anchorId = handler.anchorId() ?: return null
        val origins = handler.originPositions()
        val page = view.currentPage
        val cols = view.currentGridCols
        val rows = view.currentGridRows
        if (dropCol !in 0 until cols || dropRow !in 0 until rows) return null

        var pageMemberIds = handler.memberIds()
            .filter { (origins[it]?.first ?: -1) == page }
        if (pageMemberIds.isEmpty()) {
            pageMemberIds = handler.memberIds().toList()
        }
        if (anchorId !in pageMemberIds || pageMemberIds.isEmpty()) return null

        val anchorItem = view.homeScreenItems.firstOrNull { it.id == anchorId } ?: return null
        val anchorOrigin = origins[anchorId] ?: return null

        val anchorSpanX = anchorItem.spanX.coerceAtLeast(1)
        val anchorSpanY = anchorItem.spanY.coerceAtLeast(1)
        val rawCol = dropCol - (anchorSpanX - 1) / 2f
        val rawRow = dropRow - (anchorSpanY - 1) / 2f
        val startCol = Math.round(rawCol).coerceIn(0, (cols - anchorSpanX).coerceAtLeast(0))
        val startRow = Math.round(rawRow).coerceIn(0, (rows - anchorSpanY).coerceAtLeast(0))

        val sortedIds = pageMemberIds.sortedWith(
            compareBy<Int> { if (it == anchorId) 0 else 1 }
                .thenBy { origins[it]?.third ?: 0 }
                .thenBy { origins[it]?.second ?: 0 }
        )

        val exclude = pageMemberIds.toList()
        val placements = mutableListOf<Slot>()
        val reserved = mutableListOf<RectSpan>()
        var cursorCol = startCol
        var cursorRow = startRow

        for (id in sortedIds) {
            val item = view.homeScreenItems.firstOrNull { it.id == id } ?: return null
            val spanX = item.spanX.coerceAtLeast(1)
            val spanY = item.spanY.coerceAtLeast(1)
            val slot = findCompactSlot(
                view, page, cols, rows, startCol, startRow,
                cursorCol, cursorRow, spanX, spanY, exclude, reserved
            ) ?: return null
            placements.add(Slot(id, slot.first, slot.second))
            reserved.add(RectSpan(slot.first, slot.second, spanX, spanY))
            cursorCol = slot.first + spanX
            cursorRow = slot.second
            if (cursorCol + spanX > cols) {
                cursorCol = startCol
                cursorRow += spanY
            }
        }
        return placements
    }

    /** Scan right from cursor, wrap to the next row at [startCol] when the row is full. */
    private fun findCompactSlot(
        view: LauncherCanvasView,
        page: Int,
        cols: Int,
        rows: Int,
        startCol: Int,
        startRow: Int,
        initialCol: Int,
        initialRow: Int,
        spanX: Int,
        spanY: Int,
        exclude: List<Int>,
        reserved: List<RectSpan>
    ): Pair<Int, Int>? {
        var col = initialCol
        var row = initialRow
        val maxAttempts = cols * rows
        repeat(maxAttempts) {
            if (row + spanY > rows) return null
            if (col + spanX <= cols &&
                !overlapsReserved(col, row, spanX, spanY, reserved) &&
                GridOccupancyHelper.findItemAtCell(
                    view.context, view.homeScreenItems, page,
                    col, row, view.fractionDerivedPositions, exclude, spanX, spanY
                ) == null
            ) {
                return col to row
            }
            col++
            if (col + spanX > cols) {
                col = startCol
                row++
            }
        }
        return null
    }

    private fun overlapsReserved(
        col: Int, row: Int, spanX: Int, spanY: Int, reserved: List<RectSpan>
    ): Boolean {
        val left = col
        val right = col + spanX - 1
        val top = row
        val bottom = row + spanY - 1
        for (r in reserved) {
            val oLeft = r.col
            val oRight = r.col + r.spanX - 1
            val oTop = r.row
            val oBottom = r.row + r.spanY - 1
            if (left <= oRight && right >= oLeft && top <= oBottom && bottom >= oTop) return true
        }
        return false
    }
}
