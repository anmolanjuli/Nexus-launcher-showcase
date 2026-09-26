package com.nexus.launcher.ui.widgets.shortcutbox

import android.content.Context
import android.content.pm.ShortcutInfo
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.DrawEngineLayout
import com.nexus.launcher.ui.canvas.HomeGridBounds
import com.nexus.launcher.ui.model.DisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Database mutations for Shortcut / App Box container and its members. */
object ShortcutBoxOperations {

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
                ShortcutBoxPlacement.newItem(page, col, row, spanX, spanY, xFraction, yFraction)
            )
        }
    }

    fun addShortcutToSlot(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        boxId: Long,
        slotIndex: Int,
        shortcut: ShortcutInfo
    ) {
        scope.launch(Dispatchers.IO) {
            val existing = dao.getItemsInFolderSync(boxId).firstOrNull { it.column == slotIndex }
            if (existing != null) {
                dao.removeItemById(existing.id)
            }
            val shortcutJson = "{\"shortcutId\":\"${shortcut.id}\"}"
            val item = HomeScreenItem(
                packageName = shortcut.`package`,
                page = -1,
                column = slotIndex,
                row = 0,
                itemType = HomeItemTypes.SHORTCUT,
                containerId = boxId,
                folderTitle = shortcut.shortLabel?.toString() ?: "",
                folderConfigJson = shortcutJson
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
        scope.launch(Dispatchers.IO) {
            val existing = dao.getItemsInFolderSync(boxId).firstOrNull { it.column == slotIndex }
            if (existing != null) {
                dao.removeItemById(existing.id)
            }
            val pkg = appItem.intent?.`package` ?: appItem.intent?.component?.packageName.orEmpty()
            val item = HomeScreenItem(
                packageName = pkg,
                page = -1,
                column = slotIndex,
                row = 0,
                itemType = HomeItemTypes.APP,
                containerId = boxId,
                folderTitle = appItem.label,
                folderConfigJson = "{}"
            )
            dao.insertItem(item)
        }
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
        config: ShortcutBoxConfig
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

        if (!isAlreadyMember) {
            val isDuplicate = if (draggedItem.itemType == HomeItemTypes.SHORTCUT) {
                val draggedShortcutId = try {
                    org.json.JSONObject(draggedItem.folderConfigJson).optString("shortcutId")
                } catch (_: Exception) { "" }
                existingMembers.any {
                    it.itemType == HomeItemTypes.SHORTCUT &&
                    it.packageName == draggedItem.packageName &&
                    try {
                        org.json.JSONObject(it.folderConfigJson).optString("shortcutId") == draggedShortcutId
                    } catch (_: Exception) { false }
                }
            } else {
                existingMembers.any { it.itemType == HomeItemTypes.APP && it.packageName == draggedItem.packageName }
            }
            if (isDuplicate) return false
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
