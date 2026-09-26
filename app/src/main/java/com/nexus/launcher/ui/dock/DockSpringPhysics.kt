package com.nexus.launcher.ui.dock

import kotlin.math.abs

/**
 * Damped spring channels for dock icon shuffle — custom canvas physics, independent of
 * system Animator duration scale. Intensity is controlled only by the dock settings slider.
 */
internal object DockSpringPhysics {

    data class Channel(
        var position: Float,
        var velocity: Float = 0f,
        var lastError: Float = Float.MAX_VALUE
    )

    private const val STIFFNESS_OPEN = 0.50f
    private const val STIFFNESS_CLOSE = 0.38f
    private const val PREMIUM_STIFFNESS_INCOMING = 0.30f
    private const val PREMIUM_DAMPING = 0.74f
    private const val MAX_SUB_STEPS = 2
    private const val SETTLE_POSITION_PX = 0.45f
    private const val SETTLE_VELOCITY = 0.08f

    fun stepCustom(
        channel: Channel,
        target: Float,
        stiffness: Float,
        damping: Float
    ): Boolean {
        return integrateSubSteps(channel, target, stiffness, damping)
    }

    fun step(
        channel: Channel,
        target: Float,
        incomingGap: Boolean,
        motionIntensity: Float
    ): Boolean {
        val t = motionIntensity.coerceIn(0f, 1f)
        if (t <= 0.01f) {
            snap(channel, target)
            return false
        }
        if (t >= DockMotionCurve.FULL_STRENGTH) {
            return stepPremium(channel, target, incomingGap)
        }
        val baseStiffness = if (incomingGap) {
            PREMIUM_STIFFNESS_INCOMING
        } else {
            resolveAsymmetricStiffness(channel, target)
        }
        val stiffness = baseStiffness * t
        val damping = PREMIUM_DAMPING + (1f - t) * 0.20f
        return integrateSubSteps(channel, target, stiffness, damping)
    }

    private fun stepPremium(
        channel: Channel,
        target: Float,
        incomingGap: Boolean
    ): Boolean {
        val stiffness = if (incomingGap) {
            PREMIUM_STIFFNESS_INCOMING
        } else {
            resolveAsymmetricStiffness(channel, target)
        }
        return integrateSubSteps(channel, target, stiffness, PREMIUM_DAMPING)
    }

    private fun resolveAsymmetricStiffness(channel: Channel, target: Float): Float {
        val error = abs(target - channel.position)
        val opening = error > channel.lastError + 0.001f || channel.lastError == Float.MAX_VALUE
        channel.lastError = error
        return if (opening) STIFFNESS_OPEN else STIFFNESS_CLOSE
    }

    fun snap(channel: Channel, target: Float) {
        channel.position = target
        channel.velocity = 0f
        channel.lastError = 0f
    }

    private fun integrateSubSteps(
        channel: Channel,
        target: Float,
        stiffness: Float,
        damping: Float
    ): Boolean {
        var stillMoving = false
        repeat(MAX_SUB_STEPS) {
            if (integrate(channel, target, stiffness, damping)) {
                stillMoving = true
            }
        }
        return stillMoving
    }

    private fun integrate(
        channel: Channel,
        target: Float,
        stiffness: Float,
        damping: Float
    ): Boolean {
        channel.velocity = (channel.velocity + (target - channel.position) * stiffness) * damping
        channel.position += channel.velocity
        return abs(target - channel.position) > SETTLE_POSITION_PX ||
            abs(channel.velocity) > SETTLE_VELOCITY
    }
}
