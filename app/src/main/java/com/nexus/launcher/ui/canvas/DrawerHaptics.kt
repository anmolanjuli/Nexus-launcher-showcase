package com.nexus.launcher.ui.canvas

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.nexus.launcher.ui.model.LauncherState

/**
 * Dedicated haptics coordinator for the App Drawer:
 * - Drawer open (strong pulse on animation start)
 * - Drawer close (strong pulse on animation start)
 * - Overflow menu button tap (light tap)
 * - Scroll boundary hit at top/bottom (light bounce tick, exactly once per hit)
 */
object DrawerHaptics {

    private var isAtTopBoundary = false
    private var isAtBottomBoundary = false

    fun onDrawerOpen(view: View?) {
        view ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun onDrawerClose(view: View?) {
        view ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun onOverflowTap(view: View?) {
        view ?: return
        if (Build.VERSION.SDK_INT >= 23) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun checkScrollBoundaries(view: LauncherCanvasView) {
        if (view.uiState != LauncherState.DRAWER || view.drawerTranslationY > 0f) {
            isAtTopBoundary = false
            isAtBottomBoundary = false
            return
        }
        val maxScroll = view.maxScrollY
        if (maxScroll <= 0f) return

        val currentY = view.scrollY
        if (currentY <= 1f) {
            if (!isAtTopBoundary) {
                isAtTopBoundary = true
                tick(view)
            }
        } else if (currentY >= maxScroll - 1.5f) {
            if (!isAtBottomBoundary) {
                isAtBottomBoundary = true
                tick(view)
            }
        } else {
            isAtTopBoundary = false
            isAtBottomBoundary = false
        }
    }

    private fun tick(view: View) {
        if (Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }
}
