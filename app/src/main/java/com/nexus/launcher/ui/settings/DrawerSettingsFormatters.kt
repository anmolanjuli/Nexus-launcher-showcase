package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData

object DrawerSettingsFormatters {

    fun sortOptions(context: Context): List<Pair<String, String>> = listOf(
        "az" to context.getString(R.string.drawer_sort_az),
        "za" to context.getString(R.string.drawer_sort_za),
        "new" to context.getString(R.string.drawer_sort_recently_installed)
    )

    fun searchPositionOptions(context: Context): List<Pair<String, String>> = listOf(
        "top" to context.getString(R.string.drawer_search_pos_top),
        "bottom" to context.getString(R.string.drawer_search_pos_bottom)
    )

    fun transitionOptions(context: Context): List<Pair<String, String>> = listOf(
        "default" to context.getString(R.string.drawer_transition_default),
        "cube" to context.getString(R.string.drawer_transition_cube),
        "zoom" to context.getString(R.string.drawer_transition_zoom),
        "tilt" to context.getString(R.string.drawer_transition_tilt),
        "stack" to context.getString(R.string.drawer_transition_stack)
    )

    fun getTransitionName(context: Context, mode: String): String = when (mode) {
        "cube" -> context.getString(R.string.drawer_transition_cube)
        "zoom" -> context.getString(R.string.drawer_transition_zoom)
        "tilt" -> context.getString(R.string.drawer_transition_tilt)
        "stack" -> context.getString(R.string.drawer_transition_stack)
        "default" -> context.getString(R.string.drawer_transition_default)
        else -> context.getString(R.string.drawer_transition_default)
    }

    fun getTransitionSubtitle(context: Context, mode: String): String = when (mode) {
        "cube" -> context.getString(R.string.drawer_transition_cube_desc)
        "zoom" -> context.getString(R.string.drawer_transition_zoom_desc)
        "tilt" -> context.getString(R.string.drawer_transition_tilt_desc)
        "stack" -> context.getString(R.string.drawer_transition_stack_desc)
        "default" -> context.getString(R.string.drawer_transition_default_desc)
        else -> context.getString(R.string.drawer_transition_default_desc)
    }

    fun categoryStyleOptions(context: Context, pillEnabled: Boolean): List<Pair<String, String>> {
        val all = listOf(
            "in_pill" to context.getString(R.string.drawer_cat_style_in_pill),
            "dropdown" to context.getString(R.string.drawer_cat_style_dropdown),
            "strip" to context.getString(R.string.drawer_cat_style_strip)
        )
        return if (pillEnabled) all else all.drop(1)
    }

    val SORT_OPTIONS = listOf(
        "az" to "A–Z",
        "za" to "Z–A",
        "new" to "Recently Installed"
    )

    val SEARCH_POSITION_OPTIONS = listOf(
        "top" to "Top",
        "bottom" to "Bottom"
    )

    val CATEGORY_STYLE_OPTIONS = listOf(
        "in_pill" to "In Pill",
        "dropdown" to "Dropdown",
        "strip" to "Strip"
    )

    /** "In Pill" is meaningless with the pill switched off — the drawer falls back to the chip
     *  strip there, so do not offer a choice the launcher will silently override. */
    /** Mirrors DrawerChromeLayout's fallback so the settings page shows the style the drawer
     *  will actually render, not a stale "In Pill" against a pill that is switched off. */
    fun effectiveCategoryMode(data: NexusSettingsData): String =
        if (data.drawerCategoryMode == "in_pill" && !data.drawerShowSearchPill) "strip"
        else data.drawerCategoryMode

    /** True when the category picker renders as a bar with its own top/bottom placement. */
    fun hasOwnCategoryBar(data: NexusSettingsData): Boolean {
        val mode = effectiveCategoryMode(data)
        return mode == "strip" || (mode == "dropdown" && !data.drawerShowSearchPill)
    }

    fun categoryStyleOptions(pillEnabled: Boolean) =
        if (pillEnabled) CATEGORY_STYLE_OPTIONS else CATEGORY_STYLE_OPTIONS.drop(1)

