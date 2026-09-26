package com.nexus.launcher.ui.widgets

import android.view.View
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared helper for handling member drop events in AppBoxView and ShortcutBoxView.
 * Intercepts drops that occur within the widget boundaries to reorder or swap slots
 * internally, preventing unintended duplicate insertions on the home screen canvas.
 */
object BoxMemberDropHelper {

    fun handleMemberDrop(
        hostView: View,
        rawX: Float,
        rawY: Float,
        boxId: Long,
        draggingMember: HomeScreenItem?,
        getSlotAt: (localX: Float, localY: Float) -> Int?,
        dao: HomeScreenDao,
        scope: CoroutineScope
    ): Boolean {
        if (draggingMember == null) return false
        val loc = IntArray(2)
        hostView.getLocationOnScreen(loc)
        val localX = rawX - loc[0]
        val localY = rawY - loc[1]
        val isInside = localX >= 0f && localX <= hostView.width &&
            localY >= 0f && localY <= hostView.height

        if (!isInside) {
            return false
        }

        val targetSlot = getSlotAt(localX, localY)
        if (targetSlot != null && targetSlot != draggingMember.column) {
            scope.launch(Dispatchers.IO) {
                val members = dao.getItemsInFolderSync(boxId)
                val existingAtTarget = members.firstOrNull { it.column == targetSlot }
                if (existingAtTarget != null) {
                    // Swap slot positions
                    dao.updateItem(existingAtTarget.copy(column = draggingMember.column))
                    dao.updateItem(draggingMember.copy(column = targetSlot))
                } else {
                    // Move to the empty slot
                    dao.updateItem(draggingMember.copy(column = targetSlot))
                }
            }
        }
        return true
    }
}
