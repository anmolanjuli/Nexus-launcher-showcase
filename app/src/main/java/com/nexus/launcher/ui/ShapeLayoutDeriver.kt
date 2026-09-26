package com.nexus.launcher.ui

import android.content.Context
import android.graphics.RectF
import com.nexus.launcher.data.GridCellPlanner
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.ItemPosition
import com.nexus.launcher.data.LayoutShapeState
import com.nexus.launcher.ui.canvas.CellFractionConverter
import com.nexus.launcher.ui.canvas.LandscapeGridSpec
import com.nexus.launcher.ui.widgets.WidgetFreeSize
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Live positions for a non-base shape (phone landscape, large screen) for every home-grid item
 * the user has not placed by hand in that shape. Installed as [LayoutShapeState.deriver].
 *
 * Positions are never stored: they are recomputed from the items' base (portrait) positions on
 * every read, so they keep tracking portrait changes. Only a manual placement is stored (see
 * ShapeAwareHomeScreenDao), and derived items then avoid its cells. The mapping is deterministic
 * — same inputs, same layout — so nothing moves between reads.
 */
class ShapeLayoutDeriver(context: Context) {

    private val appContext = context.applicationContext

    /** Below this a stored fraction is treated as agreeing with its cell. */
    private val DRIFT = 0.004f

    /** The measured grid of one shape, from its last settled layout. */
    class Geometry(
        val columns: Int,
        val rows: Int,
        val cells: List<RectF>,
        val frameLeft: Float,
        val frameWidth: Float,
        val overlayHeight: Float,
        /** The whole view, which is the basis a free size is stored against. */
        val viewWidth: Float = 0f,
    )

    @Volatile private var geometries: Map<String, Geometry> = emptyMap()

    /** Records [shape]'s grid after it has been laid out; returns true if it changed. */
    fun recordGeometry(shape: String, geometry: Geometry): Boolean {
        val old = geometries[shape]
        val changed = old == null || old.columns != geometry.columns || old.rows != geometry.rows ||
            old.cells != geometry.cells || old.frameLeft != geometry.frameLeft ||
            old.frameWidth != geometry.frameWidth || old.overlayHeight != geometry.overlayHeight ||
            old.viewWidth != geometry.viewWidth
        if (changed) geometries = geometries + (shape to geometry)
        return changed
    }

