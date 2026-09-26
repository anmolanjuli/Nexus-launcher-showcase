package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FolderActionEngine {
    private val gson = com.google.gson.Gson()

    suspend fun removeFolder(folderId: Long, dao: HomeScreenDao) {
        withContext(Dispatchers.IO) {
            dao.deleteFolderAndContents(folderId)
        }
    }

    suspend fun saveFolderConfig(
        folderId: Long, 
        newTitle: String, 
        newConfig: com.nexus.launcher.data.FolderConfig, 
        dao: com.nexus.launcher.data.HomeScreenDao
    ) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val json = gson.toJson(newConfig)
            dao.updateFolderConfig(folderId, newTitle, json)
        }
    }

    suspend fun applySort(
        folderId: Long,
        sortMode: Int,
        dao: HomeScreenDao,
        context: android.content.Context
    ) {
        if (sortMode == 0) return // Custom
        
        withContext(Dispatchers.IO) {
            val items = dao.getItemsInFolderSync(folderId)
            val pm = context.packageManager
            
            val sorted = items.sortedWith(Comparator { a, b ->
                val labelA = try { pm.getApplicationLabel(pm.getApplicationInfo(a.packageName, 0)).toString() } catch (e: Exception) { a.packageName }
                val labelB = try { pm.getApplicationLabel(pm.getApplicationInfo(b.packageName, 0)).toString() } catch (e: Exception) { b.packageName }
                if (sortMode == 1) {
                    labelA.compareTo(labelB, ignoreCase = true)
                } else {
                    labelB.compareTo(labelA, ignoreCase = true)
                }
            })
            
            sorted.forEachIndexed { index, item ->
                val row = index / 4
                val col = index % 4
                if (item.row != row || item.column != col) {
                    dao.updateItem(item.copy(row = row, column = col))
                }
            }
        }
    }
}
