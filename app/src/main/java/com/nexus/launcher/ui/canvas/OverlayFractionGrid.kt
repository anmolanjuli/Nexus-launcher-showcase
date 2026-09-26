package com.nexus.launcher.ui.canvas

import android.graphics.RectF
import android.widget.FrameLayout
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.widgets.WidgetFreeSize
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView

/**
 * Overlay-relative widget/mosaic fractions → home-grid occupancy.
 *
 * yFraction is centerY / overlay height (same as AppWidgetOverlayBinder).
 * A cell is occupied when a placed icon in that cell would be at least
 * [ICON_HIT_FRACTION] covered by the painted widget. Full-cell intersection
 * claimed leftover sliver columns that still look empty.
 */
object OverlayFractionGrid {

    const val ICON_HIT_FRACTION = 0.25f

    data class CellRange(val col0: Int, val row0: Int, val col1: Int, val row1: Int)

    fun originCell(view: LauncherCanvasView, item: HomeScreenItem): Pair<Int, Int> {
        val r = cellRange(view, item)
        return r.col0 to r.row0
    }

    fun originCell(
        view: LauncherCanvasView,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        folderConfigJson: String?
    ): Pair<Int, Int> {
        val r = cellRange(view, xFraction, yFraction, spanX, spanY, folderConfigJson)
        return r.col0 to r.row0
    }

    fun cellRange(view: LauncherCanvasView, item: HomeScreenItem): CellRange {
        return cellRange(
            view, item.xFraction, item.yFraction, item.spanX, item.spanY,
            item.folderConfigJson, overlayChildRect(view, item)
        )
    }

    fun paintedRect(view: LauncherCanvasView, item: HomeScreenItem): RectF {
        return overlayChildRect(view, item)
            ?: computedPaintedRect(
                view, item.xFraction, item.yFraction, item.spanX, item.spanY, item.folderConfigJson
            )
    }

    fun overlapsDropTarget(
        view: LauncherCanvasView,
        item: HomeScreenItem,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int
    ): Boolean {
        return iconOverlapFraction(view, paintedRect(view, item), col, row, spanX, spanY) >= ICON_HIT_FRACTION
    }

    fun iconOverlapFraction(
        view: LauncherCanvasView,
        painted: RectF,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int
    ): Float {
        val drop = dropTargetRect(view, col, row, spanX, spanY) ?: return 0f
        val icon = iconRectInCell(view, drop, spanX, spanY)
        val iconArea = icon.width() * icon.height()
        if (iconArea <= 0f) return 0f
        return overlapArea(painted, icon) / iconArea
    }

    fun dropTargetRect(
        view: LauncherCanvasView,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int
    ): RectF? {
        val cols = view.currentGridCols
        if (cols <= 0) return null
        val cells = view.homeGridCells
        val first = cells.getOrNull(row * cols + col)
        val last = cells.getOrNull((row + spanY - 1) * cols + (col + spanX - 1))
        if (first != null && last != null) {
            return RectF(first.left, first.top, last.right, last.bottom)
        }
        val metrics = gridMetrics(view, cols, view.currentGridRows) ?: return null
        return metrics.getCellBounds(
            col, row, spanX, spanY, view.gridAreaLeft.toFloat(), view.topInset.toFloat()
        )
    }

    /**
     * Fractions that make the overlay paint an item spanning ([col], [row], [spanX], [spanY]) of
     * the CURRENT grid centred on those cells — the inverse of [computedPaintedRect]'s
     * x via [com.nexus.launcher.ui.widgets.WidgetCoordinateSpace] / `yFraction * overlayHeight`. Null before the grid is laid out.
     */
    fun fractionsForCells(
        view: LauncherCanvasView,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int
    ): Pair<Float, Float>? {
        val rect = dropTargetRect(view, col, row, spanX, spanY) ?: return null
        val pageW = view.viewWidth.toFloat()
        val overlayH = overlayHeightPx(view).toFloat()
        if (pageW <= 0f || overlayH <= 0f) return null
        return Pair(
            com.nexus.launcher.ui.widgets.WidgetCoordinateSpace.xFractionFromCenterX(rect.centerX(), pageW).coerceIn(0f, 1f),
            (rect.centerY() / overlayH).coerceIn(0f, 1f)
        )
    }

    fun originCellOrFallback(
        context: android.content.Context,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        folderConfigJson: String?,
        cols: Int,
        rows: Int
    ): Pair<Int, Int> {
        val canvas = FolderBlurCoordinator.findCanvas(context)
        if (canvas != null && canvas.viewWidth > 0 && canvas.viewHeight > 0 &&
            canvas.currentGridCols > 0 && canvas.currentGridRows > 0
        ) {
            return originCell(canvas, xFraction, yFraction, spanX, spanY, folderConfigJson)
        }
        return DrawEngineLayout.fractionToCell(
            xFraction, yFraction, cols, rows, context, spanX, spanY
        )
    }

    private fun cellRange(
        view: LauncherCanvasView,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        folderConfigJson: String?,
        childRect: RectF? = null
    ): CellRange {
        val cols = view.currentGridCols
        val rows = view.currentGridRows
        if (cols <= 0 || rows <= 0) return CellRange(0, 0, 0, 0)
        val painted = childRect ?: computedPaintedRect(
            view, xFraction, yFraction, spanX, spanY, folderConfigJson
        )
        return intersectingCells(view, painted, cols, rows)
            ?: CellRange(0, 0, (spanX - 1).coerceAtLeast(0), (spanY - 1).coerceAtLeast(0))
    }

