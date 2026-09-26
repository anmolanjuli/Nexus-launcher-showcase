package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.view.View
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.SelectionModeCardTrack
import com.nexus.launcher.ui.canvas.SelectionModeTransform
import com.nexus.launcher.ui.widgets.appbox.AppBoxView
import com.nexus.launcher.ui.widgets.liveapp.LiveAppView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView

/**
 * Helper for managing widget page gap offsets when card-track overview mode is active.
 * Offsets child pages before ViewGroup scaling so widgets match the canvas card stride.
 */
internal object WidgetOverlayCardOffsetHelper {

    fun pageOffset(canvas: LauncherCanvasView, page: Int): Float =
        if (SelectionModeTransform.isCardTrackActive(canvas)) {
            page * SelectionModeCardTrack.gapPx(canvas) / SelectionModeTransform.SCALE
        } else 0f

    fun pageLeft(canvas: LauncherCanvasView, page: Int, pageWidth: Float): Float =
        page * pageWidth + pageOffset(canvas, page)

    fun applyPageOffsets(overlay: WidgetOverlayLayout, canvas: LauncherCanvasView) {
        val dragging = overlay.draggingWidgetView
        val draggingAppWidgetId = (dragging as? AppWidgetHostView)?.appWidgetId
        for (index in 0 until overlay.childCount) {
            val child = overlay.getChildAt(index)
            if (child === dragging) continue
            if (child is WidgetGlassLiveBackdropView && draggingAppWidgetId != null && child.appWidgetId == draggingAppWidgetId) continue
            val page = childPage(overlay, child) ?: continue
            child.translationX = pageOffset(canvas, page)
        }
    }

    /**
     * Performs continuous sub-pixel scrolling and scaling in selection card-track mode.
     * Prevents discontinuous jumps or layout pops on page commit.
     */
    fun syncSelectionScroll(
        overlay: WidgetOverlayLayout,
        canvas: LauncherCanvasView,
        currentPage: Int,
        dragScrollOffset: Float,
        pageW: Float
    ) {
        val scale = SelectionModeTransform.SCALE
        val exact = pageLeft(canvas, currentPage, pageW) - (dragScrollOffset / scale)
        val whole = kotlin.math.floor(exact)
        overlay.desiredScrollX = whole.toInt()
        overlay.translationX = (whole - exact) * scale
        overlay.pivotX = overlay.viewWidth / 2f
        overlay.pivotY = overlay.height.toFloat().coerceAtLeast(1f) / 2f
        overlay.scaleX = scale
        overlay.scaleY = scale
        val cy = SelectionModeCardTrack.centerY(canvas)
        overlay.translationY = cy - overlay.height.toFloat() / 2f
        applyPageOffsets(overlay, canvas)
    }

    fun clearPageOffsets(overlay: WidgetOverlayLayout) {
        for (index in 0 until overlay.childCount) {
            overlay.getChildAt(index).translationX = 0f
        }
    }

    fun childPage(overlay: WidgetOverlayLayout, child: View): Int? = when (child) {
        is AppWidgetHostView -> overlay.liveItems[child.appWidgetId]?.page
        is WidgetGlassLiveBackdropView -> overlay.liveItems[child.appWidgetId]?.page
        is LivingMosaicView -> child.currentItem()?.page
        is ShortcutBoxView -> child.currentItem()?.page
        is AppBoxView -> child.currentItem()?.page
        is LiveAppView -> child.currentItem()?.page
        else -> null
    }
}
