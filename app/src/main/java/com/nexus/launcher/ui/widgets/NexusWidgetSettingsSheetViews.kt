package com.nexus.launcher.ui.widgets

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics
import com.nexus.launcher.ui.widgets.mosaic.MosaicBackgroundPickerBinder

/**
 * Section view builders for NexusWidgetSettingsSheet.
 * Keeps NexusWidgetSettingsSheet well below the 400-line architectural ceiling.
 */
object NexusWidgetSettingsSheetViews {

    fun buildAppearanceGroup(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)

        // 2. Opacity Slider
        val opacity = NexusSliderRow(context).apply {
            val cfg = getConfig()
            configure(context.getString(com.nexus.launcher.R.string.dock_settings_opacity), 0, 100, (cfg.backgroundOpacity * 100).toInt().coerceIn(0, 100)) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            onValueChanged = { value ->
                updateConfig(getConfig().copy(backgroundOpacity = value / 100f))
                onChanged()
            }
        }
        group.addChildRow(opacity)

        // 3. Glass Refraction Slider — visible whenever the global UI Style is Frosted Glass;
        // the per-item Glass/Soft-UI choice that used to gate this was removed (that toggle no
        // longer does anything at render time — NexusWidgetConfig.isGlassSurface()/
        // isNeumorphicSurface() already only follow the global toggle), so refraction visibility
        // now follows the same global toggle directly instead of a dead per-item field.
        val isInitialGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val refraction = NexusSliderRow(context).apply {
            val cfg = getConfig()
            configure(context.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction), 0, 100, (cfg.glassRefraction * 100).toInt().coerceIn(0, 100)) { "$it%" }
            applyAccentColor(tokens.textPrimary)
            onValueChanged = { value ->
                updateConfig(getConfig().copy(glassRefraction = value / 100f))
                onChanged()
            }
        }
        group.addChildRow(refraction, isInitialGlass)

        // 4. Font Style Row
        val fontRow = NexusSegmentedRow(context).apply {
            val cfg = getConfig()
            val fontOptions = listOf(
                "default" to "Nexus",
                "manrope" to context.getString(com.nexus.launcher.R.string.widget_font_bauhaus),
                "bitcount_single" to context.getString(com.nexus.launcher.R.string.widget_font_retro),
                "playpen_sans" to context.getString(com.nexus.launcher.R.string.widget_font_playful),
                "elms_sans" to context.getString(com.nexus.launcher.R.string.widget_font_groovy)
            )
            configure(context.getString(com.nexus.launcher.R.string.widget_font_style), fontOptions, cfg.fontFamily)
            onValueChanged = { value ->
                updateConfig(getConfig().copy(fontFamily = value))
                onChanged()
            }
        }
        group.addChildRow(fontRow)

        // 5. Borderless / Flush Toggle
        val borderlessToggle = NexusToggleRow(context).apply {
            val cfg = getConfig()
            configure(context.getString(com.nexus.launcher.R.string.widget_flush_borderless), cfg.isBorderless)
            onCheckedChanged = { checked ->
                updateConfig(getConfig().copy(isBorderless = checked))
                onChanged()
            }
        }
        group.addChildRow(borderlessToggle)

        // 6. Expressive Background Controls
        val expressiveToggle = NexusToggleRow(context)
        val bgPickerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        val bgPicker = MosaicBackgroundPickerBinder(bgPickerContainer) { mode, index ->
            updateConfig(getConfig().copy(backgroundMode = mode, frostedGradientIndex = index))
            onChanged()
        }
        expressiveToggle.apply {
            configure(context.getString(com.nexus.launcher.R.string.expressive_gradient), getConfig().isExpressive)
            onCheckedChanged = { checked ->
                updateConfig(getConfig().copy(isExpressive = checked))
                bgPicker.setVisible(checked)
                onChanged()
            }
        }
        group.addChildRow(expressiveToggle)
        group.addChildRow(bgPickerContainer)
        bgPicker.bind(getConfig().backgroundMode, getConfig().frostedGradientIndex)
        bgPicker.setVisible(getConfig().isExpressive)

        val resetViews = {
            val current = getConfig()
            opacity.configure(context.getString(com.nexus.launcher.R.string.dock_settings_opacity), 0, 100, (current.backgroundOpacity * 100).toInt().coerceIn(0, 100)) { "$it%" }
            group.setRowVisibility(refraction, com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled)
            refraction.configure(context.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction), 0, 100, (current.glassRefraction * 100).toInt().coerceIn(0, 100)) { "$it%" }
            fontRow.configure(
                context.getString(com.nexus.launcher.R.string.widget_font_style),
                listOf(
                    "default" to "Nexus",
                    "manrope" to context.getString(com.nexus.launcher.R.string.widget_font_bauhaus),
                    "bitcount_single" to context.getString(com.nexus.launcher.R.string.widget_font_retro),
                    "playpen_sans" to context.getString(com.nexus.launcher.R.string.widget_font_playful),
                    "elms_sans" to context.getString(com.nexus.launcher.R.string.widget_font_groovy)
                ),
                current.fontFamily
            )
            borderlessToggle.configure(context.getString(com.nexus.launcher.R.string.widget_flush_borderless), current.isBorderless)
            bgPicker.bind(current.backgroundMode, current.frostedGradientIndex)
            bgPicker.setVisible(current.isExpressive)
            expressiveToggle.configure(context.getString(com.nexus.launcher.R.string.expressive_gradient), current.isExpressive)
        }

        return Pair(group, resetViews)
    }

    fun buildGeometryGroup(
        context: Context,
        tokens: NexusColorTokens,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit,
        allowCircle: Boolean = false,
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)
        // Circle only for widgets built around a centred subject (NexusWidgetShapeGeometry).
        val shapeOptions = buildList {
            add("1" to context.getString(com.nexus.launcher.R.string.icon_shape_squircle))
            add("2" to context.getString(com.nexus.launcher.R.string.icon_shape_square))
            add("11" to context.getString(com.nexus.launcher.R.string.icon_shape_pill))
            if (allowCircle) add("0" to context.getString(com.nexus.launcher.R.string.icon_shape_circle))
        }

        val radiusSlider = NexusSliderRow(context).apply {
            val cfg = getConfig()
            configure(context.getString(com.nexus.launcher.R.string.widget_corner_radius), 0, 48, cfg.cornerRadius) { "${it}dp" }
            applyAccentColor(tokens.textPrimary)
            onValueChanged = { value ->
                updateConfig(getConfig().copy(cornerRadius = value))
                onChanged()
            }
        }

        fun syncRadius(shape: Int) {
            radiusSlider.visibility = if (shape == 1) View.VISIBLE else View.GONE
        }

        val shapeRow = NexusSegmentedRow(context).apply {
            val cfg = getConfig()
            configure(
                context.getString(com.nexus.launcher.R.string.widget_shape),
                shapeOptions,
                cfg.shapeStyle.toString()
            )
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val shape = value.toInt()
                updateConfig(getConfig().copy(shapeStyle = shape))
                syncRadius(shape)
                onChanged()
            }
        }

        group.addChildRow(shapeRow)
        syncRadius(getConfig().shapeStyle)
        group.addChildRow(radiusSlider)

        val resetViews = {
            val current = getConfig()
            shapeRow.configure(context.getString(com.nexus.launcher.R.string.widget_shape), shapeOptions, current.shapeStyle.toString())
            syncRadius(current.shapeStyle)
            radiusSlider.configure(context.getString(com.nexus.launcher.R.string.widget_corner_radius), 0, 48, current.cornerRadius) { "${it}dp" }
        }

        return Pair(group, resetViews)
    }

    fun buildClockMessageGroup(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        return NexusWidgetSettingsClockCalendarViews.buildClockMessageGroup(
            context, dp, tokens, getConfig, updateConfig, onChanged
        )
    }

    fun buildCalendarGroup(
        context: Context,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): SettingsSectionGroupView {
        return NexusWidgetSettingsClockCalendarViews.buildCalendarGroup(
            context, getConfig, updateConfig, onChanged
        )
    }
}
