package com.nexus.launcher.ui.canvas

/**
 * Clearance the drawer's floating chrome reserves along each edge, so exactly one place computes
 * it for the grid, the fade gradient and the A–Z rail.
 *
 * There are two independent bars — the Split Search Pill and the category bar — each of which the
 * user can show, hide, and dock to either edge. They can share an edge or sit on opposite ones,
 * so the reserve at a given edge is the sum of whichever bars are docked there.
 *
 * Written by [com.nexus.launcher.ui.DrawerChromeController], read by the drawer layout passes.
 */
class DrawerChromeReserve {

    var pillVisible: Boolean = false
    var pillHeightPx: Int = 0
    var pillAtBottom: Boolean = false

    var categoryBarVisible: Boolean = false
    var categoryBarHeightPx: Int = 0
    var categoryBarAtBottom: Boolean = false

    /** Total clearance below the status bar, folded into the drawer's grid top. */
    val topReservePx: Int
        get() = (if (pillVisible && !pillAtBottom) pillHeightPx else 0) +
            (if (categoryBarVisible && !categoryBarAtBottom) categoryBarHeightPx else 0)

    /** Total clearance above the nav bar, subtracted from the drawer's grid bottom. */
    val bottomReservePx: Int
        get() = (if (pillVisible && pillAtBottom) pillHeightPx else 0) +
            (if (categoryBarVisible && categoryBarAtBottom) categoryBarHeightPx else 0)
}
