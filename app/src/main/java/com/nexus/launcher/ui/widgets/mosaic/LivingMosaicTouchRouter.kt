package com.nexus.launcher.ui.widgets.mosaic

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * Handles touch routing, mode swipes, focus page swipes, and canvas handoff for [LivingMosaicView].
 */
internal class LivingMosaicTouchRouter(
    private val view: LivingMosaicView,
    private val swipeAnimator: LivingMosaicSwipeAnimator,
    private val editGestures: LivingMosaicEditGestures,
    private val modeSwipe: LivingMosaicModeSwipe
) {
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val pageSwipeHandoff = com.nexus.launcher.ui.widgets.WidgetPageSwipeHandoff()
    private var downX = 0f
    private var downY = 0f
    private var swipeArmed = false
    private var trackingSwipe = false
    // A vertical swipe that starts over something that can scroll that way (a mail or calendar
    // list inside a cell) belongs to it for the whole gesture, not to the mode swipe.
    private var childOwnsVertical = false
    private var verticalChecked = false
    private var downRawX = 0f
    private var downRawY = 0f

    fun dispatch(ev: MotionEvent): Boolean {
        if (pageSwipeHandoff.isActive) {
            pageSwipeHandoff.forward(view, ev)
            return true
        }
        if (editGestures.isEditBlockingSwipe() || swipeAnimator.isAnimating) {
            return editGestures.dispatch(ev) || true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downRawX = ev.rawX
                downRawY = ev.rawY
                childOwnsVertical = false
                verticalChecked = false
                val modeButtonHit = isModeButtonHit(ev.x, ev.y)
                editGestures.childInputEnabled = !modeButtonHit
                swipeArmed = view.canSwipeMosaicFocus() || view.canSwipeMosaicPages()
                trackingSwipe = false
                modeSwipe.reset()
                pageSwipeHandoff.onDown(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!trackingSwipe && !modeSwipe.isTracking) {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (!verticalChecked && dy > touchSlop && dy > dx) {
                        verticalChecked = true
                        val direction = if (ev.y < downY) 1 else -1
                        childOwnsVertical = com.nexus.launcher.ui.widgets.ScrollableUnderPoint.canScroll(view, downRawX, downRawY, direction)
                    }
                    if (childOwnsVertical) {
                        // Leave it to the cell: fall through to editGestures.dispatch below.
                    } else if (modeSwipe.tryStart(ev.x - downX, ev.y - downY, touchSlop, view.config.currentChildren().isNotEmpty())) {
                        editGestures.cancelPendingLongPress()
                        editGestures.sendCancelToChildren()
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (swipeArmed && dx > touchSlop && dx > dy) {
                        trackingSwipe = true
                        editGestures.cancelPendingLongPress()
                        editGestures.sendCancelToChildren()
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (view.config.mode == MosaicConfig.MODE_MOSAIC &&
                        pageSwipeHandoff.tryStart(view, ev, touchSlop)
                    ) {
                        editGestures.cancelPendingLongPress()
                        editGestures.sendCancelToChildren()
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                if (trackingSwipe) {
                    swipeAnimator.onDrag(ev.x - downX)
                }
                if (modeSwipe.isTracking) modeSwipe.drag(ev.y - downY)
            }
        }

        if (trackingSwipe) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (ev.actionMasked == MotionEvent.ACTION_UP) {
                        LivingMosaicSwipeCommit.complete(view, ev.x - downX)
                    } else {
                        swipeAnimator.settle(0, view.width.toFloat(), {}, {})
                    }
                    trackingSwipe = false
                    swipeArmed = false
                }
            }
            return true
        }

        if ((ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) && modeSwipe.finish(
                deltaY = ev.y - downY,
                commit = ev.actionMasked == MotionEvent.ACTION_UP,
                onSwipeUp = { if (view.config.mode == MosaicConfig.MODE_MOSAIC) view.setModeExternal(MosaicConfig.MODE_SINGLE) },
                onSwipeDown = { if (view.config.mode == MosaicConfig.MODE_SINGLE) view.setModeExternal(MosaicConfig.MODE_MOSAIC) }
            )) {
            swipeArmed = false
            return true
        }

        if (editGestures.dispatch(ev)) {
            swipeArmed = false
            return true
        }
        return true
    }

    private fun isModeButtonHit(x: Float, y: Float): Boolean =
        view.modeButton.visibility == View.VISIBLE &&
            x >= view.modeButton.left && x <= view.modeButton.right &&
            y >= view.modeButton.top && y <= view.modeButton.bottom
}
