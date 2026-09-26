package com.nexus.launcher.ui.folder

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.dock.DockContextMenuLauncher
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.data.HomeScreenItem
import dagger.hilt.android.EntryPointAccessors

/** Shows folder Aurora context menu from dock long-press. */
object FolderDockMenuHelper {

    fun showContextMenu(dock: DockLayout, item: HomeScreenItem) {
        if (item.itemType != 1) return
        val activity = dock.context as? AppCompatActivity ?: return
        val coords = DockContextMenuLauncher.dockItemScreenCoords(dock, item) ?: return
        val scope = activity.lifecycleScope
        val dao = EntryPointAccessors.fromApplication(
            dock.context.applicationContext,
            DaoEntryPoint::class.java
        ).homeScreenDao()
        FolderContextMenuLauncher.show(
            context = activity,
            folderItem = item,
            dao = dao,
            coroutineScope = scope,
            iconX = coords.first,
            iconY = coords.second,
            iconSize = coords.third
        )
    }
}
