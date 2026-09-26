package com.nexus.launcher.ui.island

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

class IslandTouchHandler(private val view: IslandView) {
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var isDragging = false
    private var isLongPressFired = false
    private var tracking = false
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var lastTapTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPressFired = true
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        view.onLongPress?.invoke()
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = hit(event.x, event.y)
                if (!tracking) return false
                downX = event.x
                downY = event.y
                isDragging = false
                isLongPressFired = false
                handler.postDelayed(longPressRunnable, longPressTimeout)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > slop || abs(dy) > slop) {
                    isDragging = true
                    handler.removeCallbacks(longPressRunnable)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                tracking = false
                isDragging = false
                isLongPressFired = false
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                handler.removeCallbacks(longPressRunnable)
                tracking = false
                if (isLongPressFired) {
                    isLongPressFired = false
                    return true
                }

                val dx = event.x - downX
                val dy = event.y - downY
                val density = view.resources.displayMetrics.density
                val swipeThreshold = 18f * density

                if (isDragging && (abs(dx) > swipeThreshold || abs(dy) > swipeThreshold)) {
                    if (abs(dy) > abs(dx)) {
                        if (dy < -swipeThreshold) {
                            view.onSwipeUp?.invoke()
                        } else if (dy > swipeThreshold && (view.shape == IslandShape.COMPACT || view.shape == IslandShape.MULTI)) {
                            view.onTap?.invoke()
                        }
                    } else {
                        if (dx < -swipeThreshold) {
                            view.onSwipeLeft?.invoke()
                        } else if (dx > swipeThreshold) {
                            view.onSwipeRight?.invoke()
                        }
                    }
                    return true
                }

                val now = event.eventTime
                if (view.shape == IslandShape.MULTI && now - lastTapTime < doubleTapTimeout &&
                    hypot((event.x - lastTapX).toDouble(), (event.y - lastTapY).toDouble()) < slop * 2
                ) {
                    lastTapTime = 0L
                    view.onDoubleTap?.invoke()
                    return true
                }

                lastTapTime = now
                lastTapX = event.x
                lastTapY = event.y
                handleTap(event.x, event.y)
                return true
            }
        }
        return false
    }

    private fun handleTap(x: Float, y: Float) {
        if (view.shape == IslandShape.EXPANDED) {
            val shown = view.pagePayload()
            if (shown?.kind == IslandKind.CALL) {
                when {
                    view.prevRect.contains(x, y) -> view.onPrev?.invoke()
                    view.nextRect.contains(x, y) -> view.onNext?.invoke()
                    else -> view.onTap?.invoke()
                }
                return
            }
            // Taps follow the same page the drawing does.
            when (view.currentPage()) {
                IslandPage.ACTIVITY -> {
                    val activityAction = IslandActivityDraw.actionRects
                        .indexOfFirst { it.contains(x, y) }
                        .takeIf { it >= 0 && shown?.usesActivityCard == true }
                    when {
                        activityAction != null -> view.onActivityAction?.invoke(activityAction)
                        view.playRect.contains(x, y) -> view.onPlayToggle?.invoke()
                        view.prevRect.contains(x, y) -> view.onPrev?.invoke()
                        view.nextRect.contains(x, y) -> view.onNext?.invoke()
                        view.actionRect.contains(x, y) -> view.onAction?.invoke()
                        view.artRect.contains(x, y) -> view.onLaunch?.invoke()
                        IslandCardDraw.hitTestMedia(x, y, view.context) -> Unit
                        else -> view.onTap?.invoke()
                    }
                }
                IslandPage.GLANCE -> {
                    if (!IslandGlanceDraw.hitTest(x, y, view.context)) view.onTap?.invoke()
                }
            }
        } else {
            when {
                view.playRect.contains(x, y) -> view.onPlayToggle?.invoke()
                view.actionRect.contains(x, y) -> view.onAction?.invoke()
                view.artRect.contains(x, y) -> view.onLaunch?.invoke()
                else -> view.onTap?.invoke()
            }
        }
    }

    private fun hit(x: Float, y: Float): Boolean {
        val density = view.resources.displayMetrics.density
        val slop = 12f * density
        if (!view.primaryRect.isEmpty) {
            if (x >= view.primaryRect.left - slop &&
                x <= view.primaryRect.right + slop &&
                y >= view.primaryRect.top - slop &&
                y <= view.primaryRect.bottom + slop
            ) return true
        }
        if (!view.secondaryRect.isEmpty) {
            if (x >= view.secondaryRect.left - slop &&
                x <= view.secondaryRect.right + slop &&
                y >= view.secondaryRect.top - slop &&
                y <= view.secondaryRect.bottom + slop
            ) return true
        }
        if (!view.capRect.isEmpty) {
            val minHalf = 24f * density
            val cx = view.capRect.centerX()
            val cy = view.capRect.centerY()
            val halfW = maxOf(view.capRect.width() / 2f + slop, minHalf)
            val halfH = maxOf(view.capRect.height() / 2f + slop, minHalf)
            if (x >= cx - halfW && x <= cx + halfW && y >= cy - halfH && y <= cy + halfH) {
                return true
            }
        }
        return false
    }
}
