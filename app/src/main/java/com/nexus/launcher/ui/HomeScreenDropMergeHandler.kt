package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderAppendEngine
import com.nexus.launcher.ui.folder.FolderMergeEngine
import com.nexus.launcher.ui.model.DragSource
import com.nexus.launcher.ui.model.DragState
import kotlinx.coroutines.flow.MutableStateFlow

internal object HomeScreenDropMergeHandler {
    suspend fun handleOccupiedDrop(
        current: DragState.Dragging,
        page: Int,
        col: Int,
        row: Int,
        xFraction: Float,
        yFraction: Float,
        atFraction: Boolean,
        homeScreenDao: HomeScreenDao,
        getAllItems: () -> List<HomeScreenItem>,
        dragState: MutableStateFlow<DragState>,
        cancelDrag: () -> Unit,
        context: Context?,
        triggerFolderGlow: (Long, Boolean) -> Unit
    ): Boolean {
        val packageName = current.item.intent?.component?.packageName
            ?: current.item.intent?.`package`
            ?: return true
        val draggedFolderItemId = current.item.intent?.getLongExtra("dragged_folder_item_id", -1L) ?: -1L
        val draggedFromFolderId = current.item.intent?.getLongExtra("dragged_from_folder_id", -1L) ?: -1L

        val item = HomeScreenItem(
            id = if (draggedFolderItemId != -1L) draggedFolderItemId.toInt() else 0,
            packageName = packageName,
            page = page,
            column = col,
            row = row,
            xFraction = if (atFraction) xFraction else 0f,
            yFraction = if (atFraction) yFraction else 0f,
            itemType = 0
        )

        val targetItem = getAllItems().find {
            it.page == page && it.containerId == -1L &&
            col >= it.column && col < it.column + it.spanX &&
            row >= it.row && row < it.row + it.spanY
        } ?: run {
            com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
            return false
        }

        if (targetItem.id.toLong() == draggedFromFolderId) {
            // Dropped back onto its own source container (box or folder) — no-op / cancel
            com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
            cancelDrag()
            return true
        }

        if (targetItem.itemType == 3 || targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC) {
            com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
            return false // Reject widget/mosaic drop
        }

        com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false

        mergeDraggedOntoTarget(current, item, targetItem, homeScreenDao, context, triggerFolderGlow)
        com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDrop(
            source = "handleOccupiedDrop",
            draggedId = item.id,
            beforeSpanX = item.spanX,
            beforeSpanY = item.spanY,
            beforeCell = "${item.column},${item.row}",
            dropCell = "$col,$row",
            overlap = true,
            occupant = targetItem,
            outcome = "MERGE_OR_APPEND",
            extra = "targetSpan=${targetItem.spanX}x${targetItem.spanY} targetType=${targetItem.itemType}"
        )
        cancelDrag()
        return true
    }

    private suspend fun mergeDraggedOntoTarget(
        drag: DragState.Dragging,
        draggedItem: HomeScreenItem,
        targetItem: HomeScreenItem,
        homeScreenDao: HomeScreenDao,
        context: Context?,
        triggerFolderGlow: (Long, Boolean) -> Unit
    ) {
        val resolvedDragged = resolveDraggedItemForMerge(drag, draggedItem, homeScreenDao)
        if (targetItem.itemType == 0 || targetItem.itemType == 2) {
            FolderMergeEngine.mergeAppsIntoFolder(resolvedDragged, targetItem, homeScreenDao, context)
        } else if (targetItem.itemType == 1) {
            val folderId = targetItem.id.toLong()
            val success = FolderAppendEngine.appendAppToFolder(resolvedDragged, folderId, homeScreenDao, context)
            triggerFolderGlow(folderId, success)
        } else if (targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX) {
            val boxId = targetItem.id.toLong()
            val config = com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxConfig.parse(targetItem.folderConfigJson)
            val maxSlots = config.gridCols * config.gridRows
            val success = com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxOperations.appendItemToBox(homeScreenDao, boxId, maxSlots, resolvedDragged)
            (context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.triggerBoxDropPulse(boxId.toInt(), success)
        } else if (targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX) {
            val boxId = targetItem.id.toLong()
            val config = com.nexus.launcher.ui.widgets.appbox.AppBoxConfig.parse(targetItem.folderConfigJson)
            val maxSlots = config.gridCols * config.gridRows
            val success = com.nexus.launcher.ui.widgets.appbox.AppBoxOperations.appendItemToBox(homeScreenDao, boxId, maxSlots, resolvedDragged)
            (context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.triggerBoxDropPulse(boxId.toInt(), success)
        }
    }

    private suspend fun resolveDraggedItemForMerge(
        drag: DragState.Dragging,
        draggedItem: HomeScreenItem,
        homeScreenDao: HomeScreenDao
    ): HomeScreenItem {
        val fromDrawer = drag.sourceType == DragSource.DRAWER
        if (!fromDrawer && draggedItem.id != 0) return draggedItem
        val drawerAppItem = HomeScreenItem(
            packageName = draggedItem.packageName,
            page = -1,
            column = 0,
            row = 0,
            itemType = draggedItem.itemType
        )
        homeScreenDao.insertItem(drawerAppItem)
        val inserted = homeScreenDao.getAllItemsDebug()
            .filter {
                it.packageName == draggedItem.packageName &&
                    it.page == -1 &&
                    it.containerId == -1L &&
                    (it.itemType == 0 || it.itemType == 2)
            }
            .maxByOrNull { it.id }
            ?: return draggedItem
        return drawerAppItem.copy(id = inserted.id)
    }
}
