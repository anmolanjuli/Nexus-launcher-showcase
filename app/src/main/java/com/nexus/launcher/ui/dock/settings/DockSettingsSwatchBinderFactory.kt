package com.nexus.launcher.ui.dock.settings

import android.content.Context
import android.graphics.Color
import androidx.fragment.app.Fragment
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.dock.DockBackgroundRenderer
import com.nexus.launcher.ui.settings.SolidColorPickerDialog

internal object DockSettingsSwatchBinderFactory {

    fun create(
        fragment: Fragment,
        ctx: Context,
        bodyViews: DockSettingsBodyViews,
        pendingSettings: PendingDockSettings,
        tokens: NexusColorTokens,
        currentTokens: () -> NexusColorTokens,
        syncDockBackground: () -> Unit,
        onPendingSettingsChanged: () -> Unit,
        onSwatchCreated: (DockFrostedGradientSwatchBinder) -> Unit
    ): DockFrostedGradientSwatchBinder {
        val initialSelection = when (pendingSettings.backgroundMode) {
            DockBackgroundMode.TRANSPARENT -> DockFrostedGradientSwatchBinder.SELECTION_NONE
            DockBackgroundMode.SOLID -> DockFrostedGradientSwatchBinder.SELECTION_SOLID
            DockBackgroundMode.FROSTED -> pendingSettings.frostedGradientIndex
            else -> DockFrostedGradientSwatchBinder.SELECTION_NONE
        }
        val binder = DockFrostedGradientSwatchBinder(
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
                val accentHex = String.format("#%06X", 0xFFFFFF and currentTokens().textPrimary)
                SolidColorPickerDialog.show(fragment.requireContext(), rgbHex, accentHex) { hex ->
                    val rgb = Color.parseColor(hex)
                    val argb = (0xFF shl 24) or (rgb and 0xFFFFFF)
                    pendingSettings.solidColorArgb = argb
                    pendingSettings.backgroundMode = DockBackgroundMode.SOLID
                    DockBackgroundRenderer.solidColorArgb = argb
                    syncDockBackground()
                    onPendingSettingsChanged()
                }
            }
        ).also {
            it.bind(initialSelection, pendingSettings.solidColorArgb, tokens)
        }
        onSwatchCreated(binder)
        return binder
    }
}
