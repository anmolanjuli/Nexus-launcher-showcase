package com.nexus.launcher.ui.dock

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/** Resolves dock-outbound drag end: in-dock shuffle vs dock→home drop. */
internal object DockOutboundDropHelper {

    fun isOverDock(
        view: LauncherCanvasView,
        dock: DockLayout,
        canvasLocalX: Float,
        canvasLocalY: Float,
        rawY: Float
    ): Boolean {
        val (parentX, parentY) = DockCoordHelper.canvasLocalToParent(view, canvasLocalX, canvasLocalY)
        if (dock.containsCanvasPoint(parentX, parentY)) return true
        val density = view.context.resources.displayMetrics.density
        val gravityWellY = view.context.resources.displayMetrics.heightPixels - (130 * density)
        
        // Only allow the gravity well to catch the drop if it's horizontally within the visual pill
        return rawY >= gravityWellY && dock.containsCanvasPoint(parentX, dock.top.toFloat() + 1f)
    }

    fun commitInternalReorder(
        view: LauncherCanvasView,
        item: com.nexus.launcher.data.HomeScreenItem,
        dock: DockLayout,
        rawX: Float,
        rawY: Float,
        internalSlot: Int?
    ): Boolean {
        val slot = internalSlot?.takeIf { it >= 0 }
            ?: dock.prepareCanvasDropSlot(rawX, rawY, item)
        if (slot < 0) return false
        view.findViewTreeViewModelStoreOwner()?.let { owner ->
            ViewModelProvider(owner)[HomeScreenViewModel::class.java]
                .moveItemToDock(item, slot)
        }
        return true
    }
}
