package com.nexus.launcher.ui.folder

import android.view.View
import android.widget.GridLayout

/** Staggered fade-in for folder grid icons after open. */
object FolderWindowGridStagger {

    private const val STAGGER_MS = 18L
    private const val DURATION_MS = 160L

    fun reveal(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i)
            child.alpha = 1f
            child.scaleX = 1f
            child.scaleY = 1f
            child.visibility = View.VISIBLE
        }
    }
}
