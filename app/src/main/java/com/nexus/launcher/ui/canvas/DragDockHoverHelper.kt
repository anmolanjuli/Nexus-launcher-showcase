package com.nexus.launcher.ui.canvas

import android.view.HapticFeedbackConstants
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.dock.DockCanvasDropHelper
import com.nexus.launcher.ui.dock.DockCoordHelper
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer

/**
 * Encapsulates magnetic dock hover detection and haptics during icon drag.
 * Extracted from [DragTouchHandler] to maintain class size limits.
 */
class DragDockHoverHelper(private val view: LauncherCanvasView) {

    var lastHoveredDockSlot: Int? = null
        private set

    fun updateDockHoverState(
        canvasLocalX: Float,
        canvasLocalY: Float,
        canvasDropDispatched: Boolean,
        onHoverChanged: (Boolean) -> Unit
    ) {
        val (rawX, rawY) = DockCoordHelper.canvasLocalToScreen(view, canvasLocalX, canvasLocalY)
        applyMagneticDockHover(rawX, rawY, canvasLocalX, canvasLocalY, canvasDropDispatched, onHoverChanged)
    }

    fun applyMagneticDockHover(
        rawX: Float,
        rawY: Float,
        canvasLocalX: Float,
        canvasLocalY: Float,
        canvasDropDispatched: Boolean,
        onHoverChanged: (Boolean) -> Unit
    ) {
        if (canvasDropDispatched) return
        val dock = DockLayout.findFrom(view) ?: run {
            clearMagneticDockHover(null, onHoverChanged)
            return
        }
        val draggedItem = view.draggedItem
        val displayItem = view.dragHandler.dragItem
        if (draggedItem == null && displayItem == null) {
            clearMagneticDockHover(dock, onHoverChanged)
            return
        }
        val displayMetrics = dock.context.resources.displayMetrics
        val gravityWellY = displayMetrics.heightPixels - (130 * displayMetrics.density)
        val (parentX, _) = DockCoordHelper.canvasLocalToParent(view, canvasLocalX, canvasLocalY)

        if (rawY >= gravityWellY && dock.containsCanvasPoint(parentX, dock.top.toFloat() + 1f)) {
            onHoverChanged(true)
            view.hoveredMergeTarget = null
            (view.context as? MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
            val localX = DockCanvasDropHelper.rawToDockLocalX(dock, rawX)
            val localY = DockCanvasDropHelper.rawToDockLocalY(dock, rawY)
            val safeMax = dock.maxDockIcons.coerceAtLeast(1)
            val draggedItemId = draggedItem?.id ?: -1
            val occupancy = view.findViewTreeViewModelStoreOwner()?.let { owner ->
                ViewModelProvider(owner)[HomeScreenViewModel::class.java]
                    .dockItems.value.count { it.id != draggedItemId }
            } ?: dock.pageItemCount()
            if (dock.isOutboundDragActive()) {
                DockLayoutRenderer.isInternalDrag = true
            }
            val preserveInternal = dock.isOutboundDragActive() && DockLayoutRenderer.isInternalDrag
            if (occupancy < safeMax) {
                if (!preserveInternal) {
                    DockLayoutRenderer.draggedItemId = if (draggedItemId != -1) draggedItemId else null
                    DockLayoutRenderer.draggedItemOriginalColumn = null
                    DockLayoutRenderer.isInternalDrag = false
                }
                dock.updateHoveredSlot(localX, localY)
                dock.invalidate()
                val currentHoverSlot = DockLayoutRenderer.hoveredSlotIndex
                if (currentHoverSlot != null && currentHoverSlot != lastHoveredDockSlot) {
                    dock.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    lastHoveredDockSlot = currentHoverSlot
                }
            } else {
                clearMagneticDockHover(dock, onHoverChanged)
            }
        } else {
            clearMagneticDockHover(dock, onHoverChanged)
        }
    }

    fun clearMagneticDockHover(
        dock: DockLayout?,
        onHoverChanged: (Boolean) -> Unit,
        deferDockInvalidate: Boolean = false
    ) {
        if (DockLayoutRenderer.hoveredSlotIndex != null ||
            DockLayoutRenderer.draggedItemId != null
        ) {
            DockLayoutRenderer.clearDragPreview()
            if (!deferDockInvalidate) dock?.invalidate()
        }
        if (deferDockInvalidate) {
            dock?.clearHoverStateOnly()
        } else {
            dock?.forceClearHover()
        }
        lastHoveredDockSlot = null
        onHoverChanged(false)
        DockLayoutRenderer.isInternalDrag = false
    }
}
