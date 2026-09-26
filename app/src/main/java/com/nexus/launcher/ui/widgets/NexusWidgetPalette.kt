package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Color
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Single ink source for first-party widget content.
 * Chrome (plate + hairline) stays in [NexusWidgetRenderer]; this is type, marks, and glyphs.
 *
 * Minimal: all colors from [ThemeObserver] tokens.
 * Expressive: user accent for marks only; body type still uses tokens so Light/Calm stay readable.
 */
data class NexusWidgetPalette(
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentMuted: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val divider: Int,
    val bg: Int
) {
    fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }

    companion object {
        fun from(context: Context, config: NexusWidgetConfig.InstanceConfig): NexusWidgetPalette {
            val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
            val accent = if (config.isExpressive) {
                parseColorOrNull(config.accentColor) ?: tokens.accent
            } else {
                tokens.accent
            }
            return NexusWidgetPalette(
                textPrimary = tokens.textPrimary,
                textSecondary = tokens.textSecondary,
                accent = accent,
                accentMuted = NexusColorTokens.computeAccentMuted(accent),
                surface = tokens.surface,
                surfaceRaised = tokens.surfaceRaised,
                divider = tokens.divider,
                bg = tokens.bg
            )
        }

        private fun parseColorOrNull(hex: String): Int? {
            return try {
                Color.parseColor(hex)
            } catch (_: Exception) {
                null
            }
        }
    }
}
