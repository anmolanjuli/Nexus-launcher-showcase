package com.nexus.launcher.ui.canvas

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Shared home-grid net rectangle (status inset + dock + page-indicator band).
 * Preview and live home must use the same numbers so cell size cannot drift.
 */
object HomeGridAvailableSpace {
    const val TOP_INSET_EXTRA_PX = 16
    /** Page dots between the last row and the dock (the dots sit 8dp above the dock). */
    const val INDICATOR_BAND_DP = 24f
    const val DOCK_LABEL_BAND_DP = 40f
    const val LABEL_TEXT_SIZE_PX = 24f
    /** Icon-to-label gap; independent of inter-cell gap. */
    const val LABEL_GAP_DP = 2f

    fun statusBarTop(view: View): Int {
        val live = ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: 0
        if (live > 0) return live
        return systemStatusBarHeightPx(view)
    }

    private fun systemStatusBarHeightPx(view: View): Int {
        val resId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) view.resources.getDimensionPixelSize(resId) else 0
    }

    fun topInset(statusBarTop: Int): Int = statusBarTop + TOP_INSET_EXTRA_PX

    fun portraitDockIconSize(displayWidth: Int, density: Float): Int {
        return ((displayWidth / 5) * 0.55f).toInt()
            .coerceAtMost((56f * density).toInt())
    }

    fun fallbackDockBottomReserve(displayWidth: Int, density: Float): Int {
        val dockIcon = portraitDockIconSize(displayWidth, density)
        val dockBase = dockIcon + (DOCK_LABEL_BAND_DP * density).toInt()
        val indicator = (INDICATOR_BAND_DP * density).toInt()
        return dockBase + indicator
    }

    fun netHeightPx(
        displayHeight: Int,
        statusBarTop: Int,
        displayWidth: Int,
        density: Float
    ): Float {
        val top = topInset(statusBarTop)
        val bottom = fallbackDockBottomReserve(displayWidth, density)
        return (displayHeight - top - bottom).toFloat().coerceAtLeast(1f)
    }
}
