package com.nexus.launcher.ui.canvas

/** Prevents / reverts accidental home-page switches while dragging toward the dock. */
internal object IconDragPageGuard {

    private var originPage: Int = -1

    fun capture(view: LauncherCanvasView) {
        if (originPage < 0) originPage = view.currentPage
    }

    fun restore(view: LauncherCanvasView) {
        if (originPage < 0) return
        val target = originPage
        originPage = -1
        view.cancelPageMotion()
        if (view.currentPage != target) {
            view.commitPageSwipe(target)
        } else {
            view.dragScrollOffset = 0f
            view.invalidate()
        }
    }

    fun clear() {
        originPage = -1
    }

    fun allowsEdgePageSwitch(rawY: Float, isHoveringDock: Boolean, density: Float, screenHeight: Int): Boolean {
        if (isHoveringDock) return false
        val gravityWellY = screenHeight - (130 * density)
        return rawY < gravityWellY
    }
}
