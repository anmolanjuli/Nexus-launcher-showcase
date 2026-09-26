package com.nexus.launcher.ui.widgets.mosaic

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Long-press + drag edit gestures for [LivingMosaicView] (NexusWidgetView parity).
 * Child AppWidgetHostViews still receive normal taps/scrolls when long-press does not fire.
 * ACTION_DOWN returns false so page/focus swipe can arm without being aborted.
 */
class LivingMosaicEditGestures(private val host: LivingMosaicView) {

    var onLongPressDetected: (() -> Unit)? = null
    var onDragStarted: (() -> Unit)? = null
    var onDragMoved: ((Float, Float) -> Unit)? = null
    var onDropDetected: ((Float, Float) -> Unit)? = null
    var onBodyTappedInMoveMode: (() -> Unit)? = null

    private var moveModeActive = false
    private var editChromeActive = false
    private var longPressActive = false
    private var dragging = false
    private var longPressStartX = 0f
    private var longPressStartY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    var childInputEnabled = true

    private val touchSlop by lazy { ViewConfiguration.get(host.context).scaledTouchSlop }
    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        longPressActive = true
        host.parent?.requestDisallowInterceptTouchEvent(true)
        sendCancelToChildren()
        host.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPressDetected?.invoke()
    }

    fun setMoveModeActive(active: Boolean) {
        moveModeActive = active
    }

    fun setEditChromeActive(active: Boolean) {
        editChromeActive = active
    }

    fun isEditBlockingSwipe(): Boolean =
        editChromeActive || longPressActive || dragging || moveModeActive

    fun cancelPendingLongPress() {
        handler.removeCallbacks(longPressRunnable)
    }

    fun sendCancelToChildren() {
        val cancel = MotionEvent.obtain(
            SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            0f, 0f, 0
        )
        host.dispatchToChildren(cancel)
        cancel.recycle()
    }

    fun onDetached() {
        handler.removeCallbacks(longPressRunnable)
    }

    /** @return true if the event was fully handled (caller should not run page-swipe). */
    fun dispatch(ev: MotionEvent): Boolean {
        if (moveModeActive) {
            if (ev.actionMasked == MotionEvent.ACTION_UP) {
                onBodyTappedInMoveMode?.invoke()
            }
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressActive = false
                dragging = false
                longPressStartX = ev.rawX
                longPressStartY = ev.rawY
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                if (childInputEnabled) host.dispatchToChildren(ev)
                // Always arm mosaic long-press (including over children)
                handler.postDelayed(
                    longPressRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
                // false: allow LivingMosaicView to arm page/focus swipe
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - longPressStartX
                val dy = ev.rawY - longPressStartY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    handler.removeCallbacks(longPressRunnable)
                    if (longPressActive) {
                        if (!dragging) {
                            dragging = true
                            lastRawX = ev.rawX
                            lastRawY = ev.rawY
                            host.parent?.requestDisallowInterceptTouchEvent(true)
                            host.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            onDragStarted?.invoke()
                        } else {
                            val pScaleX = (host.parent as? android.view.View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
                            val pScaleY = (host.parent as? android.view.View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
                            host.translationX += (ev.rawX - lastRawX) / pScaleX
                            host.translationY += (ev.rawY - lastRawY) / pScaleY
                            lastRawX = ev.rawX
                            lastRawY = ev.rawY
                            onDragMoved?.invoke(host.translationX, host.translationY)
                        }
                        return true
                    }
                }
                if (dragging) return true
                if (childInputEnabled) host.dispatchToChildren(ev)
                return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    val dropX = host.translationX
                    val dropY = host.translationY
                    host.translationX = 0f
                    host.translationY = 0f
                    dragging = false
                    longPressActive = false
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        host.performHapticFeedback(
                            android.view.HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
                        )
                    }
                    onDropDetected?.invoke(dropX, dropY)
                    return true
                }
                if (longPressActive) {
                    longPressActive = false
                    return true
                }
                if (childInputEnabled) host.dispatchToChildren(ev)
                return false
            }
        }
        return false
    }
}
