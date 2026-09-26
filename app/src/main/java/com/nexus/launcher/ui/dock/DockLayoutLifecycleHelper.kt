package com.nexus.launcher.ui.dock

import androidx.lifecycle.findViewTreeLifecycleOwner
import com.nexus.launcher.ui.dock.settings.DockSettingsEntryPoint
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

/**
 * Window attachment, settings observation, and cleanup logic extracted from [DockLayout].
 */
internal object DockLayoutLifecycleHelper {

    fun dockSettingsRepository(dock: DockLayout): DockSettingsRepository =
        EntryPointAccessors.fromApplication(
            dock.context.applicationContext,
            DockSettingsEntryPoint::class.java
        ).dockSettingsRepository()

    fun onAttachedToWindow(dock: DockLayout) {
        if (!dock.ioScope.isActive) dock.ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DockLayoutRenderer.requestInvalidate = { dock.invalidate() }
        DockShuffleSpringLoop.attach(dock)
        DockLayoutRenderer.ensureIconContext(dock.context.applicationContext)
        val repo = dockSettingsRepository(dock)
        DockBackgroundRenderer.backgroundMode = repo.backgroundMode.value
        DockBackgroundRenderer.solidColorArgb = repo.solidColorArgb.value
        DockBackgroundRenderer.frostedGradientIndex = repo.frostedGradientIndex.value
        DockCornerRadius.cornerRadiusDp = repo.cornerRadiusDp.value
        DockLabelRenderer.showLabels = repo.showLabels.value
        DockLabelRenderer.labelFontSizeSp = repo.labelFontSizeSp.value
        DockSearchSlot.enabled = repo.searchInDock.value
        DockSlotLayout.applyUserIconSizeDp(repo.iconSizeDp.value)
        dock.maxDockIcons = repo.maxIcons.value
        dock.dockHeightDp = repo.dockHeightDp.value
        DockLayoutSettingsBinder.updateOrientationBounds(dock, dock.dockHeightDp, dock.density)
        DockLayoutRenderer.onCommitItems = { dockList ->
            dock.applyItems(dockList, refreshAllIntents = false)
            dock.loadMissingIconsSync(dockList)
            dock.invalidate()
        }

        val owner = dock.findViewTreeLifecycleOwner()
        if (owner != null) {
            DockLayoutSettingsBinder.observeDockHeight(
                dock, owner, dock.density,
                onHeightDp = { dock.dockHeightDp = it },
                onJob = { dock.settingsCollectJob = it },
                recompute = { dock.recomputeLayout(); dock.requestLayout(); dock.invalidate() }
            )
            DockLayoutSettingsBinder.observeAppearance(dock, owner, onJob = {})
        }
        dock.dockBackgroundInternal.attach()
    }

    fun onDetachedFromWindow(dock: DockLayout) {
        dock.dockBlurInternal.detach()
        dock.dockBackgroundInternal.detach()
        DockLayoutRenderer.onCommitItems = null
        dock.settingsCollectJob?.cancel()
        dock.settingsCollectJob = null
        dock.cancelSnapAnimator()
        dock.ioScope.cancel()
    }
}
