package com.nexus.launcher.data.prefs

/**
 * Allowed values for the drawer's layout keys. Grid and List remain independent of
 * Categories; Categories then has its own Spatial / Strip / List presentation.
 */
object DrawerLayoutModes {
    const val GRID = "grid"
    const val LIST = "list"
    const val CATEGORIES = "categories"

    const val SPATIAL = "spatial"
    const val STRIP = "strip"
    const val CAT_LIST = "list"

    val GRID_OR_LIST = setOf(GRID, LIST, CATEGORIES)
    val CATEGORY_LAYOUTS = setOf(SPATIAL, STRIP, CAT_LIST)
}
