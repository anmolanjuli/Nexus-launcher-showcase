package com.nexus.launcher.ui

import android.view.animation.OvershootInterpolator
import android.widget.GridLayout

/** Staggered rise/fade for Manage Pages cards on first layout. */
object ManagePagesEntranceAnimator {

    private const val STAGGER_MS = 45L
    private const val DURATION_MS = 280L

    fun play(grid: GridLayout, density: Float, onComplete: (() -> Unit)? = null) {
        val rise = 20f * density
        val last = grid.childCount - 1
        if (last < 0) {
            onComplete?.invoke()
            return
        }
        for (i in 0 until grid.childCount) {
            val child = grid.getChildAt(i) ?: continue
            child.alpha = 0f
            child.translationY = rise
            child.scaleX = 0.94f
            child.scaleY = 0.94f
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(i * STAGGER_MS)
                .setDuration(DURATION_MS)
                .setInterpolator(OvershootInterpolator(0.85f))
                .withEndAction {
                    if (i == last) onComplete?.invoke()
                }
                .start()
        }
    }

    fun cancel(grid: GridLayout) {
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i)?.animate()?.cancel()
        }
    }
}
