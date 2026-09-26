package com.nexus.launcher.ui.dock

/** Finger position for dock layout passes. */
internal data class DockLayoutAnimParams(
    val fingerLocalX: Float?,
    val density: Float,
    val motionIntensity: Float = 1f
)
