package com.nexus.launcher.ui

import android.view.View
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat

/** Edge insets for the drawer's docked bars (search pill, category bar) — split from DrawerChromeController. */
internal object DrawerBarInsets {

    /**
     * Clears the status bar at the top or the gesture/nav bar at the bottom, plus the other bar's
     * height when both are docked to the same edge.
     */
    fun apply(
        view: View, insets: WindowInsetsCompat, atBottom: Boolean, stackOffset: Int
    ) {
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        if (atBottom) {
            lp.topMargin = 0
            lp.bottomMargin =
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom + stackOffset
        } else {
            lp.topMargin =
                maxOf(
                    insets.getInsets(
                        WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
                    ).top,
                    com.nexus.launcher.ui.immersive.ImmersiveStatus.reservedTopPx
                ) + stackOffset
            lp.bottomMargin = 0
        }
        // Phone landscape: the camera cutout (and a 3-button nav bar) sit on a side edge.
        val side = if (com.nexus.launcher.ui.canvas.LayoutProfile.isPhoneLandscape(view.context)) {
            insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.navigationBars())
        } else null
        lp.leftMargin = side?.left ?: 0
        lp.rightMargin = side?.right ?: 0
        view.layoutParams = lp
    }
}