    private fun intersectingCells(
        view: LauncherCanvasView,
        painted: RectF,
        cols: Int,
        rows: Int
    ): CellRange? {
        var col0 = cols
        var row0 = rows
        var col1 = -1
        var row1 = -1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (iconOverlapFraction(view, painted, c, r, 1, 1) >= ICON_HIT_FRACTION) {
                    col0 = minOf(col0, c)
                    row0 = minOf(row0, r)
                    col1 = maxOf(col1, c)
                    row1 = maxOf(row1, r)
                }
            }
        }
        if (col1 < 0) return null
        return CellRange(col0, row0, col1, row1)
    }

    private fun overlapArea(a: RectF, b: RectF): Float {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (w <= 0f || h <= 0f) return 0f
        return w * h
    }

    private fun iconRectInCell(
        view: LauncherCanvasView,
        cell: RectF,
        spanX: Int,
        spanY: Int
    ): RectF {
        val renderer = view.homeScreenRenderer
        val dm = view.resources.displayMetrics
        val result = IconLayoutMetrics.compute(
            cell = cell,
            gridRows = renderer.gridRows,
            gapHorizontalPx = renderer.gapHorizontalPx,
            gapVerticalPx = renderer.gapVerticalPx,
            spanX = spanX.coerceAtLeast(1),
            spanY = spanY.coerceAtLeast(1),
            showLabels = renderer.showLabels,
            userIconSizeMultiplier = renderer.userIconSizeMultiplier,
            density = dm.density,
            displayWidth = dm.widthPixels,
            displayHeight = dm.heightPixels,
            twoLineLabels = renderer.twoLineLabels
        )
        val r = result.iconRect
        return RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
    }

    private fun computedPaintedRect(
        view: LauncherCanvasView,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        folderConfigJson: String?
    ): RectF {
        val cols = view.currentGridCols.coerceAtLeast(1)
        val rows = view.currentGridRows.coerceAtLeast(1)
        val overlayH = overlayHeightPx(view).toFloat().coerceAtLeast(1f)
        val pageW = view.viewWidth.toFloat().coerceAtLeast(1f)
        val metrics = gridMetrics(view, cols, rows)
        val cellW = metrics?.cellWidthPx ?: (pageW / cols)
        val cellH = metrics?.cellHeightPx ?: (overlayH / rows)
        val (width, height) = WidgetFreeSize.pixelSize(
            folderConfigJson, spanX, spanY, pageW, overlayH, cellW, cellH, view.isLandscape,
            // The page being dragged on: the painted rectangle has to match the size the widget
            // is actually drawn at, cap included, or the drop lands somewhere else.
            page = view.currentPage,
        )
        val padY = metrics?.paddingTopBottomPx ?: 0f
        val minTop = view.topInset + minOf(0f, padY).toInt()
        val maxTop = view.viewHeight - view.dockBottomReserve
        val left = (com.nexus.launcher.ui.widgets.WidgetCoordinateSpace.centerXFromFraction(xFraction, pageW) - width / 2f).toInt()
            .coerceIn(0, (pageW.toInt() - width).coerceAtLeast(0))
        val top = (yFraction * overlayH - height / 2f).toInt()
            .coerceIn(minTop, (maxTop - height).coerceAtLeast(minTop))
        return RectF(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat())
    }

    private fun overlayChildRect(view: LauncherCanvasView, item: HomeScreenItem): RectF? {
        val overlay = FolderBlurCoordinator.findWidgetOverlay(view.context) ?: return null
        val pageW = overlay.singlePageWidth.coerceAtLeast(1f)
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            val lp = child.layoutParams as? FrameLayout.LayoutParams ?: continue
            val match = when {
                item.itemType == 3 && child is android.appwidget.AppWidgetHostView ->
                    child.appWidgetId == item.appWidgetId
                item.itemType == HomeItemTypes.MOSAIC && child is LivingMosaicView ->
                    child.currentItem()?.id == item.id
                else -> false
            }
            if (!match) continue
            val localLeft = lp.leftMargin - item.page * pageW
            return RectF(
                localLeft,
                lp.topMargin.toFloat(),
                localLeft + lp.width,
                (lp.topMargin + lp.height).toFloat()
            )
        }
        return null
    }

    private fun gridMetrics(
        view: LauncherCanvasView,
        cols: Int,
        rows: Int
    ): GridMetrics.Layout? {
        val metrics = GridMetrics.compute(
            availableWidthPx = view.gridAreaWidth.toFloat(),
            availableHeightPx = (view.viewHeight - view.topInset - view.dockBottomReserve)
                .toFloat().coerceAtLeast(0f),
            columns = cols,
            rows = rows,
            paddingLeftRightDp = view.homePaddingLeftRightDp,
            paddingTopBottomDp = view.homePaddingTopBottomDp,
            gapHorizontalDp = view.homeGapHorizontalDp,
            gapVerticalDp = view.homeGapVerticalDp,
            density = view.resources.displayMetrics.density
        )
        if (metrics.cellWidthPx <= 0f || metrics.cellHeightPx <= 0f) return null
        return metrics
    }

    private fun overlayHeightPx(view: LauncherCanvasView): Int {
        val h = FolderBlurCoordinator.findWidgetOverlay(view.context)?.height ?: 0
        return if (h > 0) h else view.viewHeight
    }
}
