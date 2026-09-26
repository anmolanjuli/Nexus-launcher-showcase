package com.nexus.launcher.ui.dock.settings

import android.graphics.Color
import android.view.View
import androidx.fragment.app.Fragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.dock.DockBackgroundRenderer
import com.nexus.launcher.ui.dock.DockCornerRadius
import com.nexus.launcher.ui.dock.DockLabelRenderer
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import com.nexus.launcher.ui.dock.DockSearchSlot
import com.nexus.launcher.ui.dock.DockSlotLayout
import com.nexus.launcher.ui.settings.SolidColorPickerDialog

/** Connects [DockSettingsBodyViews] with [PendingDockSettings] and [DockLayout]. */
internal class DockSettingsControlsBinder(
    private val fragment: Fragment,
    private val bodyViews: DockSettingsBodyViews,
    private val pendingSettings: PendingDockSettings,
    private val onPendingSettingsChanged: () -> Unit,
    private val dock: () -> DockLayout?
) {
    private var swatchBinder: DockFrostedGradientSwatchBinder? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    fun bind(tokens: NexusColorTokens) {
        this.currentTokens = tokens
        val ctx = fragment.requireContext()

        // 1. Capacity
        bodyViews.capacitySlider.configure(
            label = ctx.getString(R.string.dock_settings_max_icons),
            min = 1,
            max = 10,
            value = pendingSettings.maxIcons,
            stepSize = 1f
        )
        bodyViews.capacitySlider.onValueChanged = { value ->
            pendingSettings.maxIcons = value
            dock()?.let {
                it.maxDockIcons = value
                it.requestLayout()
                it.invalidate()
            }
            onPendingSettingsChanged()
        }

        // 2. Icon Size
        bodyViews.iconSizeSlider.configure(
            label = ctx.getString(R.string.dock_settings_icon_size),
            min = DockSettingsRepository.MIN_ICON_SIZE_DP,
            max = DockSettingsRepository.MAX_ICON_SIZE_DP,
            value = pendingSettings.iconSizeDp,
            stepSize = 2f,
            formatValue = { "${it}dp" }
        )
        bodyViews.iconSizeSlider.onValueChanged = { value ->
            pendingSettings.iconSizeDp = value
            DockSlotLayout.applyUserIconSizeDp(value)
            dock()?.let {
                it.recomputeLayout()
                it.invalidate()
            }
            onPendingSettingsChanged()
        }

        // 3. Height
        val minHeight = DockSettingsRepository.MIN_DOCK_HEIGHT_DP
        val maxHeight = DockSettingsRepository.MAX_DOCK_HEIGHT_DP.coerceAtLeast(minHeight + 1)
        bodyViews.heightSlider.configure(
            label = ctx.getString(R.string.dock_settings_height),
            min = minHeight,
            max = maxHeight,
            value = pendingSettings.dockHeightDp.coerceIn(minHeight, maxHeight),
            stepSize = 1f,
            formatValue = { "${it}dp" }
        )
        bodyViews.heightSlider.onValueChanged = { value ->
            pendingSettings.dockHeightDp = value
            dock()?.let {
                it.dockHeightDp = value
                it.updateOrientationBounds()
                it.recomputeLayout()
                it.invalidate()
            }
            onPendingSettingsChanged()
        }

        // 4. Corner Radius
        bodyViews.cornerRadiusSlider.configure(
            label = ctx.getString(R.string.dock_settings_corner_radius),
            min = DockSettingsRepository.MIN_CORNER_RADIUS_DP,
            max = DockSettingsRepository.MAX_CORNER_RADIUS_DP,
            value = pendingSettings.cornerRadiusDp,
            stepSize = 2f,
            formatValue = { "${it}dp" }
        )
        bodyViews.cornerRadiusSlider.onValueChanged = { radius ->
            pendingSettings.cornerRadiusDp = radius
            DockCornerRadius.cornerRadiusDp = radius
            dock()?.let {
                it.applyCornerRadiusOutline()
                it.invalidate()
            }
            onPendingSettingsChanged()
        }

        // 5. Expressive Gradient Toggle
        // The "Dock UI: Glass vs Soft UI" row that used to live here was removed — that per-item
        // choice was redundant with the global UI Style toggle (Nexus Settings > Appearance):
        // DockBackgroundRenderer.shouldDrawNeumorphicOnCanvas() already falls back to the
        // Neumorphic look automatically whenever Frosted Glass is off globally, for any dock
        // whose mode is TRANSPARENT/FROSTED. A dock with NEUMORPHIC explicitly persisted from
        // before this removal keeps rendering as Neumorphic (harmless legacy state, no migration
        // needed) until the Expressive Gradient toggle below is touched, which moves it to
        // FROSTED/TRANSPARENT and back under the global toggle's control like everything else.
        val isSoftUi = pendingSettings.backgroundMode == DockBackgroundMode.NEUMORPHIC
        val isExpressive = (pendingSettings.backgroundMode == DockBackgroundMode.FROSTED ||
                pendingSettings.backgroundMode == DockBackgroundMode.SOLID) && !isSoftUi
        bodyViews.expressiveToggle.configure(
            ctx.getString(R.string.expressive_gradient),
            isExpressive
        )
        bodyViews.expressiveToggle.onCheckedChanged = { checked ->
            if (checked) {
                pendingSettings.backgroundMode = DockBackgroundMode.FROSTED
                swatchBinder?.setSelection(pendingSettings.frostedGradientIndex, pendingSettings.solidColorArgb)
            } else {
                pendingSettings.backgroundMode = DockBackgroundMode.TRANSPARENT
                swatchBinder?.setSelection(DockFrostedGradientSwatchBinder.SELECTION_NONE, pendingSettings.solidColorArgb)
            }
            applyModeVisibility(pendingSettings.backgroundMode)
            syncDockBackground()
            onPendingSettingsChanged()
        }

        // 7. Swatches (None + Gradients + Custom Solid)
        val initialSelection = when (pendingSettings.backgroundMode) {
            DockBackgroundMode.TRANSPARENT -> DockFrostedGradientSwatchBinder.SELECTION_NONE
            DockBackgroundMode.SOLID -> DockFrostedGradientSwatchBinder.SELECTION_SOLID
            DockBackgroundMode.FROSTED -> pendingSettings.frostedGradientIndex
            else -> DockFrostedGradientSwatchBinder.SELECTION_NONE
        }
        swatchBinder = DockFrostedGradientSwatchBinder(
            context = ctx,
            row = bodyViews.frostedRow,
            scroll = bodyViews.frostedScroll,
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
                    pendingSettings.backgroundMode = DockBackgroundMode.SOLID
                    DockBackgroundRenderer.solidColorArgb = argb
                    swatchBinder?.setSelection(DockFrostedGradientSwatchBinder.SELECTION_SOLID, argb)
                    syncDockBackground()
                    onPendingSettingsChanged()
                }
            }
        ).also {
            it.bind(initialSelection, pendingSettings.solidColorArgb, tokens)
        }

        // 8. Opacity
        bodyViews.opacitySlider.configure(
            label = ctx.getString(R.string.dock_settings_opacity),
            min = 0,
            max = 100,
            value = (pendingSettings.dockBackgroundOpacity * 100).toInt(),
            formatValue = { "$it%" }
        )
        bodyViews.opacitySlider.onValueChanged = { value ->
            val opacity = value / 100f
            pendingSettings.dockBackgroundOpacity = opacity
            DockBackgroundRenderer.backgroundOpacity = opacity
            syncDockBackground()
            onPendingSettingsChanged()
        }

        // 8b. Glass Refraction — blur strength / background bleed, Frosted Glass only
        bodyViews.refractionSlider.configure(
            label = ctx.getString(com.nexus.launcher.R.string.folder_edit_glass_refraction),
            min = 0,
            max = 100,
            value = (pendingSettings.dockGlassRefraction * 100).toInt(),
            formatValue = { "$it%" }
        )
        bodyViews.refractionSlider.onValueChanged = { value ->
            val refraction = value / 100f
            pendingSettings.dockGlassRefraction = refraction
            DockBackgroundRenderer.glassRefraction = refraction
            syncDockBackground()
            onPendingSettingsChanged()
        }

        // 9. Show Labels
        bodyViews.showLabelsToggle.configure(
            ctx.getString(R.string.dock_settings_show_labels),
            pendingSettings.showLabels
        )
        bodyViews.showLabelsToggle.onCheckedChanged = { checked ->
            pendingSettings.showLabels = checked
            bodyViews.labelFontSlider.visibility = if (checked) View.VISIBLE else View.GONE
            DockLabelRenderer.showLabels = checked
            dock()?.let {
                it.updateOrientationBounds()
                it.invalidate()
            }
            (fragment as? DockSettingsDialog)?.rePin()
            onPendingSettingsChanged()
        }

        // 10. Label Font Size
        bodyViews.labelFontSlider.visibility = if (pendingSettings.showLabels) View.VISIBLE else View.GONE
        bodyViews.labelFontSlider.configure(
            label = ctx.getString(R.string.dock_settings_font_size),
            min = DockSettingsRepository.MIN_LABEL_FONT_SIZE_SP,
            max = DockSettingsRepository.MAX_LABEL_FONT_SIZE_SP,
            value = pendingSettings.labelFontSizeSp,
            stepSize = 1f,
            formatValue = { "${it}sp" }
        )
        bodyViews.labelFontSlider.onValueChanged = { sp ->
            pendingSettings.labelFontSizeSp = sp
            DockLabelRenderer.labelFontSizeSp = sp
            dock()?.let {
                it.updateOrientationBounds()
                it.invalidate()
            }
            onPendingSettingsChanged()
        }

        // 11. Search in Dock
        bodyViews.searchToggle.configure(
            ctx.getString(R.string.dock_settings_show_search),
            pendingSettings.searchInDock
        )
        bodyViews.searchToggle.onCheckedChanged = { enabled ->
            pendingSettings.searchInDock = enabled
            DockSearchSlot.enabled = enabled
            dock()?.recomputeLayout()
            onPendingSettingsChanged()
        }

        applyModeVisibility(pendingSettings.backgroundMode)
        applyTokens(tokens)
    }

    fun rebindValues() {
        bodyViews.capacitySlider.setValue(pendingSettings.maxIcons)
        bodyViews.iconSizeSlider.setValue(pendingSettings.iconSizeDp)
        bodyViews.heightSlider.setValue(pendingSettings.dockHeightDp)
        bodyViews.cornerRadiusSlider.setValue(pendingSettings.cornerRadiusDp)
        val isExpressive = pendingSettings.backgroundMode == DockBackgroundMode.FROSTED ||
                pendingSettings.backgroundMode == DockBackgroundMode.SOLID
        bodyViews.expressiveToggle.setChecked(isExpressive)
        val selection = when (pendingSettings.backgroundMode) {
            DockBackgroundMode.TRANSPARENT -> DockFrostedGradientSwatchBinder.SELECTION_NONE
            DockBackgroundMode.SOLID -> DockFrostedGradientSwatchBinder.SELECTION_SOLID
            DockBackgroundMode.FROSTED -> pendingSettings.frostedGradientIndex
            else -> DockFrostedGradientSwatchBinder.SELECTION_NONE
        }
        swatchBinder?.setSelection(selection, pendingSettings.solidColorArgb)
        bodyViews.opacitySlider.setValue((pendingSettings.dockBackgroundOpacity * 100).toInt())
        bodyViews.refractionSlider.setValue((pendingSettings.dockGlassRefraction * 100).toInt())
        bodyViews.showLabelsToggle.setChecked(pendingSettings.showLabels)
        bodyViews.labelFontSlider.visibility = if (pendingSettings.showLabels) View.VISIBLE else View.GONE
        bodyViews.labelFontSlider.setValue(pendingSettings.labelFontSizeSp)
        bodyViews.searchToggle.setChecked(pendingSettings.searchInDock)
        applyModeVisibility(pendingSettings.backgroundMode)
    }

    fun applyTokens(tokens: NexusColorTokens) {
        this.currentTokens = tokens
        bodyViews.capacitySlider.applyTokens(tokens)
        bodyViews.iconSizeSlider.applyTokens(tokens)
        bodyViews.heightSlider.applyTokens(tokens)
        bodyViews.cornerRadiusSlider.applyTokens(tokens)
        bodyViews.expressiveToggle.applyTokens(tokens)
        swatchBinder?.applyTokens(tokens)
        bodyViews.opacitySlider.applyTokens(tokens)
        bodyViews.refractionSlider.applyTokens(tokens)
        bodyViews.showLabelsToggle.applyTokens(tokens)
        bodyViews.labelFontSlider.applyTokens(tokens)
        bodyViews.searchToggle.applyTokens(tokens)
    }

    private fun applyModeVisibility(mode: DockBackgroundMode) {
        val isSoftUi = mode == DockBackgroundMode.NEUMORPHIC
        bodyViews.expressiveToggle.visibility = if (isSoftUi) View.GONE else View.VISIBLE
        val isExpressive = (mode == DockBackgroundMode.FROSTED || mode == DockBackgroundMode.SOLID) && !isSoftUi
        bodyViews.expressiveToggle.setChecked(isExpressive)
        bodyViews.frostedScroll.visibility = if (isExpressive) View.VISIBLE else View.GONE
        val showOpacity = isExpressive || DockBackgroundRenderer.frostedGlassEnabled
        bodyViews.opacitySlider.visibility = if (showOpacity && !isSoftUi) View.VISIBLE else View.GONE
        val showRefraction = DockBackgroundRenderer.frostedGlassEnabled && !isSoftUi
        bodyViews.refractionSlider.visibility = if (showRefraction) View.VISIBLE else View.GONE
        DockBackgroundRenderer.backgroundMode = mode
        syncDockBackground()
        (fragment as? DockSettingsDialog)?.rePin()
    }

    private fun syncDockBackground() {
        dock()?.let {
            DockBackgroundRenderer.syncBackgroundLayer(it)
            it.applyCornerRadiusOutline()
            it.invalidate()
        }
    }
}
