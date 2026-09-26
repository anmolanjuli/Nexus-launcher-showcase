package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.model.SelectionState

/**
 * Batch drag for home-screen multi-select: moves every selected icon/folder together,
 * packing them as a tight group at the finger drop point.
 */
class HomeScreenMultiDragHandler {

    data class DragSnapshot(
        val anchorId: Int,
        val memberIds: Set<Int>,
        val originById: Map<Int, Triple<Int, Int, Int>>
    )

    var isActive: Boolean = false
        private set
  var dragX: Float = 0f
        private set
    var dragY: Float = 0f
        private set

    private var snapshot: DragSnapshot? = null

    fun tryStart(view: LauncherCanvasView, itemId: Int): Boolean {
        val state = view.selectionState as? SelectionState.Selecting ?: return false
        if (itemId !in state.selectedIds || state.selectedIds.isEmpty()) return false
        val originById = mutableMapOf<Int, Triple<Int, Int, Int>>()
        for (id in state.selectedIds) {
            val item = view.homeScreenItems.firstOrNull { it.id == id } ?: continue
            val pos = view.fractionDerivedPositions[id]
                ?: Triple(item.page, item.column, item.row)
            originById[id] = pos
        }
        if (originById.isEmpty()) return false
        snapshot = DragSnapshot(itemId, state.selectedIds, originById)
        isActive = true
        return true
    }

    fun updatePosition(x: Float, y: Float) {
        if (!isActive) return
        dragX = x
        dragY = y
    }

    fun anchorId(): Int? = snapshot?.anchorId

    fun anchorItem(view: LauncherCanvasView): HomeScreenItem? {
        val snap = snapshot ?: return null
        return view.homeScreenItems.firstOrNull { it.id == snap.anchorId }
    }

    fun memberIds(): Set<Int> = snapshot?.memberIds ?: emptySet()

    fun originPositions(): Map<Int, Triple<Int, Int, Int>> = snapshot?.originById ?: emptyMap()

    fun cancel() {
        isActive = false
        snapshot = null
    }

    fun finish() {
        cancel()
    }
}
