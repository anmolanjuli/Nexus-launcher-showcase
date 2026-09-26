package com.nexus.launcher.ui.widgets

import android.view.View
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.isPageMotionRunning
import com.nexus.launcher.ui.canvas.cancelPageMotion
import com.nexus.launcher.ui.widgets.appbox.AppBoxView
import com.nexus.launcher.ui.widgets.liveapp.LiveAppView
import com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView

/** Helper to wire drag gestures and move mode on widget views during edit mode. */
object WidgetEditDragHelper {

    fun wire(
        widgetView: View,
        item: HomeScreenItem,
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        widgetOverlayLayout: WidgetOverlayLayout,
        widgetViewModel: WidgetViewModel,
        menu: WidgetContextMenuView,
        density: Float,
        dismissAllOverlays: (String) -> Unit
    ) {
        val liveId = if (item.appWidgetId != -1) item.appWidgetId else item.id

        val pageAdvanceHandler = canvasView.handler ?: android.os.Handler(android.os.Looper.getMainLooper())
        var pageAdvanceRunnable: Runnable? = null

        var lastSnap: WidgetDragHighlight.CellSnap? = null

        val onBodyTapped = { menu.exitMoveMode() }
        val onDragStarted = {
            (widgetView as? NexusWidgetView)?.setResizeModeActive(false)
            (widgetView as? NexusWidgetView)?.setMoveModeActive(false)
            (widgetView as? ShortcutBoxView)?.setResizeModeActive(false)
            (widgetView as? ShortcutBoxView)?.setMoveModeActive(false)
            (widgetView as? AppBoxView)?.setResizeModeActive(false)
            (widgetView as? AppBoxView)?.setMoveModeActive(false)
            (widgetView as? LiveAppView)?.setResizeModeActive(false)
            (widgetView as? LiveAppView)?.setMoveModeActive(false)
            dismissAllOverlays("drag_start")
            widgetView.animate().scaleX(1.04f).scaleY(1.04f).translationZ(32f * density).setDuration(120).start()

            widgetOverlayLayout.draggingWidgetView = widgetView
            canvasView.setDragOverviewActive(true)

            val live = widgetOverlayLayout.liveItems[liveId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
            widgetOverlayLayout.startDragHighlight(live.spanX, live.spanY, WidgetDragHighlight.pixelSize(canvasView, live))
            val initial = WidgetDragHighlight.computeDragPosition(widgetView, canvasView, live)
            lastSnap = widgetOverlayLayout.updateDragHighlight(initial.hoverPage, initial.pageLocalLeft, initial.pageLocalTop)
        }
        val onDragMoved: (Float, Float) -> Unit = drag@ { deltaX, deltaY ->
            val currentItem = widgetOverlayLayout.liveItems[liveId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
            val target = WidgetDragHighlight.computeDragPosition(widgetView, canvasView, currentItem)

            val delta = com.nexus.launcher.ui.canvas.PageAdvanceTouchHelper.computePageAdvanceDirection(
                view = canvasView,
                screenX = target.centerScreenX,
                itemLeft = target.screenLeft,
                itemRight = target.screenRight
            )

            if (delta != null && !canvasView.isPageMotionRunning) {
                if (pageAdvanceRunnable == null) {
                    val pageTarget = canvasView.currentPage + delta
                    if (pageTarget in 0 until canvasView.totalPages) {
                        val r = Runnable {
                            com.nexus.launcher.ui.canvas.DrawerSnapAnimator.animatePageTransition(canvasView, pageTarget)
                            pageAdvanceRunnable = null
                        }
                        pageAdvanceRunnable = r
                        pageAdvanceHandler.postDelayed(r, com.nexus.launcher.ui.canvas.PageAdvanceTouchHelper.ADVANCE_DELAY_MS)
                    }
                }
            } else {
                pageAdvanceRunnable?.let {
                    pageAdvanceHandler.removeCallbacks(it)
                    pageAdvanceRunnable = null
                }
            }

            val overMosaic = com.nexus.launcher.ui.widgets.mosaic.LivingMosaicDropPreview
                .updateForView(widgetOverlayLayout, widgetView)
            if (overMosaic) {
                widgetOverlayLayout.setDragHighlightSuppressed(true)
            } else {
                widgetOverlayLayout.setDragHighlightSuppressed(false)
                val snap = widgetOverlayLayout.updateDragHighlight(
                    hoverPage = target.hoverPage,
                    pageLocalLeft = target.pageLocalLeft,
                    pageLocalTop = target.pageLocalTop
                )
                if (snap != null) {
                    lastSnap = snap
                }
            }
            WidgetGlassBackdropDragSync.setDragTranslation(widgetOverlayLayout, liveId, deltaX, deltaY)
        }
        val onDropDetected: (Float, Float) -> Unit = drop@ { deltaX, deltaY ->
            pageAdvanceRunnable?.let {
                pageAdvanceHandler.removeCallbacks(it)
                pageAdvanceRunnable = null
            }
            val currentItem = widgetOverlayLayout.liveItems[liveId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
            widgetView.translationX = deltaX
            widgetView.translationY = deltaY
            val target = WidgetDragHighlight.computeDragPosition(widgetView, canvasView, currentItem)
            val snap = widgetOverlayLayout.updateDragHighlight(target.hoverPage, target.pageLocalLeft, target.pageLocalTop) ?: lastSnap
            widgetOverlayLayout.draggingWidgetView = null
            canvasView.cancelPageMotion()
            canvasView.dragScrollOffset = 0f
            WidgetGlassBackdropDragSync.clearDragTranslation(widgetOverlayLayout, liveId)
            val targetPage = snap?.hoverPage ?: currentItem.page
            val newXFrac = snap?.snappedXFrac ?: currentItem.xFraction
            val newYFrac = snap?.snappedYFrac ?: currentItem.yFraction
            val col = snap?.col ?: currentItem.column
            val row = snap?.row ?: currentItem.row
            WidgetEditDropHandler.handle(
                activity = activity,
                canvasView = canvasView,
                widgetOverlayLayout = widgetOverlayLayout,
                widgetViewModel = widgetViewModel,
                item = item,
                widgetView = widgetView,
                targetPage = targetPage,
                newXFraction = newXFrac,
                newYFraction = newYFrac,
                col = col,
                row = row,
                onAbsorbedOrRejected = { dismissAllOverlays("drop_absorbed") }
            )
        }

        if (widgetView is NexusWidgetView) {
            widgetView.onWidgetBodyTappedInMoveMode = onBodyTapped
            widgetView.onDragStarted = onDragStarted
            widgetView.onDragMoved = onDragMoved
            widgetView.onDropDetected = onDropDetected
        } else if (widgetView is ShortcutBoxView) {
            widgetView.onBodyTappedInMoveMode = onBodyTapped
            widgetView.onDragStarted = onDragStarted
            widgetView.onDragMoved = onDragMoved
            widgetView.onDropDetected = onDropDetected
        } else if (widgetView is AppBoxView) {
            widgetView.onBodyTappedInMoveMode = onBodyTapped
            widgetView.onDragStarted = onDragStarted
            widgetView.onDragMoved = onDragMoved
            widgetView.onDropDetected = onDropDetected
        } else if (widgetView is LiveAppView) {
            widgetView.onBodyTappedInMoveMode = onBodyTapped
            widgetView.onDragStarted = onDragStarted
            widgetView.onDragMoved = onDragMoved
            widgetView.onDropDetected = onDropDetected
        }
    }
}
