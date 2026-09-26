package com.nexus.launcher.ui.widgets

import android.view.HapticFeedbackConstants
import android.view.View
import com.nexus.launcher.ui.canvas.GridMetrics
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.SelectionModeCardTrack
import com.nexus.launcher.ui.canvas.SelectionModeTransform
import kotlin.math.roundToInt

/**
 * Drag ghost highlight cell math — must match [com.nexus.launcher.ui.canvas.CanvasHitTestHelper]
 * grid origin (topInset + padding), not raw y/cellHeight from screen top.
 */
object WidgetDragHighlight {

    data class DragTarget(
        val hoverPage: Int,
        val pageLocalLeft: Float,
        val pageLocalTop: Float,
        val centerScreenX: Float,
        val centerScreenY: Float,
        val screenLeft: Float = centerScreenX,
        val screenRight: Float = centerScreenX
    )

    data class CellSnap(
        val col: Int,
        val row: Int,
        val drawLeft: Float,
        val drawTop: Float,
        val snappedXFrac: Float,
        val snappedYFrac: Float,
        val hoverPage: Int
    ) {
        val left: Float get() = drawLeft
        val top: Float get() = drawTop
    }

    fun computeDragPosition(
        view: View,
        canvas: LauncherCanvasView,
        item: com.nexus.launcher.data.HomeScreenItem
    ): DragTarget {
        val (width, height) = pixelSize(canvas, item)
        return computeDragPosition(view, canvas, width, height)
    }

    fun pixelSize(canvas: LauncherCanvasView, item: com.nexus.launcher.data.HomeScreenItem): Pair<Int, Int> {
        val metrics = GridMetrics.compute(
            canvas.gridAreaWidth.toFloat(),
            (canvas.height - canvas.topInset - canvas.dockBottomReserve).toFloat().coerceAtLeast(0f),
            canvas.currentGridCols, canvas.currentGridRows,
            canvas.homePaddingLeftRightDp, canvas.homePaddingTopBottomDp,
            canvas.homeGapHorizontalDp, canvas.homeGapVerticalDp,
            canvas.resources.displayMetrics.density
        )
        return WidgetFreeSize.pixelSize(
            item.folderConfigJson, item.spanX, item.spanY, canvas.width.toFloat(),
            canvas.height.toFloat(), metrics.cellWidthPx, metrics.cellHeightPx,
            canvas.isLandscape, item.page
        )
    }

