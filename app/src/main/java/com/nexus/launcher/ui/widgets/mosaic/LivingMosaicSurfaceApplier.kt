package com.nexus.launcher.ui.widgets.mosaic

import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.folder.FolderGlassEdgeBuilder
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Manages surface background chrome (Glass backdrop, Neumorphic backdrop, or bare tint)
 * for the outer LivingMosaicView container.
 */
object LivingMosaicSurfaceApplier {

    data class Result(
        val stroke: GradientDrawable?,
        val glassBackdrop: LivingMosaicGlassBackdropView?,
        val neumorphicBackdrop: LivingMosaicNeumorphicBackdropView?,
        val glassToggle: Boolean,
        val flatStyle: Boolean
    )

    fun apply(
        view: LivingMosaicView,
        cfg: MosaicConfig,
        currentGlassBackdrop: LivingMosaicGlassBackdropView?,
        currentNeumorphicBackdrop: LivingMosaicNeumorphicBackdropView?
    ): Result {
        val stack = LivingMosaicGlass.build(view.context, cfg)
        val globalGlassEnabled = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val defaultFlatStyle = FrostedGlassEngine.isDefaultFlatStyleEnabled
        val showChrome = !cfg.isExpressive && cfg.surfaceOpacity > 0.01f
        val wantsGlassBlur = cfg.backgroundMode == MosaicConfig.BG_GLASS && showChrome && globalGlassEnabled
        val wantsNeumorphicFallback = !wantsGlassBlur && showChrome && !globalGlassEnabled

        var glassBackdrop = currentGlassBackdrop
        var neumorphicBackdrop = currentNeumorphicBackdrop

        if (wantsGlassBlur) {
            neumorphicBackdrop?.let { view.removeView(it) }
            neumorphicBackdrop = null

            var backdrop = glassBackdrop
            if (backdrop == null) {
                backdrop = LivingMosaicGlassBackdropView(view.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                view.addView(backdrop, 0)
                glassBackdrop = backdrop
            }
            val tokens = try {
                ThemeObserver.currentTokens(view.context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
            backdrop.setTint(cfg.surfaceOpacity, cfg.glassRefraction, tokens.surface)
            backdrop.setRefraction(cfg.glassRefraction, cfg.surfaceOpacity)
            view.background = stack.stroke
        } else if (wantsNeumorphicFallback) {
            glassBackdrop?.let { view.removeView(it) }
            glassBackdrop = null

            var backdrop = neumorphicBackdrop
            if (backdrop == null) {
                backdrop = LivingMosaicNeumorphicBackdropView(view.context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                view.addView(backdrop, 0)
                neumorphicBackdrop = backdrop
            }
            val tokens = try {
                ThemeObserver.currentTokens(view.context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
            val palette = NexusNeumorphicDraw.resolvePalette(tokens)
            backdrop.configure(
                flat = defaultFlatStyle,
                palette = palette,
                cornerRadiusPx = FolderGlassEdgeBuilder.cornerRadiusPx(view.context),
                shapeStyle = cfg.shapeStyle
            )
            view.background = stack.stroke
        } else {
            glassBackdrop?.let { view.removeView(it) }
            glassBackdrop = null
            neumorphicBackdrop?.let { view.removeView(it) }
            neumorphicBackdrop = null
            view.background = stack.drawable
        }
        view.invalidateOutline()

        return Result(
            stroke = stack.stroke,
            glassBackdrop = glassBackdrop,
            neumorphicBackdrop = neumorphicBackdrop,
            glassToggle = globalGlassEnabled,
            flatStyle = defaultFlatStyle
        )
    }
}
