package com.nexus.launcher.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Paints the abstract home-screen thumbnail — wallpaper, widget cards, folder tiles, icon
 * silhouettes and the dock row — from plain data.
 *
 * Extracted from [PageThumbnailRenderer], which could only work against a live
 * [LauncherCanvasView]. Backup export runs in SettingsActivity, where no canvas exists, but the
 * drawing never actually needed one: it derives its own cell rects from the column/row counts
 * rather than reading the live layout. Keeping the geometry in one place means a backup's
 * thumbnail and Manage Pages' thumbnail are the same picture.
 */
object HomeThumbnailPainter {

    /** Everything the painter needs that would otherwise come off the canvas view. */
    class Source(
        val items: List<HomeScreenItem>,
        val gridCols: Int,
        val gridRows: Int,
        val tokens: NexusColorTokens,
        val density: Float,
        /** Draws the wallpaper layer. Null paints the theme background instead. */
        val canvasRenderer: CanvasRenderer?,
        /** Container id whose items render as the dock row. */
        val dockContainerId: Int
    )

    fun render(source: Source, page: Int, thumbWidth: Int, thumbHeight: Int): Bitmap {
        val bmp = Bitmap.createBitmap(
            thumbWidth.coerceAtLeast(1), thumbHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888
        )
        paint(Canvas(bmp), source, page, thumbWidth, thumbHeight)
        return bmp
    }

    fun paint(canvas: Canvas, source: Source, page: Int, thumbW: Int, thumbH: Int) {
        val density = source.density
        val tokens = source.tokens

        // 1. Wallpaper background
        if (source.canvasRenderer != null) {
            source.canvasRenderer.draw(canvas, thumbW, thumbH)
        } else {
            canvas.drawColor(tokens.bg)
        }

        // 2. Grid & Dock bounds fitted directly to thumbnail dimensions
        val marginH = 8f * density
        val marginV = 8f * density
        val dockReserve = 26f * density
        val cols = source.gridCols.coerceAtLeast(1)
        val rows = source.gridRows.coerceAtLeast(1)
        val gridW = (thumbW - marginH * 2f).coerceAtLeast(1f)
        val gridH = (thumbH - marginV * 2f - dockReserve).coerceAtLeast(1f)
        val cellW = gridW / cols
        val cellH = gridH / rows

        val widgetBgPaint = fill(tokens.surfaceRaised)
        val widgetStrokePaint = stroke(tokens.divider, density)
        val folderBgPaint = fill(tokens.surfaceRaised)
        val folderStrokePaint = stroke(tokens.divider, density)
        val iconBgPaint = fill(tokens.surfaceRaised)
        val iconStrokePaint = stroke(tokens.divider, density)
        val dotPaint = fill(tokens.textSecondary)

        val pageItems = source.items.filter { it.page == page }

        // Widgets (itemType 3 or 4) as sleek bounded cards matching their spans
        pageItems.filter { it.itemType == 3 || it.itemType == 4 }.forEach { item ->
            val left = marginH + item.column * cellW + 1.5f * density
            val top = marginV + item.row * cellH + 1.5f * density
            val spanW = (item.spanX.coerceAtLeast(1) * cellW - 3f * density).coerceAtLeast(4f)
            val spanH = (item.spanY.coerceAtLeast(1) * cellH - 3f * density).coerceAtLeast(4f)
            val r = RectF(left, top, left + spanW, top + spanH)
            val corner = 6f * density
            canvas.drawRoundRect(r, corner, corner, widgetBgPaint)
            canvas.drawRoundRect(r, corner, corner, widgetStrokePaint)
            canvas.drawCircle(r.centerX(), r.centerY(), 2f * density, dotPaint)
        }

        // Folders (itemType 1)
        pageItems.filter { it.itemType == 1 }.forEach { item ->
            val cx = marginH + (item.column + 0.5f) * cellW
            val cy = marginV + (item.row + 0.5f) * cellH
            val radius = minOf(cellW, cellH) * 0.38f
            drawFolder(canvas, cx, cy, radius, folderBgPaint, folderStrokePaint, dotPaint)
        }

        // App icons (itemType 0 or 2) as matching silhouettes
        pageItems.filter { it.itemType == 0 || it.itemType == 2 }.forEach { item ->
            val cx = marginH + (item.column + 0.5f) * cellW
            val cy = marginV + (item.row + 0.5f) * cellH
            val radius = minOf(cellW, cellH) * 0.36f
            drawIcon(canvas, cx, cy, radius, iconBgPaint, iconStrokePaint, dotPaint)
        }

        // Dock row at the bottom
        val dockItems = source.items
            .filter { it.page == source.dockContainerId }
            .sortedBy { it.column }
        if (dockItems.isNotEmpty()) {
            val dockSlotCount = dockItems.maxOfOrNull { it.column + 1 }
                ?.coerceAtLeast(dockItems.size)?.coerceAtLeast(4) ?: 4
            val dockSlotW = gridW / dockSlotCount
            val dockCenterY = thumbH - marginV - dockReserve / 2f
            dockItems.forEach { item ->
                val dcx = marginH + (item.column + 0.5f) * dockSlotW
                val dradius = minOf(dockSlotW, dockReserve) * 0.36f
                if (item.itemType == 1) {
                    drawFolder(canvas, dcx, dockCenterY, dradius, folderBgPaint, folderStrokePaint, dotPaint)
                } else {
                    drawIcon(canvas, dcx, dockCenterY, dradius, iconBgPaint, iconStrokePaint, dotPaint)
                }
            }
        }
    }

    private fun drawFolder(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        bg: Paint, stroke: Paint, dot: Paint
    ) {
        val r = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val corner = radius * 0.45f
        canvas.drawRoundRect(r, corner, corner, bg)
        canvas.drawRoundRect(r, corner, corner, stroke)
        val dotR = radius * 0.18f
        val off = radius * 0.35f
        canvas.drawCircle(cx - off, cy - off, dotR, dot)
        canvas.drawCircle(cx + off, cy - off, dotR, dot)
        canvas.drawCircle(cx - off, cy + off, dotR, dot)
        canvas.drawCircle(cx + off, cy + off, dotR, dot)
    }

    private fun drawIcon(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        bg: Paint, stroke: Paint, dot: Paint
    ) {
        val r = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val corner = radius * 0.45f
        canvas.drawRoundRect(r, corner, corner, bg)
        canvas.drawRoundRect(r, corner, corner, stroke)
        canvas.drawCircle(cx, cy, radius * 0.28f, dot)
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, density: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
}
