package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.ui.model.LauncherState

/** Retains the visible page position when a new swipe interrupts a settle animation. */
internal class PageSwipeTracker(private val view: LauncherCanvasView) {
    private var anchorX = 0f
    private var anchorOffset = 0f

    fun onActionDown(event: MotionEvent) {
        anchorX = event.x
        anchorOffset = 0f
    }

    fun tryBegin(event: MotionEvent, totalDx: Float, totalDy: Float): Boolean {
        val isHorizontal = view.uiState == LauncherState.HOME &&
            view.drawerTranslationY >= view.viewHeight - 1 &&
            kotlin.math.abs(totalDx) > view.touchSlop * HORIZONTAL_SLOP_FRACTION &&
            kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)
        if (!isHorizontal) return false

        val isInterruptingSettle = view.isPageMotionRunning
        if (isInterruptingSettle) {
            anchorX = event.x
            anchorOffset = view.dragScrollOffset
        } else {
            // Anchor to the CURRENT finger position, not startTouchX, so the slop already
            // travelled is consumed rather than replayed as an instant jump on the first
            // tracked frame. Pages now leave 0 and follow the finger 1:1 from rest.
            anchorX = event.x
            anchorOffset = 0f
        }
        view.cancelPageMotion()
        return true
    }

    fun offsetAt(event: MotionEvent): Float = anchorOffset + (event.x - anchorX)

    /**
     * [offsetAt] with out-of-bounds resistance applied at the first and last page.
     *
     * The previous flat `offset * 0.2f` held the response at a constant fifth of the finger, so
     * the very first pixel past the edge already read as a wall. [SettlePhysics.rubberBand]
     * opens near the finger and tightens the further it is pulled.
     */
    fun resistedOffsetAt(event: MotionEvent): Float {
        val offset = offsetAt(event)
        val width = if (SelectionModeTransform.isCardTrackActive(view)) {
            SelectionModeCardTrack.swipeSpanPx(view)
        } else {
            view.viewWidth.toFloat().coerceAtLeast(1f)
        }
        val minOffset = -(view.totalPages - 1 - view.currentPage).coerceAtLeast(0) * width
        val maxOffset = view.currentPage.coerceAtLeast(0) * width
        return when {
            offset > maxOffset -> maxOffset + SettlePhysics.rubberBand(offset - maxOffset, width)
            offset < minOffset -> minOffset + SettlePhysics.rubberBand(offset - minOffset, width)
            else -> offset
        }
    }

    private companion object {
        const val HORIZONTAL_SLOP_FRACTION = 0.5f
    }
}
