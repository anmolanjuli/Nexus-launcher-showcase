package com.nexus.launcher.ui.dock.settings

import android.content.Context
import com.nexus.launcher.ui.dock.DockBackgroundRenderer
import com.nexus.launcher.ui.dock.DockCornerRadius
import com.nexus.launcher.ui.dock.DockLabelRenderer
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import com.nexus.launcher.ui.dock.DockSearchSlot
import com.nexus.launcher.ui.dock.DockSlotLayout
import com.nexus.launcher.ui.settings.IconEditConfirmationDialog
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Builds and binds the sticky Reset / Apply footer for the Dock Settings Bottom Sheet. */
internal object DockSettingsFooterBinder {

    fun buildFooter(
        context: Context,
        dp: Float,
        pendingSettings: PendingDockSettings,
        repository: DockSettingsRepository,
        coroutineScope: CoroutineScope,
        controlsBinder: () -> DockSettingsControlsBinder?,
        dock: () -> DockLayout?,
        onDismiss: () -> Unit,
        refreshApplyEnabled: () -> Unit
    ): NexusSettingsButtons.Footer {
        return NexusSettingsButtons.buildFooter(
            context = context,
            dp = dp,
            onReset = {
                IconEditConfirmationDialog.showResetConfirmation(context) {
                    pendingSettings.resetToDefaults()

                    // Sync live dock to defaults
                    DockCornerRadius.cornerRadiusDp = pendingSettings.cornerRadiusDp
                    DockLabelRenderer.showLabels = pendingSettings.showLabels
                    DockLabelRenderer.labelFontSizeSp = pendingSettings.labelFontSizeSp
                    DockSearchSlot.enabled = pendingSettings.searchInDock
                    DockBackgroundRenderer.backgroundMode = pendingSettings.backgroundMode
                    DockBackgroundRenderer.solidColorArgb = pendingSettings.solidColorArgb
                    DockBackgroundRenderer.frostedGradientIndex = pendingSettings.frostedGradientIndex
                    DockBackgroundRenderer.backgroundOpacity = pendingSettings.dockBackgroundOpacity
                    DockBackgroundRenderer.glassRefraction = pendingSettings.dockGlassRefraction
                    DockSlotLayout.applyUserIconSizeDp(pendingSettings.iconSizeDp)

                    dock()?.let { d ->
                        d.maxDockIcons = pendingSettings.maxIcons
                        d.dockHeightDp = pendingSettings.dockHeightDp
                        d.updateOrientationBounds()
                        d.applyCornerRadiusOutline()
                        DockBackgroundRenderer.syncBackgroundLayer(d)
                        d.recomputeLayout()
                        d.invalidate()
                        d.requestLayout()
                    }

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                DockResetEntryPoint::class.java
                            ).homeScreenDao()
                            com.nexus.launcher.ui.LegacyDockMigration.resetDockToDefaultApps(dao, context.applicationContext)
                        } catch (e: Exception) {
                            android.util.Log.e("DockReset", "Error resetting dock items: ${e.message}")
                        }
                    }

                    controlsBinder()?.rebindValues()
                    refreshApplyEnabled()
                }
            },
            onApply = {
                val snapshot = pendingSettings.copy()
                repository.save(snapshot)
                onDismiss()
            }
        )
    }
}
