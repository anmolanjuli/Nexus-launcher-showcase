package com.nexus.launcher.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * The [HomeScreenDao] the whole app is given. In the base shape (portrait phone) every call goes
 * straight to Room, unchanged. In any other shape, home-grid items (`containerId = -1`,
 * `page >= 0`) are read with their position for that shape: a stored `item_positions` row if the
 * user placed the item there by hand, otherwise a position derived live from its base position
 * (so it keeps tracking portrait changes until it is edited). Position writes land in
 * `item_positions` as manual rows — the row in `home_screen_items` keeps its base position.
 *
 * Dock items, drawer entries and folder children have a single position shared by every shape
 * and always pass straight through.
 */
class ShapeAwareHomeScreenDao(
    val base: HomeScreenDao,
    val positions: ItemPositionDao
) : HomeScreenDao by base {

    private val shape: String get() = LayoutShapeState.active.value
    private val isBase: Boolean get() = LayoutShapeState.isBase

    // ---- reads ----

    /**
     * Items and every shape's positions stay subscribed, so a rotation only re-maps what is
     * already in memory — no new query between the old frame and the new one.
     */
    override fun getAllItems(): Flow<List<HomeScreenItem>> =
        combine(
            LayoutShapeState.active, base.getAllItems(), positions.getAllPositionsFlow(),
            LayoutShapeState.deriveEpoch
        ) { active, items, pos, _ ->
            lastItems = items
            lastPositions = pos
            mapForShape(active, items, pos)
        }.distinctUntilChanged()

    @Volatile private var lastItems: List<HomeScreenItem>? = null
    @Volatile private var lastPositions: List<ItemPosition> = emptyList()

    /**
     * The latest items mapped for the shape active right now, without waiting for [getAllItems]
     * to re-emit. A rotation uses this to put items in their new-shape positions in the very
     * first frame; the stream catches up with the same list a moment later.
     */
    fun mappedNow(): List<HomeScreenItem>? {
        val items = lastItems ?: return null
        return mapForShape(LayoutShapeState.active.value, items, lastPositions)
    }

    override suspend fun getAllItemsDebug(): List<HomeScreenItem> =
        if (isBase) base.getAllItemsDebug() else mappedHomeItems()

    override suspend fun getItemById(id: Int): HomeScreenItem? {
        val item = base.getItemById(id) ?: return null
        if (isBase || !isEligible(item)) return item
        return mappedHomeItems().firstOrNull { it.id == id } ?: item
    }

    override suspend fun getItemPage(itemId: Long): Int? =
        if (isBase) base.getItemPage(itemId) else getItemById(itemId.toInt())?.page

    override suspend fun getItemsForPage(page: Int): List<HomeScreenItem> =
        if (isBase || page < 0) base.getItemsForPage(page)
        else mappedHomeItems().filter { it.page == page }

    override suspend fun getItemsAbovePage(page: Int): List<HomeScreenItem> =
        if (isBase) base.getItemsAbovePage(page)
        else mappedHomeItems().filter { it.page > page }

    override suspend fun isCellOccupied(page: Int, col: Int, row: Int): Int {
        if (isBase || page < 0) return base.isCellOccupied(page, col, row)
        return mappedHomeItems().count {
            it.page == page && it.column == col && it.row == row &&
                !(it.appWidgetId == -1 && (it.itemType == 3 || it.itemType == 4))
        }
    }

    override suspend fun isFractionOccupied(page: Int, xF: Float, yF: Float): Int {
        if (isBase || page < 0) return base.isFractionOccupied(page, xF, yF)
        return mappedHomeItems().count {
            it.page == page && abs(it.xFraction - xF) < 0.1f && abs(it.yFraction - yF) < 0.1f
        }
    }

    override suspend fun findItemId(packageName: String, page: Int): Int? {
        if (isBase || page < 0) return base.findItemId(packageName, page)
        return mappedHomeItems().firstOrNull { it.packageName == packageName && it.page == page }?.id
    }

    override suspend fun removeItem(packageName: String, page: Int) {
        if (isBase || page < 0) return base.removeItem(packageName, page)
        mappedHomeItems().filter { it.packageName == packageName && it.page == page }
            .forEach { base.removeItemByIdRaw(it.id) }
    }

    override suspend fun deleteItemsOnPage(page: Int) {
        if (isBase || page < 0) return base.deleteItemsOnPage(page)
        mappedHomeItems().filter { it.page == page }.forEach { base.removeItemByIdRaw(it.id) }
    }

    // ---- writes ----

    override suspend fun updateItemPosition(
        id: Int, page: Int, xFraction: Float, yFraction: Float, column: Int, row: Int
    ) {
        val raw = base.getItemById(id)
        if (isBase || raw == null || raw.containerId != -1L || page < 0) {
            return base.updateItemPosition(id, page, xFraction, yFraction, column, row)
        }
        val current = getItemById(id)
        ensureBasePosition(raw, page)
        positions.upsertPosition(
            ItemPosition(
                id, shape, page, column, row, xFraction, yFraction,
                current?.spanX ?: raw.spanX, current?.spanY ?: raw.spanY, isManual = true
            )
        )
    }

    override suspend fun updateWidgetBounds(
        id: Int, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float, column: Int, row: Int
    ) {
        val raw = base.getItemById(id)
        if (isBase || raw == null || !isEligible(raw)) {
            return base.updateWidgetBounds(id, spanX, spanY, xFraction, yFraction, column, row)
        }
        val page = getItemById(id)?.page ?: raw.page
        positions.upsertPosition(
            ItemPosition(id, shape, page, column, row, xFraction, yFraction, spanX, spanY, isManual = true)
        )
    }

    override suspend fun updateItemPage(id: Int, newPage: Int) {
        val raw = base.getItemById(id)
        if (isBase || raw == null || !isEligible(raw) || newPage < 0) {
            return base.updateItemPage(id, newPage)
        }
        val current = positionFrom(getItemById(id) ?: raw)
        positions.upsertPosition(current.copy(page = newPage))
    }

    override suspend fun updateItem(item: HomeScreenItem) {
        if (isBase) return base.updateItem(item)
        if (!isEligible(item)) {
            // Into a folder, the dock or the drawer: one shared position from now on.
            base.updateItem(item)
            positions.deletePositionsForItem(item.id)
            return
        }
        val raw = base.getItemById(item.id) ?: return base.updateItem(item)
        base.updateItem(withBasePosition(item, raw))
        positions.upsertPosition(positionFrom(item))
    }

    override suspend fun insertItem(item: HomeScreenItem) {
        insertItemAndGetId(item)
    }

    override suspend fun insertItemAndGetId(item: HomeScreenItem): Long {
        if (isBase || !isEligible(item)) return base.insertItemAndGetId(item)
        val raw = if (item.id != 0) base.getItemById(item.id) else null
        val id = base.insertItemAndGetId(withBasePosition(item, raw))
        positions.upsertPosition(positionFrom(item.copy(id = id.toInt())))
        return id
    }

    // ---- helpers ----

    private suspend fun mappedHomeItems(): List<HomeScreenItem> =
        mapForShape(shape, base.getAllItemsDebug(), positions.getPositionsForShape(shape))

    /**
     * [item] carrying the base position to store in `home_screen_items`: the row's existing base
     * position when it already had one on the home grid, otherwise a free base cell.
     */
    private suspend fun withBasePosition(item: HomeScreenItem, raw: HomeScreenItem?): HomeScreenItem {
        if (raw != null && isEligible(raw)) {
            return item.copy(
                page = raw.page, column = raw.column, row = raw.row,
                xFraction = raw.xFraction, yFraction = raw.yFraction,
                spanX = raw.spanX, spanY = raw.spanY
            )
        }
        val cell = freeBaseCell(item.page, item.spanX, item.spanY, item.id)
        return item.copy(
            page = cell.page, column = cell.column, row = cell.row,
            xFraction = cell.xFraction, yFraction = cell.yFraction,
            spanX = cell.spanX, spanY = cell.spanY
        )
    }

    /** Gives a row coming onto the home grid from the dock or drawer a real base cell. */
    private suspend fun ensureBasePosition(raw: HomeScreenItem, page: Int) {
        if (isEligible(raw)) return
        val cell = freeBaseCell(page, raw.spanX, raw.spanY, raw.id)
        base.updateItemPosition(raw.id, cell.page, cell.xFraction, cell.yFraction, cell.column, cell.row)
    }

    private data class BaseCell(
        val page: Int, val column: Int, val row: Int,
        val xFraction: Float, val yFraction: Float, val spanX: Int, val spanY: Int
    )

    private suspend fun freeBaseCell(page: Int, spanX: Int, spanY: Int, excludeId: Int): BaseCell {
        val cols = LayoutShapeState.baseColumns.coerceAtLeast(1)
        val rows = LayoutShapeState.baseRows.coerceAtLeast(1)
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, rows)
        val occupied = mutableSetOf<GridCellPlanner.Cell>()
        base.getAllItemsDebug().filter { isEligible(it) && it.id != excludeId }.forEach {
            GridCellPlanner.occupy(
                occupied, it.page, it.column, it.row,
                it.spanX.coerceIn(1, cols), it.spanY.coerceIn(1, rows)
            )
        }
        val cell = GridCellPlanner.place(occupied, page.coerceAtLeast(0), 0, 0, sx, sy, cols, rows)
            ?: GridCellPlanner.Cell(page.coerceAtLeast(0), 0, 0)
        val (xF, yF) = com.nexus.launcher.ui.canvas.CellFractionConverter.cellToFraction(
            cell.column, cell.row, cols, rows, sx, sy
        )
        return BaseCell(cell.page, cell.column, cell.row, xF, yF, sx, sy)
    }

    /** A manual row for [item]'s current position in the active shape — only used for writes. */
    private fun positionFrom(item: HomeScreenItem): ItemPosition = ItemPosition(
        item.id, shape, item.page, item.column, item.row,
        item.xFraction, item.yFraction, item.spanX, item.spanY, isManual = true
    )

    companion object {
        fun isEligible(item: HomeScreenItem): Boolean = item.containerId == -1L && item.page >= 0

        fun apply(item: HomeScreenItem, pos: ItemPosition?): HomeScreenItem =
            if (pos == null || !isEligible(item)) item
            else item.copy(
                page = pos.page, column = pos.column, row = pos.row,
                xFraction = pos.xFraction, yFraction = pos.yFraction,
                spanX = pos.spanX, spanY = pos.spanY
            )

        /**
         * [items] with their positions for [shape]: base positions in the base shape; otherwise
         * the manual rows for [shape] from [allPositions], plus live-derived positions for every
         * other home-grid item.
         */
        fun mapForShape(
            shape: String, items: List<HomeScreenItem>, allPositions: List<ItemPosition>
        ): List<HomeScreenItem> {
            if (shape == ItemPosition.SHAPE_PHONE_PORTRAIT) return items
            val manual = allPositions.filter { it.shape == shape && it.isManual }
            val eligible = items.filter { isEligible(it) }
            val derived = LayoutShapeState.deriver
                ?.invoke(shape, eligible, manual)
                .orEmpty()
            return applyAll(items, manual + derived)
        }

        fun applyAll(items: List<HomeScreenItem>, pos: List<ItemPosition>): List<HomeScreenItem> {
            val byId = pos.associateBy { it.itemId }
            return items.map { apply(it, byId[it.id]) }
                .sortedWith(compareBy({ it.page }, { it.row }, { it.column }))
        }

        /** The raw Room DAO behind [dao] — for code that must work on base positions only. */
        fun baseOf(dao: HomeScreenDao): HomeScreenDao = (dao as? ShapeAwareHomeScreenDao)?.base ?: dao

        fun positionsOf(dao: HomeScreenDao): ItemPositionDao? = (dao as? ShapeAwareHomeScreenDao)?.positions
    }
}
