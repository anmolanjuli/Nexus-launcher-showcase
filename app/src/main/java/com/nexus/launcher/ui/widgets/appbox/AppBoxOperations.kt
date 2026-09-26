package com.nexus.launcher.ui.widgets.appbox

import android.content.Context
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.HomeGridBounds
import com.nexus.launcher.ui.model.DisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Database mutations for App Box container and its app members. */
object AppBoxOperations {

    fun addBox(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        appContext: Context,
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ) {
        scope.launch(Dispatchers.IO) {
            val (maxCols, maxRows) = HomeGridBounds.liveOrDefault(appContext)
            val (col, row) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                appContext, xFraction, yFraction, spanX, spanY, "{}", maxCols, maxRows
            )
            dao.insertItem(
                AppBoxPlacement.newItem(page, col, row, spanX, spanY, xFraction, yFraction)
            )
        }
    }

    fun addAppToSlot(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        boxId: Long,
        slotIndex: Int,
        packageName: String,
        label: String
    ) {
        scope.launch(Dispatchers.IO) {
            val existing = dao.getItemsInFolderSync(boxId).firstOrNull { it.column == slotIndex }
            if (existing != null) {
                dao.removeItemById(existing.id)
            }
            val item = HomeScreenItem(
                packageName = packageName,
                page = -1,
                column = slotIndex,
                row = 0,
                itemType = HomeItemTypes.APP,
                containerId = boxId,
                folderTitle = label,
                folderConfigJson = "{}"
            )
            dao.insertItem(item)
        }
    }

    fun addAppToSlot(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        boxId: Long,
        slotIndex: Int,
        appItem: DisplayItem
    ) {
        val pkg = appItem.intent?.`package` ?: appItem.intent?.component?.packageName.orEmpty()
        addAppToSlot(scope, dao, boxId, slotIndex, pkg, appItem.label)
    }

    fun removeMember(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        memberId: Int
    ) {
        scope.launch(Dispatchers.IO) {
            dao.removeItemById(memberId)
        }
    }

    fun updateConfig(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        boxItem: HomeScreenItem,
        config: AppBoxConfig
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(boxItem.id) ?: boxItem
            dao.updateItem(fresh.copy(folderConfigJson = config.toJson()))
        }
    }

    suspend fun appendItemToBox(
        dao: HomeScreenDao,
        boxId: Long,
        maxSlots: Int,
        draggedItem: HomeScreenItem,
        preferredSlot: Int? = null
    ): Boolean {
        val existingMembers = dao.getItemsInFolderSync(boxId)
        val isAlreadyMember = existingMembers.any { it.id == draggedItem.id && draggedItem.id > 0 }

        if (!isAlreadyMember && existingMembers.any { it.packageName == draggedItem.packageName }) {
            return false
        }

        val occupiedSlots = existingMembers.filter { it.id != draggedItem.id }.map { it.column }.toSet()
        val targetSlot = if (preferredSlot != null && preferredSlot in 0 until maxSlots && preferredSlot !in occupiedSlots) {
            preferredSlot
        } else {
            (0 until maxSlots).firstOrNull { it !in occupiedSlots } ?: return false
        }

        if (isAlreadyMember) {
            val fresh = dao.getItemById(draggedItem.id) ?: draggedItem
            dao.updateItem(fresh.copy(column = targetSlot))
            return true
        }

        val newItem = draggedItem.copy(
            id = 0,
            page = -1,
            column = targetSlot,
            row = 0,
            containerId = boxId
        )
        dao.insertItem(newItem)
        if (draggedItem.id > 0 && draggedItem.containerId != boxId) {
            dao.removeItemById(draggedItem.id)
        }
        return true
    }
}
