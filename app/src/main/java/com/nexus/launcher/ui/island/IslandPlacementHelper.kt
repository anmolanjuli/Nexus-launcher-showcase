package com.nexus.launcher.ui.island

import android.view.Gravity
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.immersive.ImmersiveStatusBarView

/** Helper for view placement, z-ordering above immersive status bar, and cutout sync. */
object IslandPlacementHelper {

    fun syncPlacement(island: IslandView, onMorph: () -> Unit) {
        val lp = island.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.topMargin != 0 || lp.gravity != (Gravity.TOP or Gravity.LEFT)) {
            lp.topMargin = 0
            lp.gravity = Gravity.TOP or Gravity.LEFT
            island.layoutParams = lp
        }
        (island.parent as? FrameLayout)?.let { liftAboveStatus(it, island) }
        val band = IslandGeometry.readBand(island)
        val changed = island.bandHeight != band.heightPx ||
            island.cutoutCenterX != band.centerX ||
            island.cutoutCenterY != band.centerY ||
            island.cutoutWidth != band.cutoutWidthPx ||
            island.cutoutHeight != band.cutoutHeightPx
        island.bandHeight = band.heightPx
        island.cutoutCenterX = band.centerX
        island.cutoutCenterY = band.centerY
        island.cutoutWidth = band.cutoutWidthPx
        island.cutoutHeight = band.cutoutHeightPx
        if (changed) island.requestLayout()
        onMorph()
    }

    fun indexAboveStatus(root: FrameLayout, activity: MainActivity): Int {
        for (i in root.childCount - 1 downTo 0) {
            if (root.getChildAt(i) is ImmersiveStatusBarView) {
                return (i + 1).coerceAtMost(root.childCount)
            }
        }
        val workspace = root.indexOfChild(activity.findViewById(R.id.workspace_container))
        return (workspace + 1).coerceIn(0, root.childCount)
    }

    fun liftAboveStatus(root: FrameLayout, island: IslandView) {
        var statusIndex = -1
        for (i in 0 until root.childCount) {
            if (root.getChildAt(i) is ImmersiveStatusBarView) statusIndex = i
        }
        val islandIndex = root.indexOfChild(island)
        if (statusIndex < 0 || islandIndex < 0 || islandIndex > statusIndex) return
        val lp = island.layoutParams
        root.removeView(island)
        root.addView(island, (statusIndex + 1).coerceAtMost(root.childCount), lp)
    }
}
