package com.nexus.launcher.ui.dock

import com.nexus.launcher.ui.dock.settings.DockSettingsRepository

/** Maps shuffle-motion slider (0–100) to animation strength. */
internal object DockMotionCurve {

    /** Strength at which full premium spring / magnification applies. */
    const val FULL_STRENGTH = 0.99f

    fun fromPercent(percent: Int): Float {
        return percent.coerceIn(
            DockSettingsRepository.MIN_SHUFFLE_MOTION_PERCENT,
            DockSettingsRepository.MAX_SHUFFLE_MOTION_PERCENT
        ) / DockSettingsRepository.MAX_SHUFFLE_MOTION_PERCENT.toFloat()
    }
}
