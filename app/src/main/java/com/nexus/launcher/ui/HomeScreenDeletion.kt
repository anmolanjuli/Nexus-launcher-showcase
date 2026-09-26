package com.nexus.launcher.ui

import android.appwidget.AppWidgetHost
import android.content.Context
import android.util.Log
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.ShapeAwareHomeScreenDao
import com.nexus.launcher.ui.widgets.NexusWidgetHost

/**
 * Cascading home-item / page deletion — folder members (page=-1, containerId=folderId)
 * and OS widget host IDs must be cleaned up with the parent row.
 */
internal object HomeScreenDeletion {
    private const val TAG = "HomeScreenDeletion"

    /**
     * Deletes [item] from DB. Real folders (not drawer-folder shortcuts) also delete
     * every row whose containerId matches the folder id, in one DAO call.
     */
    suspend fun deleteItem(dao: HomeScreenDao, item: HomeScreenItem, context: Context? = null) {
        if (item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC && context != null) {
            val host = AppWidgetHost(context, NexusWidgetHost.HOST_ID)
            releaseMosaicChildren(host, item)
        }
        if (item.itemType == 1) {
            val contentsId = item.resolveFolderContentsId()
            if (contentsId == item.id.toLong()) {
                dao.deleteFolderAndContents(contentsId)
            } else {
                // Shortcut pointing at a drawer folder — remove the shortcut only
                dao.removeItemById(item.id)
            }
        } else if (item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX) {
            dao.deleteFolderAndContents(item.id.toLong())
        } else {
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDelete(
                source = "HomeScreenDeletion.deleteItem",
                id = item.id,
                extra = com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(item)
            )
            dao.removeItemById(item.id)
        }
    }

    /**
     * Releases OS widget allocations for widgets on [page], cascades real folders
     * (members live on page=-1), then deletes remaining page rows and shifts pages above.
     */
    suspend fun deletePage(shapeDao: HomeScreenDao, context: Context?, page: Int) {
        // A page is shared by every layout shape: delete by base page, then keep per-shape
        // positions in step (anything a shape had placed on the deleted page is refilled).
        val dao = ShapeAwareHomeScreenDao.baseOf(shapeDao)
        ShapeAwareHomeScreenDao.positionsOf(shapeDao)?.let {
            it.deletePositionsOnPage(page)
            it.shiftPages(page + 1, -1)
        }
        val itemsOnPage = dao.getItemsForPage(page)
        releaseWidgetHostIds(context, itemsOnPage)
        itemsOnPage.forEach { item ->
            if (item.itemType == 1) {
                val contentsId = item.resolveFolderContentsId()
                if (contentsId == item.id.toLong()) {
                    dao.deleteFolderAndContents(contentsId)
                }
            } else if (item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX) {
                dao.deleteFolderAndContents(item.id.toLong())
            }
        }
        dao.deleteItemsOnPage(page)
        dao.getItemsAbovePage(page).forEach { item ->
            dao.updateItemPage(item.id, item.page - 1)
        }
    }

    private fun releaseWidgetHostIds(context: Context?, items: List<HomeScreenItem>) {
        val ctx = context ?: return
        val host = AppWidgetHost(ctx, NexusWidgetHost.HOST_ID)
        items.forEach { item ->
            if (item.appWidgetId != -1) {
                try {
                    host.deleteAppWidgetId(item.appWidgetId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to release appWidgetId=${item.appWidgetId}", e)
                }
            }
            if (item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC) {
                releaseMosaicChildren(host, item)
            }
        }
    }

    private fun releaseMosaicChildren(host: AppWidgetHost, item: HomeScreenItem) {
        val cfg = com.nexus.launcher.ui.widgets.mosaic.MosaicConfig.parse(item.folderConfigJson)
        cfg.allChildren().forEach { child ->
            if (child.appWidgetId != -1) {
                try {
                    host.deleteAppWidgetId(child.appWidgetId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to release mosaic child appWidgetId=${child.appWidgetId}", e)
                }
            }
        }
    }
}
