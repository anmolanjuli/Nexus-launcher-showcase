package com.nexus.launcher.ui

import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutDropHandler
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import com.nexus.launcher.ui.folder.FolderAppendEngine
import com.nexus.launcher.ui.folder.FolderDragHandoffHelper
import com.nexus.launcher.ui.folder.FolderMergeEngine
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.DragSource
import com.nexus.launcher.ui.model.DragState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class HomeScreenDropHandler(
    private val homeScreenDao: HomeScreenDao,
    private val dockSettingsRepository: DockSettingsRepository,
    private val scope: CoroutineScope,
    private val updateAllItems: (List<HomeScreenItem>) -> Unit,
    private val getAllItems: () -> List<HomeScreenItem>,
    private val dragState: MutableStateFlow<DragState>,
    private val cancelDrag: () -> Unit,
    private val context: android.content.Context? = null,
    private val triggerFolderGlow: (Long, Boolean) -> Unit
) {
    private val db = HomeScreenDropDbSupport(homeScreenDao, dockSettingsRepository, updateAllItems)

    companion object {
        fun showWidgetRejection(context: android.content.Context, view: android.view.View? = null) {
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            val toast = android.widget.Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_drop_not_allowed), android.widget.Toast.LENGTH_SHORT)
            val dp = context.resources.displayMetrics.density
            toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, (100 * dp).toInt())
            toast.show()
        }
    }

    fun dropItem(page: Int, col: Int, row: Int) {
        runDrop(page, col, row, atFraction = false)
    }

    fun dropItemAtFraction(page: Int, xFraction: Float, yFraction: Float, col: Int, row: Int) {
        runDrop(page, col, row, xFraction, yFraction, atFraction = true)
    }

    private fun runDrop(
        page: Int,
        col: Int,
        row: Int,
        xFraction: Float = 0f,
        yFraction: Float = 0f,
        atFraction: Boolean = false
    ) {
        val current = dragState.value as? DragState.Dragging ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val dbItems = homeScreenDao.getAllItemsDebug()
                val occupant = if (context != null) {
                    com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                        context, dbItems, page, col, row, emptyMap()
                    )
                } else null
                // TEMPORARY — HomeGridDropDiag; remove with the diag object.
                com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDrop(
                    source = "runDrop",
                    draggedId = 0,
                    beforeSpanX = 1,
                    beforeSpanY = 1,
                    beforeCell = "n/a",
                    dropCell = "$col,$row",
                    overlap = occupant != null,
                    occupant = occupant,
                    outcome = if (occupant != null) "OCCUPIED_NO_EXCLUDE" else "EMPTY_WILL_INSERT",
                    extra = "visualPositions=emptyMap excludeIds=none pkg=${current.item.intent?.component?.packageName}"
                )
                if (occupant != null) {
                    if (!HomeScreenDropMergeHandler.handleOccupiedDrop(current, page, col, row, xFraction, yFraction, atFraction, homeScreenDao, getAllItems, dragState, cancelDrag, context, triggerFolderGlow)) {
                        withContext(Dispatchers.Main) {
                            context?.let { showWidgetRejection(it, null) }
                            cancelDrag()
                        }
                    }
                    return@launch
                }
                if (atFraction && current.item.intent?.action == "nexus.folder.OPEN") {
                    val folderId = current.item.intent.getLongExtra("folderId", -1L)
                    if (folderId != -1L) {
                        FolderDragHandoffHelper.moveFolderToNewGrid(
                            folderId, page, row, col, homeScreenDao, xFraction, yFraction
                        )
                        db.refreshAllItemsFromDb()
                    }
                    dragState.value = DragState.Dropping(current.item, page, col, row)
                    return@launch
                }
                val packageName = current.item.intent?.component?.packageName
                    ?: current.item.intent?.`package`
                    ?: return@launch
                val draggedFolderItemId = current.item.intent?.getLongExtra("dragged_folder_item_id", -1L) ?: -1L
                val draggedFromFolderId = current.item.intent?.getLongExtra("dragged_from_folder_id", -1L) ?: -1L
                
                if (draggedFolderItemId != -1L) {
                    val existingItem = homeScreenDao.getItemById(draggedFolderItemId.toInt())
                    if (existingItem != null) {
                        val updatedItem = existingItem.copy(
                            page = page,
                            column = col,
                            row = row,
                            xFraction = if (atFraction) xFraction else 0f,
                            yFraction = if (atFraction) yFraction else 0f,
                            containerId = -1L
                        )
                        homeScreenDao.updateItem(updatedItem)
                        
                        if (draggedFromFolderId != -1L) {
                            val container = homeScreenDao.getItemById(draggedFromFolderId.toInt())
                            val isBox = container?.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || container?.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX
                            if (!isBox) {
                                val remaining = homeScreenDao.getItemsInFolderSync(draggedFromFolderId)
                                if (remaining.isEmpty()) {
                                    homeScreenDao.deleteFolderAndContents(draggedFromFolderId)
                                    withContext(Dispatchers.Main) {
                                        com.nexus.launcher.ui.folder.FolderWindowManager.dismissFolder()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        com.nexus.launcher.ui.folder.FolderWindowManager.activeCard?.context?.let { ctx ->
                                            com.nexus.launcher.ui.folder.FolderWindowManager.refreshOpenWindowIfShowing(ctx)
                                        }
                                    }
                                }
                            }
                        }
                        db.refreshAllItemsFromDb()
                        dragState.value = DragState.Dropping(current.item, page, col, row)
                        return@launch
                    }
                }

                val item = if (atFraction) {
                    HomeScreenItem(
                        packageName = packageName,
                        page = page,
                        column = col,
                        row = row,
                        xFraction = xFraction,
                        yFraction = yFraction
                    )
                } else {
                    HomeScreenItem(
                        packageName = packageName,
                        page = page,
                        column = col,
                        row = row
                    )
                }
                homeScreenDao.insertItem(item)
                val inserted = homeScreenDao.getAllItemsDebug()
                    .filter { it.packageName == packageName && it.page == page && it.column == col && it.row == row }
                    .maxByOrNull { it.id }
                com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDb(
                    source = "runDrop.insert",
                    id = inserted?.id ?: -1,
                    after = inserted,
                    extra = "newRow=true"
                )
                dragState.value = DragState.Dropping(current.item, page, col, row)
            } finally {
                com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
            }
        }
    }

    fun moveItemToDock(item: HomeScreenItem, requestedSlot: Int, dockPage: Int = 0) {
        val all = getAllItems()
        val currentDock = all.filter { it.page == HomeScreenViewModel.DOCK_CONTAINER }
        val optimisticDock = DockItemMover.computeOptimisticReorder(
            currentDock, item, requestedSlot, HomeScreenViewModel.maxDockIcons
        )
        DockLayoutRenderer.commitItems(optimisticDock)
        val isHomeDrag = item.page >= 0 && item.id > 0
        val remaining = all.filter { it.page != HomeScreenViewModel.DOCK_CONTAINER && (!isHomeDrag || it.id != item.id) }
        updateAllItems(remaining + optimisticDock)
        scope.launch(Dispatchers.IO) { moveItemToDockInternal(item, requestedSlot) }
    }

    fun moveItemToDock(item: DisplayItem, slotIndex: Int, dockPage: Int = 0) {
        FolderDragHandoffHelper.folderIdFromIntent(item.intent)?.let { folderId ->
            scope.launch(Dispatchers.IO) {
                FolderDragHandoffHelper.moveFolderToDock(folderId, slotIndex, homeScreenDao)
                db.refreshAllItemsFromDb()
            }
            return
        }
        val cleanPackage = item.intent?.component?.packageName ?: item.intent?.`package` ?: return
        val existing = getAllItems().find {
            it.page == HomeScreenViewModel.DOCK_CONTAINER && it.packageName == cleanPackage
        }
        val dockItem = existing?.copy(packageName = cleanPackage)
            ?: HomeScreenItem(packageName = cleanPackage, page = -1, column = 0, row = 0)
        moveItemToDock(dockItem, slotIndex, dockPage)
    }

    private suspend fun moveItemToDockInternal(item: HomeScreenItem, requestedSlot: Int) {
        val oldPage = item.page
        val oldCol = item.column
        val oldRow = item.row
        val oldXF = item.xFraction
        val oldYF = item.yFraction
        val movedId = item.id
        DockItemMover.moveItemToDock(
            homeScreenDao,
            homeScreenDao.getAllItemsDebug(),
            item,
            requestedSlot,
            HomeScreenViewModel.maxDockIcons
        ) { merged -> updateAllItems(merged) }
        if (oldPage != HomeScreenViewModel.DOCK_CONTAINER && oldPage >= 0) {
            db.clearGhostCell(oldPage, oldCol, oldRow, movedId)
            db.refreshAllItemsFromDb()
        }
    }

    fun bridgeCanvasHomeToDockDrop(item: HomeScreenItem, dock: DockLayout, dropXLocal: Float) {
        scope.launch(Dispatchers.IO) {
            DockLayoutDropHandler.handleCanvasDrop(
                dock, dropXLocal, item, homeScreenDao, scope
            ) { merged -> updateAllItems(merged) }
        }
    }

    fun deleteItem(item: HomeScreenItem) {
        com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDelete(
            source = "HomeScreenDropHandler.deleteItem",
            id = item.id,
            extra = com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(item)
        )
        val wasInDock = item.page == HomeScreenViewModel.DOCK_CONTAINER
        updateAllItems(getAllItems().filter { it.id != item.id })
        scope.launch(Dispatchers.IO) {
            HomeScreenDeletion.deleteItem(homeScreenDao, item, context)
            if (wasInDock) db.reindexDockCollapse()
        }
    }

    fun removeFromHomeScreenById(id: Int) {
        getAllItems().firstOrNull { it.id == id }?.let { deleteItem(it) }
    }

    fun updateItemPosition(id: Int, page: Int, xFraction: Float, yFraction: Float, col: Int, row: Int) {
        val currentList = getAllItems().toMutableList()
        val index = currentList.indexOfFirst { it.id == id }
        if (index == -1) return
        val oldItem = currentList[index]
        val oldPage = oldItem.page
        val oldCol = oldItem.column
        val oldRow = oldItem.row
        val wasInDock = oldItem.page == HomeScreenViewModel.DOCK_CONTAINER
        val isNowInWorkspace = page != HomeScreenViewModel.DOCK_CONTAINER
        val updated = oldItem.copy(
            page = page,
            column = col,
            row = row,
            xFraction = xFraction,
            yFraction = yFraction
        )
        currentList[index] = updated
        updateAllItems(currentList)
        val willClearGhost = (oldPage != page || oldCol != col || oldRow != row) &&
            oldPage != HomeScreenViewModel.DOCK_CONTAINER
        // TEMPORARY — HomeGridDropDiag; remove with the diag object.
        com.nexus.launcher.ui.canvas.HomeGridDropDiag.logPositionPersist(
            trigger = "updateItemPosition",
            mover = oldItem,
            newPage = page,
            newCol = col,
            newRow = row,
            allItems = getAllItems(),
            willClearGhost = willClearGhost
        )

        scope.launch(Dispatchers.IO) {
            homeScreenDao.updateItem(updated)
            if (willClearGhost) {
                db.clearGhostCell(oldPage, oldCol, oldRow, id)
            }
            val after = homeScreenDao.getItemById(id)
            // TEMPORARY — HomeGridDropDiag; remove with the diag object.
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDb(
                source = "updateItemPosition",
                id = id,
                after = after,
                extra = "oldCell=$oldPage,$oldCol,$oldRow newCell=$page,$col,$row " +
                    "spanBefore=${oldItem.spanX}x${oldItem.spanY}"
            )
            db.refreshAllItemsFromDb()
            if (wasInDock && isNowInWorkspace) db.reindexDockCollapse()
        }
    }

    /** Commits the exact app target that received the drag hover preview. */
    fun mergeAppsAtHoverTarget(draggedItem: HomeScreenItem, targetApp: HomeScreenItem) {
        scope.launch(Dispatchers.IO) {
            val resolvedDragged = if (draggedItem.id != 0) {
                draggedItem
            } else {
                val drawerAppItem = HomeScreenItem(
                    packageName = draggedItem.packageName,
                    page = -1,
                    column = 0,
                    row = 0,
                    itemType = draggedItem.itemType
                )
                homeScreenDao.insertItem(drawerAppItem)
                homeScreenDao.getAllItemsDebug()
                    .filter {
                        it.packageName == draggedItem.packageName &&
                            it.page == -1 &&
                            it.containerId == -1L &&
                            (it.itemType == 0 || it.itemType == 2)
                    }
                    .maxByOrNull { it.id }
                    ?: return@launch
            }
            FolderMergeEngine.mergeAppsIntoFolder(
                resolvedDragged, targetApp, homeScreenDao, context
            )
            db.refreshAllItemsFromDb()
        }
    }

    fun updateWidgetBounds(id: Int, spanX: Int, spanY: Int, xFraction: Float, yFraction: Float) {
        HomeScreenDropOperations.updateWidgetBounds(
            id, spanX, spanY, xFraction, yFraction, context,
            getAllItems, updateAllItems, homeScreenDao, scope
        )
    }

    fun pinToHome(packageName: String, page: Int, col: Int, row: Int) {
        HomeScreenDropOperations.pinToHome(packageName, page, col, row, context, homeScreenDao, scope)
    }

    fun removeFromHomeScreen(packageName: String, page: Int) {
        HomeScreenDropOperations.removeFromHomeScreen(packageName, page, context, homeScreenDao, scope)
    }

    fun deletePage(page: Int, totalPages: Int, onComplete: () -> Unit) {
        HomeScreenDropOperations.deletePage(page, context, homeScreenDao, scope, onComplete)
    }
}
