package com.nexus.launcher.ui.dock

import android.view.MotionEvent
import com.nexus.launcher.data.HomeScreenItem

/** Resolves dock item at touch coordinates and resolves display labels. */
internal object DockLayoutItemResolver {

    fun getItemAt(dock: DockLayout, e: MotionEvent): HomeScreenItem? {
        dock.syncRendererLayoutInternal()
        val index = DockLayoutRenderer.getIconIndexForTouch(e.x, e.y)
        if (index < 0) return null
        return dock.pageItemsInternal().filter { it.column < dock.maxDockIcons }.sortedBy { it.column }.getOrNull(index)
    }

    fun labelForItem(dock: DockLayout, item: HomeScreenItem): String? {
        if (DockSearchSlot.isSearchItem(item)) return null
        return try {
            val info = dock.context.packageManager.getApplicationInfo(item.packageName, 0)
            dock.context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            null
        }
    }
}
