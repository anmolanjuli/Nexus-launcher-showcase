package com.nexus.launcher.ui.canvas

/**
 * Horizontal extent of the home grid — the single source for [LauncherCanvasView.gridAreaLeft]
 * and [LauncherCanvasView.gridAreaWidth].
 *
 * Portrait: the full width. Phone landscape: the width minus the dock strip on one side and
 * the cutout / side navigation bar on the other ([SideInsets]), minus the slack that keeps
 * landscape cells at the portrait aspect ratio ([LandscapeGridSpec.sideSlack]), on the dock side.
 */
object HomeGridArea {

    /** Width available to the grid before the aspect-ratio slack is taken out. */
    fun usableWidth(view: LauncherCanvasView): Int {
        if (!view.isLandscape) return view.viewWidth - view.dockStripWidth
        val oppositeInset = if (view.dockOnRight) SideInsets.left else SideInsets.right
        return (view.viewWidth - view.dockStripWidth - oppositeInset).coerceAtLeast(0)
    }

    fun left(view: LauncherCanvasView): Int {
        if (!view.isLandscape) return 0
        // The slack goes on the dock side, so the grid sits right against the cutout inset
        // instead of leaving a wide gap next to the camera.
        return if (view.dockOnRight) SideInsets.left
        else view.dockStripWidth + LandscapeGridSpec.sideSlack(view, usableWidth(view))
    }

    fun width(view: LauncherCanvasView): Int {
        val usable = usableWidth(view)
        return usable - LandscapeGridSpec.sideSlack(view, usable)
    }
}
