package com.nexus.launcher.ui.dock

import android.graphics.Rect
import com.nexus.launcher.data.HomeScreenItem

internal object DockLayoutHitTest {

    fun iconHitRect(dock: DockLayout, item: HomeScreenItem): Rect {
        dock.syncRendererLayoutInternal()
        val visible = dock.pageItemsInternal()
            .filter { it.column < dock.maxDockIcons }
            .sortedBy { it.column }
        val index = visible.indexOfFirst { it.id == item.id }
        val bounds = DockLayoutRenderer.getIconBoundsAt(index) ?: return Rect()
        return Rect(
            bounds.left.toInt(), bounds.top.toInt(),
            bounds.right.toInt(), bounds.bottom.toInt()
        )
    }
}
