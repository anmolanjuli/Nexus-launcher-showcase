package com.nexus.launcher.ui.drawercategories

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlin.math.abs

class CategoriesDrawerDismissRelay(
    private val host: ViewGroup,
    private val canvasProvider: () -> LauncherCanvasView?,
) {
    var isForwarding: Boolean = false
        private set

    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private val hostLoc = IntArray(2)
    private val canvasLoc = IntArray(2)
    private var downX = 0f
    private var downY = 0f
    private var capturedDown: MotionEvent? = null
    private var sentDown = false
    private var downOnRail = false

    fun onIntercept(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                downX = ev.x
                downY = ev.y
                capturedDown = MotionEvent.obtain(ev)
                downOnRail = isOnAlphabetRail(host, ev.x, ev.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isForwarding) return true
                if (downOnRail) return false
                val dy = ev.y - downY
                val dx = ev.x - downX
                if (dy > touchSlop && dy > abs(dx) && !canScrollUpAt(host, ev.x, ev.y)) {
                    isForwarding = true
                    host.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isForwarding) reset()
            }
        }
        return isForwarding
    }

    fun onTouch(ev: MotionEvent): Boolean {
        if (!isForwarding) return false
        if (!sentDown) {
            capturedDown?.let { forward(it) }
            sentDown = true
        }
        forward(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            reset()
        }
        return true
    }

    fun cancel() {
        reset()
    }

    private fun forward(ev: MotionEvent) {
        val canvas = canvasProvider() ?: return
        host.getLocationOnScreen(hostLoc)
        canvas.getLocationOnScreen(canvasLoc)
        val ox = (hostLoc[0] - canvasLoc[0]).toFloat()
        val oy = (hostLoc[1] - canvasLoc[1]).toFloat()
        ev.offsetLocation(ox, oy)
        canvas.onTouchEvent(ev)
        ev.offsetLocation(-ox, -oy)
    }

    private fun reset() {
        isForwarding = false
        sentDown = false
        downOnRail = false
        capturedDown?.recycle()
        capturedDown = null
        host.parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun isOnAlphabetRail(view: View, x: Float, y: Float): Boolean {
        if (view is com.nexus.launcher.ui.drawercategories.StripAlphabetRail) {
            return view.isInHitZone(x)
        }
        if (view !is ViewGroup) return false
        for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val cx = x + view.scrollX - child.left
            val cy = y + view.scrollY - child.top
            if (cx < 0f || cy < 0f || cx >= child.width || cy >= child.height) continue
            if (isOnAlphabetRail(child, cx, cy)) return true
        }
        return false
    }

    private fun canScrollUpAt(view: View, x: Float, y: Float): Boolean {
        if (view.canScrollVertically(-1)) return true
        if (view !is ViewGroup) return false
        for (i in view.childCount - 1 downTo 0) {
            val child = view.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val cx = x + view.scrollX - child.left
            val cy = y + view.scrollY - child.top
            if (cx < 0f || cy < 0f || cx >= child.width || cy >= child.height) continue
            if (canScrollUpAt(child, cx, cy)) return true
        }
        return false
    }
}
