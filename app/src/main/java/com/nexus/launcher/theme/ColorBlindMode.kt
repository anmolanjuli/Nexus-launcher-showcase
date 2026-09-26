package com.nexus.launcher.theme

import androidx.annotation.StringRes
import com.nexus.launcher.R

/**
 * Color vision deficiency (colorblind) modes for Nexus Launcher.
 */
enum class ColorBlindMode(
    val key: String,
    @StringRes val displayNameRes: Int,
    val safeDangerColor: Int? = null
) {
    NONE("none", R.string.color_accessibility_off, null),
    RED_GREEN("red_green", R.string.color_accessibility_red_green, 0xFFFF8C00.toInt()),
    BLUE_YELLOW("blue_yellow", R.string.color_accessibility_blue_yellow, 0xFFFFC107.toInt());

    companion object {
        fun fromKey(key: String?): ColorBlindMode {
            if (key.isNullOrEmpty()) return NONE
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NONE
        }
    }
}