    fun derive(
        shape: String,
        items: List<HomeScreenItem>,
        manual: List<ItemPosition>
    ): List<ItemPosition> {
        val (cols, rows) = gridSize(shape) ?: return emptyList()
        if (cols <= 0 || rows <= 0) return emptyList()
        val placedByHand = manual.map { it.itemId }.toSet()
        val byId = items.associateBy { it.id }
        val free = items.filter { it.id !in placedByHand }

        // What each item really covers, and the least it can be asked to take. A free-sized
        // widget covers a fractional number of cells; rounding every one of them up is honest
        // but expensive — a 615px widget on a 180px pitch reserves four columns for three and a
        // half — and on a page that is nearly full the cost is an item with nowhere to go.
        val cover = { item: HomeScreenItem?, sx: Int, sy: Int -> coveredSpan(shape, item, sx, sy) }
        val tight = { item: HomeScreenItem?, sx: Int, sy: Int -> tightSpan(shape, item, sx, sy) }

        // Per page: if what everything covers will not fit, everything on that page is measured
        // tightly instead. Deciding this up front beats discovering it item by item, where
        // whoever is placed first takes the roomy footprint and the last one overlaps.
        val capacity = cols * rows
        val needed = mutableMapOf<Int, Int>()
        // Measured at full size: the question being asked is whether everything fits as it is.
        WidgetFreeSize.cappedPages = emptySet()
        free.forEach {
            val (sx, sy) = cover(it, it.spanX, it.spanY)
            needed[it.page] = (needed[it.page] ?: 0) + sx * sy
        }
        manual.forEach {
            val (sx, sy) = cover(byId[it.itemId], it.spanX, it.spanY)
            needed[it.page] = (needed[it.page] ?: 0) + sx * sy
        }
        val squeezed = needed.filterValues { it > capacity }.keys
        // From here on those pages are measured and drawn capped — the footprints below, and
        // every binder that asks WidgetFreeSize for a size.
        WidgetFreeSize.cappedPages = if (shape == ItemPosition.SHAPE_PHONE_PORTRAIT) emptySet() else squeezed

        val occupied = mutableSetOf<GridCellPlanner.Cell>()
        manual.forEach {
            // What it really covers, not what its row says: a free-sized box keeps its portrait
            // pixel size, so a box stored as 2x1 can be drawing across four cells and two rows,
            // and the cells it is standing on have to count as taken or the next item is put
            // underneath it.
            val measure = if (it.page in squeezed) tight else cover
            val (spanX, spanY) = measure(byId[it.itemId], it.spanX, it.spanY)
            GridCellPlanner.occupy(
                occupied, it.page, it.column, it.row,
                spanX.coerceIn(1, cols), spanY.coerceIn(1, rows)
            )
        }
        val sources = free.map {
            // Its real footprint in this shape, so two free-sized widgets cannot be given
            // overlapping cells the way a stored 3x2 that draws 2.7 rows tall allows.
            val measure = if (it.page in squeezed) tight else cover
            val (spanX, spanY) = measure(it, it.spanX, it.spanY)
            val (tightX, tightY) = tight(it, it.spanX, it.spanY)
            GridCellPlanner.Source(
                it.id, it.page, it.column, it.row, spanX, spanY, tightX, tightY,
            )
        }
        val placements = GridCellPlanner.mapInto(
            sources, LayoutShapeState.baseColumns, LayoutShapeState.baseRows, cols, rows, occupied
        )
        val geometry = geometries[shape]?.takeIf { it.columns == cols && it.rows == rows }
        val derived = placements.map { p ->
            val (xF, yF) = fractions(geometry, p, cols, rows)
            ItemPosition(p.id, shape, p.page, p.column, p.row, xF, yF, p.spanX, p.spanY, isManual = false)
        }
        return derived + repaired(shape, geometry, manual, cols, rows, byId, squeezed)
    }

    /**
     * Hand-placed rows whose stored fraction no longer matches the cell they name, corrected
     * from the measured grid.
     *
     * A fraction written before the shape had ever been laid out comes from
     * [CellFractionConverter], which spreads the rows across the whole view — it knows nothing
     * about the band kept clear at the top for the status row, or the one at the bottom for the
     * dock. Row 0 of 5 becomes 0.1 of the height, which on a 1080px landscape screen is 108px:
     * above the reserve, i.e. drawn across the status row. Once real cells exist the row can be
     * read back properly, and nothing has to be rewritten in the database to fix it.
     */
    private fun repaired(
        shape: String,
        geometry: Geometry?,
        manual: List<ItemPosition>,
        cols: Int,
        rows: Int,
        byId: Map<Int, HomeScreenItem>,
        squeezed: Set<Int>,
    ): List<ItemPosition> {
        geometry ?: return emptyList()
        return manual.mapNotNull { pos ->
            // Centred on the cells it covers, not on the two its row happens to name.
            val (spanX, spanY) = if (pos.page in squeezed) {
                tightSpan(shape, byId[pos.itemId], pos.spanX, pos.spanY)
            } else {
                coveredSpan(shape, byId[pos.itemId], pos.spanX, pos.spanY)
            }
            val p = GridCellPlanner.Placement(
                pos.itemId, pos.page, pos.column, pos.row,
                spanX.coerceAtMost(cols - pos.column), spanY.coerceAtMost(rows - pos.row),
            )
            val (xF, yF) = fractions(geometry, p, cols, rows)
            if (kotlin.math.abs(xF - pos.xFraction) < DRIFT && kotlin.math.abs(yF - pos.yFraction) < DRIFT) {
                return@mapNotNull null
            }
            pos.copy(xFraction = xF, yFraction = yF)
        }
    }

