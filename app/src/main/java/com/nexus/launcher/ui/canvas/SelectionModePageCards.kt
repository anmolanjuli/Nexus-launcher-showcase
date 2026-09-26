package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.RectF

/** Draws scaled page cards on the selection-mode horizontal track. */
internal object SelectionModePageCards {

    private val borderRect = RectF()

    fun isActive(view: LauncherCanvasView): Boolean =
        SelectionModeTransform.isCardTrackActive(view)

    fun drawPageContent(
        view: LauncherCanvasView,
        targetCanvas: Canvas,
        pageIndex: Int,
        dragOffsetPx: Float,
        drawHomeAlpha: Int,
        homeOffset: Float,
        bounceScale: Float
    ) {
        SelectionModeCardTrack.withPageCanvas(view, targetCanvas, pageIndex, dragOffsetPx) {
            targetCanvas.translate(0f, homeOffset)
            DrawEngineHomeIcons.drawPage(view, targetCanvas, pageIndex, drawHomeAlpha, bounceScale)
            PageTransitionDispatcher.drawSelectionOverlaysForPage(
                view, targetCanvas, pageIndex, drawHomeAlpha
            )
        }
    }

    fun drawPageBorder(
        canvas: Canvas,
        view: LauncherCanvasView,
        pageIndex: Int,
        dragOffsetPx: Float
    ) {
        SelectionModeCardTrack.screenBounds(view, pageIndex, dragOffsetPx, borderRect)
        SelectionModeTransform.drawCardBorder(
            canvas, borderRect, view.resources.displayMetrics.density
        )
    }
}
