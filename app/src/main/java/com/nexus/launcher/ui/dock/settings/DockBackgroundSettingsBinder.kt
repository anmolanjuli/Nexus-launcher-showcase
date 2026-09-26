package com.nexus.launcher.ui.dock.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.dock.DockBackgroundRenderer
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.settings.SolidColorPickerDialog
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow

/** Background mode controls for [DockSettingsDialog]. */
internal class DockBackgroundSettingsBinder(
    private val fragment: Fragment,
    private val pendingSettings: PendingDockSettings,
    private val onPendingSettingsChanged: () -> Unit,
    private val root: View,
    private val dock: () -> DockLayout?
) {
    private val modeRow: NexusSegmentedRow? = root.findViewById(id("dock_background_mode"))
    private val frostedScroll: HorizontalScrollView? = root.findViewById(id("dock_frosted_gradient_scroll"))
    private val frostedRow: LinearLayout? = root.findViewById(id("dock_frosted_gradient_row"))
    private val solidColorRow: View? = root.findViewById(id("dock_solid_color_row"))
    private val colorSwatch: View? = root.findViewById(id("dock_solid_color_swatch"))
    private val opacitySlider: NexusSliderRow? = root.findViewById(id("dock_background_opacity_slider"))
    private var gradientBinder: DockFrostedGradientSwatchBinder? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    fun bind(tokens: NexusColorTokens) {
        this.currentTokens = tokens
        modeRow?.configure(
            label = "",
            options = backgroundModeOptions(fragment.requireContext()),
            initialValue = pendingSettings.backgroundMode.name
        )
        modeRow?.onValueChanged = { value ->
            val mode = DockBackgroundMode.fromStored(value)
            pendingSettings.backgroundMode = mode
            applyModeVisibility(mode)
            onPendingSettingsChanged()
        }

        frostedRow?.let { row ->
            gradientBinder = DockFrostedGradientSwatchBinder(
                context = fragment.requireContext(),
                row = row,
                scroll = frostedScroll,
                onNoneSelected = {
                    pendingSettings.backgroundMode = DockBackgroundMode.TRANSPARENT
                    syncDockBackground()
                    onPendingSettingsChanged()
                },
                onGradientSelected = { index ->
                    pendingSettings.backgroundMode = DockBackgroundMode.FROSTED
                    pendingSettings.frostedGradientIndex = index
                    DockBackgroundRenderer.frostedGradientIndex = index
                    syncDockBackground()
                    onPendingSettingsChanged()
                },
                onSolidColorClicked = {
                    showSolidColorDialog()
                }
            ).also {
                it.bind(pendingSettings.frostedGradientIndex, pendingSettings.solidColorArgb, tokens)
            }
        }

        colorSwatch?.setOnClickListener {
            showSolidColorDialog()
        }

        opacitySlider?.configure(
            label = fragment.requireContext().getString(com.nexus.launcher.R.string.dock_settings_opacity),
            min = 0,
            max = 100,
            value = (pendingSettings.dockBackgroundOpacity * 100).toInt(),
            formatValue = { "$it%" }
        )
        opacitySlider?.onValueChanged = { value ->
            val opacity = value / 100f
            pendingSettings.dockBackgroundOpacity = opacity
            DockBackgroundRenderer.backgroundOpacity = opacity
            syncDockBackground()
            onPendingSettingsChanged()
        }

        applyModeVisibility(pendingSettings.backgroundMode)
        updateSwatch(pendingSettings.solidColorArgb)
        applyTokens(tokens)
    }

    private fun showSolidColorDialog() {
        val current = pendingSettings.solidColorArgb
        val rgbHex = String.format(
            "#%02X%02X%02X",
            Color.red(current), Color.green(current), Color.blue(current)
        )
        val accentHex = String.format("#%06X", 0xFFFFFF and currentTokens.textPrimary)
        SolidColorPickerDialog.show(fragment.requireContext(), rgbHex, accentHex) { hex ->
            val rgb = Color.parseColor(hex)
            val argb = (0xFF shl 24) or (rgb and 0xFFFFFF)
            pendingSettings.solidColorArgb = argb
            updateSwatch(argb)
            DockBackgroundRenderer.solidColorArgb = argb
            syncDockBackground()
            onPendingSettingsChanged()
        }
    }

    fun applyTokens(tokens: NexusColorTokens) {
        this.currentTokens = tokens
        modeRow?.applyTokens(tokens)
        gradientBinder?.applyTokens(tokens)
        opacitySlider?.applyTokens(tokens)
        updateSwatch(pendingSettings.solidColorArgb)
    }

    private fun applyModeVisibility(mode: DockBackgroundMode) {
        setRowVisible(frostedScroll, mode == DockBackgroundMode.FROSTED)
        setRowVisible(solidColorRow, mode == DockBackgroundMode.SOLID)
        setRowVisible(opacitySlider, mode == DockBackgroundMode.SOLID || mode == DockBackgroundMode.FROSTED)
        DockBackgroundRenderer.backgroundMode = mode
        syncDockBackground()
    }

    private fun syncDockBackground() {
        dock()?.let {
            DockBackgroundRenderer.syncBackgroundLayer(it)
            it.applyCornerRadiusOutline()
            it.invalidate()
        }
    }

    private fun setRowVisible(view: View?, visible: Boolean) {
        view?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateSwatch(argb: Int) {
        val drawable = GradientDrawable().apply {
            cornerRadius = 8f * root.resources.displayMetrics.density
            setColor(argb)
            setStroke(
                (1 * root.resources.displayMetrics.density).toInt().coerceAtLeast(1),
                currentTokens.divider
            )
        }
        colorSwatch?.background = drawable
        DockBackgroundRenderer.solidColorArgb = argb
    }

    private fun id(name: String): Int =
        root.resources.getIdentifier(name, "id", fragment.requireContext().packageName)

    private companion object {
        fun backgroundModeOptions(context: android.content.Context) = listOf(
            DockBackgroundMode.TRANSPARENT.name to context.getString(com.nexus.launcher.R.string.dock_bg_mode_none),
            DockBackgroundMode.FROSTED.name to context.getString(com.nexus.launcher.R.string.dock_bg_mode_gradient),
            DockBackgroundMode.SOLID.name to context.getString(com.nexus.launcher.R.string.dock_bg_mode_solid),
        )
    }
}
