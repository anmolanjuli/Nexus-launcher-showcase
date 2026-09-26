package com.nexus.launcher.theme

import androidx.annotation.StringRes
import com.nexus.launcher.R

/**
 * Available palettes for [ThemeMode.CALM].
 *
 * Each entry needs a matching token set in [com.nexus.launcher.theme.NexusColorTokens]'s
 * `getCalmTokens` — the two preview colours here are only what the picker swatch shows, and are
 * kept equal to that set's `bgTop`/`bgBottom` so the swatch matches the wallpaper it produces.
 *
 * All of them stay dark and desaturated: Calm is a low-stimulus mode, so a palette is a *hue*
 * for the background gradient and accent, never a bright fill. The constant names are the storage
 * format (persisted via `.name`, read back with `valueOf`), so they cannot be renamed.
 */
enum class CalmPalette(
    val key: String,
    @StringRes val displayNameRes: Int,
    val previewTopColor: Int,
    val previewBottomColor: Int
) {
    RICH_SAND("rich_sand", R.string.calm_palette_rich_sand, 0xFF3D2B1F.toInt(), 0xFF1D130B.toInt()),
    GOLDEN_SAND("golden_sand", R.string.calm_palette_golden_sand, 0xFF4E4422.toInt(), 0xFF2A2411.toInt()),
    OCEAN_BLUE("ocean_blue", R.string.calm_palette_ocean_blue, 0xFF1C3B4A.toInt(), 0xFF0B1C28.toInt()),
    NAVY_INK("navy_ink", R.string.calm_palette_navy_ink, 0xFF1A2B4A.toInt(), 0xFF0C1224.toInt()),
    DEEP_NAVY("deep_navy", R.string.calm_palette_deep_navy, 0xFF0F1F3D.toInt(), 0xFF050913.toInt()),
    EMBER_RED("ember_red", R.string.calm_palette_ember_red, 0xFF4A2024.toInt(), 0xFF210C0F.toInt()),
    FOREST_GREEN("forest_green", R.string.calm_palette_forest_green, 0xFF1F3D2E.toInt(), 0xFF0B1D14.toInt()),
    AMBER_DUSK("amber_dusk", R.string.calm_palette_amber_dusk, 0xFF524211.toInt(), 0xFF261E05.toInt()),
    BURNT_ORANGE("burnt_orange", R.string.calm_palette_burnt_orange, 0xFF4C2A15.toInt(), 0xFF231206.toInt()),
    DEEP_PLUM("deep_plum", R.string.calm_palette_deep_plum, 0xFF33213F.toInt(), 0xFF160D1D.toInt()),
    SLATE_GREY("slate_grey", R.string.calm_palette_slate_grey, 0xFF2B3036.toInt(), 0xFF12151A.toInt());

    companion object {
        fun fromKey(key: String?): CalmPalette {
            if (key.isNullOrEmpty()) return OCEAN_BLUE
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: OCEAN_BLUE
        }
    }
}
