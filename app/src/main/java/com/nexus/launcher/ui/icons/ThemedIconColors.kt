package com.nexus.launcher.ui.icons

import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeMode

/**
 * Themed-icon plate colors — identical tokens to the widget container in
 * [com.nexus.launcher.ui.widgets.NexusWidgetRenderer]: [NexusColorTokens.surface] fill,
 * [NexusColorTokens.divider] hairline, [NexusColorTokens.textPrimary] content. Icons and
 * widgets therefore read as one surface family in every theme.
 *
 * Polarity (used by the silhouette pass) is decided from surface luminance rather than the
 * ThemeMode enum so AUTOMATIC and every Calm palette resolve correctly.
 */
object ThemedIconColors {

    data class PlateColors(
        val background: Int,
        val foreground: Int,
        val borderColor: Int
    )

    fun resolve(tokens: NexusColorTokens, @Suppress("UNUSED_PARAMETER") themeMode: ThemeMode): PlateColors =
        PlateColors(
            background = tokens.surface,
            foreground = tokens.textPrimary,
            borderColor = tokens.divider
        )

    fun isLightTheme(@Suppress("UNUSED_PARAMETER") themeMode: ThemeMode, tokens: NexusColorTokens): Boolean =
        ColorUtils.calculateLuminance(tokens.surface) > 0.5

    fun isDarkTheme(themeMode: ThemeMode, tokens: NexusColorTokens): Boolean =
        !isLightTheme(themeMode, tokens)

    fun cacheKey(tokens: NexusColorTokens, themeMode: ThemeMode): String {
        val plate = resolve(tokens, themeMode)
        return "${plate.background}:${plate.foreground}:${plate.borderColor}"
    }
}
