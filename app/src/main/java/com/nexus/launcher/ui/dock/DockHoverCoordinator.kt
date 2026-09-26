package com.nexus.launcher.ui.dock

/** Hover slot tracking, finger updates, and slot-change velocity impulses. */
internal object DockHoverCoordinator {

    const val SLOT_CHANGE_IMPULSE_PX = 80f

    private var lastHoverSlot: Int? = null

    fun reset() {
        lastHoverSlot = null
    }

    /**
     * Returns whether the hover slot changed this frame and the previous slot (for impulse).
     */
    fun onHoverFrame(slot: Int?): Pair<Boolean, Int?> {
        val previous = lastHoverSlot
        val changed = slot != null && slot != previous
        lastHoverSlot = slot
        return changed to previous
    }

    fun updateHover(dock: DockLayout, localX: Float, localY: Float) {
        if (dock.width == 0 || dock.height == 0) return
        val isVertical = DockAxis.isVertical(dock)
        val fingerMain = DockAxis.main(localX, localY, isVertical)
        DockLayoutRenderer.dragFingerLocalX = fingerMain
        dock.syncRendererLayoutInternal()
        val slot = DockLayoutRenderer.resolveInsertIndexForX(fingerMain)
        DockLayoutRenderer.hoveredSlotIndex = slot
        dock.setHoveredSlot(slot)
        dock.invalidate()
    }
}
