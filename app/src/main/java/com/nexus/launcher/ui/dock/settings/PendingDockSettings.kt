package com.nexus.launcher.ui.dock.settings

import android.graphics.Color

data class PendingDockSettings(
    var maxIcons: Int,
    var dockHeightDp: Int,
    var iconSizeDp: Int,
    var cornerRadiusDp: Int,
    var labelFontSizeSp: Int,
    var showLabels: Boolean,
    var searchInDock: Boolean,
    var backgroundMode: DockBackgroundMode,
    var frostedGradientIndex: Int,
    var solidColorArgb: Int,
    var dockBackgroundOpacity: Float,
    var dockGlassRefraction: Float
) {
    fun hasChanges(repository: DockSettingsRepository): Boolean {
        return maxIcons != repository.maxIcons.value ||
                dockHeightDp != repository.dockHeightDp.value ||
                iconSizeDp != repository.iconSizeDp.value ||
                cornerRadiusDp != repository.cornerRadiusDp.value ||
                labelFontSizeSp != repository.labelFontSizeSp.value ||
                showLabels != repository.showLabels.value ||
                searchInDock != repository.searchInDock.value ||
                backgroundMode != repository.backgroundMode.value ||
                frostedGradientIndex != repository.frostedGradientIndex.value ||
                solidColorArgb != repository.solidColorArgb.value ||
                dockBackgroundOpacity != repository.dockBackgroundOpacity.value ||
                dockGlassRefraction != repository.dockGlassRefraction.value
    }

    fun isDefault(): Boolean {
        return maxIcons == 4 &&
                dockHeightDp == DockSettingsRepository.DEFAULT_DOCK_HEIGHT_DP &&
                iconSizeDp == DockSettingsRepository.DEFAULT_ICON_SIZE_DP &&
                cornerRadiusDp == DockSettingsRepository.DEFAULT_CORNER_RADIUS_DP &&
                labelFontSizeSp == DockSettingsRepository.DEFAULT_LABEL_FONT_SIZE_SP &&
                showLabels == false &&
                searchInDock == false &&
                backgroundMode == DockBackgroundMode.TRANSPARENT &&
                frostedGradientIndex == DockSettingsRepository.DEFAULT_FROSTED_GRADIENT_INDEX &&
                solidColorArgb == DockSettingsRepository.DEFAULT_SOLID_COLOR_ARGB &&
                dockBackgroundOpacity == DockSettingsRepository.DEFAULT_DOCK_BACKGROUND_OPACITY &&
                dockGlassRefraction == DockSettingsRepository.DEFAULT_DOCK_GLASS_REFRACTION
    }

    fun resetToDefaults() {
        maxIcons = 4
        dockHeightDp = DockSettingsRepository.DEFAULT_DOCK_HEIGHT_DP
        iconSizeDp = DockSettingsRepository.DEFAULT_ICON_SIZE_DP
        cornerRadiusDp = DockSettingsRepository.DEFAULT_CORNER_RADIUS_DP
        labelFontSizeSp = DockSettingsRepository.DEFAULT_LABEL_FONT_SIZE_SP
        showLabels = false
        searchInDock = false
        backgroundMode = DockBackgroundMode.TRANSPARENT
        frostedGradientIndex = DockSettingsRepository.DEFAULT_FROSTED_GRADIENT_INDEX
        solidColorArgb = DockSettingsRepository.DEFAULT_SOLID_COLOR_ARGB
        dockBackgroundOpacity = DockSettingsRepository.DEFAULT_DOCK_BACKGROUND_OPACITY
        dockGlassRefraction = DockSettingsRepository.DEFAULT_DOCK_GLASS_REFRACTION
    }
}
