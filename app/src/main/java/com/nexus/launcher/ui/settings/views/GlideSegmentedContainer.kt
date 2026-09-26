package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class GlideSegmentedContainer(context: Context) : LinearLayout(context) {

    var onGlideSelect: ((String) -> Unit)? = null
    var buttonsProvider: (() -> Map<String, TextView>)? = null
    var scrollView: HorizontalScrollView? = null

    private var isGliding = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = 160L
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastHoveredValue: String? = null

    private val longPressRunnable = Runnable {
        isGliding = true
        parent?.requestDisallowInterceptTouchEvent(true)
        scrollView?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        findButtonAt(downX)?.let { (value, _) ->
            lastHoveredValue = value
            onGlideSelect?.invoke(value)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                isGliding = false
                lastHoveredValue = null
                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - downX)
                val dy = Math.abs(ev.y - downY)
                if (!isGliding && (dx > touchSlop || dy > touchSlop)) {
                    mainHandler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                lastHoveredValue = null
            }
        }
        return isGliding
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isGliding) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    scrollView?.requestDisallowInterceptTouchEvent(true)
                    findButtonAt(ev.x)?.let { (value, btn) ->
                        if (value != lastHoveredValue) {
                            lastHoveredValue = value
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            onGlideSelect?.invoke(value)
                        }
                        scrollView?.let { sv ->
                            val density = resources.displayMetrics.density
                            val scrollMargin = (48 * density).toInt()
                            val leftDiff = (btn.left - sv.scrollX)
                            val rightDiff = (sv.scrollX + sv.width) - btn.right
                            if (leftDiff < scrollMargin) {
                                sv.smoothScrollBy(-((scrollMargin - leftDiff).coerceAtLeast(16)), 0)
                            } else if (rightDiff < scrollMargin) {
                                sv.smoothScrollBy((scrollMargin - rightDiff).coerceAtLeast(16), 0)
                            }
                        }
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
                lastHoveredValue = null
                if (isGliding) {
                    isGliding = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    scrollView?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun findButtonAt(x: Float): Pair<String, TextView>? {
        val btns = buttonsProvider?.invoke() ?: return null
        val rect = Rect()
        for ((value, btn) in btns) {
            btn.getHitRect(rect)
            if (x >= rect.left && x <= rect.right) {
                return value to btn
            }
        }
        return null
    }
}
