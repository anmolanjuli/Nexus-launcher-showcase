package com.nexus.launcher.ui.gestures

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs

/**
 * Translates raw [MotionEvent] streams into semantic launcher gestures.
 */
class GestureManager(
    context: Context,
    private val listener: LauncherGestureListener,
) {

    private val viewConfiguration = ViewConfiguration.get(context)

    private var isLongPressActive = false
    private var hasDown = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    /**
     * Note: GestureDetector detects long presses by default ([GestureDetectorCompat] keeps this on).
     * When long press fires, further movement for this sequence is suppressed for scroll detection.
     */
    private val detector = GestureDetectorCompat(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                hasDown = true
                downX = e.x
                downY = e.y
                isLongPressActive = false
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Prevent "long-press then release" from leaking into a single-tap.
                if (!isLongPressActive) {
                    listener.onSingleTap()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                isLongPressActive = true
                listener.onLongPress()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (e1 == null || !hasDown) {
                    return false
                }

                // Priority fix: if a touch sequence originated on an icon/folder that is gesture-eligible,
                // do not process this as a global swipe — the per-icon system takes priority regardless of velocity.
                if (com.nexus.launcher.ui.canvas.IconSwipeGestureDetector.swipeCandidateItem != null ||
                    com.nexus.launcher.ui.canvas.IconSwipeGestureDetector.isIconSwipeCandidate) {
                    return false
                }

                // Use full gesture displacement from stored DOWN -> latest touch coordinates.
                val deltaX = lastX - downX
                val deltaY = lastY - downY

                // Velocity & distance minimums to prevent "slow drag velocity trap".
                if (abs(deltaY) < MIN_SWIPE_DISTANCE) {
                    return false
                }
                if (abs(velocityY) < MIN_SWIPE_VELOCITY) {
                    return false
                }

                // Strict verticality: require a mostly-vertical gesture.
                // If horizontal deviation is more than half of the vertical movement, discard it.
                if (abs(deltaY) <= abs(deltaX) * 2f) {
                    return false
                }

                // Additional guard against diagonal bleed: vertical velocity must dominate horizontal velocity.
                if (abs(velocityY) <= abs(velocityX) * 2f) {
                    return false
                }

                return when {
                    velocityY < 0f && deltaY < 0f -> {
                        listener.onSwipeUp()
                        true
                    }
                    velocityY > 0f && deltaY > 0f -> {
                        listener.onSwipeDown()
                        true
                    }
                    else -> false
                }
            }
        },
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        // Multi-touch blocking: ignore any pointerCount > 1.
        if (event.pointerCount > 1) {
            isLongPressActive = false
            return false
        }

        // Track latest coordinates so onFling can use full displacement.
        lastX = event.x
        lastY = event.y

        val handled = detector.onTouchEvent(event)

        // Reset long-press state on release/cancel AFTER delivering the event to GestureDetector.
        // This ordering prevents "long press release" from leaking into onSingleTapConfirmed().
        when (event.actionMasked) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> isLongPressActive = false
        }

        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            hasDown = false
        }

        return handled
    }

    private companion object {
        // Absolute swipe thresholds in raw pixels.
        const val MIN_SWIPE_DISTANCE = 120f
        const val MIN_SWIPE_VELOCITY = 1000f
    }
}
