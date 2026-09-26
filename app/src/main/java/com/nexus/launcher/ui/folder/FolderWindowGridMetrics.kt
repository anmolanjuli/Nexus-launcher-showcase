package com.nexus.launcher.ui.folder

import android.content.Context
import android.util.Log
import com.nexus.launcher.data.FolderConfig

object FolderWindowGridMetrics {

    private const val TAG = "FolderWidth"

    data class Layout(
        val columns: Int,
        val widthCols: Int,
        val cardWidthPx: Int,
        val gridWidthPx: Int,
        val iconSizePx: Int,
        val cellWidthPx: Int,
        val gapPx: Int,
        val rowCount: Int
    )

    fun compute(
        context: Context,
        config: FolderConfig,
        itemCount: Int,
        baseIconSizePx: Int
    ): Layout {
        val density = context.resources.displayMetrics.density
        val columns = config.gridColumns.coerceIn(3, 10)
        val count = itemCount.coerceAtLeast(1)
        val rows = ((count + columns - 1) / columns).coerceAtLeast(2)
        val widthCols = if (count < columns) maxOf(3, count) else columns

        val gapPx = (18 * density).toInt().coerceAtLeast(2)
        val minIconSizePx = (36 * density).toInt()

        val screenW = context.resources.displayMetrics.widthPixels
        val edgeMargin = (16 * density).toInt()
        val cardPad = (32 * density).toInt()
        val maxCardWidth = screenW - edgeMargin * 2
        val maxGridWidth = (maxCardWidth - cardPad).coerceAtLeast(minIconSizePx)

        val gapBetweenCols = (widthCols - 1).coerceAtLeast(0) * gapPx
        // The full width occupied by the grid layout including outer half-gaps
        val naturalLayoutGridWidth = widthCols * (baseIconSizePx + gapPx)

        val iconSizePx: Int
        if (naturalLayoutGridWidth <= maxGridWidth) {
            iconSizePx = baseIconSizePx
        } else {
            // maxGridWidth = widthCols * (iconSizePx + gapPx)
            iconSizePx = ((maxGridWidth - widthCols * gapPx) / widthCols.coerceAtLeast(1))
                .coerceAtLeast(minIconSizePx)
        }

        val layoutGridWidthPx = widthCols * (iconSizePx + gapPx)
        val finalGridWidth = widthCols * iconSizePx + gapBetweenCols
        
        val finalIconSizePx = iconSizePx
        val cellWidthPx = finalIconSizePx + gapPx
        val cardWidth = (layoutGridWidthPx + cardPad).coerceAtMost(maxCardWidth)

        Log.d(
            TAG,
            "compute columns=$columns widthCols=$widthCols baseIcon=$baseIconSizePx " +
                "iconSizePx=$finalIconSizePx gridWidthPx=$finalGridWidth " +
                "maxGridWidth=$maxGridWidth maxCardWidth=$maxCardWidth"
        )

        return Layout(
            columns,
            widthCols,
            cardWidth,
            finalGridWidth,
            finalIconSizePx,
            cellWidthPx,
            gapPx,
            rows
        )
    }
}
