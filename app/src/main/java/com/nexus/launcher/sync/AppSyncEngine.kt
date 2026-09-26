package com.nexus.launcher.sync

import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppSyncEngine {
    suspend fun removeGhostIcons(dao: HomeScreenDao, installedPackages: Set<String>) {
        withContext(Dispatchers.IO) {
            val allDbItems = dao.getAllItemsDebug()
            for (item in allDbItems) {
                // Synthetic / non-app rows — never treat as uninstall ghosts
                if (item.itemType == HomeItemTypes.FOLDER) continue
                if (item.itemType == HomeItemTypes.MOSAIC) continue
                if (item.itemType == HomeItemTypes.WIDGET) continue
                if (item.itemType == HomeItemTypes.SHORTCUT_BOX) continue
                if (item.itemType == HomeItemTypes.APP_BOX) continue
                if (item.itemType == HomeItemTypes.LIVE_APP_BOX) continue
                if (item.itemType == HomeItemTypes.SHORTCUT) continue

                if (!installedPackages.contains(item.packageName)) {
                    dao.removeItemById(item.id)
                }
            }
        }
    }
}
