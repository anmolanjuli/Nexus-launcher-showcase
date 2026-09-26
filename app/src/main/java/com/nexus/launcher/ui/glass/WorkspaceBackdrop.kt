package com.nexus.launcher.ui.glass

import android.graphics.Canvas
import android.view.View
import android.view.ViewParent

/**
 * Draws the home workspace — canvas, widgets, dock (`R.id.workspace_container`) — in screen
 * coordinates, for a [FrostedPanelLayout] that should frost what is really behind it rather than
 * only the wallpaper. See [FrostedPanelLayout.frostWorkspace].
 *
 * The system wallpaper is not part of it (the window shows that behind the canvas); the panel
 * draws its wallpaper slice first and this on top.
 */
internal object WorkspaceBackdrop {

    private val loc = IntArray(2)

    /** False when there is no workspace, or [panel] sits inside it (it would draw itself). */
    fun draw(panel: View, canvas: Canvas): Boolean {
        val workspace = panel.rootView.findViewById<View>(com.nexus.launcher.R.id.workspace_container)
            ?: return false
        if (workspace.width <= 0 || workspace.height <= 0 || isInside(panel, workspace)) return false
        workspace.getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(loc[0].toFloat(), loc[1].toFloat())
        workspace.draw(canvas)
        canvas.restore()
        return true
    }

    private fun isInside(view: View, ancestor: View): Boolean {
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p === ancestor) return true
            p = p.parent
        }
        return false
    }
}
