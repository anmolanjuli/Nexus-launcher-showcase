package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem

object FolderEditResetHelper {

    fun resetState(
        bodyViews: FolderEditBodyViews,
        folderItem: HomeScreenItem,
        backgroundBinder: FolderBackgroundPickerBinder,
        onShapeReset: (Int) -> Unit
    ): FolderConfig {
        val context = bodyViews.root.context
        val defaultConfig = FolderConfig()
        val gridColumns = defaultConfig.gridColumns.coerceIn(2, 10)
        bodyViews.columnsSlider.configure(context.getString(com.nexus.launcher.R.string.folder_edit_columns), 2, 10, gridColumns)

        val iconOpacity = (defaultConfig.backgroundOpacity * 100).toInt().coerceIn(0, 100)
        val windowOpacity = (defaultConfig.windowBackgroundOpacity * 100).toInt().coerceIn(0, 100)
        bodyViews.iconOpacitySlider.configure(iconOpacity)
        bodyViews.windowOpacitySlider.configure(windowOpacity)
        bodyViews.iconOpacityValueText.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(iconOpacity, context)
        bodyViews.windowOpacityValueText.text = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(windowOpacity, context)

        bodyViews.labelsToggle.configure(context.getString(com.nexus.launcher.R.string.folder_edit_show_labels), defaultConfig.showLabels)
        FolderEditAppearanceBinder.refresh(
            context,
            bodyViews,
            try {
                com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                com.nexus.launcher.theme.NexusColorTokens.Dark
            },
            defaultConfig
        )
        bodyViews.expressiveToggle.configure(context.getString(com.nexus.launcher.R.string.folder_edit_expressive_gradient), defaultConfig.isExpressive)
        bodyViews.backgroundContainer.visibility = if (defaultConfig.isExpressive) android.view.View.VISIBLE else android.view.View.GONE

        val solidHex = defaultConfig.solidBackgroundColor ?: "#131822"
        val frostedIndex = defaultConfig.frostedGradientIndex
        backgroundBinder.bind(defaultConfig.windowBackgroundMode, frostedIndex, solidHex)
        onShapeReset(defaultConfig.shapeStyle)

        bodyViews.previewRow.configure(
            "",
            FolderPreviewOptions.getAvailableLabels(context, folderItem.spanX, folderItem.spanY),
            defaultConfig.previewStyle.toString()
        )

        return defaultConfig
    }
}
