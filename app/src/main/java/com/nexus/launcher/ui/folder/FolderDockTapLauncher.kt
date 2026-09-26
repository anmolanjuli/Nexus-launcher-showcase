package com.nexus.launcher.ui.folder

import android.view.ViewGroup
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import dagger.hilt.android.EntryPointAccessors
import com.nexus.launcher.di.DaoEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Opens a dock folder via [FolderWindowManager] using dock layout geometry. */
object FolderDockTapLauncher {

    fun openFromDock(dock: DockLayout, item: HomeScreenItem, scope: CoroutineScope) {
        if (item.itemType != 1) return

        val visibleItems = dock.pageItemsInternal().sortedBy { it.column }
        val visualSlot = visibleItems.indexOfFirst { it.id == item.id }
        if (visualSlot < 0) return

        dock.syncRendererLayoutInternal()
        val bounds = DockLayoutRenderer.getIconBoundsAt(visualSlot) ?: return
        val dockLoc = IntArray(2)
        dock.getLocationOnScreen(dockLoc)
        val iconX = bounds.centerX() + dockLoc[0]
        val iconY = bounds.centerY() + dockLoc[1]
        val iconSize = bounds.width().toFloat().coerceAtLeast(bounds.height().toFloat())

        val activity = dock.context as? android.app.Activity ?: return
        val rootFrame = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val contentsId = item.resolveFolderContentsId()

        scope.launch(Dispatchers.IO) {
            val entryPoint = EntryPointAccessors.fromApplication(
                dock.context.applicationContext,
                DaoEntryPoint::class.java
            )
            val dao = entryPoint.homeScreenDao()
            val contents = dao.getItemsInFolderSync(contentsId)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                FolderWindowManager.showFolder(
                    context = dock.context,
                    rootView = rootFrame,
                    folderItem = item,
                    iconX = iconX,
                    iconY = iconY,
                    contents = contents,
                    iconCache = dock.iconCacheInternal,
                    iconSize = iconSize
                )
            }
        }
    }
}
