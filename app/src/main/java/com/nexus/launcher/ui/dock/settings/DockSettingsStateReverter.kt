package com.nexus.launcher.ui.dock.settings

import com.nexus.launcher.ui.dock.DockBackgroundRenderer
import com.nexus.launcher.ui.dock.DockCornerRadius
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.dock.DockLabelRenderer
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import com.nexus.launcher.ui.dock.DockSearchSlot
import com.nexus.launcher.ui.dock.DockSlotLayout

/** Reverts live dock configuration back to the persisted values in [DockSettingsRepository]. */
internal object DockSettingsStateReverter {

    fun revert(repository: DockSettingsRepository, dock: DockLayout?) {
        DockCornerRadius.cornerRadiusDp = repository.cornerRadiusDp.value
        DockLabelRenderer.showLabels = repository.showLabels.value
        DockLabelRenderer.labelFontSizeSp = repository.labelFontSizeSp.value
        DockSearchSlot.enabled = repository.searchInDock.value
        DockBackgroundRenderer.backgroundMode = repository.backgroundMode.value
        DockBackgroundRenderer.solidColorArgb = repository.solidColorArgb.value
        DockFrostedGradients.clampIndex(repository.frostedGradientIndex.value)
        DockBackgroundRenderer.frostedGradientIndex = repository.frostedGradientIndex.value
        DockBackgroundRenderer.backgroundOpacity = repository.dockBackgroundOpacity.value
        DockBackgroundRenderer.glassRefraction = repository.dockGlassRefraction.value
        DockSlotLayout.applyUserIconSizeDp(repository.iconSizeDp.value)

        val d = dock ?: return
        d.maxDockIcons = repository.maxIcons.value
        d.dockHeightDp = repository.dockHeightDp.value
        d.updateOrientationBounds()
        d.applyCornerRadiusOutline()
        DockBackgroundRenderer.syncBackgroundLayer(d)
        d.recomputeLayout()
        d.invalidate()
        d.requestLayout()
    }
}
