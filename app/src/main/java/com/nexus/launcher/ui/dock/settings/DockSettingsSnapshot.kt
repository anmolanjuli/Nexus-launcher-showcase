package com.nexus.launcher.ui.dock.settings

/** Point-in-time dock settings for backup export. */
data class DockSettingsSnapshot(
    val maxIcons: Int,
    val dockHeightDp: Int,
    val iconSizeDp: Int,
    val shuffleMotionPercent: Int,
    val backgroundMode: String,
    val solidColorArgb: Int,
    val frostedGradientIndex: Int,
    val showLabels: Boolean,
    val labelFontSizeSp: Int,
    val searchInDock: Boolean,
    val searchSlotIndex: Int,
    val cornerRadiusDp: Int,
    val dockBackgroundOpacity: Float,
    val dockGlassRefraction: Float
)
