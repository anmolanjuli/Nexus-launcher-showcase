package com.nexus.launcher.ui.dock

import android.util.Log
import android.view.View
import com.nexus.launcher.data.HomeScreenItem

/** Screen-space → dock-local coordinates and canvas-drop slot resolution. */
object DockCanvasDropHelper {

    private const val TAG = "DockDrop"
    private const val PENDING_EXPIRY_MS = 2000L

    private data class PendingCanvasDrop(val slot: Int, val item: HomeScreenItem)

    @Volatile private var pendingCanvasDrop: PendingCanvasDrop? = null
    @Volatile private var lastPreparedDropItem: HomeScreenItem? = null
    private var pendingClearDock: View? = null

    private val pendingCanvasDropClear = Runnable {
        pendingCanvasDrop?.let { pending ->
            Log.d(TAG, "pendingSlot EXPIRED (was ${pending.slot})")
        }
        pendingCanvasDrop = null
        pendingClearDock = null
        DockLayoutRenderer.clearDragPreview()
    }

    fun rawToDockLocalX(dock: View, rawX: Float): Float {
        val loc = IntArray(2)
        dock.getLocationOnScreen(loc)
        return rawX - loc[0] + dock.translationX
    }

    fun rawToDockLocalY(dock: View, rawY: Float): Float {
        val loc = IntArray(2)
        dock.getLocationOnScreen(loc)
        return rawY - loc[1] + dock.translationY
    }

    fun prepareCanvasDropSlot(dock: DockLayout, rawX: Float, rawY: Float, item: HomeScreenItem): Int {
        Log.d(TAG, "prepareCanvasDrop: rawX=$rawX rawY=$rawY pkg=${item.packageName} id=${item.id}")
        clearPendingCallbacks(dock)
        val localX = rawToDockLocalX(dock, rawX)
        val localY = rawToDockLocalY(dock, rawY)
        Log.d(TAG, "prepareCanvasDrop: localX=$localX localY=$localY")

        val maxSlots = dock.maxDockIcons.coerceAtLeast(1)
        val occupancy = dock.pageItemCount() + if (DockSearchSlot.enabled && dock.currentPage == 0) 1 else 0
        val alreadyInDock = dock.containsDockItem(item.id)
        val duplicatePkg = dock.containsDockPackage(item.packageName) && item.id == 0
        if (occupancy >= maxSlots && !alreadyInDock) {
            Log.d(TAG, "prepareCanvasDrop: REJECTED capacity full occupancy=$occupancy maxSlots=$maxSlots")
            DockLayoutRenderer.clearDragPreview()
            return -1
        }
        if (duplicatePkg) {
            Log.d(TAG, "prepareCanvasDrop: REJECTED duplicate package=${item.packageName}")
            DockLayoutRenderer.clearDragPreview()
            return -1
        }

        val axis = dock.dropAxisCoordInternal(localX, localY)
        val slot = resolveDropSlot(
            dock::syncRendererLayoutInternal,
            axis,
            item.id
        )
        if (slot !in 0 until maxSlots) {
            Log.d(TAG, "prepareCanvasDrop: REJECTED invalid index slot=$slot maxSlots=$maxSlots")
            DockLayoutRenderer.clearDragPreview()
            return -1
        }

        pendingCanvasDrop = PendingCanvasDrop(slot, item)
        lastPreparedDropItem = item
        Log.d(TAG, "prepareCanvasDrop: pendingSlot=$slot axis=$axis")
        pendingClearDock = dock
        dock.postDelayed(pendingCanvasDropClear, PENDING_EXPIRY_MS)
        return slot
    }

    /**
     * Restores the item identity stashed during [prepareCanvasDropSlot] when the async bridge
     * delivers a stale copy with id=0.
     */
    fun resolveIncomingItem(incoming: HomeScreenItem): HomeScreenItem {
        val prepared = lastPreparedDropItem ?: return incoming
        if (prepared.packageName != incoming.packageName) return incoming
        lastPreparedDropItem = null
        if (incoming.id == 0 && prepared.id != 0) {
            Log.d(TAG, "resolveIncomingItem: restored id=${prepared.id} for pkg=${incoming.packageName}")
            return incoming.copy(id = prepared.id)
        }
        if (incoming.id == 0) return prepared
        return incoming
    }

    fun insertIndexForLocalCoord(dock: DockLayout, localX: Float, localY: Float = 0f): Int {
        pendingCanvasDrop?.let { pending ->
            pendingCanvasDrop = null
            clearPendingCallbacks(dock)
            lastPreparedDropItem = pending.item
            Log.d(TAG, "insertIndex: pendingSlot CONSUMED slot=${pending.slot} " +
                "localX=$localX localY=$localY pkg=${pending.item.packageName}")
            return pending.slot
        }
        Log.d(TAG, "insertIndex: pendingSlot none/expired, computing fresh localX=$localX localY=$localY")
        dock.syncRendererLayoutInternal()
        val axis = dock.dropAxisCoordInternal(localX, localY)
        DockLayoutRenderer.hoveredSlotIndex = DockLayoutRenderer.resolveInsertIndexForX(axis)
        dock.syncRendererLayoutInternal()
        val computed = DockLayoutRenderer.getInsertIndexForX(axis)
        Log.d(TAG, "insertIndex: computed slot=$computed axis=$axis")
        return computed
    }

    fun resolveDropSlot(
        syncLayout: () -> Unit,
        axisCoord: Float,
        draggedItemId: Int
    ): Int {
        DockLayoutRenderer.draggedItemId = draggedItemId
        DockLayoutRenderer.draggedItemOriginalColumn = null
        DockLayoutRenderer.isInternalDrag = false
        syncLayout()
        DockLayoutRenderer.hoveredSlotIndex =
            DockLayoutRenderer.resolveInsertIndexForX(axisCoord)
        syncLayout()
        return DockLayoutRenderer.getInsertIndexForX(axisCoord)
    }

    private fun clearPendingCallbacks(dock: View) {
        pendingClearDock?.removeCallbacks(pendingCanvasDropClear)
        pendingClearDock = null
    }
}