    fun computeDragPosition(
        view: View,
        canvasView: LauncherCanvasView,
        pixelWidth: Float,
        pixelHeight: Float
    ): DragTarget {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val parentScaleX = (view.parent as? View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
        val parentScaleY = (view.parent as? View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
        val visualW = view.width * view.scaleX * parentScaleX
        val screenLeft = loc[0].toFloat()
        val screenRight = screenLeft + visualW
        val centerScreenX = screenLeft + visualW / 2f
        val centerScreenY = loc[1] + (view.height * view.scaleY * parentScaleY) / 2f

        val hoverPage = if (SelectionModeTransform.isCardTrackActive(canvasView)) {
            SelectionModeCardTrack.pageAtScreenPoint(canvasView, centerScreenX, centerScreenY)
                ?: SelectionModeCardTrack.nearestPageAtScreenPoint(canvasView, centerScreenX)
        } else {
            canvasView.currentPage
        }.coerceIn(0, (canvasView.totalPages - 1).coerceAtLeast(0))

        val (pageX, pageY) = if (SelectionModeTransform.isCardTrackActive(canvasView)) {
            SelectionModeCardTrack.mapTouchToPage(canvasView, centerScreenX, centerScreenY, hoverPage)
        } else {
            centerScreenX to centerScreenY
        }

        val pageLocalLeft = pageX - pixelWidth / 2f
        val pageLocalTop = pageY - pixelHeight / 2f

        return DragTarget(
            hoverPage = hoverPage,
            pageLocalLeft = pageLocalLeft,
            pageLocalTop = pageLocalTop,
            centerScreenX = centerScreenX,
            centerScreenY = centerScreenY,
            screenLeft = screenLeft,
            screenRight = screenRight
        )
    }

    fun computeDragPosition(
        view: View,
        canvasView: LauncherCanvasView,
        pixelWidth: Int,
        pixelHeight: Int
    ): DragTarget = computeDragPosition(view, canvasView, pixelWidth.toFloat(), pixelHeight.toFloat())

    fun snapTopLeft(
        canvas: LauncherCanvasView,
        gridAreaHeight: Float,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        hoverPage: Int,
        pageLocalLeft: Float,
        pageLocalTop: Float,
        pageW: Float,
        pixelSize: Pair<Int, Int>? = null
    ): CellSnap? {
        if (columns <= 0 || rows <= 0) return null
        val metrics = GridMetrics.compute(
            availableWidthPx = canvas.gridAreaWidth.toFloat(),
            availableHeightPx = gridAreaHeight,
            columns = columns,
            rows = rows,
            paddingLeftRightDp = canvas.homePaddingLeftRightDp,
            paddingTopBottomDp = canvas.homePaddingTopBottomDp,
            gapHorizontalDp = canvas.homeGapHorizontalDp,
            gapVerticalDp = canvas.homeGapVerticalDp,
            density = canvas.resources.displayMetrics.density
        )
        val gridLeft = canvas.gridAreaLeft.toFloat()
        val gridTop = canvas.topInset.toFloat()
        val width = pixelSize?.first?.toFloat() ?: (spanX * metrics.cellWidthPx)
        val height = pixelSize?.second?.toFloat() ?: (spanY * metrics.cellHeightPx)
        // WidgetFreeSize rounds to integer pixels; allow its half-pixel rounding at the edge.
        val rounding = if (pixelSize != null) 0.5f else 0f
        val maxCol = kotlin.math.floor(columns - (width - rounding) / metrics.cellWidthPx).toInt().coerceAtLeast(0)
        val maxRow = kotlin.math.floor(rows - (height - rounding) / metrics.cellHeightPx).toInt().coerceAtLeast(0)
        val col = ((pageLocalLeft - gridLeft - metrics.paddingLeftRightPx) / metrics.cellWidthPx)
            .roundToInt().coerceIn(0, maxCol)
        val row = ((pageLocalTop - gridTop - metrics.paddingTopBottomPx) / metrics.cellHeightPx)
            .roundToInt().coerceIn(0, maxRow)
        val bounds = metrics.getCellBounds(col, row, spanX, spanY, gridLeft, gridTop)
        bounds.right = bounds.left + width
        bounds.bottom = bounds.top + height

        val drawX = WidgetOverlayCardOffsetHelper.pageLeft(canvas, hoverPage, pageW) + bounds.left
        val drawY = bounds.top

        val snappedCenterX = (bounds.left + bounds.right) / 2f
        val snappedCenterY = (bounds.top + bounds.bottom) / 2f
        val snappedXFrac = WidgetCoordinateSpace.xFractionFromCenterX(snappedCenterX, pageW).coerceIn(0.01f, 0.99f)
        val snappedYFrac = (snappedCenterY / canvas.height.toFloat().coerceAtLeast(1f)).coerceIn(0.01f, 0.99f)

        return CellSnap(col, row, drawX, drawY, snappedXFrac, snappedYFrac, hoverPage)
    }

    fun snapTopLeft(
        canvas: LauncherCanvasView,
        gridAreaHeight: Float,
        columns: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        pageLocalLeft: Float,
        screenTop: Float,
        scrollX: Int
    ): CellSnap? = snapTopLeft(
        canvas = canvas,
        gridAreaHeight = gridAreaHeight,
        columns = columns,
        rows = rows,
        spanX = spanX,
        spanY = spanY,
        hoverPage = canvas.currentPage,
        pageLocalLeft = pageLocalLeft,
        pageLocalTop = screenTop,
        pageW = canvas.width.toFloat()
    )

    fun tickIfCellChanged(
        host: View,
        prevCol: Int,
        prevRow: Int,
        col: Int,
        row: Int
    ): Boolean {
        if (col == prevCol && row == prevRow) return false
        host.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        return true
    }
}
