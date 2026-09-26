package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.FolderConfig

/** Normalizes persisted folder shape ids (legacy migrations). */
object FolderShapeStyle {

    const val SQUIRCLE = 1
    /** Legacy app-pouch id — not offered in the shape picker. */
    const val LEGACY_APP_POUCH = 8
    const val BALL = 7
    const val FILE_FOLDER = 10
    const val PILL = 11
    const val SOFT_CAPSULE = 13
    /** Retired pilot shape — maps to [SQUIRCLE]. */
    private const val LEGACY_GLASS_SHEET = 12
    /** Retired picker shape — maps to [SQUIRCLE]. */
    private const val RETIRED_CENTER_NOTCH = 14

    fun normalize(shapeStyle: Int): Int = when (shapeStyle) {
        LEGACY_GLASS_SHEET, RETIRED_CENTER_NOTCH -> SQUIRCLE
        else -> shapeStyle
    }

    fun normalize(config: FolderConfig): FolderConfig {
        val normalized = normalize(config.shapeStyle)
        return if (normalized == config.shapeStyle) config else config.copy(shapeStyle = normalized)
    }

    fun scrimShape(config: FolderConfig): Int =
        normalize(config.shapeStyle).let { shape ->
            when (shape) {
                6 -> 5
                BALL, FILE_FOLDER, SOFT_CAPSULE -> shape
                else -> shape.coerceIn(0, 11)
            }
        }
}
