package com.nexus.launcher.ui.folder

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout

/** Resolves folder card width from grid metrics vs header chrome. */
object FolderWindowCardSizer {

    private const val TAG = "FolderWidth"

    data class LayoutWidths(val gridWidthPx: Int, val cardWidthPx: Int)

    /**
     * Width that fits [widthCols] cells at [cellWidthPx] each plus horizontal
     * half-gap margins on every cell (alignBounds GridLayout).
     */
    fun layoutGridWidthPx(widthCols: Int, cellWidthPx: Int, gapPx: Int, density: Float = 0f): Int {
        val iconSizePx = (cellWidthPx - gapPx).coerceAtLeast(1)
        return if (density > 0f) {
            FolderGridWidthResolver.layoutWidthPx(widthCols, iconSizePx, gapPx, density)
        } else {
            widthCols * cellWidthPx + widthCols * gapPx
        }
    }

    fun cardWidthForGrid(gridWidthPx: Int, density: Float): Int {
        val nameMaxWidthPx = (120f * density).toInt()
        val buttonPx = (32f * density).toInt()
        val minHeaderWidthPx = nameMaxWidthPx + buttonPx
        return FolderGridWidthResolver.cardWidthPx(gridWidthPx, density, minHeaderWidthPx)
    }

    fun applyLayout(
        card: LinearLayout,
        grid: GridLayout,
        gridWidthPx: Int,
        columns: Int = -1,
        iconSizePx: Int = -1,
        gapPx: Int = -1
    ): LayoutWidths {
        Log.d(
            TAG,
            "received columns=$columns iconSize=$iconSizePx gap=$gapPx gridWidthPx=$gridWidthPx"
        )
        val layoutW = resolveLayoutGridWidthPx(gridWidthPx, columns, iconSizePx, gapPx)
        Log.d(TAG, "layoutGridWidthPx=$layoutW metricsGridWidthPx=$gridWidthPx")
        applyGridAndScrollWidth(grid, layoutW)
        forceGridRemeasure(grid, layoutW)
        val cardWidthPx = applyCardFromGridWidth(card, layoutW)
        return LayoutWidths(layoutW, cardWidthPx)
    }

    fun applyGridAndScrollWidth(grid: GridLayout, layoutGridWidthPx: Int) {
        grid.layoutParams = (grid.layoutParams ?: ViewGroup.LayoutParams(
            layoutGridWidthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).apply { width = layoutGridWidthPx }
        syncScrollWidth(grid, layoutGridWidthPx)
    }

    fun forceGridRemeasure(grid: GridLayout, layoutGridWidthPx: Int) {
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            layoutGridWidthPx,
            View.MeasureSpec.EXACTLY
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        grid.measure(widthSpec, heightSpec)
        grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
    }

    fun applyCardFromGridWidth(card: LinearLayout, layoutGridWidthPx: Int): Int {
        val cardWidthPx = cardWidthForGrid(
            layoutGridWidthPx,
            card.resources.displayMetrics.density
        )
        applyCardWidth(card, cardWidthPx)
        (card.parent as? FrameLayout)?.let { applyHolderWidth(it, cardWidthPx) }
        return cardWidthPx
    }

    private fun resolveLayoutGridWidthPx(
        gridWidthPx: Int,
        columns: Int,
        iconSizePx: Int,
        gapPx: Int
    ): Int {
        if (columns > 0 && iconSizePx > 0 && gapPx > 0) {
            return layoutGridWidthPx(columns, iconSizePx + gapPx, gapPx)
        }
        return gridWidthPx
    }

    fun apply(card: LinearLayout, gridWidthPx: Int) {
        val density = card.resources.displayMetrics.density
        applyCardWidth(card, cardWidthForGrid(gridWidthPx, density))
    }

    private fun syncScrollWidth(grid: GridLayout, widthPx: Int) {
        (grid.parent as? View)?.let { scroll ->
            scroll.layoutParams = scroll.layoutParams.apply { width = widthPx }
            scroll.requestLayout()
        }
    }

    private fun applyCardWidth(card: LinearLayout, cardWidthPx: Int) {
        val cardLp = (card.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        cardLp.width = cardWidthPx
        cardLp.leftMargin = 0
        cardLp.topMargin = 0
        cardLp.rightMargin = 0
        cardLp.bottomMargin = 0
        card.layoutParams = cardLp
        card.minimumWidth = 0
        card.requestLayout()
        card.invalidate()
        card.post { card.requestLayout() }
    }

    private fun applyHolderWidth(holder: FrameLayout, cardWidthPx: Int) {
        val holderLp = (holder.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        holderLp.width = cardWidthPx
        holder.layoutParams = holderLp
        holder.requestLayout()
        holder.invalidate()
    }
}
