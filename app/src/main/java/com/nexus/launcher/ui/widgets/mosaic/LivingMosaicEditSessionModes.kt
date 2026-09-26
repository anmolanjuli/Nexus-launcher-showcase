package com.nexus.launcher.ui.widgets.mosaic

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.widgets.WidgetCoordinateSpace
import com.nexus.launcher.ui.widgets.WidgetMenuScrimView
import com.nexus.launcher.ui.widgets.WidgetMoveArrowView
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout
import com.nexus.launcher.ui.widgets.WidgetResizeArrowView
import com.nexus.launcher.ui.widgets.WidgetResizeOverlay
import kotlin.math.roundToInt

/** Move / resize-nudge / drag wiring for [LivingMosaicEditSession]. */
object LivingMosaicEditSessionModes {

    fun onMoveModeChanged(
        isMoveMode: Boolean,
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        overlay: WidgetOverlayLayout,
        mainContainer: FrameLayout,
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        sessionState: LivingMosaicEditSession.SessionState,
        activeScrimView: WidgetMenuScrimView?,
        activeResizeOverlay: WidgetResizeOverlay?,
        setMoveArrow: (WidgetMoveArrowView?) -> Unit,
        getMoveArrow: () -> WidgetMoveArrowView?,
        homeScreenViewModel: HomeScreenViewModel
    ) {
        if (isMoveMode) {
            activeScrimView?.animateDim(0f)
            activeResizeOverlay?.visibility = View.GONE
            mosaicView.setMoveModeActive(true)

            val startItem = overlay.liveMosaics[item.id] ?: item
            sessionState.currentX = startItem.xFraction
            sessionState.currentY = startItem.yFraction

            val arrows = WidgetMoveArrowView(
                context = activity,
                widgetView = mosaicView,
                page = startItem.page,
                pageWidth = WidgetCoordinateSpace.pageWidthOf(overlay),
                onNudge = { dx, dy ->
                    nudgeMosaic(
                        canvasView, overlay, item, mosaicView,
                        sessionState, dx, dy, getMoveArrow
                    )
                }
            ).apply { tag = LivingMosaicEditSession.TAG_EDIT }
            setMoveArrow(arrows)
            mainContainer.addView(arrows)
        } else {
            activeScrimView?.animateDim(1f)
            activeResizeOverlay?.visibility = View.VISIBLE
            mosaicView.setMoveModeActive(false)
            getMoveArrow()?.let { mainContainer.removeView(it) }
            setMoveArrow(null)
            persistPendingMove(activity, homeScreenViewModel, item, sessionState)
        }
    }

