package com.nexus.launcher.ui.widgets.mosaic

import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.isPageMotionRunning
import com.nexus.launcher.ui.canvas.cancelPageMotion
import com.nexus.launcher.ui.widgets.WidgetDragHighlight
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/**
 * Handles drag and drop gestures for Living Mosaic views, including card-track
 * overview zoom, edge-zone page navigation, and cross-page relocation.
 */
object LivingMosaicDragHelper {

    fun wireDrag(
        mosaicView: LivingMosaicView,
        item: HomeScreenItem,
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        overlay: WidgetOverlayLayout,
        density: Float,
        dismissAll: (String) -> Unit,
        homeScreenViewModel: HomeScreenViewModel
    ) {
        val pageAdvanceHandler = canvasView.handler ?: android.os.Handler(android.os.Looper.getMainLooper())
        var pageAdvanceRunnable: Runnable? = null
        var lastSnap: WidgetDragHighlight.CellSnap? = null

        mosaicView.onDragStarted = {
            mosaicView.setEditChromeActive(false)
            mosaicView.setMoveModeActive(false)
            dismissAll("drag_start")
            overlay.draggingWidgetView = mosaicView
            canvasView.setDragOverviewActive(true)
            mosaicView.animate().scaleX(1.04f).scaleY(1.04f).translationZ(32f * density).setDuration(120).start()
            val live = overlay.liveMosaics[item.id] ?: item
            overlay.startDragHighlight(live.spanX, live.spanY, WidgetDragHighlight.pixelSize(canvasView, live))
            val initial = WidgetDragHighlight.computeDragPosition(mosaicView, canvasView, live)
            lastSnap = overlay.updateDragHighlight(initial.hoverPage, initial.pageLocalLeft, initial.pageLocalTop)
        }
        mosaicView.onDragMoved = drag@ { deltaX, deltaY ->
            val currentItem = overlay.liveMosaics[item.id] ?: return@drag
            val target = WidgetDragHighlight.computeDragPosition(mosaicView, canvasView, currentItem)

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

            val snap = overlay.updateDragHighlight(
                hoverPage = target.hoverPage,
                pageLocalLeft = target.pageLocalLeft,
                pageLocalTop = target.pageLocalTop
            )
            if (snap != null) {
                lastSnap = snap
            }
        }
        mosaicView.onDropDetected = drop@ { deltaX, deltaY ->
            pageAdvanceRunnable?.let {
                pageAdvanceHandler.removeCallbacks(it)
                pageAdvanceRunnable = null
            }
            val currentItem = overlay.liveMosaics[item.id] ?: return@drop
            // Gesture routers clear translation before invoking this callback; restore release geometry.
            mosaicView.translationX = deltaX
            mosaicView.translationY = deltaY
            val target = WidgetDragHighlight.computeDragPosition(mosaicView, canvasView, currentItem)
            val snap = overlay.updateDragHighlight(target.hoverPage, target.pageLocalLeft, target.pageLocalTop) ?: lastSnap
            overlay.draggingWidgetView = null
            canvasView.cancelPageMotion()
            canvasView.dragScrollOffset = 0f
            overlay.clearDragHighlight()
            mosaicView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(100).start()

            val dropTargetPage = (snap?.hoverPage ?: currentItem.page)
                .coerceIn(0, (canvasView.totalPages - 1).coerceAtLeast(0))
            val newXFrac = snap?.snappedXFrac ?: currentItem.xFraction
            val newYFrac = snap?.snappedYFrac ?: currentItem.yFraction
            val col = snap?.col ?: currentItem.column
            val row = snap?.row ?: currentItem.row

            mosaicView.translationX = 0f
            mosaicView.translationY = 0f

            overlay.updateMosaicLayoutParams(
                item.id, newXFrac, newYFrac, currentItem.spanX, currentItem.spanY, dropTargetPage
            )
            homeScreenViewModel.updateItemPosition(
                item.id, dropTargetPage, newXFrac, newYFrac, col, row
            )

            val prefs = canvasView.context.getSharedPreferences("nexus_prefs", android.content.Context.MODE_PRIVATE)
            val pageCountKey = "home_page_count"
            val currentExplicit = prefs.getInt(pageCountKey, 1)
            if (dropTargetPage + 1 > currentExplicit) {
                prefs.edit().putInt(pageCountKey, dropTargetPage + 1).apply()
            }

            if (canvasView.currentPage != dropTargetPage) {
                canvasView.setCurrentPage(dropTargetPage)
                canvasView.onPageSwipe?.invoke(0)
            }

            canvasView.setDragOverviewActive(false)
            overlay.resyncScroll()
        }
    }

    internal fun clampFractions(
        canvasView: LauncherCanvasView,
        xFrac: Float,
        yFrac: Float,
        spanX: Int,
        spanY: Int
    ): Pair<Float, Float> {
        val metrics = gridMetrics(canvasView)
        val maxCol = (canvasView.effectiveHomeColumns - spanX).coerceAtLeast(0)
        val maxRow = (canvasView.effectiveHomeRows - spanY).coerceAtLeast(0)
        val gridLeft = canvasView.gridAreaLeft.toFloat()
        val gridTop = canvasView.topInset.toFloat()
        val minB = metrics.getCellBounds(0, 0, spanX, spanY, gridLeft, gridTop)
        val maxB = metrics.getCellBounds(maxCol, maxRow, spanX, spanY, gridLeft, gridTop)
        val w = canvasView.width.toFloat().coerceAtLeast(1f)
        val h = canvasView.height.toFloat().coerceAtLeast(1f)
        return xFrac.coerceIn(
            (minB.left + minB.right) / 2f / w,
            (maxB.left + maxB.right) / 2f / w
        ) to yFrac.coerceIn(
            (minB.top + minB.bottom) / 2f / h,
            (maxB.top + maxB.bottom) / 2f / h
        )
    }

    internal fun gridMetrics(canvasView: LauncherCanvasView) =
        com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = canvasView.gridAreaWidth.toFloat(),
            availableHeightPx = (canvasView.height - canvasView.topInset - canvasView.dockBottomReserve)
                .toFloat().coerceAtLeast(0f),
            columns = canvasView.effectiveHomeColumns,
            rows = canvasView.effectiveHomeRows,
            paddingLeftRightDp = canvasView.homePaddingLeftRightDp,
            paddingTopBottomDp = canvasView.homePaddingTopBottomDp,
            gapHorizontalDp = canvasView.homeGapHorizontalDp,
            gapVerticalDp = canvasView.homeGapVerticalDp,
            density = canvasView.resources.displayMetrics.density
        )
}
