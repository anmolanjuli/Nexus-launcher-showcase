package com.nexus.launcher.data

/**
 * Pure grid-cell placement math (no Android types) for giving home items a position in a grid
 * of a different shape — the landscape grid on first rotation, or the base grid for an item
 * created while another shape was showing.
 */
object GridCellPlanner {

    /** Page count searched past the starting page before giving up. */
    private const val MAX_PAGE_SEARCH = 50

    data class Cell(val page: Int, val column: Int, val row: Int)

    data class Placement(
        val id: Int,
        val page: Int,
        val column: Int,
        val row: Int,
        val spanX: Int,
        val spanY: Int
    )

    /**
     * An item to place: its position and span in the SOURCE grid.
     *
     * [spanX]/[spanY] is what it covers; [tightSpanX]/[tightSpanY] is the smallest it can be
     * asked to occupy before overlapping something. A free-sized widget covers a fractional
     * number of cells and has to round up, which on a five-row landscape grid costs a third of
     * its height; when the page is too full for that, the tighter figure is tried before
     * anything is allowed to overlap.
     */
    data class Source(
        val id: Int,
        val page: Int,
        val column: Int,
        val row: Int,
        val spanX: Int,
        val spanY: Int,
        val tightSpanX: Int = spanX,
        val tightSpanY: Int = spanY,
    )

    fun occupy(occupied: MutableSet<Cell>, page: Int, column: Int, row: Int, spanX: Int, spanY: Int) {
        for (r in row until row + spanY) for (c in column until column + spanX) {
            occupied.add(Cell(page, c, r))
        }
    }

    fun fits(
        occupied: Set<Cell>, page: Int, column: Int, row: Int,
        spanX: Int, spanY: Int, columns: Int, rows: Int
    ): Boolean {
        if (column < 0 || row < 0 || column + spanX > columns || row + spanY > rows) return false
        for (r in row until row + spanY) for (c in column until column + spanX) {
            if (Cell(page, c, r) in occupied) return false
        }
        return true
    }

