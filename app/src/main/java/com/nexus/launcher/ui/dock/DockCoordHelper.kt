package com.nexus.launcher.ui.dock

import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/** Converts between dock-local, parent-relative, canvas-local, and screen coordinates. */
object DockCoordHelper {

    fun parentToCanvasLocal(canvas: LauncherCanvasView, parentX: Float, parentY: Float): Pair<Float, Float> =
        parentX - canvas.left to parentY - canvas.top

    fun dockLocalToCanvasLocal(dock: DockLayout, localX: Float, localY: Float): Pair<Float, Float> {
        val canvas = findCanvasSibling(dock)
            ?: return localX + dock.left to localY + dock.top
        return parentToCanvasLocal(canvas, dock.left + localX, dock.top + localY)
    }

    fun canvasLocalToParent(canvas: LauncherCanvasView, localX: Float, localY: Float): Pair<Float, Float> =
        localX + canvas.left to localY + canvas.top

    fun canvasLocalToScreen(canvas: LauncherCanvasView, localX: Float, localY: Float): Pair<Float, Float> {
        val loc = IntArray(2)
        canvas.getLocationOnScreen(loc)
        return loc[0] + localX to loc[1] + localY
    }

    fun findCanvasSibling(anchor: View): LauncherCanvasView? {
        var parent = anchor.parent
        while (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is LauncherCanvasView) return child
            }
            parent = parent.parent
        }
        return null
    }
}
