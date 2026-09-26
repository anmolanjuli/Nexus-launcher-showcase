package com.nexus.launcher.ui.dock

import android.view.HapticFeedbackConstants
import com.nexus.launcher.ui.model.DisplayItem

/** Platform and finger drop dispatch extracted from [DockLayout]. */
internal object DockLayoutDropApi {

    fun setupDropTarget(
        dock: DockLayout,
        onItemDropped: (DisplayItem, Int) -> Unit
    ) {
        dock.setDropCallback(onItemDropped)
        DockLayoutDropHandler.attach(
            view = dock,
            context = dock.context,
            items = { dock.getItemsSnapshot() },
            maxDockIcons = { dock.maxDockIcons },
            globalInsertionIndexAt = dock::globalInsertionIndexAt,
            localInsertionIndexAt = dock::localInsertionIndexAt,
            onItemDropped = { item, index -> onItemDropped(item, index) },
            onHoveredSlotChanged = { slot ->
                dock.setHoveredSlot(slot)
                DockLayoutRenderer.hoveredSlotIndex = slot
                dock.invalidate()
            },
            getHoveredSlot = { dock.hoveredSlot },
            invalidate = { dock.invalidate() }
        )
    }

    fun dispatchDrop(dock: DockLayout, canvasX: Float, item: DisplayItem, canvasY: Float) {
        DockLayoutDropHandler.handleFingerDrop(
            dock, canvasX, canvasY, item,
            items = dock::getItemsSnapshot,
            maxDockIcons = { dock.maxDockIcons },
            onItemDropped = { dropped, index -> dock.invokeDropCallback(dropped, index) }
        )
    }

    fun tryDropFromCanvas(
        dock: DockLayout, canvasX: Float, canvasY: Float, item: DisplayItem
    ): Boolean {
        if (!dock.containsCanvasPoint(canvasX, canvasY)) return false
        dispatchDrop(dock, canvasX, item, canvasY)
        return true
    }
}
