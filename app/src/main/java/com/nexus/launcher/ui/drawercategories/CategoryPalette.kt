package com.nexus.launcher.ui.drawercategories

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedGlassEngine

class CategoryPalette(context: Context) {
    val tokens: NexusColorTokens = resolveTokens(context)
    val base: Int = tokens.bg
    val surface: Int
    val glassSurface: Int
    val glassBorder: Int
    val mistBlue: Int = tokens.accent
    val textPrimary: Int = tokens.textPrimary
    val textSecondary: Int = tokens.textSecondary
    val isLight: Boolean = ColorUtils.calculateLuminance(tokens.bg) >= 0.45
    val isGlass: Boolean = FrostedGlassEngine.isGlobalFrostedGlassEnabled
    val isFlat: Boolean = FrostedGlassEngine.isDefaultFlatStyleEnabled
    val categoryPresets: IntArray = PRESET_RES.map { ContextCompat.getColor(context, it) }.toIntArray()

    init {
        val frost = FrostedGlassEngine.resolveFrostedTokens(tokens)
        if (isGlass) {
            val alpha = (FrostedGlassEngine.sheetFillAlpha() * 255f).toInt().coerceIn(0, 255)
            surface = frost.surface
            glassSurface = (frost.surface and 0x00FFFFFF) or (alpha shl 24)
            glassBorder = frost.border
        } else {
            surface = if (isFlat) tokens.surface else tokens.surfaceRaised
            glassSurface = surface
            glassBorder = tokens.divider
        }
    }

    fun appColor(categoryAccent: Int, index: Int, count: Int): Int {
        val t = if (count <= 1) {
            0.12f
        } else {
            0.06f + 0.28f * (index.toFloat() / (count - 1).toFloat())
        }
        return ColorUtils.blendARGB(categoryAccent, textPrimary, t)
    }

    fun plateFill(): Int {
        return if (isLight) {
            ColorUtils.blendARGB(tokens.surface, mistBlue, 0.08f)
        } else {
            ColorUtils.blendARGB(textPrimary, mistBlue, 0.08f)
        }
    }

    fun onFill(fill: Int): Int {
        val opaque = fill or 0xFF000000.toInt()
        val fillLum = ColorUtils.calculateLuminance(opaque)
        val bgLum = ColorUtils.calculateLuminance(base)
        val textLum = ColorUtils.calculateLuminance(textPrimary)
        val darkInk = if (bgLum < textLum) base else textPrimary
        val lightInk = if (darkInk == base) textPrimary else base
        return if (fillLum >= 0.45) darkInk else lightInk
    }

    fun spatialBlendTop(): Float = if (isLight) 0.38f else 0.82f

    fun spatialBlendBottom(): Float = if (isLight) 0.14f else 0.34f

    fun scrim(): Int {
        val ink = if (isLight) textPrimary else base
        return ColorUtils.setAlphaComponent(ink, 0xB3)
    }

    companion object {
        val PRESET_RES: IntArray = intArrayOf(
            R.color.drawer_preview_accent_harbor,
            R.color.drawer_preview_accent_mist,
            R.color.drawer_preview_accent_sage,
            R.color.drawer_preview_accent_clay,
            R.color.drawer_preview_accent_sand,
            R.color.drawer_preview_accent_rose,
            R.color.drawer_preview_accent_lilac,
            R.color.drawer_preview_accent_slate,
        )

        fun resolveTokens(context: Context): NexusColorTokens {
            return try {
                ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
        }
    }
}