    private fun nudgeMosaic(
        canvasView: LauncherCanvasView,
        overlay: WidgetOverlayLayout,
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        sessionState: LivingMosaicEditSession.SessionState,
        dx: Float,
        dy: Float,
        getMoveArrow: () -> WidgetMoveArrowView?
    ) {
        val pw = canvasView.width.toFloat().coerceAtLeast(1f)
        val ph = canvasView.height.toFloat().coerceAtLeast(1f)
        sessionState.currentX += dx / pw
        sessionState.currentY += dy / ph

        val liveItem = overlay.liveMosaics[item.id] ?: item
        val clamped = LivingMosaicDragHelper.clampFractions(
            canvasView, sessionState.currentX, sessionState.currentY,
            liveItem.spanX, liveItem.spanY
        )
        sessionState.currentX = clamped.first
        sessionState.currentY = clamped.second

        overlay.updateMosaicLayoutParams(
            item.id, sessionState.currentX, sessionState.currentY,
            liveItem.spanX, liveItem.spanY
        )
        sessionState.pendingMovePersist = true

        val lp = mosaicView.layoutParams as FrameLayout.LayoutParams
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlay)
        val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
            lp.leftMargin.toFloat(), liveItem.page, pageW
        ).toInt()
        getMoveArrow()?.updateWidgetBounds(screenLeft, lp.topMargin, lp.width, lp.height)
    }

    fun onResizeNudgeModeChanged(
        isResizeNudgeMode: Boolean,
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        overlay: WidgetOverlayLayout,
        mainContainer: FrameLayout,
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        sentinel: Int,
        activeScrimView: WidgetMenuScrimView?,
        activeResizeOverlay: WidgetResizeOverlay?,
        setResizeArrow: (WidgetResizeArrowView?) -> Unit,
        getResizeArrow: () -> WidgetResizeArrowView?,
        homeScreenViewModel: HomeScreenViewModel
    ) {
        if (isResizeNudgeMode) {
            activeScrimView?.animateDim(0f)
            activeResizeOverlay?.visibility = View.GONE
            val lp = mosaicView.layoutParams as FrameLayout.LayoutParams
            val pageW = WidgetCoordinateSpace.pageWidthOf(overlay)
            val livePage = overlay.liveMosaics[item.id]?.page ?: item.page
            val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
                lp.leftMargin.toFloat(), livePage, pageW
            ).toInt()
            // Keep sentinel liveItems in sync with mosaic (includes wFrac when present)
            overlay.liveItems[sentinel] = overlay.liveMosaics[item.id] ?: item
            val arrows = WidgetResizeArrowView(
                context = activity,
                widgetView = mosaicView,
                overlayLayout = overlay,
                appWidgetId = sentinel,
                cachedColumns = canvasView.currentGridCols,
                cachedRows = canvasView.currentGridRows
            ).apply {
                tag = LivingMosaicEditSession.TAG_EDIT
                updateWidgetBounds(screenLeft, lp.topMargin, lp.width, lp.height)
            }
            setResizeArrow(arrows)
            mainContainer.addView(
                arrows,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        } else {
            activeScrimView?.animateDim(1f)
            persistResizeNudgeIfNeeded(
                getResizeArrow, overlay, mosaicView, item, sentinel,
                homeScreenViewModel, canvasView
            )
            getResizeArrow()?.let { mainContainer.removeView(it) }
            setResizeArrow(null)
            activeResizeOverlay?.visibility = View.VISIBLE
        }
    }

    fun persistPendingMove(
        activity: MainActivity,
        homeScreenViewModel: HomeScreenViewModel,
        item: HomeScreenItem,
        sessionState: LivingMosaicEditSession.SessionState
    ) {
        if (!sessionState.pendingMovePersist) return
        Log.d(
            "MosaicNudge",
            "Persisting position xFrac=${sessionState.currentX} yFrac=${sessionState.currentY}"
        )
        val (cols, rows) = com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(activity)
        val (col, row) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
            activity, sessionState.currentX, sessionState.currentY, item.spanX, item.spanY,
            item.folderConfigJson, cols, rows
        )
        // TEMPORARY — HomeGridDropDiag; remove with the diag object.
        com.nexus.launcher.ui.canvas.HomeGridDropDiag.logMosaicPersist(
            trigger = "persistPendingMove",
            item = item,
            xFrac = sessionState.currentX,
            yFrac = sessionState.currentY,
            col = col,
            row = row
        )
        homeScreenViewModel.updateItemPosition(
            item.id, item.page, sessionState.currentX, sessionState.currentY, col, row
        )
        sessionState.pendingMovePersist = false
    }

    /**
     * Persist free-size from arrow nudge (same contract as handle drag).
     * Does not call updateAppWidgetOptions (mosaic box is not an AppWidget).
     */
    fun persistResizeNudgeIfNeeded(
        getResizeArrow: () -> WidgetResizeArrowView?,
        overlay: WidgetOverlayLayout,
        mosaicView: LivingMosaicView,
        item: HomeScreenItem,
        sentinel: Int,
        homeScreenViewModel: HomeScreenViewModel,
        canvasView: LauncherCanvasView
    ) {
        if (getResizeArrow() == null) return
        val lp = mosaicView.layoutParams as FrameLayout.LayoutParams
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlay)
        val overlayH = overlay.height.toFloat().coerceAtLeast(1f)
        val wFrac = lp.width / overlay.viewWidth.coerceAtLeast(1f)
        val hFrac = lp.height / overlayH
        val cols = canvasView.currentGridCols
        val rows = canvasView.currentGridRows
        val cellW = canvasView.gridAreaWidth / cols.toFloat().coerceAtLeast(1f)
        val cellH = (canvasView.viewHeight - canvasView.topInset - canvasView.dockBottomReserve).toFloat().coerceAtLeast(0f) / rows.toFloat().coerceAtLeast(1f)
        if (cellW <= 0f || cellH <= 0f) return
        val spanX = (lp.width / cellW).roundToInt().coerceAtLeast(1)
        val spanY = (lp.height / cellH).roundToInt().coerceAtLeast(1)

        val xFrac = WidgetCoordinateSpace.xFractionFromAbsoluteCenter(
            lp.leftMargin + lp.width / 2f, item.page, pageW
        )
        val yFrac = (lp.topMargin + lp.height / 2f) / overlayH

        val base = overlay.liveMosaics[item.id] ?: item
        val cfg = MosaicConfig.parse(base.folderConfigJson).copy(wFrac = wFrac, hFrac = hFrac)
        val updated = base.copy(
            spanX = spanX,
            spanY = spanY,
            xFraction = xFrac,
            yFraction = yFrac,
            folderConfigJson = cfg.toJson()
        )
        overlay.liveMosaics[item.id] = updated
        overlay.liveItems[sentinel] = updated
        homeScreenViewModel.updateMosaicFreeSize(
            item.id, spanX, spanY, xFrac, yFrac, wFrac, hFrac
        )
    }

    fun wireDrag(
        mosaicView: LivingMosaicView,
        item: HomeScreenItem,
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        overlay: WidgetOverlayLayout,
        density: Float,
        dismissAll: (String) -> Unit,
        homeScreenViewModel: HomeScreenViewModel
    ) = LivingMosaicDragHelper.wireDrag(
        mosaicView, item, activity, canvasView, overlay, density, dismissAll, homeScreenViewModel
    )
}
