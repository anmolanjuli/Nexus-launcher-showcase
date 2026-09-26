package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Color
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Resolves theme tokens into theme-specific [NexusNeumorphicDraw.SoftPalette] values.
 */
object NexusNeumorphicPaletteResolver {

    fun resolvePalette(tokens: NexusColorTokens): NexusNeumorphicDraw.SoftPalette {
        val bg = tokens.bg
        val lum = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0
        val isLight = lum > 0.5

        if (isLight) {
            // Light Theme: crisp #FFFFFF surface, neutral #000000 shadow at 10-15% opacity, deep slate #14181B text
            return NexusNeumorphicDraw.SoftPalette(
                surfaceLight = Color.parseColor("#FFFFFF"),
                surfaceDark = Color.parseColor("#FFFFFF"),
                shadow = Color.BLACK,
                highlight = Color.parseColor("#FFFFFF"),
                textPrimary = Color.parseColor("#14181B"),
                textSecondary = Color.parseColor("#5B6770"),
                accent = Color.parseColor("#14181B"),
                todayText = Color.WHITE,
                debossedBg = Color.parseColor("#E4E7EB"),
                isLight = true,
                isAmoled = false
            )
        }

        val isCalm = tokens.bgTop != null
        if (isCalm) {
            // Calm Theme: uniform surface derived from tokens.surfaceRaised
            val baseSurface = tokens.surfaceRaised or 0xFF000000.toInt()
            val debossed = blendColor(baseSurface, Color.BLACK, 0.25f)

            return NexusNeumorphicDraw.SoftPalette(
                surfaceLight = baseSurface,
                surfaceDark = baseSurface,
                shadow = Color.BLACK,
                highlight = Color.TRANSPARENT,
                textPrimary = tokens.textPrimary,
                textSecondary = tokens.textSecondary,
                accent = tokens.textPrimary,
                todayText = baseSurface,
                debossedBg = debossed,
                isLight = false,
                isAmoled = false
            )
        }

        val isAmoled = (tokens.bg and 0x00FFFFFF) == 0 && tokens.bgTop == null
        if (isAmoled) {
            // AMOLED Theme: rich deep neutral OLED charcoal #1C1D21, uniform brightness
            val amoledSurface = Color.parseColor("#1C1D21")
            return NexusNeumorphicDraw.SoftPalette(
                surfaceLight = amoledSurface,
                surfaceDark = amoledSurface,
                shadow = Color.BLACK,
                highlight = Color.TRANSPARENT,
                textPrimary = Color.parseColor("#FFFFFF"),
                textSecondary = Color.parseColor("#9AA0A6"),
                accent = Color.WHITE,
                todayText = Color.BLACK,
                debossedBg = Color.parseColor("#0A0B0D"),
                isLight = false,
                isAmoled = true
            )
        }

        // Standard Dark Theme: solid rich dark slate #24262C, uniform brightness
        val darkSurface = Color.parseColor("#24262C")
        return NexusNeumorphicDraw.SoftPalette(
            surfaceLight = darkSurface,
            surfaceDark = darkSurface,
            shadow = Color.BLACK,
            highlight = Color.TRANSPARENT,
            textPrimary = Color.parseColor("#F5F5F5"),
            textSecondary = Color.parseColor("#9E9E9E"),
            accent = Color.WHITE,
            todayText = Color.BLACK,
            debossedBg = Color.parseColor("#16171B"),
            isLight = false,
            isAmoled = false
        )
    }

    fun resolvePalette(context: Context, themeMode: String? = null): NexusNeumorphicDraw.SoftPalette {
        val tokens = NexusWidgetThemeResolver.resolve(context, themeMode)
        return resolvePalette(tokens)
    }

    private fun blendColor(color1: Int, color2: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        val a = (Color.alpha(color1) * inverse + Color.alpha(color2) * ratio).toInt()
        val r = (Color.red(color1) * inverse + Color.red(color2) * ratio).toInt()
        val g = (Color.green(color1) * inverse + Color.green(color2) * ratio).toInt()
        val b = (Color.blue(color1) * inverse + Color.blue(color2) * ratio).toInt()
        return Color.argb(a, r, g, b)
    }
}
