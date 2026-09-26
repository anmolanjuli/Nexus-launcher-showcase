package com.nexus.launcher.ui.folder

import android.content.Context
import android.view.View
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.theme.NexusColorTokens

/** Glass Refraction slider binding (mirrors widget appearance controls). */
object FolderEditAppearanceBinder {

    fun bind(
        context: Context,
        bodyViews: FolderEditBodyViews,
        tokens: NexusColorTokens,
        getConfig: () -> FolderConfig,
        onConfigChanged: (FolderConfig) -> Unit
    ) {
        // The "Folder UI: Soft UI / Glass" row that used to live here was removed — that per-item
        // choice was redundant with the global UI Style toggle (Nexus Settings > Appearance):
        // FolderIconPlateDraw already routes through NexusWidgetConfig.isGlassSurface()/
        // isNeumorphicSurface(), which only follow the global toggle (aside from BG_SOLID/
        // isExpressive). Refraction visibility now follows that same global toggle directly.
        val initialCfg = getConfig()
        val isGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        bodyViews.refractionSlider.visibility = if (isGlass) View.VISIBLE else View.GONE
        bodyViews.refractionSlider.configure(
            context.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction),
            0,
            100,
            (initialCfg.glassRefraction * 100).toInt().coerceIn(0, 100)
        ) { com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(it, context) }
        bodyViews.refractionSlider.applyAccentColor(tokens.textPrimary)
        bodyViews.refractionSlider.onValueChanged = { value ->
            onConfigChanged(getConfig().copy(glassRefraction = value / 100f))
        }
    }

    fun bindSliders(
        bodyViews: FolderEditBodyViews,
        getConfig: () -> FolderConfig,
        onConfigChanged: (FolderConfig) -> Unit
    ) {
        val context = bodyViews.root.context
        val cfg = getConfig()
        var iconOpacity = (cfg.backgroundOpacity * 100).toInt().coerceIn(0, 100)
        var windowOpacity = (cfg.windowBackgroundOpacity * 100).toInt().coerceIn(0, 100)
        bodyViews.iconOpacitySlider.configure(iconOpacity)
        bodyViews.windowOpacitySlider.configure(windowOpacity)
        bodyViews.iconOpacityValueText.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(iconOpacity, context)
        bodyViews.windowOpacityValueText.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(windowOpacity, context)

        bodyViews.iconOpacitySlider.onValueChanged = {
            iconOpacity = it.coerceIn(0, 100)
            bodyViews.iconOpacityValueText.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(iconOpacity, context)
            onConfigChanged(getConfig().copy(backgroundOpacity = iconOpacity / 100f))
        }
        bodyViews.windowOpacitySlider.onValueChanged = {
            windowOpacity = it.coerceIn(0, 100)
            bodyViews.windowOpacityValueText.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(windowOpacity, context)
            onConfigChanged(getConfig().copy(windowBackgroundOpacity = windowOpacity / 100f))
        }
    }

    fun refresh(context: Context, bodyViews: FolderEditBodyViews, tokens: NexusColorTokens, config: FolderConfig) {
        val isGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val sliderVis = if (isGlass) View.VISIBLE else View.GONE
        bodyViews.refractionSlider.visibility = sliderVis
        (bodyViews.refractionSlider.parent as? View)?.visibility = sliderVis
        bodyViews.refractionSlider.configure(
            context.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction),
            0,
            100,
            (config.glassRefraction * 100).toInt().coerceIn(0, 100)
        ) { com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(it, context) }
    }
}