    /** Free top-left cell on [page] closest to ([targetColumn], [targetRow]), or null if full. */
    fun nearestFree(
        occupied: Set<Cell>, page: Int, targetColumn: Int, targetRow: Int,
        spanX: Int, spanY: Int, columns: Int, rows: Int
    ): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestDistance = Int.MAX_VALUE
        for (r in 0..rows - spanY) for (c in 0..columns - spanX) {
            if (!fits(occupied, page, c, r, spanX, spanY, columns, rows)) continue
            val distance = (r - targetRow) * (r - targetRow) + (c - targetColumn) * (c - targetColumn)
            if (distance < bestDistance) {
                bestDistance = distance
                best = c to r
            }
        }
        return best
    }

    /**
     * Places one item: nearest free cell to the target on [page], else the first page after it
     * with room. Returns null only if [MAX_PAGE_SEARCH] pages are all full.
     */
    fun place(
        occupied: MutableSet<Cell>, page: Int, targetColumn: Int, targetRow: Int,
        spanX: Int, spanY: Int, columns: Int, rows: Int
    ): Cell? {
        val sx = spanX.coerceIn(1, columns)
        val sy = spanY.coerceIn(1, rows)
        for (p in page..page + MAX_PAGE_SEARCH) {
            val target = if (p == page) targetColumn to targetRow else 0 to 0
            val cell = nearestFree(occupied, p, target.first, target.second, sx, sy, columns, rows)
                ?: continue
            occupy(occupied, p, cell.first, cell.second, sx, sy)
            return Cell(p, cell.first, cell.second)
        }
        return null
    }

    /**
     * Maps [sources] from a [fromColumns] x [fromRows] grid into a [toColumns] x [toRows] grid,
     * keeping each item near the same relative spot on the same page.
     *
     * When the target is about two source-widths wide (one column short is fine) — a 6x10
     * portrait page into an 11- or 12-column landscape one — the page is folded instead: its top
     * half goes on the left and its bottom half on the right, so every item keeps its
     * neighbours. At 11 columns the halves share the middle column. If the target has fewer
     * rows than half a source page (e.g. 4 vs 5), each half's rows are compressed
     * proportionally. Clashes move to the nearest free cell.
     * Otherwise positions are scaled proportionally. Multi-cell items are
     * placed first (they are the hardest to fit), then the rest in reading order. Cells already
     * in [occupied] are respected and it is updated with every placement.
     */
    fun mapInto(
        sources: List<Source>,
        fromColumns: Int, fromRows: Int,
        toColumns: Int, toRows: Int,
        occupied: MutableSet<Cell>
    ): List<Placement> {
        // Biggest first, not merely "bigger than one cell": with several multi-cell widgets the
        // old order packed a 2x2 into the corner a 4x3 needed, and the 4x3 then had nowhere to
        // go on its page.
        val ordered = sources.sortedWith(
            compareByDescending<Source> { it.spanX * it.spanY }
                .thenBy { it.page }.thenBy { it.row }.thenBy { it.column }
        )
        val half = (fromRows + 1) / 2
        val fold = fromColumns > 1 && toColumns >= 2 * fromColumns - 1
        val bottomHalfOffset = minOf(fromColumns, toColumns - fromColumns)
        // Each half is compressed onto the rows it actually uses, so a landscape page fills its
        // height instead of copying the portrait page's empty rows. Mapping row for row left the
        // bottom of the screen bare whenever the portrait layout had a gap — which it usually
        // does, since the icons live in the lower rows and those are not items here.
        // Per page: a page with two widgets near the top should fill the screen on its own,
        // whatever the next page holds.
        val bands = sources.groupBy { it.page to (it.row >= half) }
            .mapValues { (_, group) -> occupiedRows(group) }
        val result = mutableListOf<Placement>()
        for (s in ordered) {
            val sx = s.spanX.coerceIn(1, toColumns)
            val sy = s.spanY.coerceIn(1, toRows)
            val (targetColumn, targetRow) = if (fold) {
                // Items straddling the fold stay in the top half.
                val bottomHalf = s.row >= half
                val col = s.column + if (bottomHalf) bottomHalfOffset else 0
                val band = bands[s.page to bottomHalf]
                val row = stretched(s.row, s.spanY, sy, band, toRows)
                col.coerceIn(0, toColumns - sx) to row.coerceIn(0, toRows - sy)
            } else {
                val centerX = (s.column + s.spanX / 2f) / fromColumns.coerceAtLeast(1)
                val centerY = (s.row + s.spanY / 2f) / fromRows.coerceAtLeast(1)
                Math.round(centerX * toColumns - sx / 2f).coerceIn(0, toColumns - sx) to
                    Math.round(centerY * toRows - sy / 2f).coerceIn(0, toRows - sy)
            }
            val page = s.page.coerceAtLeast(0)
            val tx = s.tightSpanX.coerceIn(1, sx)
            val ty = s.tightSpanY.coerceIn(1, sy)
            // Its own page only: a derived layout re-arranges a page, it does not empty it onto
            // the next one. Full footprint first, then the tighter one, and only then a spot it
            // has to share — which the user can see and move; a widget on another page just
            // looks lost.
            var used = sx to sy
            var free = nearestFree(occupied, page, targetColumn, targetRow, sx, sy, toColumns, toRows)
            if (free == null && (tx < sx || ty < sy)) {
                free = nearestFree(occupied, page, targetColumn, targetRow, tx, ty, toColumns, toRows)
                if (free != null) used = tx to ty
            }
            val cell = free
                ?.let { (c, r) -> occupy(occupied, page, c, r, used.first, used.second); Cell(page, c, r) }
                ?: leastCrowded(occupied, page, targetColumn, targetRow, tx, ty, toColumns, toRows)
                    .also { occupy(occupied, page, it.column, it.row, tx, ty) }
                    .also { used = tx to ty }
            result.add(Placement(s.id, cell.page, cell.column, cell.row, used.first, used.second))
        }
        return result
    }

    /**
     * The position that treads on the fewest taken cells, nearest the target among equals.
     *
     * When a page cannot hold everything, something has to share. Dropping the item on its
     * target buries whatever is there; this finds the emptiest corner instead, so what is left
     * is usually a sliver of one neighbour rather than a widget sitting on top of another.
     */
    private fun leastCrowded(
        occupied: Set<Cell>, page: Int, targetColumn: Int, targetRow: Int,
        spanX: Int, spanY: Int, columns: Int, rows: Int
    ): Cell {
        val lastCol = (columns - spanX).coerceAtLeast(0)
        val lastRow = (rows - spanY).coerceAtLeast(0)
        var best = Cell(page, targetColumn.coerceIn(0, lastCol), targetRow.coerceIn(0, lastRow))
        var bestCrowd = Int.MAX_VALUE
        var bestDistance = Int.MAX_VALUE
        for (r in 0..lastRow) for (c in 0..lastCol) {
            var crowd = 0
            for (rr in r until r + spanY) for (cc in c until c + spanX) {
                if (Cell(page, cc, rr) in occupied) crowd++
            }
            val distance = (r - targetRow) * (r - targetRow) + (c - targetColumn) * (c - targetColumn)
            if (crowd < bestCrowd || (crowd == bestCrowd && distance < bestDistance)) {
                bestCrowd = crowd
                bestDistance = distance
                best = Cell(page, c, r)
            }
        }
        return best
    }

    /** First and last row a half's items stand on, as a source-row range. */
    private fun occupiedRows(half: List<Source>): IntRange {
        val first = half.minOf { it.row }
        val last = half.maxOf { it.row + it.spanY - 1 }
        return first..last
    }

    /**
     * A source row inside [band], spread across all [toRows] of the target.
     *
     * With one row of content the item goes to the top rather than being stretched to nothing;
     * otherwise the band's first row lands on 0 and its last on the bottom, and everything in
     * between keeps its order and its relative spacing.
     */
    private fun stretched(row: Int, sourceSpanY: Int, spanY: Int, band: IntRange?, toRows: Int): Int {
        band ?: return 0
        val depth = band.last - band.first
        if (depth <= 0) return 0
        val centre = (row + sourceSpanY / 2f - band.first) / (depth + 1f)
        return Math.round(centre * toRows - spanY / 2f)
    }
}
