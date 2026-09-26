package com.nexus.launcher.ui.widgets.mosaic

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/**
 * Rubber-band + snap swipe for mosaic page / focus carousel.
 * Translates [contentHost] during the gesture; Focus settle adds a deep zoom rebound.
 */
class LivingMosaicSwipeAnimator(
    private val contentHost: FrameLayout,
    private val density: Float
) {
    private var dragOffset = 0f
    private var snapAnimator: ValueAnimator? = null

    val isAnimating: Boolean get() = snapAnimator?.isRunning == true

    fun onDrag(deltaX: Float) {
        snapAnimator?.cancel()
        dragOffset = deltaX * 0.92f
        contentHost.scrollX = -dragOffset.toInt()
        contentHost.pivotX = contentHost.width / 2f
        contentHost.pivotY = contentHost.height / 2f
        val pull = (kotlin.math.abs(dragOffset) / (contentHost.width.coerceAtLeast(1))).coerceIn(0f, 1f)
        val s = 1f - 0.04f * pull
        contentHost.scaleX = s
        contentHost.scaleY = s
    }

    /**
     * @param direction -1 = next (swipe left), +1 = previous (swipe right), 0 = cancel
     * @param pageWidthPx width used for snap distance
     * @param deepZoom when true (Focus mode), rebound includes overshoot zoom
     */
    fun settle(
        direction: Int,
        pageWidthPx: Float,
        onCommit: () -> Unit,
        onDone: () -> Unit,
        deepZoom: Boolean = false
    ) {
        snapAnimator?.cancel()
        val target = when (direction) {
            -1 -> -pageWidthPx * 0.35f
            1 -> pageWidthPx * 0.35f
            else -> 0f
        }
        val start = -contentHost.scrollX.toFloat()
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (direction == 0) 220L else 260L
            interpolator = PathInterpolator(0.16f, 0.82f, 0.20f, 1f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                val currentAnimVal = start + (target - start) * t
                contentHost.scrollX = -currentAnimVal.toInt()
                if (direction != 0) {
                    contentHost.alpha = 1f - 0.15f * t
                    val s = 1f - 0.06f * t
                    contentHost.scaleX = s
                    contentHost.scaleY = s
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (direction != 0) {
                        onCommit()
                        val reboundStart = -target * 0.4f
                        contentHost.scrollX = -reboundStart.toInt()
                        contentHost.alpha = 0.85f
                        contentHost.scaleX = if (deepZoom) 0.90f else 0.96f
                        contentHost.scaleY = contentHost.scaleX
                        ValueAnimator.ofFloat(0f, 1f).apply {
                            duration = if (deepZoom) 320L else 220L
                            interpolator = DecelerateInterpolator(1.6f)
                            addUpdateListener { a2 ->
                                val t2 = a2.animatedValue as Float
                                val currentRebound = reboundStart * (1f - t2)
                                contentHost.scrollX = -currentRebound.toInt()
                                contentHost.alpha = 0.85f + 0.15f * t2
                                val from = if (deepZoom) 0.90f else 0.96f
                                val peak = 1f
                                val s = if (t2 < 0.6f) {
                                    from + (peak - from) * (t2 / 0.6f)
                                } else {
                                    peak + (1f - peak) * ((t2 - 0.6f) / 0.4f)
                                }
                                contentHost.scaleX = s
                                contentHost.scaleY = s
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    reset()
                                    onDone()
                                }
                            })
                            start()
                        }
                    } else {
                        reset()
                        onDone()
                    }
                }
            })
        }
        snapAnimator = anim
        anim.start()
    }

    fun reset() {
        snapAnimator?.cancel()
        snapAnimator = null
        dragOffset = 0f
        contentHost.translationX = 0f
        contentHost.scrollX = 0
        contentHost.alpha = 1f
        contentHost.scaleX = 1f
        contentHost.scaleY = 1f
    }

    fun thresholdPx(): Float = 48f * density
}
