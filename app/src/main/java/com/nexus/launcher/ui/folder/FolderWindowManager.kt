package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object FolderWindowManager {

    internal var activeCard: LinearLayout? = null
    internal var activeCardHolder: FrameLayout? = null
    internal var activeGrid: GridLayout? = null
    internal var activeScrim: FolderWindowScrimView? = null
    internal var activeFolderItem: HomeScreenItem? = null
    internal var activeContents: List<HomeScreenItem> = emptyList()
    internal var activeIconCache: Map<String, Drawable> = emptyMap()
    internal var activeConfig: FolderConfig? = null
    internal var activeCanvas: WeakReference<LauncherCanvasView>? = null
    internal var isClosing = false

    var pendingReopenFolderId: Long? = null
    var pendingReopenIconX: Float = 0f
    var pendingReopenIconY: Float = 0f

    val currentFolderId: Long? get() = FolderWindowLifecycle.activeFolderId
    val activeIconX: Float get() = FolderWindowLifecycle.activeIconX
    val activeIconY: Float get() = FolderWindowLifecycle.activeIconY

    fun isShowing(): Boolean = FolderOverlayController.isShowing()

    fun applySavedEdits(
        context: Context,
        folderId: Long,
        title: String,
        config: FolderConfig
    ) {
        FolderWindowRefresh.applySavedEdits(context, folderId, title, config)
    }

    fun refreshBackground(context: Context, folderId: Long, config: FolderConfig) {
        FolderWindowRefresh.refreshBackground(context, folderId, config)
    }

    fun setOpenFolderChromeHidden(hidden: Boolean) {
        FolderWindowRefresh.setOpenFolderChromeHidden(hidden)
    }

    fun refreshOpenWindow(
        context: Context,
        folderId: Long,
        config: FolderConfig,
        iconCache: Map<String, Drawable>
    ) {
        FolderWindowRefresh.refreshOpenWindow(context, folderId, config, iconCache)
    }

    fun refreshOpenWindowIfShowing(context: Context) {
        FolderWindowRefresh.refreshOpenWindowIfShowing(context)
    }

    fun showFolder(
        context: Context,
        rootView: android.view.ViewGroup,
        folderItem: HomeScreenItem,
        iconX: Float,
        iconY: Float,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        iconSize: Float = 0f,
        highlightBounds: FolderScrimHighlight.Bounds? = null
    ) {
        FolderWindowLifecycle.showFolder(
            context, rootView, folderItem, iconX, iconY, contents, iconCache, iconSize, highlightBounds
        )
    }

    fun showFolder(
        folderId: Long,
        iconX: Float,
        iconY: Float,
        context: Context
    ) {
        val activity = context as? android.app.Activity ?: return
        val rootView = activity.window.decorView as android.view.ViewGroup
        val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.nexus.launcher.di.DaoEntryPoint::class.java
        ).homeScreenDao()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val item = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                dao.getItemById(folderId.toInt())
            } ?: return@launch
            val contents = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                dao.getItemsInFolderSync(folderId)
            }
            val iconCache = FolderEditIconCache.load(context, contents)
            FolderWindowLifecycle.showFolder(
                context, rootView, item, iconX, iconY, contents, iconCache
            )
        }
    }

    fun dismissFolder() {
        FolderWindowLifecycle.dismissFolder()
    }

    fun dismissDialog() = dismissFolder()
}
