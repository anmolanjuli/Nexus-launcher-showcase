package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.graphics.Bitmap
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Builds mock/preview bitmap for the Device Performance Widget in the Widget Picker catalog.
 */
object PerformancePreviewBuilder {

    @Volatile
    private var cachedPreview: Bitmap? = null

    fun buildPreview(context: Context, dp: Float): Bitmap {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        val isGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val isDefault = com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled
        val bgMode = when {
            isGlass -> NexusWidgetConfig.BG_GLASS
            isDefault -> NexusWidgetConfig.BG_SOLID
            else -> NexusWidgetConfig.BG_NEUMORPHIC
        }
        val accentHex = String.format("#%06X", 0xFFFFFF and tokens.accent)

        cachedPreview?.let { return it }

        val widthPx = (200 * dp).toInt().coerceAtLeast(1)
        val heightPx = (120 * dp).toInt().coerceAtLeast(1)

        val config = NexusWidgetConfig.InstanceConfig(
            appWidgetId = -1,
            backgroundMode = bgMode,
            backgroundOpacity = if (isGlass) 0.65f else 0.85f,
            accentColor = accentHex
        )

        val renderer = NexusPerformanceRenderer()
        val bmp = renderer.render(context, widthPx, heightPx, config, 200, 120, -1f)
        cachedPreview = bmp
        return bmp
    }

    fun clearCache() {
        cachedPreview = null
    }
}
