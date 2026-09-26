package com.nexus.launcher.ui.folder

import android.content.Intent
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject

object FolderDragHandoffHelper {

    const val FOLDER_OPEN_ACTION = "nexus.folder.OPEN"
    const val FOLDER_ID_EXTRA = "folderId"

    fun folderIdFromIntent(intent: Intent?): Long? {
        if (intent?.action != FOLDER_OPEN_ACTION) return null
        return intent.getLongExtra(FOLDER_ID_EXTRA, -1L).takeIf { it > 0L }
    }

    suspend fun moveFolderToDock(
        folderId: Long,
        dockColumn: Int,
        dao: HomeScreenDao
    ) = moveFolderToNewGrid(
        folderId = folderId,
        newPage = HomeScreenViewModel.DOCK_CONTAINER,
        newRow = 0,
        newColumn = dockColumn,
        dao = dao
    )

    suspend fun moveFolderToNewGrid(
        folderId: Long,
        newPage: Int,
        newRow: Int,
        newColumn: Int,
        dao: HomeScreenDao,
        xFraction: Float = 0f,
        yFraction: Float = 0f
    ) = withContext(NonCancellable) {
        val dbItems = dao.getAllItemsDebug()
        val folderItem = dbItems.find { it.id.toLong() == folderId && it.itemType == 1 }
            ?: return@withContext

        if (folderItem.page == -2) {
            deepCloneDrawerFolder(folderItem, newPage, newRow, newColumn, dao, xFraction, yFraction)
            return@withContext
        }

        val updatedFolder = folderItem.copy(
            page = newPage,
            row = newRow,
            column = newColumn,
            xFraction = xFraction,
            yFraction = yFraction,
            containerId = -1L
        )
        dao.updateItem(updatedFolder)
    }

    private suspend fun deepCloneDrawerFolder(
        source: HomeScreenItem,
        newPage: Int,
        newRow: Int,
        newColumn: Int,
        dao: HomeScreenDao,
        xFraction: Float,
        yFraction: Float
    ) {
        val originalFolderId = source.id.toLong()
        val contents = dao.getItemsInFolderSync(originalFolderId)
        val configJson = copyConfigWithoutReference(source.folderConfigJson)

        val newFolder = HomeScreenItem(
            packageName = source.packageName.ifBlank { "folder_${System.currentTimeMillis()}" },
            page = newPage,
            row = newRow,
            column = newColumn,
            xFraction = xFraction,
            yFraction = yFraction,
            itemType = 1,
            containerId = -1L,
            folderTitle = source.folderTitle,
            folderConfigJson = configJson
        )
        // Was: insertItem() followed by re-querying the whole table for a row matching
        // (page, row, column) and picking the max id — a TOCTOU race. If the same drop is
        // delivered twice in quick succession (a duplicate touch-up/retry), both calls could
        // pass this same position-matching lookup and attach contents to whichever insert
        // happened to have the higher id, leaving a second, empty "clone" folder header behind
        // with no children — rendering as a flat, blank plate on the home screen (no icon grid
        // ever gets drawn for a folder with zero contents). insertItemAndGetId sidesteps this
        // entirely: no re-query, no ambiguity about which row is "the" new one.
        val newFolderId = dao.insertItemAndGetId(newFolder)

        contents.forEachIndexed { index, child ->
            dao.insertItem(
                HomeScreenItem(
                    packageName = child.packageName,
                    page = -1,
                    row = index / 4,
                    column = index % 4,
                    itemType = 0,
                    containerId = newFolderId
                )
            )
        }
    }

    private fun copyConfigWithoutReference(configJson: String): String {
        return try {
            val json = JSONObject(configJson.ifBlank { "{}" })
            json.remove("referenceFolderId")
            json.toString()
        } catch (_: Exception) {
            configJson
        }
    }
}
