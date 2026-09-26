package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Intercepts a horizontal Focus Window swipe before a child widget can consume it. */
internal class MosaicFocusSwipeContainer(
    context: Context,
    private val onDrag: (deltaX: Float) -> Unit,
    private val onRelease: (deltaX: Float) -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                intercepting = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > slop && abs(dx) > abs(dy)) {
                    intercepting = true
                    onDrag(dx)
                }
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> if (intercepting) {
                onDrag(event.x - downX)
                return true
            }
            MotionEvent.ACTION_UP -> if (intercepting) {
                onRelease(event.x - downX)
                intercepting = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> if (intercepting) {
                intercepting = false
                onCancel()
                return true
            }
        }
        return intercepting
    }

    /** A hosted widget may request its own horizontal gesture; Focus owns carousel paging. */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (!disallowIntercept) super.requestDisallowInterceptTouchEvent(false)
    }
}
