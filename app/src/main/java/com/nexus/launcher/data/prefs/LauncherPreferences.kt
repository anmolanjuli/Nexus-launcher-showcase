package com.nexus.launcher.data.prefs

data class LauncherPreferences(
    val rowCount: Int = 12,
    val columnCount: Int = 6,
    val labelVisibility: LabelVisibility = LabelVisibility.ELLIPSIZE
)

enum class LabelVisibility {
    ELLIPSIZE,
    HIDDEN,
    CLIPPED
}
