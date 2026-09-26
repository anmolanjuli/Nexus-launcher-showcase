package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live mini-folder preview embedded at the top of the edit sheet. */
class FolderEditSheetPreviewBinder(
    private val context: Context,
    private val folderItem: HomeScreenItem,
    private val dao: HomeScreenDao,
    private val scope: CoroutineScope
) {
    private var previewView: FolderEditPreviewView? = null
    private var iconPreviewView: FolderEditIconPreviewView? = null
    private var contents: List<HomeScreenItem> = emptyList()
    private var iconCache: Map<String, Drawable> = emptyMap()
    private var workingConfig: FolderConfig = FolderConfig()
    private var previewTitle: String = FolderContextMenuLauncher.folderDisplayName(context, folderItem)

    fun attach(preview: FolderEditPreviewView, iconPreview: FolderEditIconPreviewView, initialConfig: FolderConfig) {
        previewView = preview
        iconPreviewView = iconPreview
        workingConfig = initialConfig
        scope.launch {
            contents = withContext(Dispatchers.IO) {
                dao.getItemsInFolderSync(folderItem.resolveFolderContentsId())
            }
            iconCache = FolderEditIconCache.load(context, contents)
            refreshPreview()
        }
    }


    fun updateConfig(config: FolderConfig, title: String? = null) {
        workingConfig = config
        title?.let { previewTitle = it.ifBlank { context.getString(com.nexus.launcher.R.string.folder_default_name) } }
        refreshPreview()
    }

    fun updateLayout(gridColumns: Int, showLabels: Boolean) {
        workingConfig = workingConfig.copy(
            gridColumns = gridColumns,
            showLabels = showLabels
        )
        refreshPreview()
    }

    private fun refreshPreview() {
        previewView?.update(folderItem, workingConfig, contents, iconCache, previewTitle)
        iconPreviewView?.update(folderItem, workingConfig, contents, iconCache)
    }
}
