package com.nexus.launcher.ui.widgets

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup

/**
 * Whether anything under a screen point can still scroll vertically — a mail or calendar list in
 * a hosted widget. A swipe that such a view can take is left to it: the home screen does not turn
 * it into a drawer swipe ([WidgetPageSwipeHandoff]) and a Living Mosaic does not turn it into a
 * mode swipe.
 *
 * Hit-tested on each view's visible rect, which includes scaling: widgets inside a Mosaic are
 * drawn as scaled-down miniatures, so their raw width and height overstate where they are.
 * Main thread only (shared scratch objects).
 */
internal object ScrollableUnderPoint {

    private val visible = Rect()
    private val windowOrigin = IntArray(2)

    /** [direction] as in [View.canScrollVertically]: positive = content can move up (finger up). */
    fun canScroll(view: View, rawX: Float, rawY: Float, direction: Int): Boolean {
        // Visible rects are in window coordinates; the touch is in screen coordinates.
        view.rootView.getLocationOnScreen(windowOrigin)
        return canScrollAt(view, (rawX - windowOrigin[0]).toInt(), (rawY - windowOrigin[1]).toInt(), direction)
    }

    private fun canScrollAt(view: View, x: Int, y: Int, direction: Int): Boolean {
        if (!view.isShown || !view.getGlobalVisibleRect(visible) || !visible.contains(x, y)) return false
        if (view.canScrollVertically(direction)) return true
        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                if (canScrollAt(view.getChildAt(i), x, y, direction)) return true
            }
        }
        return false
    }
}
