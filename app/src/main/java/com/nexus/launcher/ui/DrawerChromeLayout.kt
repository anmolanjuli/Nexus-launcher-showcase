package com.nexus.launcher.ui

import com.nexus.launcher.data.prefs.DrawerLayoutModes
import com.nexus.launcher.data.prefs.NexusSettingsData

/**
 * Resolves the drawer's chrome preferences into the arrangement actually rendered.
 *
 * The raw preferences allow combinations that don't describe a usable drawer — categories set to
 * live inside a pill the user has since switched off, or every surface hidden with no home left
 * for the overflow trigger. Resolving that here, in one place, keeps
 * [DrawerChromeController] free of "what did they really mean" branching.
 */
object DrawerChromeLayout {

    const val CATEGORY_IN_PILL = "in_pill"
    const val CATEGORY_DROPDOWN = "dropdown"
    const val CATEGORY_STRIP = "strip"

    enum class OverflowHome {
        /** Trailing segment of the Split Search Pill. */
        PILL,

        /** Trailing element of the category bar, past the chips. */
        CATEGORY_BAR,

        /** Neither bar is on screen — fall back to the legacy button pinned to the rail edge. */
        RAIL
    }

    data class Resolved(
        val pillVisible: Boolean,
        val pillAtBottom: Boolean,
        /** Category picker rendered as the pill's left segment. */
        val categoryInPill: Boolean,
        /** Category picker rendered as a chip sharing the pill's row, to its left. */
        val categoryBesidePill: Boolean,
        /** A standalone category bar is on screen (a chip strip, or a single dropdown chip). */
        val categoryBarVisible: Boolean,
        /** When a category bar is shown: chips strip if true, single dropdown chip if false. */
        val categoryBarIsStrip: Boolean,
        /** False when the bar exists only to carry the overflow trigger. */
        val categoryBarHasCategories: Boolean,
        val categoryBarAtBottom: Boolean,
        val overflowHome: OverflowHome
    )

    fun resolve(settings: NexusSettingsData): Resolved {
        val pillVisible = settings.drawerShowSearchPill
        val categoriesOn = settings.drawerShowCategoryBar

        // "Inside the pill" is meaningless without a pill: fall back to the chip strip, which is
        // what the drawer shows when the pill is switched off.
        //
        // The Categories drawer uses the same placement as every other mode — it was forced to
        // the dropdown for a while, which put the category outside a pill that had asked to hold
        // it. Picking a category there takes the reader to it instead of filtering.
        val mode = when {
            !categoriesOn -> null
            settings.drawerCategoryMode == CATEGORY_IN_PILL && !pillVisible -> CATEGORY_STRIP
            else -> settings.drawerCategoryMode
        }

        val categoryInPill = pillVisible && mode == CATEGORY_IN_PILL
        // A dropdown chip is small enough to share the pill's row, which reads as one control
        // rather than two stacked bars — so it only earns its own bar when there is no pill.
        val categoryBesidePill = pillVisible && mode == CATEGORY_DROPDOWN
        val hasOwnCategoryBar = mode == CATEGORY_STRIP || (mode == CATEGORY_DROPDOWN && !pillVisible)

        // The overflow trigger must always be reachable: the pill carries it, else the category
        // bar does, else the legacy rail-edge button comes back rather than reserving a whole
        // bar to hold a single icon.
        val overflowHome = when {
            pillVisible -> OverflowHome.PILL
            hasOwnCategoryBar -> OverflowHome.CATEGORY_BAR
            else -> OverflowHome.RAIL
        }

        return Resolved(
            pillVisible = pillVisible,
            pillAtBottom = settings.drawerSearchBarPosition == "bottom",
            categoryInPill = categoryInPill,
            categoryBesidePill = categoryBesidePill,
            categoryBarVisible = hasOwnCategoryBar,
            categoryBarIsStrip = mode == CATEGORY_STRIP,
            categoryBarHasCategories = hasOwnCategoryBar,
            categoryBarAtBottom = settings.drawerCategoryPosition == "bottom",
            overflowHome = overflowHome
        )
    }
}
