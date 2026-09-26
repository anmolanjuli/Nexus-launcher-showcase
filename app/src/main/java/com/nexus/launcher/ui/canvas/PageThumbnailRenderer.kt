package com.nexus.launcher.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Lightweight, high-performance page thumbnail renderer for Manage Pages.
 * Renders an abstract representation of page contents (icons, folders, widget bounding cards)
 * with zero SoC/battery strain and zero aspect distortion.
 */
object PageThumbnailRenderer {

    fun renderAll(
        view: LauncherCanvasView,
        thumbWidth: Int,
        thumbHeight: Int
    ): Map<Int, Bitmap> {
        val result = mutableMapOf<Int, Bitmap>()
        for (page in 0 until view.totalPages) {
            result[page] = renderPage(view, page, thumbWidth, thumbHeight)
        }
        return result
    }

    fun renderPage(
        view: LauncherCanvasView,
        page: Int,
        thumbWidth: Int,
        thumbHeight: Int,
        includeDock: Boolean = true,
        includeWidgets: Boolean = false,
        /** Labels are noise in a fold-gesture preview but part of a real home-screen replica. */
        includeLabels: Boolean = false
    ): Bitmap {
        val bmp = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (includeWidgets) {
            drawLivePage(view, canvas, page, thumbWidth, thumbHeight, includeDock, includeWidgets, includeLabels)
        } else {
            drawGrid(view, canvas, page, thumbWidth, thumbHeight)
        }
        return bmp
    }

    /**
     * Delegates to [HomeThumbnailPainter], which owns this geometry so a backup's thumbnail and
     * a Manage Pages thumbnail are the same picture drawn by the same code.
     */
    private fun drawGrid(
        view: LauncherCanvasView,
        canvas: Canvas,
        page: Int,
        thumbW: Int,
        thumbH: Int
    ) {
        HomeThumbnailPainter.paint(
            canvas = canvas,
            source = HomeThumbnailPainter.Source(
                items = view.homeScreenItems,
                gridCols = view.currentGridCols,
                gridRows = view.currentGridRows,
                tokens = view.currentThemeTokens,
                density = view.resources.displayMetrics.density,
                canvasRenderer = view.canvasRenderer,
                dockContainerId = com.nexus.launcher.ui.HomeScreenViewModel.DOCK_CONTAINER
            ),
            page = page,
            thumbW = thumbW,
            thumbH = thumbH
        )
    }

    private fun drawLivePage(
        view: LauncherCanvasView,
        canvas: Canvas,
        page: Int,
        screenW: Int,
        screenH: Int,
        includeDock: Boolean,
        includeWidgets: Boolean,
        includeLabels: Boolean
    ) {
        view.canvasRenderer.draw(canvas, screenW, screenH)

        // The engine's own icon pass rather than a second copy of it, so a captured page can
        // never diverge from what the launcher actually paints.
        val savedLabels = view.homeScreenRenderer.showLabels
        if (!includeLabels) view.homeScreenRenderer.showLabels = false
        try {
            if (view.homeGridCells.isEmpty()) {
                // Restore the engine's own precondition before delegating; it reads
                // view.homeGridCells directly rather than taking cells as a parameter.
                view.recalculateLayout()
            }
            DrawEngineHomeIcons.drawPage(view, canvas, page, 255, 1f)
        } finally {
            view.homeScreenRenderer.showLabels = savedLabels
        }

        if (includeDock) drawDock(view, canvas)
        if (includeWidgets) drawWidgets(view, canvas, page)
    }

    private fun drawWidgets(view: LauncherCanvasView, canvas: Canvas, page: Int) {
        val overlay = com.nexus.launcher.ui.folder.FolderBlurCoordinator.findWidgetOverlay(view.context)
            ?: return
        val pageW = overlay.singlePageWidth
        if (pageW <= 0f) return
        val pageLeft = page * pageW
        val pageRight = pageLeft + pageW
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child.visibility != android.view.View.VISIBLE) continue
            val cx = child.left + child.width / 2f
            if (cx < pageLeft || cx >= pageRight) continue
            canvas.save()
            canvas.translate(child.left - pageLeft, child.top.toFloat())
            child.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawDock(view: LauncherCanvasView, canvas: Canvas) {
        val dock = com.nexus.launcher.ui.dock.DockLayout.findFrom(view) ?: return
        if (dock.width <= 0 || dock.height <= 0) return
        val dockLoc = IntArray(2)
        val viewLoc = IntArray(2)
        dock.getLocationOnScreen(dockLoc)
        view.getLocationOnScreen(viewLoc)
        canvas.save()
        canvas.translate(
            (dockLoc[0] - viewLoc[0]).toFloat(),
            (dockLoc[1] - viewLoc[1]).toFloat()
        )
        dock.draw(canvas)
        canvas.restore()
    }
}
