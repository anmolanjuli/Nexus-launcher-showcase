package com.nexus.launcher.ui.canvas

import android.graphics.Rect
import android.graphics.RectF
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.GridItem

class GridRenderer(
    var columnCount: Int = 5,
    private val rowCount: Int = 12
) {
    var cellHeight: Float = 0f
        private set

    // Drawer layout mode: "grid" (Grid), or "list"/"list_1" / "list_2" (List, 1/2 columns).
    // Search results always lay out as a grid regardless of this.
    var layoutMode: String = "grid"

    /** Columns the A–Z rail / scroll math should assume for the drawer. */
    val railColumns: Int
        get() = when (layoutMode) {
            "list", "list_1" -> 1
            "list_2" -> 2
            else -> columnCount
        }

    // Home-screen grid dimensions, settable at runtime from NexusSettingsData
    var homeColumns: Int = 6
        private set
    var homeRows: Int = 10
        private set

    fun setColumns(cols: Int) {
        columnCount = cols.coerceIn(2, 10)
    }

    fun setHomeColumns(cols: Int) {
        homeColumns = HomeGridBounds.columns(cols)
    }

    fun setHomeRows(rows: Int) {
        homeRows = HomeGridBounds.rows(rows)
    }

    fun calculateGrid(
        items: List<DisplayItem>,
        canvasWidth: Int,
        gridBottom: Int,
        topInset: Int,
        startY: Int = topInset,
        isSearchMode: Boolean = false,
        iconSizeMultiplier: Float = 0.6f,
        leftOffset: Int = 0,
        density: Float = 1f,
        showLabels: Boolean = true,
        twoLineLabels: Boolean = false
    ): List<GridItem> {
        val cellWidth = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = canvasWidth.toFloat(),
            availableHeightPx = 1f,
            columns = columnCount,
            rows = 1
        ).cellWidthPx
        val iconSize = (cellWidth * iconSizeMultiplier).toInt()

        if ((layoutMode == "list" || layoutMode == "list_1") && !isSearchMode) {
            // Single-column list rows: icon left, label drawn to its right
            val listIconSize = iconSize.coerceAtLeast((32f * density).toInt())
            val twoLineExtra = if (showLabels && twoLineLabels) (16f * density).toInt() else 0
            val rowHeight = (listIconSize + (24f * density).toInt() + twoLineExtra)
                .coerceAtLeast((64f * density).toInt())
            this.cellHeight = rowHeight.toFloat()
            val iconLeftMargin = (16f * density).toInt()
            return items.mapIndexed { index, item ->
                val rowTop = startY + (index * rowHeight)
                val iconTop = rowTop + (rowHeight - listIconSize) / 2
                val iconLeft = leftOffset + iconLeftMargin
                val rowRect = Rect(leftOffset, rowTop, leftOffset + canvasWidth, rowTop + rowHeight)
                val anchorRect = Rect(iconLeft, iconTop, iconLeft + listIconSize, iconTop + listIconSize)
                GridItem(item.label, item.icon, item.intent, anchorRect, item.categoryName, rowRect)
            }
        }
        if (layoutMode == "list_2" && !isSearchMode) {
            // Two-column list rows: two items per row, full half-width hit targets
            val halfWidth = canvasWidth / 2
            val list2IconSize = iconSize.coerceAtLeast((28f * density).toInt())
            val twoLineExtra = if (showLabels && twoLineLabels) (14f * density).toInt() else 0
            val rowHeight = (list2IconSize + (20f * density).toInt() + twoLineExtra)
                .coerceAtLeast((56f * density).toInt())
            this.cellHeight = rowHeight.toFloat()
            val iconLeftMargin = (12f * density).toInt()
            return items.mapIndexed { index, item ->
                val row = index / 2
                val col = index % 2
                val itemLeft = leftOffset + col * halfWidth
                val rowTop = startY + (row * rowHeight)
                val iconTop = rowTop + (rowHeight - list2IconSize) / 2
                val iconLeft = itemLeft + iconLeftMargin
                val itemRect = Rect(itemLeft, rowTop, itemLeft + halfWidth, rowTop + rowHeight)
                val anchorRect = Rect(iconLeft, iconTop, iconLeft + list2IconSize, iconTop + list2IconSize)
                GridItem(item.label, item.icon, item.intent, anchorRect, item.categoryName, itemRect)
            }
        }
        
        val labelHeight = when {
            !showLabels -> 40f
            twoLineLabels -> 56f
            else -> 40f
        }
        val subtitleHeight = 24f
        val padding = 24f
        val currentCellHeight = (iconSize + labelHeight + subtitleHeight + padding).toInt()
        this.cellHeight = currentCellHeight.toFloat()
        
        return items.mapIndexedNotNull { index, item ->
            val row = index / columnCount
            val col = index % columnCount
            
            val cellLeft = leftOffset + (col * cellWidth)
            val cellTop = startY + (row * currentCellHeight)
            
            val marginX = (cellWidth - iconSize) / 2
            val marginY = (currentCellHeight - iconSize) / 4
            
            val left = (cellLeft + marginX).toInt()
            val top = (cellTop + marginY).toInt()
            val right = left + iconSize
            val bottom = top + iconSize
            
            if (isSearchMode && (cellTop + currentCellHeight) > gridBottom) {
                null
            } else {
                GridItem(item.label, item.icon, item.intent, Rect(left, top, right, bottom), item.categoryName)
            }
        }
    }

    /**
     * Returns [columns] × [rows] RectF bounds for the home screen grid.
     * Leaves [bottomInset] (dock + nav bar) clear at the bottom.
     */
    fun calculateHomeGrid(
        canvasWidth: Int,
        canvasHeight: Int,
        topInset: Int,
        bottomInset: Int,
        columns: Int = 5,
        rows: Int = 6,
        leftOffset: Int = 0,
        paddingLeftRightDp: Float = 0f,
        paddingTopBottomDp: Float = 0f,
        gapHorizontalDp: Float = 0f,
        gapVerticalDp: Float = 0f,
        density: Float = 1f
    ): List<RectF> {
        val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = canvasWidth.toFloat(),
            availableHeightPx = (canvasHeight - topInset - bottomInset).toFloat(),
            columns = columns,
            rows = rows,
            paddingLeftRightDp = paddingLeftRightDp,
            paddingTopBottomDp = paddingTopBottomDp,
            gapHorizontalDp = gapHorizontalDp,
            gapVerticalDp = gapVerticalDp,
            density = density,
            caller = HomeGridOverlapDiag.CALLER_HOME,
            statusBarPx = (topInset - HomeGridAvailableSpace.TOP_INSET_EXTRA_PX)
                .coerceAtLeast(0),
            topInsetPx = topInset,
            dockSource = HomeGridOverlapDiag.homeDockSource,
            dockReservePx = bottomInset,
            dockMeasuredHeightPx = HomeGridOverlapDiag.homeDockMeasuredHeightPx
        )
        return (0 until rows).flatMap { row ->
            (0 until columns).map { col ->
                metrics.getCellBounds(
                    col = col,
                    row = row,
                    spanX = 1,
                    spanY = 1,
                    gridAreaLeft = leftOffset.toFloat(),
                    gridAreaTop = topInset.toFloat()
                )
            }
        }
    }

    /**
     * Returns (column, row) for the cell at ([x], [y]), or null if outside the grid.
     */
    fun getCellAtPosition(
        x: Float,
        y: Float,
        canvasWidth: Int,
        canvasHeight: Int,
        topInset: Int,
        bottomInset: Int,
        columns: Int = 5,
        rows: Int = 6,
        paddingLeftRightDp: Float = 0f,
        paddingTopBottomDp: Float = 0f,
        gapHorizontalDp: Float = 0f,
        gapVerticalDp: Float = 0f,
        density: Float = 1f
    ): Pair<Int, Int>? {
        val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = canvasWidth.toFloat(),
            availableHeightPx = (canvasHeight - topInset - bottomInset).toFloat(),
            columns = columns,
            rows = rows,
            paddingLeftRightDp = paddingLeftRightDp,
            paddingTopBottomDp = paddingTopBottomDp,
            gapHorizontalDp = gapHorizontalDp,
            gapVerticalDp = gapVerticalDp,
            density = density
        )
        val cellWidth = metrics.cellWidthPx
        val cellHeight = metrics.cellHeightPx

        val col = (x / cellWidth).toInt()
        val row = ((y - topInset) / cellHeight).toInt()

        if (col < 0 || col >= columns || row < 0 || row >= rows) return null
        return Pair(col, row)
    }
}
