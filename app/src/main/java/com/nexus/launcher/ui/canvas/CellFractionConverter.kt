package com.nexus.launcher.ui.canvas

import android.content.Context

/**
 * Coordinate converter between grid cells and fractional coordinates.
 *
 * Fractions are in the frame of the screen as currently shown — the same frame the widget
 * overlay paints them in (`xFraction * pageWidth`). Each layout shape stores its own positions
 * (see [com.nexus.launcher.data.ShapeAwareHomeScreenDao]), so fractions are never carried
 * across a rotation and need no rotation mapping. (They used to be rotated by the display
 * rotation, which put landscape widgets somewhere other than where they were painted.)
 */
object CellFractionConverter {

    @Suppress("UNUSED_PARAMETER")
    fun fractionToCell(
        xFraction: Float, yFraction: Float,
        cols: Int, rows: Int, context: Context,
        spanX: Int = 1, spanY: Int = 1
    ): Pair<Int, Int> {
        val rawCol = xFraction * cols - spanX / 2f
        val col = Math.round(rawCol)

        val rawRow = if (yFraction >= 0.5f) {
            val distanceFromBottom = 1f - yFraction
            val rawOffsetFromBottom = distanceFromBottom * rows - 0.5f
            (rows - 1 - rawOffsetFromBottom) - (spanY - 1) / 2f
        } else {
            yFraction * rows - 0.5f - (spanY - 1) / 2f
        }
        val row = Math.round(rawRow)

        return Pair(col.coerceIn(0, cols - 1), row.coerceIn(0, rows - 1))
    }

    @Suppress("UNUSED_PARAMETER")
    fun cellToFraction(
        col: Int, row: Int,
        cols: Int, rows: Int, context: Context,
        spanX: Int = 1, spanY: Int = 1
    ): Pair<Float, Float> = cellToFraction(col, row, cols, rows, spanX, spanY)

    fun cellToFraction(
        col: Int, row: Int,
        cols: Int, rows: Int,
        spanX: Int, spanY: Int
    ): Pair<Float, Float> {
        val xF = (col + spanX / 2f) / cols
        val yF = (row + 0.5f + (spanY - 1) / 2f) / rows
        return Pair(xF.coerceIn(0f, 1f), yF.coerceIn(0f, 1f))
    }
}