    /**
     * How many cells an item actually covers in [shape].
     *
     * Free-sized widgets and boxes keep their portrait pixel size in every orientation
     * ([WidgetFreeSize]), while the span stored beside them describes the grid they were sized
     * on. Where the two disagree the drawn size wins, because that is what the user sees.
     */
    private fun coveredSpan(shape: String, item: HomeScreenItem?, spanX: Int, spanY: Int): Pair<Int, Int> =
        // Every cell it touches, so neighbours are not given space it is standing in.
        spanFrom(shape, item, spanX, spanY) { ceil(it).toInt() }

    /**
     * The same measurement rounded to nearest: the least an item can be asked to occupy.
     *
     * Rounding up costs a third of a widget's height on a five-row grid, and with several tall
     * widgets a landscape page runs out of rows. This is what the planner falls back to before
     * it lets anything overlap.
     */
    private fun tightSpan(shape: String, item: HomeScreenItem?, spanX: Int, spanY: Int): Pair<Int, Int> =
        spanFrom(shape, item, spanX, spanY) { it.roundToInt() }

    private fun spanFrom(
        shape: String, item: HomeScreenItem?, spanX: Int, spanY: Int, cells: (Float) -> Int,
    ): Pair<Int, Int> {
        val g = geometries[shape] ?: return spanX to spanY
        item ?: return spanX to spanY
        val (wFrac, hFrac) = WidgetFreeSize.readFracs(item.folderConfigJson)
        if (wFrac <= 0f && hFrac <= 0f) return spanX to spanY
        val pitchX = g.cells.getOrNull(1)?.let { it.left - g.cells[0].left } ?: return spanX to spanY
        val pitchY = g.cells.getOrNull(g.columns)?.let { it.top - g.cells[0].top } ?: return spanX to spanY
        if (pitchX <= 0f || pitchY <= 0f || g.viewWidth <= 0f || g.overlayHeight <= 0f) return spanX to spanY
        val (width, height) = WidgetFreeSize.pixelSize(
            item.folderConfigJson, spanX, spanY,
            g.viewWidth, g.overlayHeight, pitchX, pitchY,
            isLandscape = g.viewWidth > g.overlayHeight,
            page = item.page,
        )
        return maxOf(spanX, cells(width / pitchX)).coerceAtLeast(1) to
            maxOf(spanY, cells(height / pitchY)).coerceAtLeast(1)
    }

    /** The grid [shape] is laid out on — the same size the canvas uses for it. */
    private fun gridSize(shape: String): Pair<Int, Int>? = when (shape) {
        ItemPosition.SHAPE_PHONE_LANDSCAPE -> LandscapeGridSpec.sizeIfMeasured(appContext)
            ?: (LayoutShapeState.baseRows to LayoutShapeState.baseColumns)
        ItemPosition.SHAPE_LARGE -> LayoutShapeState.baseColumns to LayoutShapeState.baseRows
        else -> null
    }

    /**
     * Fractions that make the widget overlay paint an item centred on its cells: x across the
     * grid frame (see WidgetCoordinateSpace), y across the overlay height. Falls back to the
     * cell-proportional estimate until the shape has been laid out once.
     */
    private fun fractions(
        geometry: Geometry?, p: GridCellPlanner.Placement, cols: Int, rows: Int
    ): Pair<Float, Float> {
        val g = geometry ?: return CellFractionConverter.cellToFraction(p.column, p.row, cols, rows, p.spanX, p.spanY)
        val first = g.cells.getOrNull(p.row * cols + p.column)
        val last = g.cells.getOrNull((p.row + p.spanY - 1) * cols + (p.column + p.spanX - 1))
        if (first == null || last == null || g.frameWidth <= 0f || g.overlayHeight <= 0f) {
            return CellFractionConverter.cellToFraction(p.column, p.row, cols, rows, p.spanX, p.spanY)
        }
        val cx = (first.left + last.right) / 2f
        val cy = (first.top + last.bottom) / 2f
        return ((cx - g.frameLeft) / g.frameWidth).coerceIn(0f, 1f) to (cy / g.overlayHeight).coerceIn(0f, 1f)
    }
}
