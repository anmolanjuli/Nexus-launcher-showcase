package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderRemoveEngine {

    suspend fun removeFromFolder(item: HomeScreenItem, dao: HomeScreenDao) = withContext(Dispatchers.IO) {
        dao.removeItemById(item.id)
    }
}
