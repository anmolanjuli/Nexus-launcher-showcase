package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.ui.folder.DrawerFolderGesture
import com.nexus.launcher.ui.folder.FolderDrawerTouchHelper
import com.nexus.launcher.ui.folder.FolderHomeTouchHelper
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState

/**
 * ACTION_DOWN press-arming for home/drawer long-press and per-icon double-tap.
 * Extracted from [LauncherTouchHandler] — pure hit-test / runnable setup, no settle/fling.
 */
internal object LauncherTouchDownHelper {

    class DoubleTapState {
        var detector: androidx.core.view.GestureDetectorCompat? = null
        var currentItemKey: String? = null
    }

    data class ArmResult(val homeIconHit: Boolean, val drawerFolderHit: Boolean)

    fun armPressTargets(
        view: LauncherCanvasView,
        event: MotionEvent,
        dragTouchHandler: DragTouchHandler,
        doubleTapState: DoubleTapState
    ): ArmResult {
        FolderDrawerTouchHelper.cancel(view)
        var homeIconHit = false
        var drawerFolderHit = false
        if (DrawerFolderGesture.isDrawerInteractive(view)) {
            if (DrawerFolderGesture.findFolderAt(view, event.x, event.y) != null) {
                drawerFolderHit = true
                FolderDrawerTouchHelper.armOnDown(view, event.x, event.y)
            }
        }
        if (view.uiState == LauncherState.HOME) {
            val homeItem = CanvasHitTestHelper.getItemAt(view, event.x, event.y)
            if (homeItem != null) {
                homeIconHit = true
                val viewModel = (view.context as? com.nexus.launcher.ui.MainActivity)?.let {
                    androidx.lifecycle.ViewModelProvider(it)[com.nexus.launcher.ui.MainViewModel::class.java]
                }
                IconSwipeGestureDetector.checkEligibility(homeItem, viewModel)

                val key = if (homeItem.itemType == 1) "folder_${homeItem.id}" else homeItem.packageName
                if (doubleTapState.currentItemKey != key) {
                    doubleTapState.currentItemKey = key
                    if (viewModel != null) {
                        doubleTapState.detector = IconSwipeGestureDetector.setupDoubleTapDetector(
                            view, dragTouchHandler, homeItem, viewModel
                        )
                    } else {
                        doubleTapState.detector = null
                    }
                }

                val pos = view.fractionDerivedPositions[homeItem.id]
                    ?: Triple(homeItem.page, homeItem.column, homeItem.row)
                view.longPressHomeCol = pos.second
                view.longPressHomeRow = pos.third
                if (homeItem.itemType == 1) {
                    FolderHomeTouchHelper.armFolderLongPress(view, homeItem)
                } else {
                    view.homeLongPressRunnable = dragTouchHandler.buildHomeLongPressRunnable(homeItem)
                    view.homeHandler.postDelayed(
                        view.homeLongPressRunnable!!,
                        android.view.ViewConfiguration.getLongPressTimeout().toLong()
                    )
                }
            } else {
                doubleTapState.currentItemKey = null
                doubleTapState.detector = null
            }
        }
        doubleTapState.detector?.onTouchEvent(event)
        view.longPressRunnable = dragTouchHandler.buildLongPressRunnable(view.startTouchX, view.startTouchY)
        if (view.selectionState !is SelectionState.Selecting && !homeIconHit && !drawerFolderHit) {
            view.postDelayed(
                view.longPressRunnable,
                android.view.ViewConfiguration.getLongPressTimeout().toLong()
            )
        }
        return ArmResult(homeIconHit, drawerFolderHit)
    }
}
