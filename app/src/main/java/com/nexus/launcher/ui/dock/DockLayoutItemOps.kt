package com.nexus.launcher.ui.dock

import com.nexus.launcher.data.HomeScreenItem

/**
 * Item binding, intent resolution, icon caching, and post-drop snapping extracted from [DockLayout].
 */
internal object DockLayoutItemOps {

    fun applyItems(
        dock: DockLayout,
        newItems: List<HomeScreenItem>,
        refreshAllIntents: Boolean
    ) {
        dock.items = newItems.toList()
        val pm = dock.context.packageManager
        if (refreshAllIntents) dock.launchIntents.clear()
        for (item in newItems) {
            if (refreshAllIntents || !dock.launchIntents.containsKey(item.id)) {
                pm.getLaunchIntentForPackage(item.packageName)?.let { dock.launchIntents[item.id] = it }
            }
        }
        DockIconLoader.loadMissingIconsSync(dock.context, newItems, dock.iconCache)
        dock.recomputeLayout()
        dock.scrollOffsetX = dock.scrollOffsetX.coerceIn(0f, DockLayoutPager.maxScrollX(dock.dockAxisSizeInternal, dock.pageCount))
    }

    fun refreshCache(dock: DockLayout) {
        DockIconLoader.loadMissingIcons(dock.ioScope, dock.context, dock.items, dock.iconCache) {
            dock.postInvalidate()
        }
        DockLayoutBindHelper.precacheFolderChildIcons(
            dock, dock.ioScope, dock.context, dock.folderContentsSnapshot, dock.iconCache
        )
    }

    fun reloadIcons(dock: DockLayout) {
        DockIconLoader.reloadIcons(dock.ioScope, dock.context, dock.items, dock.iconCache) {
            dock.postInvalidate()
            DockLayoutBindHelper.precacheFolderChildIcons(
                dock, dock.ioScope, dock.context, dock.folderContentsSnapshot, dock.iconCache
            )
        }
    }

    fun loadMissingIconsAsync(dock: DockLayout, onLoaded: () -> Unit) {
        DockIconLoader.loadMissingIcons(dock.ioScope, dock.context, dock.items, dock.iconCache, onLoaded)
    }

    fun loadMissingIconsSync(dock: DockLayout, newItems: List<HomeScreenItem>) {
        DockIconLoader.loadMissingIconsSync(dock.context, newItems, dock.iconCache)
    }

    fun snapRendererAfterDrop(dock: DockLayout) {
        if (dock.width != 0 && dock.height != 0) {
            DockLayoutRenderer.snapAnimToFinal(dock.dockAxisSizeInternal, dock.maxDockIcons, dock.pageItemsInternal())
        }
    }
}
