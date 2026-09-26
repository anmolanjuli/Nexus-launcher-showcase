package com.nexus.launcher.ui.dock

import kotlin.math.abs

/** macOS-style dock magnification during shuffle hover — finger-proximity scale curve. */
internal object DockMagnification {

    private const val MAX_SCALE = 1.26f
    private const val RADIUS_DP = 68f

    fun scaleFor(
        iconCenterX: Float,
        fingerLocalX: Float?,
        density: Float,
        shuffleActive: Boolean,
        motionIntensity: Float
    ): Float {
        if (!shuffleActive || fingerLocalX == null) return 1f
        val t = motionIntensity.coerceIn(0f, 1f)
        if (t <= 0.01f) return 1f
        val radius = RADIUS_DP * density
        val dist = abs(fingerLocalX - iconCenterX)
        if (dist >= radius) return 1f
        val eased = smoothstep(1f - (dist / radius))
        val strength = if (t >= DockMotionCurve.FULL_STRENGTH) 1f else t
        return 1f + (MAX_SCALE - 1f) * eased * strength
    }

    private fun smoothstep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
