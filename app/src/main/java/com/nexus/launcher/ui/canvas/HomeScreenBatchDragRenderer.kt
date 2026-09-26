package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.RectF
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderDragPreviewRenderer

/** Flying batch-drag shadows + multi-cell drop highlights. */
internal object HomeScreenBatchDragRenderer {

    fun drawHighlights(canvas: Canvas, view: LauncherCanvasView) {
        val handler = view.multiDragHandler
        if (!handler.isActive || !view.isDraggingIcon) return
        val cell = CanvasHitTestHelper.getCellAtDrop(view, view.dragX, view.dragY) ?: return
        val slots = HomeScreenBatchPlacement.preview(
            view, cell.first, cell.second, handler
        ) ?: return
        val cols = view.currentGridCols
        val padding = 4f * view.resources.displayMetrics.density
        for (slot in slots) {
            val item = view.homeScreenItems.firstOrNull { it.id == slot.id } ?: continue
            val rect = cellRectFor(view, slot.col, slot.row, item.spanX, item.spanY, cols) ?: continue
            val exclude = handler.memberIds().toList()
            val occupant = GridOccupancyHelper.findItemAtCell(
                view.context, view.homeScreenItems, view.currentPage,
                slot.col, slot.row, view.fractionDerivedPositions,
                exclude, item.spanX.coerceAtLeast(1), item.spanY.coerceAtLeast(1)
            )
            val highlight = RectF(
                rect.left + padding, rect.top + padding,
                rect.right - padding, rect.bottom - padding
            )
            view.dragRenderer.drawCellHighlight(canvas, highlight, occupant == null)
        }
    }

    fun drawFlyingIcons(canvas: Canvas, view: LauncherCanvasView) {
        val handler = view.multiDragHandler
        if (!handler.isActive || !view.isDraggingIcon) return
        val (baseX, baseY) = view.dragX to view.dragY
        val density = view.resources.displayMetrics.density
        val members = orderedMembers(view, handler.memberIds())
        val spread = 16f * density
        val lift = 12f * density

        members.forEachIndexed { index, item ->
            val t = index - (members.size - 1) / 2f
            val x = baseX + t * spread
            val y = baseY - index * lift * 0.55f
            canvas.save()
            canvas.translate(x, y)
            val scale = 1.05f + index * 0.025f
            canvas.scale(scale, scale)
            when (item.itemType) {
                1 -> FolderDragPreviewRenderer.draw(canvas, view, item.id.toLong(), 0f, 0f)
                0, 2 -> drawAppIcon(canvas, view, item, density)
            }
            canvas.restore()
        }
    }

    private fun orderedMembers(view: LauncherCanvasView, ids: Set<Int>): List<HomeScreenItem> {
        return ids.mapNotNull { id ->
            view.homeScreenItems.firstOrNull { it.id == id }?.let { item ->
                val pos = view.fractionDerivedPositions[id]
                    ?: Triple(item.page, item.column, item.row)
                if (pos.first == view.currentPage) item to pos else null
            }
        }.sortedWith(
            compareByDescending<Pair<HomeScreenItem, Triple<Int, Int, Int>>> { it.second.third }
                .thenBy { it.second.second }
        ).map { it.first }
    }

    private fun drawAppIcon(
        canvas: Canvas,
        view: LauncherCanvasView,
        item: HomeScreenItem,
        density: Float
    ) {
        var icon = view.homeScreenRenderer.iconCache[view.homeScreenRenderer.getCacheKey(item)]
        if (icon == null) {
            icon = com.nexus.launcher.ui.dock.DockLayout.findFrom(view)?.cachedIconFor(item.packageName)
        }
        if (icon == null) return
        val iconSize = (56f * density).toInt()
        val half = iconSize / 2
        val prev = icon.alpha
        icon.alpha = 230
        icon.setBounds(-half, -half, half, half)
        icon.draw(canvas)
        icon.alpha = prev
    }

    private fun cellRectFor(
        view: LauncherCanvasView,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        cols: Int
    ): RectF? {
        val startIdx = row * cols + col
        if (startIdx !in view.homeGridCells.indices) return null
        val start = view.homeGridCells[startIdx]
        val endCol = (col + spanX - 1).coerceAtMost(cols - 1)
        val endRow = row + spanY - 1
        val endIdx = endRow * cols + endCol
        if (endIdx !in view.homeGridCells.indices) return RectF(start)
        val end = view.homeGridCells[endIdx]
        return RectF(start.left, start.top, end.right, end.bottom)
    }
}
