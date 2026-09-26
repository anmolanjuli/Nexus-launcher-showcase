package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.ui.model.LauncherState

object LauncherRailTouchHelper {
    /**
     * Touch zone measured from the screen edge: covers the drawn letters (16dp edge margin +
     * 20dp rail, see DrawerRailRenderer) plus 8dp of slack. It used to be 20dp, which only
     * reached the letters when a finger landed right at the edge.
     */
    private const val RAIL_TOUCH_DP = 44f

    fun handleRailTouchMove(
        view: LauncherCanvasView,
        event: MotionEvent
    ): Boolean {
        val railTop = if (view.railTopY > 0) view.railTopY else view.drawerGridTop
        val railBottom = if (view.railBottomY > 0) view.railBottomY else view.drawerGridBottom
        val isRtl = view.isRtl
        view.railRenderer.isRtl = isRtl
        view.railRenderer.updateTouchY(event.y, view)
        val newScrollY = view.railRenderer.handleRailTouch(
            view, event.y, view.viewHeight, railTop, railBottom,
            view.maxScrollY, view.rawDrawerApps, view.gridRenderer.cellHeight, view.gridRenderer.railColumns
        )
        newScrollY?.let { view.scrollY = it; view.postInvalidateOnAnimation() }
        val density = view.resources.displayMetrics.density
        val railEdge = if (isRtl) (SideInsets.left + RAIL_TOUCH_DP * density) else (view.viewWidth - SideInsets.right - (RAIL_TOUCH_DP * density))
        view.railRenderer.updateFingerPosition(event.x, event.y, railEdge)
        view.fastScrollLetter = view.activeLetter?.toString()
        view.postInvalidateOnAnimation()
        return true
    }

    fun handleRailTouchDown(
        view: LauncherCanvasView,
        event: MotionEvent
    ): Boolean {
        val railTop = if (view.railTopY > 0) view.railTopY else view.drawerGridTop
        val railBottom = if (view.railBottomY > 0) view.railBottomY else view.drawerGridBottom
        val isRtl = view.isRtl
        view.railRenderer.isRtl = isRtl
        val density = view.resources.displayMetrics.density
        val isTouchOnRail = if (isRtl) {
            val railRight = SideInsets.left + RAIL_TOUCH_DP * density
            event.x <= railRight
        } else {
            val railLeft = view.viewWidth - SideInsets.right - (RAIL_TOUCH_DP * density)
            event.x >= railLeft
        }
        if (view.uiState == LauncherState.DRAWER && !view.isSearchMode && isTouchOnRail && event.y >= railTop && event.y <= railBottom) {
            view.removeCallbacks(view.longPressRunnable)
            view.railRenderer.updateTouchY(event.y, view)
            val newScrollY = view.railRenderer.handleRailTouch(
                view, event.y, view.viewHeight, railTop, railBottom,
                view.maxScrollY, view.rawDrawerApps, view.gridRenderer.cellHeight, view.gridRenderer.railColumns
            )
            newScrollY?.let { view.scrollY = it; view.postInvalidateOnAnimation() }
            return true
        }
        return false
    }
}
