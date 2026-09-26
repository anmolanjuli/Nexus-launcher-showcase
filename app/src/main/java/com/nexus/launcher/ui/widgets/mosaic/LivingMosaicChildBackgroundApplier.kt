package com.nexus.launcher.ui.widgets.mosaic

import android.widget.FrameLayout
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Manages background plates and live backdrops (Glass, Neumorphic, Default, or bare tint)
 * for individual child cells within a Living Mosaic tile.
 */
object LivingMosaicChildBackgroundApplier {

    const val GLASS_BACKDROP_TAG = "mosaic_child_glass_backdrop"
    const val NEUMORPHIC_BACKDROP_TAG = "mosaic_child_neumorphic_backdrop"

    fun apply(parent: FrameLayout, cell: FrameLayout, child: MosaicChild?, density: Float) {
        if (child == null) {
            cell.background = null
            cell.foreground = null
            clearChildGlassBackdrop(cell)
            clearChildNeumorphicBackdrop(cell)
            return
        }
        val mosaicConfig = owningMosaic(parent)?.config
        val effectiveConfig = if (child.backgroundMode == MosaicConfig.BG_INHERIT) {
            if (mosaicConfig == null) {
                cell.background = null
                clearChildGlassBackdrop(cell)
                clearChildNeumorphicBackdrop(cell)
                return
            }
            mosaicConfig
        } else {
            MosaicConfig(
                surfaceOpacity = child.backgroundOpacity,
                backgroundMode = child.backgroundMode,
                frostedGradientIndex = child.frostedGradientIndex,
                isExpressive = child.backgroundMode == MosaicConfig.BG_FROSTED || child.isExpressive
            )
        }
        val stack = LivingMosaicGlass.build(parent.context, effectiveConfig)
        val globalGlassEnabled = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val showChrome = !effectiveConfig.isExpressive && effectiveConfig.surfaceOpacity > 0.01f
        val wantsGlassBlur = effectiveConfig.backgroundMode == MosaicConfig.BG_GLASS && showChrome && globalGlassEnabled
        val wantsNeumorphicFallback = !wantsGlassBlur && showChrome && !globalGlassEnabled
        // An inheriting child is glass through the Mosaic's own backdrop, which already spans the
        // whole tile at the Mosaic's opacity and refraction. It used to add a second backdrop of
        // its own — another blur and another tint over the first — so the children stayed
        // frosted however far the Mosaic's slider went down. Only the separating border remains.
        val inheritsGlass = wantsGlassBlur && child.backgroundMode == MosaicConfig.BG_INHERIT
        if (inheritsGlass) {
            clearChildGlassBackdrop(cell)
            clearChildNeumorphicBackdrop(cell)
            cell.background = null
        } else if (wantsGlassBlur) {
            clearChildNeumorphicBackdrop(cell)
            var backdrop = cell.findViewWithTag<LivingMosaicGlassBackdropView>(GLASS_BACKDROP_TAG)
            if (backdrop == null) {
                backdrop = LivingMosaicGlassBackdropView(parent.context).apply {
                    tag = GLASS_BACKDROP_TAG
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                cell.addView(backdrop, 0)
            }
            val tokens = try {
                ThemeObserver.currentTokens(parent.context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
            backdrop.setTint(effectiveConfig.surfaceOpacity, effectiveConfig.glassRefraction, tokens.surface)
            backdrop.setRefraction(effectiveConfig.glassRefraction, effectiveConfig.surfaceOpacity)
            backdrop.invalidateBackdrop()
            cell.background = stack.stroke
        } else if (wantsNeumorphicFallback) {
            clearChildGlassBackdrop(cell)
            var backdrop = cell.findViewWithTag<LivingMosaicNeumorphicBackdropView>(NEUMORPHIC_BACKDROP_TAG)
            if (backdrop == null) {
                backdrop = LivingMosaicNeumorphicBackdropView(parent.context).apply {
                    tag = NEUMORPHIC_BACKDROP_TAG
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                cell.addView(backdrop, 0)
            }
            val tokens = try {
                ThemeObserver.currentTokens(parent.context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
            val palette = NexusNeumorphicDraw.resolvePalette(tokens)
            backdrop.configure(
                flat = FrostedGlassEngine.isDefaultFlatStyleEnabled,
                palette = palette,
                cornerRadiusPx = 12f * density
            )
            cell.background = stack.stroke
        } else {
            clearChildGlassBackdrop(cell)
            clearChildNeumorphicBackdrop(cell)
            cell.background = stack.drawable
        }

        // Minimal 1dp border overlay separating adjacent child widgets
        val tokens = try {
            ThemeObserver.currentTokens(parent.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        val strokeColor = if (effectiveConfig.isExpressive) {
            android.graphics.Color.parseColor(com.nexus.launcher.ui.NexusDesignSystem.COLOR_GLASS_BORDER)
        } else if (wantsGlassBlur) {
            val frosted = FrostedGlassEngine.resolveFrostedTokens(tokens)
            frosted.border
        } else {
            tokens.divider
        }
        cell.foreground = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(android.graphics.Color.TRANSPARENT)
            setStroke((1f * density).toInt().coerceAtLeast(1), strokeColor)
        }
    }

    /**
     * The Mosaic that holds [view], however deeply. This read `parent.parent` until 2026-09-24,
     * when a clipping frame was put between the Mosaic and its cells' frame (Neumorphic shadows):
     * the lookup then found nothing and every inherit-style cell lost its background and divider.
     */
    private fun owningMosaic(view: android.view.View): LivingMosaicView? {
        var v: android.view.ViewParent? = view as? android.view.ViewParent ?: view.parent
        while (v != null) {
            if (v is LivingMosaicView) return v
            v = v.parent
        }
        return null
    }

    fun clearChildGlassBackdrop(cell: FrameLayout) {
        cell.findViewWithTag<LivingMosaicGlassBackdropView>(GLASS_BACKDROP_TAG)?.let { cell.removeView(it) }
    }

    fun clearChildNeumorphicBackdrop(cell: FrameLayout) {
        cell.findViewWithTag<LivingMosaicNeumorphicBackdropView>(NEUMORPHIC_BACKDROP_TAG)?.let { cell.removeView(it) }
    }
}
