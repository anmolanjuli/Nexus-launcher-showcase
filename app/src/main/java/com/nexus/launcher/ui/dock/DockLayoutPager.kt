package com.nexus.launcher.ui.dock

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

object DockLayoutPager {

    fun maxScrollX(width: Int, pageCount: Int): Float =
        if (width == 0 || pageCount <= 1) 0f else ((pageCount - 1) * width).toFloat()

    fun currentPageIndex(width: Int, scrollOffsetX: Float, pageCount: Int): Int =
        if (width == 0) 0 else Math.round(scrollOffsetX / width)
            .coerceIn(0, (pageCount - 1).coerceAtLeast(0))

    fun animateToPage(
        width: Int,
        pageCount: Int,
        page: Int,
        scrollOffsetX: Float,
        snapAnimator: ValueAnimator?,
        onOffset: (Float) -> Unit,
        onAnimator: (ValueAnimator?) -> Unit
    ) {
        if (width == 0) return
        val target = (page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)) * width).toFloat()
        snapAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(scrollOffsetX, target).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener { onOffset(it.animatedValue as Float) }
            start()
        }
        onAnimator(animator)
    }
}
