package com.nexus.launcher.ui.canvas

import android.content.Context
import android.view.MotionEvent
import com.nexus.launcher.ui.model.GestureAction
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.data.HomeScreenItem
import androidx.lifecycle.ViewModelProvider
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.MainViewModel

object LauncherGlobalSwipeHelper {

    fun handleSingleFingerSwipe(
        view: LauncherCanvasView,
        event: MotionEvent,
        totalDx: Float,
        totalDy: Float,
        isHorizontalSwipe: Boolean
    ): Boolean {
        if (event.pointerCount == 1 && !isHorizontalSwipe && !IconSwipeGestureDetector.isIconSwipeCandidate &&
            view.uiState == LauncherState.HOME && view.drawerTranslationY >= view.viewHeight - 1f) {
            
            // Only log if they have actually swiped down enough to matter, to avoid spamming tap events
            if (Math.abs(totalDy) > 20f && Math.abs(totalDy) > Math.abs(totalDx)) {
                android.util.Log.d("GestureDebug", "Single-finger swipe on blank space detected. dy=$totalDy")
            }
            
            if (Math.abs(totalDy) > 120f && Math.abs(totalDy) > Math.abs(totalDx)) {
                android.util.Log.d("GestureDebug", "Single-finger swipe exceeded threshold!")
                val viewModel = (view.context as? MainActivity)?.let { ViewModelProvider(it)[MainViewModel::class.java] }
                val actionKey = if (totalDy < 0) {
                    viewModel?.nexusSettings?.value?.globalSwipeUp ?: "OPEN_APP_DRAWER"
                } else {
                    viewModel?.nexusSettings?.value?.globalSwipeDown ?: "NONE"
                }
                
                if (totalDy < 0 && actionKey == "OPEN_APP_DRAWER") {
                    // Do nothing, let existing drawer open logic run
                } else {
                    val baseEnum = try { GestureAction.valueOf(actionKey) } catch(e: Exception) { GestureAction.NONE }
                    if (baseEnum != GestureAction.NONE) {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                        view.closeDrawerInstantly()
                        val dummyItem = HomeScreenItem(id = -1, packageName = "", page = -1, column = -1, row = -1)
                        if (viewModel != null) {
                            IconSwipeGestureDetector.dispatchAction(view.context, baseEnum, null, dummyItem, viewModel)
                        }
                        return true
                    }
                }
            }
        }
        return false
    }
}
