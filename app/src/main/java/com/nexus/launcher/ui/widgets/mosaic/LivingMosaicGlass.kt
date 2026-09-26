package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.folder.FolderGlassEdgeBuilder

/**
 * Mosaic surface fill. Opacity is baked into the fill color only (no LayerDrawable.alpha
 * multiply) so 100% is truly opaque. Border auto-hides at ~0 opacity (floating tiles).
 */
object LivingMosaicGlass {

    data class Stack(
        val drawable: LayerDrawable,
        val stroke: GradientDrawable
    )

    fun build(context: Context, config: MosaicConfig): Stack {
        val opacity = config.surfaceOpacity.coerceIn(0f, 1f)
        val density = context.resources.displayMetrics.density
        val cornerPx = when (config.shapeStyle) {
            2 -> 0f
            11 -> 9999f * density
            else -> FolderGlassEdgeBuilder.cornerRadiusPx(context)
        }
        val alpha = (opacity * 255f).toInt().coerceIn(0, 255)
        val showChrome = opacity > 0.01f

        val stroke = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setColor(Color.TRANSPARENT)
            if (showChrome) {
                val strokeColor = if (config.isExpressive) {
                    Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
                } else {
                    try {
                        com.nexus.launcher.theme.ThemeObserver.currentTokens(context).divider
                    } catch (_: Exception) {
                        Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
                    }
                }
                setStroke(
                    (1.25f * density).toInt().coerceAtLeast(1),
                    strokeColor
                )
            } else {
                setStroke(0, Color.TRANSPARENT)
            }
        }

        val layers = ArrayList<Drawable>()
        if (!showChrome) {
            layers.add(ColorDrawable(Color.TRANSPARENT))
        } else if (config.isExpressive) {
            if (config.backgroundMode == MosaicConfig.BG_FROSTED) {
                val idx = DockFrostedGradients.clampIndex(config.frostedGradientIndex)
                val preset = DockFrostedGradients.presets[idx]
                // Use solid RGB + slider alpha (ignore preset's baked glass alpha)
                layers.add(
                    GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(
                            Color.argb(alpha, Color.red(preset.startRgb), Color.green(preset.startRgb), Color.blue(preset.startRgb)),
                            Color.argb(alpha, Color.red(preset.endRgb), Color.green(preset.endRgb), Color.blue(preset.endRgb))
                        )
                    ).apply { cornerRadius = cornerPx }
                )
            } else {
                val base = Color.parseColor(NexusDesignSystem.COLOR_BASE)
                layers.add(
                    GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = cornerPx
                        setColor(
                            Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
                        )
                    }
                )
            }
        } else {
            val tokens = try {
                com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                com.nexus.launcher.theme.NexusColorTokens.Dark
            }
            val base = tokens.surface
            // Only ever actually shown when this tile ISN'T in Glass mode (Default/Neumorphism —
            // see LivingMosaicView/LivingMosaicChildHost, which use `stack.stroke` alone, a
            // transparent-filled border, for Glass instead of this drawable). In Glass mode the
            // backdrop's own tint uses FrostedGlassEngine.frostFillAlpha, which applies a density
            // boost well above the raw opacity value — this flat fallback used the SAME raw
            // opacity linearly with no such boost and no floor, so a mosaic tuned with a low
            // opacity (typical for a Glass-mode "let the blur carry it" look) read as
            // near-transparent the moment Default/Neumorphism took over with no blur to
            // compensate. Floor it so Default/Neumorphism always reads as a solid plate,
            // matching how widgets/folders' own Neumorphic drawing ignores opacity entirely.
            val flatAlpha = (opacity.coerceAtLeast(0.85f) * 255f).toInt().coerceIn(0, 255)
            layers.add(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cornerPx
                    setColor(
                        Color.argb(flatAlpha, Color.red(base), Color.green(base), Color.blue(base))
                    )
                }
            )
        }
        layers.add(stroke)

        // Do not set LayerDrawable.alpha — that double-multiplied fill opacity and
        // made "100%" still look translucent.
        return Stack(LayerDrawable(layers.toTypedArray()), stroke)
    }
}
