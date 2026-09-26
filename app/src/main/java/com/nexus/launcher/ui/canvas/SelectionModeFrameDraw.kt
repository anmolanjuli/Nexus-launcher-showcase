package com.nexus.launcher.ui.canvas

import android.graphics.Canvas

/** Phone-frame borders drawn outside the status-bar content clip. */
internal object SelectionModeFrameDraw {

    fun draw(view: LauncherCanvasView, canvas: Canvas) {
        if (!SelectionModePageCards.isActive(view)) return
        val dragPx = view.dragScrollOffset
        for (pageIndex in SelectionModeCardTrack.pagesToRender(view, dragPx)) {
            SelectionModePageCards.drawPageBorder(canvas, view, pageIndex, dragPx)
        }
    }
}
