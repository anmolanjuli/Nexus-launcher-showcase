package com.nexus.launcher.ui

import android.app.Activity
import android.view.ViewTreeObserver
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Immersive home screen (Settings → Home → Immersive mode): hides the status bar and the
 * navigation / gesture bar while the launcher is in front, so the home screen uses the full
 * height. A swipe in from the top or bottom edge shows the bars; [reassertSoon] hides them again
 * shortly after, and they are hidden again whenever the launcher regains focus — after the
 * notification shade, a dialog, or another app.
 *
 * Uses BEHAVIOR_DEFAULT rather than "transient bars by swipe": with gesture navigation, the
 * transient behaviour spends the first edge swipe on revealing the bars, so Back took two swipes.
 * With the default behaviour the system gestures (Back, Home) work in one swipe while the bars
 * are hidden.
 *
 * With the bars hidden their insets read 0, so every inset-driven layout (the grid's top inset,
 * the dock, the drawer) takes the freed space by itself.
 */
class ImmersiveModeController(private val activity: Activity) {

    private var enabled = false
    private var focusListenerAdded = false

    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
        if (hasFocus && enabled) apply()
    }

    fun setEnabled(value: Boolean) {
        isActive = value
        if (!focusListenerAdded) {
            activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            focusListenerAdded = true
        }
        if (value == enabled) return
        enabled = value
        apply()
    }

    companion object {
        /**
         * True while immersive mode is on. Anything else that shows or hides system bars (the
         * drawer hides the nav bar while open and used to show it again on close) must leave
         * them hidden while this is set — showing them here keeps them up until focus changes.
         */
        @Volatile var isActive: Boolean = false
            private set

        private const val REASSERT_DELAY_MS = 2_500L
        private val reassert = Runnable { reassertTarget?.let { hideBars(it) } }
        private var reassertTarget: Activity? = null

        /**
         * Re-hides the bars a moment after they came back — an edge swipe or a Back gesture
         * shows them for good under BEHAVIOR_DEFAULT, and nothing else would hide them again
         * until the launcher lost and regained focus. Delayed so a swipe to look at the bars
         * is not cut short.
         */
        fun reassertSoon(view: android.view.View, activity: Activity) {
            if (!isActive) return
            reassertTarget = activity
            view.removeCallbacks(reassert)
            view.postDelayed(reassert, REASSERT_DELAY_MS)
        }

        private fun hideBars(activity: Activity) {
            if (!isActive) return
            hideOn(activity.window)
        }

        /**
         * The status bar is never wanted in immersive mode — the launcher's own status row sits
         * in the same place and the two would overlap; the notification icon in that row opens
         * the shade. A top-edge swipe still brings it up (Android does not let an app block that),
         * so it is put away again straight away. The navigation bar is left to [reassertSoon].
         */
        fun hideStatusBarNow(activity: Activity) {
            if (!isActive) return
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.statusBars())
        }

        /** Hides the bars on [window] the immersive way; also used for sheets over the home screen. */
        fun hideOn(window: Window) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun apply() {
        if (enabled) {
            hideOn(activity.window)
        } else {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
