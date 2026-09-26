package com.nexus.launcher.ui.dock

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.hypot

/** Routes dock long-press drags into canvas coordinate space for homescreen drop. */
class DockOutboundDrag(private val dock: DockLayout) {

    init {
        DockLayoutRenderer.requestInvalidate = { dock.invalidate() }
    }

    var active = false
        private set

    var draggedItemId: Int? = null
        private set

    fun currentDraggedItem(): HomeScreenItem? = draggedItem

    var onDragMove: ((Float, Float) -> Unit)? = null
    /** Canvas-local coords; [internalSlot] and [wasInternalDrag] captured before preview clear. */
    var onDragEnd: ((Float, Float, Int?, Boolean) -> Unit)? = null

    private var draggedItem: HomeScreenItem? = null
    private var lastHoverSlot: Int? = null
    private var pendingItem: HomeScreenItem? = null
    private var deferredMenuItem: HomeScreenItem? = null
    private var pendingDownX = 0f
    private var pendingDownY = 0f

    fun armPendingDrag(item: HomeScreenItem) {
        pendingItem = item
        deferredMenuItem = item
    }

    /** Search slot drags without a deferred context-menu open on lift. */
    fun armPendingSearchDrag(item: HomeScreenItem) {
        pendingItem = item
        deferredMenuItem = null
    }

    /** Returns the long-pressed item if the finger lifted without starting a drag. */
    fun consumeDeferredMenu(): HomeScreenItem? {
        val item = deferredMenuItem
        deferredMenuItem = null
        return item
    }

    fun clearDeferredMenuOnLift() {
        deferredMenuItem = null
    }

    private fun cancelDeferredMenu() {
        deferredMenuItem = null
    }

    fun recordTouchDown(x: Float, y: Float) {
        pendingDownX = x
        pendingDownY = y
    }

    fun clearPending() {
        pendingItem = null
        deferredMenuItem = null
    }

    /**
     * Starts outbound drag once the finger moves past [touchSlop] from the initial down point.
     * Returns true when drag becomes active on this event.
     */
    fun tryStartPending(
        event: MotionEvent,
        touchSlop: Float,
        onInitiateDrag: (HomeScreenItem, View) -> Unit
    ): Boolean {
        val item = pendingItem ?: return false
        if (hypot((event.x - pendingDownX).toDouble(), (event.y - pendingDownY).toDouble()) < touchSlop) {
            return false
        }
        pendingItem = null
        cancelDeferredMenu()
        DockContextMenuLauncher.dismissIfShowing()
        if (!DockSearchSlot.isSearchItem(item)) {
            onInitiateDrag(item, dock)
        }
        begin(item, event.x, event.y)
        handleTouch(event)
        return true
    }

    fun begin(item: HomeScreenItem, localX: Float, localY: Float) {
        draggedItem = item
        draggedItemId = item.id
        active = true
    }

    fun handleTouch(event: MotionEvent): Boolean {
        if (!active) return false
        val (canvasX, canvasY) = DockCoordHelper.dockLocalToCanvasLocal(dock, event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                updateInternalDragPreview(event)
                if (!DockSearchSlot.isSearchItem(draggedItem)) {
                    onDragMove?.invoke(canvasX, canvasY)
                }
                dock.invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val internalSlot = DockLayoutRenderer.hoveredSlotIndex
                val wasInternal = DockLayoutRenderer.isInternalDrag
                clearInternalDragPreview()
                onDragEnd?.invoke(canvasX, canvasY, internalSlot, wasInternal)
                active = false
                draggedItemId = null
                draggedItem = null
                lastHoverSlot = null
            }
        }
        return true
    }

    private fun updateInternalDragPreview(event: MotionEvent) {
        val item = draggedItem ?: return
        dock.updateHoveredSlot(event.x, event.y)
        DockLayoutRenderer.draggedItemId = item.id
        DockLayoutRenderer.draggedItemOriginalColumn = item.column
        DockLayoutRenderer.isInternalDrag = true
        val hoverSlot = DockLayoutRenderer.hoveredSlotIndex
        if (hoverSlot != null && hoverSlot != lastHoverSlot) {
            dock.performHapticFeedback(
                HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            lastHoverSlot = hoverSlot
        }
    }

    private fun clearInternalDragPreview() {
        DockLayoutRenderer.clearDragPreview()
        dock.forceClearHover()
        lastHoverSlot = null
    }

    fun cancel() {
        clearInternalDragPreview()
        active = false
        draggedItemId = null
        draggedItem = null
        pendingItem = null
    }
}
