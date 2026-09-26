package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.ui.dock.DockLayout

object PageAdvanceTouchHelper {
    const val ADVANCE_DELAY_MS = 280L
    private val tempCardBounds = android.graphics.RectF()

    fun computePageAdvanceDirection(
        view: LauncherCanvasView,
        screenX: Float,
        itemLeft: Float = screenX,
        itemRight: Float = screenX
    ): Int? {
        if (view.isLandscape) return null
        if (SelectionModeTransform.isCardTrackActive(view)) {
            SelectionModeCardTrack.screenBounds(view, view.currentPage, view.dragScrollOffset, tempCardBounds)
            val density = view.resources.displayMetrics.density
            val gap = SelectionModeCardTrack.gapPx(view)
            val nextCardLeft = tempCardBounds.right + gap
            val prevCardRight = tempCardBounds.left - gap
            val enterThreshold = 16f * density

            // Near the shrunken border inside the current page, keep the user on current page so edge drops stay safe.
            // When dragged content or touch starts entering the adjacent shrunken card, smoothly advance without hassle.
            val advanceRight = (itemRight >= nextCardLeft + enterThreshold) || (screenX > tempCardBounds.right + 2f * density)
            val advanceLeft = (itemLeft <= prevCardRight - enterThreshold) || (screenX < tempCardBounds.left - 2f * density)

            return when {
                advanceLeft && view.currentPage > 0 -> -1
                advanceRight && view.currentPage < view.totalPages - 1 -> 1
                else -> null
            }
        }
        val edgeZone = maxOf(40f * view.resources.displayMetrics.density, view.viewWidth * 0.12f)
        return when {
            screenX < edgeZone && view.currentPage > 0 -> -1
            screenX > view.viewWidth - edgeZone && view.currentPage < view.totalPages - 1 -> 1
            else -> null
        }
    }

    fun handleDragMoveEdgeZone(
        view: LauncherCanvasView,
        event: MotionEvent,
        pageAdvanceHandler: android.os.Handler,
        pageAdvanceRunnable: Runnable?,
        setRunnable: (Runnable?) -> Unit
    ) {
        val dock = DockLayout.findFrom(view)
        val overDock = dock != null && event.y >= view.viewHeight - dock.height.toFloat()
        val iconRadius = 24f * view.resources.displayMetrics.density
        val delta = if (!overDock) {
            computePageAdvanceDirection(
                view = view,
                screenX = event.x,
                itemLeft = event.x - iconRadius,
                itemRight = event.x + iconRadius
            )
        } else null
        if (delta != null) {
            DockLayout.findFrom(view)?.forceClearHover()
            if (pageAdvanceRunnable == null && !view.isPageMotionRunning) {
                val target = view.currentPage + delta
                if (target in 0 until view.totalPages) {
                    val runnable = Runnable {
                        DrawerSnapAnimator.animatePageTransition(view, target)
                        setRunnable(null)
                    }
                    setRunnable(runnable)
                    pageAdvanceHandler.postDelayed(runnable, ADVANCE_DELAY_MS)
                }
            }
        } else {
            if (pageAdvanceRunnable != null) {
                pageAdvanceHandler.removeCallbacks(pageAdvanceRunnable)
                setRunnable(null)
            }
        }
    }
}
