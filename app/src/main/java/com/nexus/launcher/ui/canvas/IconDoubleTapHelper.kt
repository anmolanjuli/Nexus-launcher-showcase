package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import com.nexus.launcher.data.HomeScreenItem

object IconDoubleTapHelper {
    fun setupDoubleTapDetector(
        view: LauncherCanvasView,
        dragTouchHandler: DragTouchHandler,
        homeItem: HomeScreenItem,
        viewModel: com.nexus.launcher.ui.MainViewModel
    ): androidx.core.view.GestureDetectorCompat? {
        return IconSwipeGestureDetector.setupDoubleTapDetector(view, dragTouchHandler, homeItem, viewModel)
    }
}
