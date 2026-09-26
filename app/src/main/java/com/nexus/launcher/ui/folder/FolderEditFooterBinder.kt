package com.nexus.launcher.ui.folder

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.settings.IconEditConfirmationDialog
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FolderEditFooterBinder {

    fun buildFooter(
        context: Context,
        dp: Float,
        folderItem: HomeScreenItem,
        bodyViews: FolderEditBodyViews,
        dao: HomeScreenDao,
        mainViewModel: MainViewModel,
        coroutineScope: CoroutineScope,
        backgroundBinder: FolderBackgroundPickerBinder,
        previewBinder: () -> FolderEditSheetPreviewBinder?,
        getConfig: () -> FolderConfig,
        setConfig: (FolderConfig) -> Unit,
        getGridColumns: () -> Int,
        setGridColumns: (Int) -> Unit,
        getShowLabels: () -> Boolean,
        setShowLabels: (Boolean) -> Unit,
        getSolidHex: () -> String,
        setSolidHex: (String) -> Unit,
        getFrostedIndex: () -> Int,
        setFrostedIndex: (Int) -> Unit,
        getIconOpacity: () -> Int,
        setIconOpacity: (Int) -> Unit,
        getWindowOpacity: () -> Int,
        setWindowOpacity: (Int) -> Unit,
        getIsExpressive: () -> Boolean,
        setIsExpressive: (Boolean) -> Unit,
        getPendingSwipeUp: () -> String,
        setPendingSwipeUp: (String) -> Unit,
        getPendingSwipeDown: () -> String,
        setPendingSwipeDown: (String) -> Unit,
        originalUp: String,
        originalDown: String,
        attachGestures: () -> Unit,
        onDismiss: () -> Unit,
        refreshApplyEnabled: () -> Unit = {},
        onUserModified: () -> Unit = {}
    ): NexusSettingsButtons.Footer {
        val folderKey = "folder_${folderItem.id}"
        return NexusSettingsButtons.buildFooter(
            context = context,
            dp = dp,
            onReset = {
                IconEditConfirmationDialog.showResetConfirmation(context) {
                    val newConfig = FolderEditResetHelper.resetState(
                        bodyViews, folderItem, backgroundBinder
                    ) { _ ->
                        FolderEditShapeBinder.bind(
                            context, bodyViews, getConfig, previewBinder
                        ) { updated -> setConfig(updated) }
                    }
                    setConfig(newConfig)
                    previewBinder()?.updateConfig(newConfig)
                    setGridColumns(newConfig.gridColumns.coerceIn(2, 10))
                    setIconOpacity((newConfig.backgroundOpacity * 100).toInt().coerceIn(0, 100))
                    setWindowOpacity((newConfig.windowBackgroundOpacity * 100).toInt().coerceIn(0, 100))
                    setShowLabels(newConfig.showLabels)
                    setIsExpressive(newConfig.isExpressive)
                    setSolidHex(newConfig.solidBackgroundColor ?: "#131822")
                    setFrostedIndex(newConfig.frostedGradientIndex)
                    setPendingSwipeUp("NONE")
                    setPendingSwipeDown("NONE")
                    attachGestures()
                    onUserModified()
                    // Staged defaults differ from the original saved config — that IS a dirty
                    // state, so Apply must re-enable (matches Dock Settings' reset behavior).
                    refreshApplyEnabled()
                }
            },
            onApply = {
                val newTitle = bodyViews.titleEdit.text.toString().trim()
                val newConfig = getConfig().copy(
                    gridColumns = getGridColumns(),
                    showLabels = getShowLabels(),
                    solidBackgroundColor = getSolidHex(),
                    backgroundOpacity = getIconOpacity() / 100f,
                    windowBackgroundOpacity = getWindowOpacity() / 100f,
                    isExpressive = getIsExpressive()
                )
                coroutineScope.launch(Dispatchers.IO) {
                    FolderActionEngine.saveFolderConfig(folderItem.id.toLong(), newTitle, newConfig, dao)
                }
                if (getPendingSwipeUp() != originalUp) mainViewModel.setSwipeUpAction(folderKey, getPendingSwipeUp())
                if (getPendingSwipeDown() != originalDown) mainViewModel.setSwipeDownAction(folderKey, getPendingSwipeDown())
                mainViewModel.setDoubleTapAction(folderKey, "NONE")
                FolderWindowManager.applySavedEdits(context, folderItem.id.toLong(), newTitle, newConfig)
                (context as? android.app.Activity)?.let {
                    FolderCanvasInvalidator.afterFolderSave(it, folderItem.id, newConfig)
                }
                onDismiss()
            }
        )
    }
}
