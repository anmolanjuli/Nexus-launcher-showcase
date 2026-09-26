package com.nexus.launcher.ui.widgets

import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlin.math.abs

/**
 * Forwards a swipe that began on a widget, box or mosaic to [LauncherCanvasView]: sideways (page
 * swipe) always, and upward (open the drawer, or whatever swipe-up is set to) when the caller
 * allows it. Widget overlay sits above the canvas, so events must be re-dispatched explicitly.
 *
 * An upward swipe stays with the widget when something under the finger can still scroll down —
 * a mail or calendar list — so those keep scrolling as they do in any launcher.
 */
class WidgetPageSwipeHandoff {
    var isActive = false
        private set

    private var downCopy: MotionEvent? = null
    private val loc = IntArray(2)

    fun onDown(event: MotionEvent) {
        downCopy?.recycle()
        downCopy = MotionEvent.obtain(event)
        isActive = false
    }

    fun tryStart(host: View, move: MotionEvent, touchSlop: Int, allowSwipeUp: Boolean = false): Boolean {
        if (isActive) return true
        val down = downCopy ?: return false
        val dx = abs(move.rawX - down.rawX)
        val dy = abs(move.rawY - down.rawY)
        val sideways = dx > touchSlop && dx > dy
        val upward = allowSwipeUp && dy > touchSlop && dy > dx && move.rawY < down.rawY &&
            !ScrollableUnderPoint.canScroll(host, down.rawX, down.rawY, 1)
        if (!sideways && !upward) return false
        val canvas = findCanvas(host) ?: return false
        isActive = true
        dispatchToCanvas(canvas, down)
        dispatchToCanvas(canvas, move)
        return true
    }

    fun forward(host: View, event: MotionEvent) {
        if (!isActive) return
        val canvas = findCanvas(host) ?: return
        dispatchToCanvas(canvas, event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            isActive = false
        }
    }

    fun cancel() {
        isActive = false
        downCopy?.recycle()
        downCopy = null
    }

    private fun dispatchToCanvas(canvas: LauncherCanvasView, event: MotionEvent) {
        canvas.getLocationOnScreen(loc)
        val copy = MotionEvent.obtain(event)
        copy.setLocation(event.rawX - loc[0], event.rawY - loc[1])
        canvas.onTouchEvent(copy)
        copy.recycle()
    }

    private fun findCanvas(host: View): LauncherCanvasView? {
        var p = host.parent
        while (p != null) {
            if (p is WidgetOverlayLayout) return p.canvasView
            p = p.parent
        }
        return null
    }
}
