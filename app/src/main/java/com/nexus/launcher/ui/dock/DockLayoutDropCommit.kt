package com.nexus.launcher.ui.dock

import com.nexus.launcher.data.HomeScreenItem
import java.util.concurrent.ConcurrentHashMap

/** Canvas-drop commit batching and single redraw scheduling. */
internal object DockLayoutDropCommit {

    private val redrawPending = ConcurrentHashMap<Int, Boolean>()

    fun prepareCanvasDrop(dock: DockLayout, rawX: Float, rawY: Float, item: HomeScreenItem): Int {
        begin(dock)
        val slot = DockCanvasDropHelper.prepareCanvasDropSlot(dock, rawX, rawY, item)
        return slot
    }

    fun begin(dock: DockLayout) {
        dock.skipNextFullRebind = true
        DockLayoutRenderer.beginDropCommitBatch()
    }

    fun scheduleRedraw(dock: DockLayout) {
        val key = System.identityHashCode(dock)
        if (redrawPending.putIfAbsent(key, true) != null) return
        dock.post {
            redrawPending.remove(key)
            DockLayoutRenderer.endDropCommitBatch()
            dock.invalidate()
        }
    }
}
