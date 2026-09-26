package com.nexus.launcher.ui.canvas

import android.animation.TimeInterpolator
import android.view.Choreographer

/**
 * Carries a gesture the rest of the way after the finger lifts, on the Choreographer.
 *
 * It used to be a `ValueAnimator`, which the system switches off entirely when "Remove
 * animations" is on — every animator then finishes in a single frame whatever duration it was
 * given, so a page jumped rather than settled and no amount of tuning the curve could show.
 *
 * Continuing a movement the hand started is not a decorative animation to be switched off:
 * stopping halfway with the finger already lifted is not a simpler animation, it is a broken
 * gesture. (Android's own launcher pages on a scroller for the same reason.) Decorative
 * transitions elsewhere still honour the setting.
 *
 * Used for the home pages and for closing the drawer; each supplies what to do with the value.
 */
internal class PageSettleDriver {

    private var frameCallback: Choreographer.FrameCallback? = null
    private var onEnd: ((cancelled: Boolean) -> Unit)? = null

    val isRunning: Boolean get() = frameCallback != null

    fun start(
        fromPx: Float,
        toPx: Float,
        durationMs: Long,
        interpolator: TimeInterpolator,
        onUpdate: (Float) -> Unit,
        onEnd: (cancelled: Boolean) -> Unit,
    ) {
        cancel()
        if (durationMs <= 0L) {
            onUpdate(toPx)
            onEnd(false)
            return
        }
        val distance = toPx - fromPx
        if (kotlin.math.abs(distance) <= 0.5f) {
            onUpdate(toPx)
            onEnd(false)
            return
        }
        val duration = durationMs.coerceAtLeast(1L) * 1_000_000L
        val startNanos = System.nanoTime()
        this.onEnd = onEnd

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (frameCallback !== this) return
                val elapsed = (System.nanoTime() - startNanos).coerceAtLeast(0L)
                val linear = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                onUpdate(fromPx + distance * interpolator.getInterpolation(linear))
                if (linear >= 1f) {
                    frameCallback = null
                    val end = this@PageSettleDriver.onEnd
                    this@PageSettleDriver.onEnd = null
                    end?.invoke(false)
                } else {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        frameCallback = callback
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun cancel() {
        val callback = frameCallback ?: return
        frameCallback = null
        Choreographer.getInstance().removeFrameCallback(callback)
        val end = onEnd
        onEnd = null
        end?.invoke(true)
    }
}

/** True while the drawer is moving on its own, whichever mechanism is carrying it. */
internal val LauncherCanvasView.isDrawerMotionRunning: Boolean
    get() = animator?.isRunning == true || drawerSettle.isRunning

/** Stops the drawer wherever it is, so a new touch takes over cleanly. */
internal fun LauncherCanvasView.cancelDrawerMotion() {
    animator?.cancel()
    drawerSettle.cancel()
}

/** True while any page motion is in flight, whichever mechanism is carrying it. */
internal val LauncherCanvasView.isPageMotionRunning: Boolean
    get() = pageAnimator != null || pageSettle.isRunning

/** Stops whatever is moving the pages, so a new gesture starts from a known state. */
internal fun LauncherCanvasView.cancelPageMotion() {
    pageAnimator?.cancel()
    pageAnimator = null
    pageSettle.cancel()
}
