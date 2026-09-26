package com.nexus.launcher.ui.folder

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Positions overflow menu below the ⋮ button on the folder card. */
object FolderOverflowMenuPlacer {

    fun showBelowAnchor(menu: View, anchor: View, overlayRoot: FrameLayout) {
        menu.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val anchorLoc = IntArray(2)
        val rootLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        overlayRoot.getLocationOnScreen(rootLoc)
        val density = overlayRoot.resources.displayMetrics.density
        val gapPx = (4f * density).toInt()
        val lp = (menu.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        lp.gravity = Gravity.TOP or Gravity.LEFT
        val menuLeft = anchorLoc[0] - rootLoc[0] + anchor.width - menu.measuredWidth
        lp.leftMargin = menuLeft.coerceAtLeast((8f * density).toInt())
        lp.topMargin = anchorLoc[1] - rootLoc[1] + anchor.height + gapPx
        lp.rightMargin = 0
        lp.bottomMargin = 0
        menu.layoutParams = lp
        menu.visibility = View.VISIBLE
        menu.bringToFront()
    }
}
