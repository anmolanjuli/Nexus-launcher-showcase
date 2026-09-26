package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.nexus.launcher.ui.canvas.LauncherCanvasView

object FolderDragPreviewRenderer {

    fun draw(canvas: Canvas, view: LauncherCanvasView, folderId: Long, x: Float, y: Float) {
        val folderItem = FolderItemLookup.findOnCanvas(view, folderId) ?: return
        val contents = FolderItemLookup.contentsFor(view, folderItem)
        val density = view.resources.displayMetrics.density
        val spanX = folderItem.spanX.coerceAtLeast(1)
        val spanY = folderItem.spanY.coerceAtLeast(1)
        val firstCell = view.homeGridCells.firstOrNull()
        val singleCellW = firstCell?.width() ?: (view.viewWidth.toFloat() / view.currentGridCols)
        val singleCellH = firstCell?.height() ?: (view.viewHeight.toFloat() / view.currentGridRows)
        val dummyCell = android.graphics.RectF(0f, 0f, singleCellW * spanX, singleCellH * spanY)
        
        val iconRect = view.homeScreenRenderer.getIconRect(folderItem, dummyCell)
        val cx = dummyCell.centerX()
        val cy = iconRect.top + iconRect.height() / 2f
        val centeredBounds = android.graphics.RectF(
            iconRect.left - cx,
            iconRect.top - cy,
            iconRect.right - cx,
            iconRect.bottom - cy
        )
        val folderRadius = Math.min(iconRect.width(), iconRect.height()) / 2f

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(1.1f, 1.1f)
        // The plate's glass backdrop caches its blurred wallpaper slice against its on-screen
        // position, so a plate following a finger missed that cache on every frame and re-blurred
        // its whole area each time — cost scaling with spanX/spanY, which is why multi-cell
        // folders lagged the finger while 1x1 ones kept up. Freeze the sample for the duration of
        // the preview draw: it keeps the reflection it lifted off with, and the shadow carries
        // the elevation.
        FolderIconPlateDraw.withFrozenBackdrop {
            view.homeScreenRenderer.folderIconRenderer.drawFolder(
                canvas = canvas,
                cx = 0f,
                cy = 0f,
                folderItem = folderItem,
                contents = contents,
                iconCache = view.homeScreenRenderer.iconCache,
                density = density,
                folderRadiusOverride = folderRadius,
                folderBoundsOverride = centeredBounds,
                matchAppIconSize = true
            )
        }
        canvas.restore()
    }

    fun folderIdFromDrag(view: LauncherCanvasView): Long? {
        val fromHandler = view.dragHandler.dragItem?.intent?.let { intent ->
            if (intent.action == "nexus.folder.OPEN") intent.getLongExtra("folderId", -1L) else -1L
        } ?: -1L
        if (fromHandler > 0L) return fromHandler
        val dragged = view.draggedItem
        if (dragged?.itemType == 1) return dragged.id.toLong()
        return null
    }
}
