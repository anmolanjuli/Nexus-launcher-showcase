package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.locale.AppLocale
import com.nexus.launcher.theme.ThemeMode
import com.nexus.launcher.typography.AppFontFamily
import com.nexus.launcher.typography.CustomFontEntry
import com.nexus.launcher.ui.icons.IconPackInfo

/**
 * Text formatting and option helpers for [ThemeSettingsFragment] subtitle summaries.
 */
object ThemeSettingsFormatters {

    val FONT_MIME_TYPES = arrayOf(
        "font/ttf",
        "font/otf",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/font-sfnt",
        "application/octet-stream"
    )

    fun getThemeModeOptions(context: Context): List<Pair<String, String>> = listOf(
        ThemeMode.AUTOMATIC.name to context.getString(R.string.theme_mode_auto),
        ThemeMode.LIGHT.name to context.getString(R.string.theme_mode_light),
        ThemeMode.DARK.name to context.getString(R.string.theme_mode_dark),
        ThemeMode.AMOLED.name to context.getString(R.string.theme_mode_amoled),
        ThemeMode.CALM.name to context.getString(R.string.theme_mode_calm)
    )

    fun getThemeModeLabel(context: Context, mode: ThemeMode): String = when (mode) {
        ThemeMode.AUTOMATIC -> context.getString(R.string.theme_mode_auto)
        ThemeMode.LIGHT -> context.getString(R.string.theme_mode_light)
        ThemeMode.DARK -> context.getString(R.string.theme_mode_dark)
        ThemeMode.AMOLED -> context.getString(R.string.theme_mode_amoled)
        ThemeMode.CALM -> context.getString(R.string.theme_mode_calm)
    }

    fun getCalmPaletteLabel(context: Context, palette: com.nexus.launcher.theme.CalmPalette): String {
        return context.getString(palette.displayNameRes)
    }

    fun getBackgroundLayerOptions(context: Context): List<Pair<String, String>> = listOf(
        com.nexus.launcher.theme.BackgroundLayerMode.WALLPAPER.name to context.getString(R.string.bg_layer_wallpaper),
        com.nexus.launcher.theme.BackgroundLayerMode.THEME_COLOR.name to context.getString(R.string.bg_layer_theme_color)
    )

    fun getBackgroundLayerLabel(context: Context, mode: com.nexus.launcher.theme.BackgroundLayerMode): String = when (mode) {
        com.nexus.launcher.theme.BackgroundLayerMode.WALLPAPER -> context.getString(R.string.bg_layer_wallpaper)
        com.nexus.launcher.theme.BackgroundLayerMode.THEME_COLOR -> context.getString(R.string.bg_layer_theme_color)
    }

    fun getColorBlindModeOptions(context: Context): List<Pair<String, String>> = listOf(
        com.nexus.launcher.theme.ColorBlindMode.NONE.name to context.getString(R.string.color_accessibility_off),
        com.nexus.launcher.theme.ColorBlindMode.RED_GREEN.name to context.getString(R.string.color_accessibility_red_green),
        com.nexus.launcher.theme.ColorBlindMode.BLUE_YELLOW.name to context.getString(R.string.color_accessibility_blue_yellow)
    )

    fun getColorBlindModeLabel(context: Context, mode: com.nexus.launcher.theme.ColorBlindMode): String = when (mode) {
        com.nexus.launcher.theme.ColorBlindMode.NONE -> context.getString(R.string.color_accessibility_off)
        com.nexus.launcher.theme.ColorBlindMode.RED_GREEN -> context.getString(R.string.color_accessibility_red_green)
        com.nexus.launcher.theme.ColorBlindMode.BLUE_YELLOW -> context.getString(R.string.color_accessibility_blue_yellow)
    }

    /** A preset's name, or the hex itself for a colour the user mixed. */
    fun getAccentLabel(context: Context, hex: String): String =
        com.nexus.launcher.ui.settings.views.AccentColorPicker.labelFor(context, hex)
            ?: context.getString(R.string.accent_label_custom, hex.uppercase())

    fun getIconSubtitle(
        context: Context,
        availablePacks: List<IconPackInfo>,
        pack: String,
        shape: Int,
        themed: Boolean = false
    ): String {
        val isDefaultPack = pack.isBlank() || pack == "none"
        val packLabel = when {
            isDefaultPack && themed -> context.getString(R.string.icon_subtitle_themed)
            isDefaultPack -> context.getString(R.string.icon_subtitle_default)
            else -> availablePacks.find { it.packageName == pack }?.label ?: pack
        }
        val shapeLabel = IconShapeTileRow.OPTIONS.find { it.id == shape }?.let { context.getString(it.labelRes) }
            ?: context.getString(R.string.icon_shape_system)
        return "$packLabel • $shapeLabel"
    }

    fun getTypographySubtitle(
        context: Context,
        candidateFontKey: String,
        customFonts: List<CustomFontEntry>,
        candidateLocale: AppLocale
    ): String {
        val fontName = if (candidateFontKey.startsWith("custom:")) {
            val id = candidateFontKey.removePrefix("custom:")
            customFonts.find { it.id == id }?.name ?: context.getString(R.string.typography_custom_font)
        } else {
            context.getString(AppFontFamily.fromKey(candidateFontKey).displayNameRes)
        }
        val langName = context.getString(candidateLocale.displayNameRes)
        return "$fontName • $langName"
    }
}
