package com.nexus.launcher.ui.canvas

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Left/right insets the home screen and drawer must keep content out of in phone landscape:
 * the camera cutout (on the left or right edge depending on which way the phone is turned)
 * and a side navigation bar (3-button navigation). The launcher window draws edge to edge, so
 * without these, icons, widgets and the drawer run under the cutout.
 *
 * Zero in portrait and on large screens, where the cutout sits in the status bar.
 */
object SideInsets {

    @Volatile var left: Int = 0
        private set
    @Volatile var right: Int = 0
        private set

    /** Re-read from [view]'s root insets; call whenever insets or configuration change. */
    fun update(view: View) = update(view, ViewCompat.getRootWindowInsets(view))

    /** From the insets an inset listener was just handed. */
    fun update(view: View, insets: WindowInsetsCompat?) {
        if (insets == null || !LayoutProfile.isPhoneLandscape(view.context)) {
            left = 0
            right = 0
            return
        }
        val side = insets.getInsets(
            WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.navigationBars()
        )
        left = side.left
        right = side.right
    }
}
