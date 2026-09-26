package com.nexus.launcher.ui.canvas

class CanvasPageAnimator(private val view: LauncherCanvasView) {
    fun animatePageMutation(onEnd: () -> Unit) {
        view.isMutatingState = true
        val speedMultiplier = MotionSpeed.multiplier(view.context)
        val a = android.animation.ValueAnimator.ofFloat(1f, 0f).setDuration((150L * speedMultiplier).toLong())
        a.addUpdateListener { view.pageTransitionAlpha = it.animatedValue as Float; view.invalidate() }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(anim: android.animation.Animator) {
                onEnd()
                android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = (150L * speedMultiplier).toLong()
                    addUpdateListener { view.pageTransitionAlpha = it.animatedValue as Float; view.invalidate() }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(anim: android.animation.Animator) {
                            view.isMutatingState = false
                            view.invalidate()
                        }
                        override fun onAnimationCancel(anim: android.animation.Animator) {
                            view.isMutatingState = false
                            view.invalidate()
                        }
                    })
                }.start()
            }
            override fun onAnimationCancel(anim: android.animation.Animator) {
                view.isMutatingState = false
                view.invalidate()
            }
        })
        a.start()
    }

    fun animateScrollToPage(targetPage: Int) {
        if (view.currentPage == targetPage) return
        val delta = targetPage - view.currentPage
        view.cancelPageMotion()
        view.dragScrollOffset = 0f
        val settle = SettlePhysics.settle(
            fromPx = 0f,
            toPx = -delta * view.viewWidth.toFloat(),
            releaseVelocityPxPerSec = 0f,
            speedMultiplier = MotionSpeed.multiplier(view.context),
            // The same curve the swipe settles on, so a page changed by tap and one changed by
            // finger arrive the same way — and on the same driver, so both survive "Remove
            // animations".
            omega = SettlePhysics.OMEGA_GLIDE,
            decay = SettlePhysics.DECAY_GLIDE,
        )
        view.isMutatingState = true
        view.pageSettle.start(
            0f, -delta * view.viewWidth.toFloat(), settle.durationMs, settle.interpolator,
            onUpdate = { value ->
                view.dragScrollOffset = value
                view.onPageScroll?.invoke(view.currentPage, view.dragScrollOffset)
                view.invalidate()
            },
        ) { cancelled ->
            if (cancelled) {
                view.isMutatingState = false
                return@start
            }
            view.commitPageSwipe(targetPage)
        }
    }
}
