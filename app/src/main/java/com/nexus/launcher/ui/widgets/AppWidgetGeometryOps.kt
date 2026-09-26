package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.GridMetrics
import com.nexus.launcher.data.HomeScreenItem

object AppWidgetGeometryOps {

    /** Cell size in dp-space units (GridMetrics with density=1f over already-dp inputs). */
    fun computeCellSizeDp(density: Float, height: Int, canvasView: LauncherCanvasView): Pair<Float, Float> {
        val gridHeight = (height - canvasView.topInset - canvasView.dockBottomReserve).toFloat().coerceAtLeast(0f)
        val metrics = GridMetrics.compute(
            (canvasView.gridAreaWidth / density), (gridHeight / density),
            canvasView.currentGridCols, canvasView.currentGridRows,
            canvasView.homePaddingLeftRightDp, canvasView.homePaddingTopBottomDp,
            canvasView.homeGapHorizontalDp, canvasView.homeGapVerticalDp, 1f
        )
        return metrics.cellWidthPx to metrics.cellHeightPx
    }

    fun computeWidgetSpans(
        info: AppWidgetProviderInfo?,
        density: Float,
        canvasView: LauncherCanvasView,
        pendingReplaceItem: HomeScreenItem?,
        pendingReplaceSpanX: Int,
        pendingReplaceSpanY: Int
    ): Pair<Int, Int> {
        if (info == null) return Pair(1, 1)

        val cols = canvasView.currentGridCols
        val rows = canvasView.currentGridRows
        val gridHeight = (canvasView.viewHeight - canvasView.topInset - canvasView.dockBottomReserve).toFloat().coerceAtLeast(0f)
        val metrics = GridMetrics.compute(
            availableWidthPx = (canvasView.gridAreaWidth / density),
            availableHeightPx = (gridHeight / density),
            columns = cols,
            rows = rows,
            paddingLeftRightDp = canvasView.homePaddingLeftRightDp,
            paddingTopBottomDp = canvasView.homePaddingTopBottomDp,
            gapHorizontalDp = canvasView.homeGapHorizontalDp,
            gapVerticalDp = canvasView.homeGapVerticalDp,
            density = 1f // Space is already DP
        )
        val cellWidthDp = metrics.cellWidthPx
        val cellHeightDp = metrics.cellHeightPx

        var spanX = 1
        var spanY = 1

        if (pendingReplaceItem != null) {
            spanX = pendingReplaceSpanX
            spanY = pendingReplaceSpanY
        } else if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            spanX = info.targetCellWidth.coerceIn(1, cols)
            spanY = info.targetCellHeight.coerceIn(1, rows)
        } else if (info.minWidth > 0 && info.minHeight > 0) {
            spanX = kotlin.math.round(info.minWidth / cellWidthDp).toInt().coerceIn(1, cols)
            spanY = kotlin.math.round(info.minHeight / cellHeightDp).toInt().coerceIn(1, rows)
        } else if (info.minResizeWidth > 0 && info.minResizeHeight > 0) {
            spanX = kotlin.math.round(info.minResizeWidth / cellWidthDp).toInt().coerceIn(1, cols)
            spanY = kotlin.math.round(info.minResizeHeight / cellHeightDp).toInt().coerceIn(1, rows)
        }

        android.util.Log.d("WidgetSpan", "minResize: ${info.minResizeWidth}x${info.minResizeHeight}, minSize: ${info.minWidth}x${info.minHeight}, final: ${spanX}x${spanY}")
        return Pair(spanX, spanY)
    }

    fun resolvePlacementFractions(
        minWidthDp: Int,
        minHeightDp: Int,
        density: Float,
        canvasView: LauncherCanvasView,
        pendingWidgetDropX: Float,
        pendingWidgetDropY: Float,
        pendingReplaceItem: HomeScreenItem?,
        pendingReplaceXFraction: Float,
        pendingReplaceYFraction: Float
    ): Pair<Float, Float> {
        if (pendingReplaceItem != null) {
            return Pair(pendingReplaceXFraction, pendingReplaceYFraction)
        }
        return getClampedFractions(
            minWidthDp, minHeightDp, density, canvasView, pendingWidgetDropX, pendingWidgetDropY
        )
    }

    fun getClampedFractions(
        minWidthDp: Int,
        minHeightDp: Int,
        density: Float,
        canvasView: LauncherCanvasView,
        pendingWidgetDropX: Float,
        pendingWidgetDropY: Float
    ): Pair<Float, Float> {
        val minWidthPx = minWidthDp * density
        val minHeightPx = minHeightDp * density
        
        val minX = canvasView.gridAreaLeft + minWidthPx / 2f
        val maxX = canvasView.gridAreaLeft + canvasView.gridAreaWidth - minWidthPx / 2f
        val clampedX = pendingWidgetDropX.coerceIn(minX, maxX.coerceAtLeast(minX))
        
        val minY = canvasView.topInset + minHeightPx / 2f
        val maxY = canvasView.viewHeight - canvasView.dockBottomReserve - minHeightPx / 2f
        val clampedY = pendingWidgetDropY.coerceIn(minY, maxY.coerceAtLeast(minY))
        
        val xFrac = if (canvasView.width > 0) clampedX / canvasView.width else 0.5f
        val yFrac = if (canvasView.height > 0) clampedY / canvasView.height else 0.5f
        return Pair(xFrac, yFrac)
    }
}
