package com.nexus.launcher.ui.canvas

import android.graphics.RectF

object GridMetrics {
    data class Layout(
        val cellWidthPx: Float,
        val cellHeightPx: Float,
        val paddingLeftRightPx: Float,
        val paddingTopBottomPx: Float,
        val gapHorizontalPx: Float,
        val gapVerticalPx: Float
    ) {
        fun getCellBounds(
            col: Int,
            row: Int,
            spanX: Int,
            spanY: Int,
            gridAreaLeft: Float,
            gridAreaTop: Float
        ): RectF {
            val left = gridAreaLeft + paddingLeftRightPx + col * cellWidthPx
            val top = gridAreaTop + paddingTopBottomPx + row * cellHeightPx
            return RectF(
                left,
                top,
                left + spanX * cellWidthPx,
                top + spanY * cellHeightPx
            )
        }
    }

    /**
     * @param availableWidthPx The raw horizontal space (e.g. gridAreaWidth, canvasWidth, or viewWidth)
     * @param availableHeightPx The raw vertical space (e.g. viewHeight minus insets/reserves)
     */
    fun compute(
        availableWidthPx: Float,
        availableHeightPx: Float,
        columns: Int,
        rows: Int,
        paddingLeftRightDp: Float = 0f,
        paddingTopBottomDp: Float = 0f,
        gapHorizontalDp: Float = 0f,
        gapVerticalDp: Float = 0f,
        density: Float = 1f,
        caller: String = "other",
        statusBarPx: Int = -1,
        topInsetPx: Int = -1,
        dockSource: String = "",
        dockReservePx: Int = -1,
        dockMeasuredHeightPx: Int = -1
    ): Layout {
        val gapHorizontalPx = gapHorizontalDp * density
        val gapVerticalPx = gapVerticalDp * density
        // Negative padding is intentional: -4dp L/R overflows into system safe
        // margins. Only clamp HIGH padding so net width/height stay positive.
        val maxPadX = (availableWidthPx / 2f - 1f).coerceAtLeast(0f)
        val maxPadY = (availableHeightPx / 2f - 1f).coerceAtLeast(0f)
        val paddingLeftRightPx = (paddingLeftRightDp * density).coerceAtMost(maxPadX)
        val paddingTopBottomPx = (paddingTopBottomDp * density).coerceAtMost(maxPadY)

        val netWidthPx = availableWidthPx - (paddingLeftRightPx * 2)
        val netHeightPx = availableHeightPx - (paddingTopBottomPx * 2)
        
        val cellWidth = netWidthPx / columns.toFloat()
        val cellHeight = netHeightPx / rows.toFloat()
        // TEMPORARY — HomeGridOverlapDiag; remove with the diag object.
        HomeGridOverlapDiag.logGrid(
            caller = caller,
            columns = columns,
            rows = rows,
            paddingLrDp = paddingLeftRightDp,
            paddingTbDp = paddingTopBottomDp,
            gapHDp = gapHorizontalDp,
            gapVDp = gapVerticalDp,
            availableW = availableWidthPx,
            availableH = availableHeightPx,
            netW = netWidthPx,
            netH = netHeightPx,
            cellW = cellWidth,
            cellH = cellHeight,
            statusBarPx = statusBarPx,
            topInsetPx = topInsetPx,
            dockSource = dockSource,
            dockReservePx = dockReservePx,
            dockMeasuredHeightPx = dockMeasuredHeightPx
        )
        return Layout(
            cellWidthPx = cellWidth,
            cellHeightPx = cellHeight,
            paddingLeftRightPx = paddingLeftRightPx,
            paddingTopBottomPx = paddingTopBottomPx,
            gapHorizontalPx = gapHorizontalPx,
            gapVerticalPx = gapVerticalPx
        )
    }
}
