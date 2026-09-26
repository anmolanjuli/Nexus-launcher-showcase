package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.ui.folder.FolderHomeTouchHelper
import com.nexus.launcher.ui.model.LauncherState

/**
 * Two-finger global swipe on HOME. Mirrors [LauncherGlobalSwipeHelper] for the
 * dual-finger settings keys. Stateful tracker owned by [LauncherTouchHandler].
 */
internal class LauncherTwoFingerSwipeTracker {

    private var isTracking = false
    private var swipeConsumed = false
    private var startY = 0f

    fun onActionDown() {
        isTracking = false
        swipeConsumed = false
    }

    /** @return true if two-finger tracking started on this event. */
    fun onPointerDown(view: LauncherCanvasView, event: MotionEvent): Boolean {
        if (event.pointerCount == 2 && view.uiState == LauncherState.HOME && !view.isDragging) {
            isTracking = true
            swipeConsumed = false
            startY = event.getY(0)
            view.homeLongPressRunnable?.let { view.homeHandler.removeCallbacks(it) }
            FolderHomeTouchHelper.cancelFolderLongPress(view)
            view.longPressRunnable?.let { view.removeCallbacks(it) }
            view.isDragging = false
            return true
        }
        return false
    }

    /** @return true if the MOVE should be fully consumed (block other drag handling). */
    fun onMove(view: LauncherCanvasView, event: MotionEvent): Boolean {
        if (!isTracking) return false
        if (!swipeConsumed && event.pointerCount == 2) {
            val dy = event.getY(0) - startY
            if (Math.abs(dy) > 120f) {
                swipeConsumed = true
                val viewModel = (view.context as? com.nexus.launcher.ui.MainActivity)?.let {
                    androidx.lifecycle.ViewModelProvider(it)[com.nexus.launcher.ui.MainViewModel::class.java]
                }
                val actionKey = if (dy < 0) {
                    viewModel?.nexusSettings?.value?.globalTwoFingerSwipeUp ?: "NONE"
                } else {
                    viewModel?.nexusSettings?.value?.globalTwoFingerSwipeDown ?: "NONE"
                }
                val baseEnum = try {
                    com.nexus.launcher.ui.model.GestureAction.valueOf(actionKey)
                } catch (e: Exception) {
                    com.nexus.launcher.ui.model.GestureAction.NONE
                }
                if (baseEnum != com.nexus.launcher.ui.model.GestureAction.NONE) {
                    val dummyItem = com.nexus.launcher.data.HomeScreenItem(
                        id = -1, packageName = "", page = -1, column = -1, row = -1
                    )
                    if (viewModel != null) {
                        IconSwipeGestureDetector.dispatchAction(
                            view.context, baseEnum, null, dummyItem, viewModel
                        )
                    }
                }
            }
        }
        return true
    }

    /** Clears tracking; returns whether this gesture was a two-finger track. */
    fun onUpOrCancel(): Boolean {
        val was = isTracking
        isTracking = false
        return was
    }
}