    fun getGridOrListSubtitle(context: Context, gridOrList: String): String =
        when (gridOrList) {
            "list" -> context.getString(R.string.drawer_layout_list)
            "categories" -> context.getString(R.string.drawer_layout_categories)
            else -> context.getString(R.string.drawer_layout_grid)
        }

    fun getGridOrListSubtitle(gridOrList: String): String =
        when (gridOrList) {
            "list" -> "List"
            "categories" -> "Categories"
            else -> "Grid"
        }

    fun categoryLayoutOptions(context: Context): List<Pair<String, String>> = listOf(
        "spatial" to context.getString(R.string.drawer_category_layout_spatial),
        "strip" to context.getString(R.string.drawer_category_layout_strip),
        "list" to context.getString(R.string.drawer_category_layout_list)
    )

    fun getSortSubtitle(context: Context, sort: String): String {
        return when (sort) {
            "za" -> context.getString(R.string.drawer_sort_za)
            "new" -> context.getString(R.string.drawer_sort_recently_installed)
            else -> context.getString(R.string.drawer_sort_az)
        }
    }

    fun getSortSubtitle(sort: String): String {
        return when (sort) {
            "za" -> "Z–A"
            "new" -> "Recently Installed"
            else -> "A–Z"
        }
    }

    fun getGridSubtitle(context: Context, data: NexusSettingsData): String {
        val cols = when (data.drawerGridOrList) {
            "grid" -> context.getString(R.string.drawer_col_abbrev, data.drawerColumns)
            "categories" -> context.getString(R.string.drawer_layout_categories)
            else -> context.getString(R.string.drawer_list_mode_abbrev)
        }
        val size = context.getString(R.string.drawer_size_percent, (data.drawerIconSizeMultiplier * 100).toInt())
        val labels = if (data.drawerShowLabels) context.getString(R.string.drawer_labels_on) else context.getString(R.string.drawer_labels_off)
        return "$cols • $size • $labels"
    }

    fun getGridSubtitle(data: NexusSettingsData): String {
        val cols = when (data.drawerGridOrList) {
            "grid" -> "${data.drawerColumns} Col"
            "categories" -> "Categories"
            else -> "List Mode"
        }
        val size = "${(data.drawerIconSizeMultiplier * 100).toInt()}% Size"
        val labels = if (data.drawerShowLabels) "Labels On" else "Labels Off"
        return "$cols • $size • $labels"
    }

    fun getNavigationSubtitle(context: Context, data: NexusSettingsData): String {
        val search = when {
            !data.drawerShowSearchPill -> context.getString(R.string.drawer_pill_off)
            data.drawerSearchBarPosition == "bottom" -> context.getString(R.string.drawer_pill_bottom)
            else -> context.getString(R.string.drawer_pill_top)
        }
        val cat = if (!data.drawerShowCategoryBar) {
            context.getString(R.string.drawer_cats_off)
        } else {
            val style = categoryStyleOptions(context, true).firstOrNull { it.first == data.drawerCategoryMode }
            style?.second ?: context.getString(R.string.drawer_cats_on)
        }
        val rail = if (data.drawerShowRail) context.getString(R.string.drawer_rail_on) else context.getString(R.string.drawer_rail_off)
        return "$search • $cat • $rail"
    }

    fun getNavigationSubtitle(data: NexusSettingsData): String {
        val search = when {
            !data.drawerShowSearchPill -> "Pill Off"
            data.drawerSearchBarPosition == "bottom" -> "Pill Bottom"
            else -> "Pill Top"
        }
        val cat = if (!data.drawerShowCategoryBar) {
            "Categories Off"
        } else {
            val style = CATEGORY_STYLE_OPTIONS.firstOrNull { it.first == data.drawerCategoryMode }
            style?.second ?: "Categories On"
        }
        val rail = if (data.drawerShowRail) "Rail On" else "Rail Off"
        return "$search • $cat • $rail"
    }
}
