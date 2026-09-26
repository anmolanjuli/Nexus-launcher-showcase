package com.nexus.launcher.ui.folder

/** Canonical grid width for alignBounds GridLayout with half-gap cell margins. */
object FolderGridWidthResolver {

    fun layoutWidthPx(widthCols: Int, iconSizePx: Int, gapPx: Int, density: Float): Int {
        val cellWidthPx = iconSizePx + gapPx
        val slackPx = (4f * density).toInt().coerceAtLeast(2)
        return widthCols * cellWidthPx + slackPx
    }

    fun cardWidthPx(layoutGridWidthPx: Int, density: Float, minHeaderWidthPx: Int): Int {
        val cardPad = (32f * density).toInt()
        val slackPx = (4f * density).toInt().coerceAtLeast(2)
        val naturalCardWidth = maxOf(
            layoutGridWidthPx + cardPad + slackPx,
            minHeaderWidthPx + cardPad
        )
        val screenWidthPx = android.content.res.Resources.getSystem().displayMetrics.widthPixels
        val maxCardWidth = screenWidthPx - (32f * density).toInt()
        return naturalCardWidth.coerceAtMost(maxCardWidth)
    }
}
