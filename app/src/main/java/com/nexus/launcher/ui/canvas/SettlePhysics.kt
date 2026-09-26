package com.nexus.launcher.ui.canvas

import android.animation.TimeInterpolator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/**
 * Shared release physics for every finger-driven surface — drawer, home pages, feed panel.
 *
 * ## Why this exists
 *
 * Each surface used to settle with `DecelerateInterpolator(f)`. That curve is
 * `1 - (1-s)^(2f)`, whose normalised opening slope is `2f` — so it leaves the release point at
 * `2f * distance / duration`, regardless of how fast the finger was actually moving. At the
 * factor 1.6 used throughout, every surface departed at **3.2x** the finger's speed: the
 * characteristic "yank" the instant you lift.
 *
 * [settle] replaces that with a critically damped spring, evaluated in closed form so it still
 * runs on a plain `ValueAnimator` (the drawer's animator is shared with
 * `DockDrawerSync.attachToDrawerAnimator`, which needs a real `ValueAnimator` to hang an update
 * listener on).
 *
 * ## The curve
 *
 * Over normalised time `s` in `[0,1]`, with `a` the settling constant and `b` the normalised
 * release velocity:
 *
 *     x(s) = 1 - (1 + (a - b) * s) * e^(-a*s)
 *
 * `x(0) = 0` and `x'(0) = b` exactly, so scaling by the travelled distance reproduces the
 * release velocity to the pixel. `a` is fixed at [SETTLE_CONSTANT]; `e^-8` leaves 0.03% of the
 * travel outstanding at `s = 1`, which the normalisation divides out.
 *
 * Because a critically damped spring's settling time depends only on its stiffness — not on how
 * far or how fast it starts — the duration falls out of [OMEGA] rather than being guessed from
 * remaining distance, which is what kept the old durations fighting the old curve.
 */
internal object SettlePhysics {

    /** `a` in the curve above. 8 puts the residual at `e^-8` — under a pixel on any real travel. */
    private const val SETTLE_CONSTANT = 8f

    /** Base stiffness, rad/s. Settling time is [SETTLE_CONSTANT] / omega, so 28 gives ~286 ms. */
    const val OMEGA = 28f

    /** Stiffer channel for surfaces that should feel crisp rather than relaxed (~200 ms). */
    const val OMEGA_CRISP = 40f

    /**
     * The gliding channel, for a whole page moving under the finger (~375 ms).
     *
     * The decay is what makes the difference, not the duration: at `a = 8` the curve is 83% done
     * at 40% of the time and then meets a wall, which is what reads as a door being shut. At 4.5
     * it tracks a plain ease-out closely — 57% at 40%, 88% at 70% — so the momentum bleeds off
     * across the whole travel while still starting at exactly the speed the finger left.
     */
    const val OMEGA_GLIDE = 12f
    const val DECAY_GLIDE = 4.5f

    /** Release speeds under 5% of the remaining travel per second carry no useful momentum. */
    private const val NEGLIGIBLE_U = 0.05f

    /**
     * Floor on `b`. Negative `b` means the finger was moving *away* from the committed target,
     * so the curve backswings before returning — natural, but -2 caps that at ~2% of the travel.
     */
    private const val MIN_B = -2f

    private const val MIN_MS = 120L
    private const val MAX_MS = 420L

    /**
     * How far ahead a release is projected when deciding which side of a threshold it landed on.
     * This is the main feel knob for "did that flick count?" — raise it to make short flicks
     * commit more eagerly, lower it to weight the finger's resting position more heavily.
     */
    private const val PROJECTION_SECONDS = 0.2f

    /** Opening slope of the page-edge resistance curve — the fraction of finger travel at the edge. */
    private const val RUBBER_TENSION = 0.55f

    private val linear = TimeInterpolator { it }

    class Settle(val durationMs: Long, val interpolator: TimeInterpolator)

    /**
     * Builds the duration/interpolator pair that carries [fromPx] to [toPx] starting at exactly
     * [releaseVelocityPxPerSec] (signed in the same axis as the positions).
     */
    fun settle(
        fromPx: Float,
        toPx: Float,
        releaseVelocityPxPerSec: Float,
        speedMultiplier: Float = 1f,
        omega: Float = OMEGA,
        minMs: Long = MIN_MS,
        maxMs: Long = MAX_MS,
        decay: Float = SETTLE_CONSTANT
    ): Settle {
        val distance = toPx - fromPx
        val travel = abs(distance)
        if (travel <= 0.5f) return Settle(minMs, linear)

        // Release speed resolved into "fractions of the remaining travel per second", signed
        // positive when it points at the target.
        val towards = if (distance >= 0f) releaseVelocityPxPerSec else -releaseVelocityPxPerSec
        val u = (towards / travel).let { if (abs(it) < NEGLIGIBLE_U) 0f else it }

        // Never let the spring be slower than the finger already is, or it would have to
        // overshoot the target to catch up.
        val stiffness = max(omega, u)
        val durationMs = ((decay / stiffness) * 1000f * speedMultiplier)
            .toLong().coerceIn(minMs, maxMs)

        val b = (u * (durationMs / 1000f)).coerceIn(MIN_B, decay)
        return Settle(durationMs, springInterpolator(b, decay))
    }

    private fun springInterpolator(b: Float, decay: Float): TimeInterpolator {
        val coefficient = decay - b
        val norm = 1f - (1f + coefficient) * exp(-decay)
        return TimeInterpolator { s ->
            (1f - (1f + coefficient * s) * exp(-decay * s)) / norm
        }
    }

    /**
     * Where the finger would be a moment from now. Comparing *this* against a commit threshold
     * rather than the raw lift position is what makes a short, fast flick commit the way the
     * hand expects it to.
     */
    fun project(positionPx: Float, velocityPxPerSec: Float): Float =
        positionPx + velocityPxPerSec * PROJECTION_SECONDS

    /**
     * Progressive out-of-bounds resistance. Replaces a flat `offset * 0.2f`, which held the
     * derivative at a constant fifth of the finger and so read as a wall rather than a stretch.
     * Opens at [RUBBER_TENSION] of finger travel and tightens asymptotically toward [spanPx].
     */
    fun rubberBand(offsetPx: Float, spanPx: Float): Float {
        if (spanPx <= 0f) return 0f
        val magnitude = abs(offsetPx)
        val resisted = (1f - 1f / (magnitude * RUBBER_TENSION / spanPx + 1f)) * spanPx
        return if (offsetPx < 0f) -resisted else resisted
    }
}
