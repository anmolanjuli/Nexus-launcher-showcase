package com.nexus.launcher.ui.widgets.mosaic

import android.widget.FrameLayout
import kotlin.math.abs

/** Owns the vertical gesture that changes between Mosaic and Focus modes. */
class LivingMosaicModeSwipe(
    private val contentHost: FrameLayout,
    private val density: Float
) {
    var isTracking = false
        private set

    fun reset() {
        isTracking = false
    }

    fun tryStart(deltaX: Float, deltaY: Float, touchSlop: Int, canSwitch: Boolean): Boolean {
        if (!canSwitch || abs(deltaY) <= touchSlop || abs(deltaY) <= abs(deltaX)) return false
        isTracking = true
        return true
    }

    fun drag(deltaY: Float) {
        contentHost.translationY = deltaY * 0.12f
    }

    fun finish(
        deltaY: Float,
        commit: Boolean,
        onSwipeUp: () -> Unit,
        onSwipeDown: () -> Unit
    ): Boolean {
        if (!isTracking) return false
        if (commit) {
            when {
                deltaY <= -48f * density -> onSwipeUp()
                deltaY >= 48f * density -> onSwipeDown()
            }
        }
        contentHost.animate().translationY(0f).setDuration(180L).start()
        isTracking = false
        return true
    }
}
