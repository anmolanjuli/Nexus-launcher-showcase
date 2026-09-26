package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver

/**
 * Closed-folder plate colors aligned with first-party widget chrome
 * ([NexusNeumorphicDraw] / [NexusWidgetRenderer]), not wallpaper-matched theme surfaces.
 */
object FolderIconSurfaceColor {

    data class Plate(
        val fill: Int,
        val raised: Int,
        val stroke: Int,
        val rim: Int
    )

    fun plate(context: Context, config: FolderConfig): Plate {
        val tokens = widgetTokens(context, config)
        if (config.isExpressive) {
            val fill = expressivePlateRgb(config)
            val raised = ColorUtils.blendARGB(fill, Color.WHITE, 0.12f)
            val stroke = Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
            return Plate(fill, raised, stroke, stroke)
        }
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)
        val fill = palette.surfaceLight or 0xFF000000.toInt()
        val raised = if (palette.isLight) {
            fill
        } else {
            ColorUtils.blendARGB(fill, Color.WHITE, 0.16f)
        }
        return Plate(
            fill = fill,
            raised = raised,
            stroke = tokens.divider,
            rim = ColorUtils.setAlphaComponent(tokens.textPrimary, 0x55)
        )
    }

    fun fillWithOpacity(context: Context, config: FolderConfig): Int {
        val alpha = opacityAlpha(config)
        return ColorUtils.setAlphaComponent(plate(context, config).fill, alpha)
    }

    fun raisedWithOpacity(context: Context, config: FolderConfig, strength: Float): Int {
        val alpha = (config.backgroundOpacity.coerceIn(0f, 1f) * strength * 255f)
            .toInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(plate(context, config).raised, alpha)
    }

    fun strokeWithOpacity(context: Context, config: FolderConfig, strength: Float = 0.9f): Int {
        val alpha = (config.backgroundOpacity.coerceIn(0f, 1f) * strength * 255f)
            .toInt().coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(plate(context, config).stroke, alpha)
    }

    fun windowPlateRgb(context: Context, config: FolderConfig): Int {
        if (config.isExpressive) return plate(context, config).fill
        // Opened folder card is an icon container (identical to app drawer). Use tokens.bg
        // so themed-icon squircle plates (tokens.surface) contrast against the card background.
        return widgetTokens(context, config).bg
    }

    private fun widgetTokens(context: Context, config: FolderConfig): NexusColorTokens =
        NexusWidgetThemeResolver.resolve(context, config.themeMode)

    private fun opacityAlpha(config: FolderConfig): Int =
        (config.backgroundOpacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)

    private fun expressivePlateRgb(config: FolderConfig): Int {
        val fallback = Color.parseColor(NexusDesignSystem.COLOR_BASE)
        return when (config.windowBackgroundMode.uppercase()) {
            "SOLID" -> parseHex(
                config.solidBackgroundColor ?: config.backgroundColor,
                NexusDesignSystem.COLOR_BASE
            )
            "FROSTED" -> {
                val preset = DockFrostedGradients.presets[
                    DockFrostedGradients.clampIndex(config.frostedGradientIndex)
                ]
                ColorUtils.blendARGB(preset.startRgb, preset.endRgb, 0.5f)
            }
            else -> parseHex(config.backgroundColor, NexusDesignSystem.COLOR_BASE)
        }.let { rgb ->
            if (Color.alpha(rgb) == 255) rgb else ColorUtils.setAlphaComponent(rgb, 255)
        }.let { if (it == Color.TRANSPARENT) fallback else it }
    }

    private fun parseHex(hex: String?, fallback: String): Int {
        return try {
            Color.parseColor(hex?.takeIf { it.isNotBlank() } ?: fallback)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }
}
