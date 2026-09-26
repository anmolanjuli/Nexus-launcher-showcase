package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderAppendEngine {
    suspend fun appendAppToFolder(app: HomeScreenItem, folderId: Long, dao: HomeScreenDao, context: android.content.Context? = null): Boolean = withContext(Dispatchers.IO) {
        val contents = dao.getItemsInFolderSync(folderId)
        
        if (contents.any { it.packageName == app.packageName }) {
            return@withContext false
        }
        
        val nextIndex = contents.size

        // Determine if this app is already in the database
        val dbItems = dao.getAllItemsDebug()
        val existsInDb = dbItems.any { it.id == app.id }

        val finalApp = app.copy(
            id = if (existsInDb) app.id else 0,
            containerId = folderId,
            page = -1,
            row = nextIndex / 4,
            column = nextIndex % 4,
            itemType = 0
        )

        if (existsInDb) {
            dao.updateItem(finalApp) // Dragged from Homescreen
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDb(
                source = "FolderAppendEngine",
                id = finalApp.id,
                after = finalApp,
                extra = "movedIntoFolder=$folderId"
            )
        } else {
            dao.insertItem(finalApp) // Dragged from Drawer or Added via Picker
        }
        return@withContext true
    }
}
