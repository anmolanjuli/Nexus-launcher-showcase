package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem

object FolderIconGridPreviewDraw {

    /** Uses the folder footprint for the default layout: 1×1 is 2×2, 2×2+ is 3×3. */
    fun gridSlotCount(iconCount: Int, spanX: Int = 1, spanY: Int = 1): Int = when {
        spanX >= 2 && spanY >= 2 -> 9
        spanX == 1 && spanY == 1 -> 4
        iconCount <= 4 -> 4
        iconCount <= 6 -> 6
        else -> 9
    }

    fun drawAdaptiveGrid(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float,
        spanX: Int, spanY: Int
    ) {
        when (gridSlotCount(icons.size, spanX, spanY)) {
            9 -> drawNineGrid(canvas, cx, cy, icons, folderRadius, density)
            6 -> drawSixGrid(canvas, cx, cy, icons, folderRadius, density)
            else -> drawStandard4Grid(canvas, cx, cy, icons, folderRadius, density)
        }
    }

    fun drawStandard4Grid(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float
    ) {
        drawFlushGrid(canvas, cx, cy, icons.take(4), 2, 2, folderRadius, density)
    }

    fun drawSixGrid(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float
    ) {
        drawFlushGrid(canvas, cx, cy, icons.take(6), 3, 2, folderRadius, density)
    }

    fun drawNineGrid(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float
    ) {
        drawFlushGrid(canvas, cx, cy, icons.take(9), 3, 3, folderRadius, density)
    }

    /**
     * Grid filling the whole area [FolderIconPreviewLayout] fitted inside the selected shape.
     *
     * Edge inset and inter-icon gap are both zero by intent: the incoming [folderRadius] is
     * already the inscribed half-extent of the shape, so any padding added here would shrink the
     * icons a second time. Adaptive icons carry their own optical margin, so touching cells still
     * read as separate.
     */
    private fun drawFlushGrid(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        cols: Int, rows: Int,
        folderRadius: Float, density: Float
    ) {
        if (icons.isEmpty() || cols <= 0 || rows <= 0) return
        val edgeInset = 0f
        val gap = 0f
        val usableW = folderRadius * 2f - edgeInset * 2f
        val usableH = folderRadius * 2f - edgeInset * 2f
        val cell = minOf(
            (usableW - gap * (cols - 1)) / cols,
            (usableH - gap * (rows - 1)) / rows
        ).coerceAtLeast(8f * density)
        val gridW = cols * cell + (cols - 1) * gap
        val gridH = rows * cell + (rows - 1) * gap
        val originX = cx - gridW / 2f
        val originY = cy - gridH / 2f
        icons.forEachIndexed { index, (_, drawable) ->
            if (index >= cols * rows) return@forEachIndexed
            val col = index % cols
            val row = index / cols
            val left = (originX + col * (cell + gap)).toInt()
            val top = (originY + row * (cell + gap)).toInt()
            val size = cell.toInt().coerceAtLeast(1)
            drawable.setBounds(left, top, left + size, top + size)
            drawable.draw(canvas)
        }
    }
}
