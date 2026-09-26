package com.nexus.launcher.feed

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.ui.canvas.LayoutProfile

/**
 * Lays out the feed page for the current orientation and insets — split from [NexusFeedPage].
 *
 * Portrait: tabs in a bottom bar, content above it.
 * Phone landscape: the screen is too short for a bottom bar, so the tabs become an icon-only
 * rail on the left edge, and content sits beside it. Both keep clear of the camera cutout and a
 * side navigation bar, which in landscape sit on a side edge.
 */
internal object NexusFeedInsetsLayout {

    private const val RAIL_WIDTH_DP = 72f

    fun apply(
        page: View,
        insets: WindowInsetsCompat,
        header: View,
        content: View,
        scroll: View,
        bar: NexusFeedBottomBar,
        dp: Float
    ) {
        val statusBar = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        ).top.coerceAtLeast((24 * dp).toInt())
        val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val nb = navBottom.coerceAtLeast((16 * dp).toInt())
        header.setPadding((20 * dp).toInt(), statusBar + (12 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())

        val landscape = LayoutProfile.isPhoneLandscape(page.context)
        bar.setRailMode(landscape)
        val barLp = bar.layoutParams as? FrameLayout.LayoutParams ?: return
        val contentLp = content.layoutParams as? FrameLayout.LayoutParams ?: return
        if (landscape) {
            val side = insets.getInsets(
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.navigationBars()
            )
            val railWidth = (RAIL_WIDTH_DP * dp).toInt()
            bar.updateRailInsets(side.left, statusBar, navBottom)
            barLp.width = railWidth + side.left
            barLp.height = FrameLayout.LayoutParams.MATCH_PARENT
            barLp.gravity = Gravity.LEFT
            contentLp.leftMargin = railWidth + side.left
            contentLp.rightMargin = side.right
            scroll.setPadding(0, 0, 0, nb + (16 * dp).toInt())
        } else {
            bar.updateInsets(nb)
            barLp.width = FrameLayout.LayoutParams.MATCH_PARENT
            barLp.gravity = Gravity.BOTTOM
            contentLp.leftMargin = 0
            contentLp.rightMargin = 0
            scroll.setPadding(0, 0, 0, (88 * dp).toInt() + nb)
        }
        bar.layoutParams = barLp
        content.layoutParams = contentLp
    }
}
