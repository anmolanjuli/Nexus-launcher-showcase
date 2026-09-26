package com.nexus.launcher.ui.folder

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.HomeGridBounds
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

object FolderMergeEngine {

    private fun getNumberedFolderTitle(context: Context?, number: Int): String {
        return context?.getString(R.string.folder_numbered_name, number) ?: "Folder $number"
    }

    private fun findFirstEmptySlotSpanAware(
        page: Int,
        allItems: List<HomeScreenItem>,
        visualPositions: Map<Int, Triple<Int, Int, Int>>,
        cols: Int,
        rows: Int,
        context: android.content.Context?
    ): Pair<Int, Int> {
        for (row in rows - 1 downTo 0) {
            for (col in cols - 1 downTo 0) {
                val occupied = if (context != null) {
                    com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                        context, allItems, page, col, row, visualPositions
                    ) != null
                } else {
                    // Rare null-context path: occupancy only (no page-type gate available)
                    allItems.any { item ->
                        val p = visualPositions[item.id] ?: Triple(item.page, item.column, item.row)
                        p.first == page && p.second == col && p.third == row &&
                            item.containerId == -1L &&
                            (item.itemType == 0 || item.itemType == 1 || item.itemType == 2 || item.itemType == 3 || item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC)
                    }
                }
                if (!occupied) return col to row
            }
        }
        return cols - 1 to rows - 1
    }

    suspend fun mergeAppsIntoFolder(
        draggedApp: HomeScreenItem,
        targetApp: HomeScreenItem,
        dao: HomeScreenDao,
        context: android.content.Context? = null
    ) = withContext(NonCancellable) {

        if (com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder) {
            return@withContext
        }

        if (draggedApp.packageName == targetApp.packageName) {
            context?.let {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(it, it.getString(com.nexus.launcher.R.string.toast_app_already_in_folder), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            return@withContext
        }

        val allItems = dao.getAllItemsDebug()
        val existingFolderCount = allItems.count { it.itemType == 1 }

        val remainingItems = allItems.filter { it.id != draggedApp.id && it.id != targetApp.id }
        
        val finalCol = targetApp.column
        val finalRow = targetApp.row
        val isOccupied = remainingItems.any { 
            it.page == targetApp.page && 
            it.containerId == -1L && 
            finalCol >= it.column && finalCol < it.column + it.spanX && 
            finalRow >= it.row && finalRow < it.row + it.spanY 
        }
        if (isOccupied) return@withContext

        var xF = 0f
        var yF = 0f
        if (context != null) {
            val (fracX, fracY) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                finalCol, finalRow, HomeGridBounds.liveOrDefault(context).first, HomeGridBounds.liveOrDefault(context).second, context, 1, 1
            )
            xF = fracX
            yF = fracY
        }

        val folderTag = "folder_${System.currentTimeMillis()}"
        val configJson = FolderConfigCodec.toJson(FolderConfig())
        val newFolder = HomeScreenItem(
            packageName = folderTag,
            page = targetApp.page,
            row = finalRow,
            column = finalCol,
            itemType = 1,
            containerId = -1L,
            xFraction = xF,
            yFraction = yF,
            folderTitle = getNumberedFolderTitle(context, existingFolderCount + 1),
            folderConfigJson = configJson
        )

        dao.insertItem(newFolder)
        val folderId = dao.getAllItemsDebug()
            .firstOrNull { it.packageName == folderTag && it.itemType == 1 }
            ?.id?.toLong() ?: return@withContext

        val updatedTarget = targetApp.copy(
            containerId = folderId,
            page = -1,
            row = 0,
            column = 0,
            itemType = 0
        )

        val updatedDragged = draggedApp.copy(
            containerId = folderId,
            page = -1,
            row = 0,
            column = 1,
            itemType = 0
        )

        dao.updateItem(updatedTarget)
        dao.updateItem(updatedDragged)
        // TEMPORARY — HomeGridDropDiag; remove with the diag object.
        com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDrop(
            source = "FolderMergeEngine",
            draggedId = draggedApp.id,
            beforeSpanX = draggedApp.spanX,
            beforeSpanY = draggedApp.spanY,
            beforeCell = "${draggedApp.column},${draggedApp.row}",
            dropCell = "$finalCol,$finalRow",
            overlap = true,
            occupant = targetApp,
            outcome = "MERGED_INTO_NEW_FOLDER",
            extra = "folderId=$folderId draggedNowPage=-1 targetId=${targetApp.id}"
        )
    }

    suspend fun createEmptyFolder(
        page: Int,
        dao: HomeScreenDao,
        targetCol: Int = -1,
        targetRow: Int = -1,
        context: android.content.Context? = null,
        visualPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap()
    ) = withContext(NonCancellable) {
        val allItems = dao.getAllItemsDebug()
        val existingFolderCount = allItems.count { it.itemType == 1 }

        var finalCol = targetCol
        var finalRow = targetRow

        if (finalCol != -1 && finalRow != -1) {
            val isOccupied = context != null && com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                context, allItems, page, finalCol, finalRow, visualPositions
            ) != null
            if (isOccupied) {
                val searchRes = findFirstEmptySlotSpanAware(page, allItems, visualPositions, HomeGridBounds.liveOrDefault(context).first, HomeGridBounds.liveOrDefault(context).second, context)
                finalCol = searchRes.first
                finalRow = searchRes.second
            }
        } else {
            val searchRes = findFirstEmptySlotSpanAware(page, allItems, visualPositions, HomeGridBounds.liveOrDefault(context).first, HomeGridBounds.liveOrDefault(context).second, context)
            finalCol = searchRes.first
            finalRow = searchRes.second
        }

        var xF = 0f
        var yF = 0f
        if (context != null) {
            val (fracX, fracY) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                finalCol, finalRow, HomeGridBounds.liveOrDefault(context).first, HomeGridBounds.liveOrDefault(context).second, context, 1, 1
            )
            xF = fracX
            yF = fracY
        }

        val emptyFolder = HomeScreenItem(
            packageName = "folder_${System.currentTimeMillis()}",
            page = page,
            row = finalRow,
            column = finalCol,
            itemType = 1,
            containerId = -1L,
            xFraction = xF,
            yFraction = yF,
            folderTitle = getNumberedFolderTitle(context, existingFolderCount + 1),
            folderConfigJson = FolderConfigCodec.toJson(FolderConfig())
        )
        dao.insertItem(emptyFolder)
    }

    /**
     * Creates a drawer group folder (page -2) containing [packages] as
     * members. Drawer folders appear as entries at the top of the drawer
     * list (see MainViewModel.filteredDrawerItems); members are ordinary
     * container rows (page -1, containerId = folderId) like home folders.
     */
    suspend fun createDrawerFolder(
        packages: List<String>,
        dao: HomeScreenDao,
        context: Context? = null
    ): Long? = withContext(NonCancellable) {
        if (packages.size < 2) return@withContext null
        val existingFolderCount = dao.getAllItemsDebug().count { it.itemType == 1 }

        val folderTag = "folder_${System.currentTimeMillis()}"
        dao.insertItem(
            HomeScreenItem(
                packageName = folderTag,
                page = -2,
                row = 0,
                column = 0,
                itemType = 1,
                containerId = -1L,
                folderTitle = getNumberedFolderTitle(context, existingFolderCount + 1),
                folderConfigJson = FolderConfigCodec.toJson(FolderConfig())
            )
        )
        val folderId = dao.getAllItemsDebug()
            .firstOrNull { it.packageName == folderTag && it.itemType == 1 }
            ?.id?.toLong() ?: return@withContext null

        packages.forEachIndexed { index, pkg ->
            dao.insertItem(
                HomeScreenItem(
                    packageName = pkg,
                    page = -1,
                    row = 0,
                    column = index,
                    itemType = 0,
                    containerId = folderId
                )
            )
        }
        folderId
    }

    suspend fun mergeMultiple(
        items: List<HomeScreenItem>,
        page: Int,
        dao: HomeScreenDao,
        context: android.content.Context? = null
    ) = withContext(NonCancellable) {
        if (items.size < 2) return@withContext
        
        val allItems = dao.getAllItemsDebug()
        val existingFolderCount = allItems.count { it.itemType == 1 }
        
        // Exclude the items that will be moved to the folder when finding available space
        val itemIdsToMove = items.map { it.id }.toSet()
        val remainingItems = allItems.filter { it.id !in itemIdsToMove }
        
        val searchRes = findFirstEmptySlotSpanAware(page, remainingItems, emptyMap(), HomeGridBounds.liveOrDefault(context).first, HomeGridBounds.liveOrDefault(context).second, context)
        val finalCol = searchRes.first
        val finalRow = searchRes.second

        var xF = 0f
        var yF = 0f
        if (context != null) {
            val (fracX, fracY) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                finalCol, finalRow, HomeGridBounds.liveOrDefault(context).first, HomeGridBounds.liveOrDefault(context).second, context, 1, 1
            )
            xF = fracX
            yF = fracY
        }

        val folderTag = "folder_${System.currentTimeMillis()}"
        val newFolder = HomeScreenItem(
            packageName = folderTag,
            page = page,
            row = finalRow,
            column = finalCol,
            itemType = 1,
            containerId = -1L,
            xFraction = xF,
            yFraction = yF,
            folderTitle = getNumberedFolderTitle(context, existingFolderCount + 1),
            folderConfigJson = FolderConfigCodec.toJson(FolderConfig())
        )
        
        dao.insertItem(newFolder)
        
        val folderId = dao.getAllItemsDebug()
            .firstOrNull { it.packageName == folderTag && it.itemType == 1 }
            ?.id?.toLong() ?: return@withContext
            
        items.forEachIndexed { index, item ->
            val updatedItem = item.copy(
                containerId = folderId,
                page = -1,
                column = index,
                row = 0,
                itemType = 0
            )
            dao.updateItem(updatedItem)
        }
    }
}
