package com.nexus.launcher.ui.dock

import android.view.MotionEvent

internal object DockLayoutTouch {

    fun onTouchEvent(dock: DockLayout, event: MotionEvent): Boolean {
        if (dock.outbound.handleTouch(event)) {
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL) {
                dock.gesture.onTouchEvent(event)
            }
            return true
        }
        dock.gesture.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dock.cancelSnapAnimator()
                dock.dockGestureRef.flingConsumed = false
                dock.outbound.clearPending()
                dock.outbound.recordTouchDown(event.x, event.y)
                dock.parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> dock.outbound.tryStartPending(event, dock.touchSlopPx) { item, anchor ->
                DockLayoutFinder.getActivitySafe(dock.context)?.contextMenuManager?.dismiss()
                dock.onInitiateDrag?.invoke(item, anchor)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dock.outbound.active) {
                    dock.outbound.consumeDeferredMenu()?.let { item ->
                        val activity = DockLayoutFinder.getActivitySafe(dock.context)
                        activity?.contextMenuManager?.let { manager ->
                            DockContextMenuLauncher.show(activity, manager, dock, item)
                        }
                    }
                }
                dock.outbound.clearPending()
                if (!dock.dockGestureRef.flingConsumed) dock.snapToNearestPageInternal()
                dock.dockGestureRef.flingConsumed = false
                dock.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
