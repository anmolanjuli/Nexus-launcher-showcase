package com.nexus.launcher.ui.settings

import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.data.prefs.NexusSettingsData

data class PendingDrawerSettings(
    var drawerColumns: Int = NexusDefaults.DRAWER_COLUMNS,
    var drawerIconSizeMultiplier: Float = NexusDefaults.DRAWER_ICON_SIZE_MULTIPLIER,
    var drawerShowLabels: Boolean = NexusDefaults.DRAWER_SHOW_LABELS,
    var drawerTwoLineLabels: Boolean = NexusDefaults.DRAWER_TWO_LINE_LABELS,
    var drawerShowCategoryBar: Boolean = NexusDefaults.DRAWER_SHOW_CATEGORY_BAR,
    var drawerShowRail: Boolean = NexusDefaults.DRAWER_SHOW_RAIL,
    var drawerSortOrder: String = NexusDefaults.DRAWER_SORT_ORDER,
    var drawerShowSearchPill: Boolean = NexusDefaults.DRAWER_SHOW_SEARCH_PILL,
    var drawerSearchBarPosition: String = NexusDefaults.DRAWER_SEARCH_BAR_POSITION,
    var drawerCategoryMode: String = NexusDefaults.DRAWER_CATEGORY_MODE,
    var drawerCategoryPosition: String = NexusDefaults.DRAWER_CATEGORY_POSITION,
    var drawerLayout: String = NexusDefaults.DRAWER_LAYOUT,
    var drawerGridOrList: String = NexusDefaults.DRAWER_GRID_OR_LIST,
    var drawerListColumns: Int = NexusDefaults.DRAWER_LIST_COLUMNS,
    var drawerCategoryLayout: String = NexusDefaults.DRAWER_CATEGORY_LAYOUT,
    var drawerTransition: String = NexusDefaults.DRAWER_TRANSITION,
    var iconShape: Int = NexusDefaults.ICON_SHAPE
) {
    companion object {
        fun from(settings: NexusSettingsData): PendingDrawerSettings {
            return PendingDrawerSettings(
                drawerColumns = settings.drawerColumns,
                drawerIconSizeMultiplier = settings.drawerIconSizeMultiplier,
                drawerShowLabels = settings.drawerShowLabels,
                drawerTwoLineLabels = settings.drawerTwoLineLabels,
                drawerShowCategoryBar = settings.drawerShowCategoryBar,
                drawerShowRail = settings.drawerShowRail,
                drawerSortOrder = settings.drawerSortOrder,
                drawerShowSearchPill = settings.drawerShowSearchPill,
                drawerSearchBarPosition = settings.drawerSearchBarPosition,
                drawerCategoryMode = settings.drawerCategoryMode,
                drawerCategoryPosition = settings.drawerCategoryPosition,
                drawerLayout = settings.drawerLayout,
                drawerGridOrList = settings.drawerGridOrList,
                drawerListColumns = settings.drawerListColumns,
                drawerCategoryLayout = settings.drawerCategoryLayout,
                drawerTransition = settings.drawerTransition,
                iconShape = settings.iconShape
            )
        }
    }

    /** Mirrors DrawerChromeLayout's fallback: "in the pill" is meaningless with the pill off,
     *  where the drawer renders the chip strip instead. */
    val effectiveCategoryMode: String
        get() = if (drawerCategoryMode == "in_pill" && !drawerShowSearchPill) "strip"
            else drawerCategoryMode

    /** Mirrors [NexusSettingsData.drawerEffectiveLayoutMode] — same derivation, kept in sync by
     *  hand since this is a plain preview-staging copy, not the persisted data class itself. */
    val drawerEffectiveLayoutMode: String
        get() = when (drawerGridOrList) {
            "grid" -> "grid"
            "categories" -> "categories"
            else -> "list_$drawerListColumns"
        }
}
