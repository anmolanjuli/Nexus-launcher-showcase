package com.nexus.launcher.feed

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.Choreographer
import com.nexus.launcher.ui.canvas.MotionSpeed
import com.nexus.launcher.ui.canvas.SettlePhysics

/** Feed panel settle animations bypass [Settings.Global.ANIMATOR_DURATION_SCALE] when it is 0
 *  so the launcher still animates smoothly when the user has system animations disabled. */
object FeedPanelAnim {

    fun systemAnimatorScale(context: Context): Float =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

    /**
     * Release physics for the panel, shared with the drawer and the home pages.
     *
     * This surface was the worst offender of the three: its duration came from remaining
     * distance alone and its curve was a fixed `DecelerateInterpolator(1.6f)`, so a hard flick
     * and a slow release settled identically and neither one continued the finger's motion.
     */
    internal fun settle(
        context: Context,
        fromPx: Float,
        toPx: Float,
        velocityXPxPerSec: Float
    ): SettlePhysics.Settle = SettlePhysics.settle(
        fromPx = fromPx,
        toPx = toPx,
        releaseVelocityPxPerSec = velocityXPxPerSec,
        speedMultiplier = MotionSpeed.multiplier(context),
        omega = SettlePhysics.OMEGA_CRISP
    )

    fun interface Cancellable {
        fun cancel()
    }

    fun animateFloat(
        context: Context,
        from: Float,
        to: Float,
        durationMs: Long,
        curve: TimeInterpolator,
        onUpdate: (Float) -> Unit,
        onEnd: () -> Unit
    ): Cancellable {
        if (NexusFeedEInkCoordinator.isEInkMode(context)) {
            val animator = ValueAnimator.ofFloat(from, to).apply {
                duration = 100L
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { onUpdate(it.animatedValue as Float) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = onEnd()
                })
                start()
            }
            return Cancellable { animator.cancel() }
        }
        if (systemAnimatorScale(context) <= 0f) {
            return runManual(from, to, durationMs, curve, onUpdate, onEnd)
        }
        val animator = ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = curve
            addUpdateListener { onUpdate(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
        return Cancellable { animator.cancel() }
    }

    private fun runManual(
        from: Float,
        to: Float,
        durationMs: Long,
        curve: TimeInterpolator,
        onUpdate: (Float) -> Unit,
        onEnd: () -> Unit
    ): Cancellable {
        var running = true
        var startNanos = 0L
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!running) return
                if (startNanos == 0L) startNanos = frameTimeNanos
                val elapsedMs = (frameTimeNanos - startNanos) / 1_000_000f
                val t = (elapsedMs / durationMs).coerceIn(0f, 1f)
                onUpdate(from + (to - from) * curve.getInterpolation(t))
                if (t < 1f) {
                    choreographer.postFrameCallback(this)
                } else {
                    running = false
                    onEnd()
                }
            }
        }
        choreographer.postFrameCallback(callback)
        return Cancellable {
            if (!running) return@Cancellable
            running = false
            choreographer.removeFrameCallback(callback)
        }
    }
}
