package com.nexus.launcher.ui

import com.nexus.launcher.R

/**
 * What each home edit radial slice branches out to. Keys are slice indexes in [HomeEditSheet]
 * (3 = Pages, 6 = Settings); a slice with no entry simply has no branch.
 */
object HomeEditBranches {

    const val SLICE_PAGES = 3
    const val SLICE_SETTINGS = 6

    /**
     * Settings pages in the hub's own order. Section names are the deep links
     * `SettingsHubCatalog.sectionIndex` understands; Dock has no page, so it opens its sheet.
     */
    fun build(
        onSettingsSection: (String) -> Unit,
        onDockSettings: () -> Unit,
        onAddPage: () -> Unit,
    ): Map<Int, List<RadialBranchItem>> = mapOf(
        SLICE_PAGES to listOf(
            RadialBranchItem(R.drawable.ic_add, onAddPage),
        ),
        SLICE_SETTINGS to listOf(
            RadialBranchItem(R.drawable.ic_home) { onSettingsSection("home") },
            RadialBranchItem(R.drawable.ic_drawermode) { onSettingsSection("drawer") },
            RadialBranchItem(R.drawable.ic_dock_tray, onDockSettings),
            RadialBranchItem(R.drawable.ic_palette_theme) { onSettingsSection("theme") },
            RadialBranchItem(R.drawable.ic_icon_tile) { onSettingsSection("icons") },
            RadialBranchItem(R.drawable.ic_fonts) { onSettingsSection("typography") },
            RadialBranchItem(R.drawable.ic_gesture) { onSettingsSection("gestures") },
            RadialBranchItem(R.drawable.outline_manage_search_24) { onSettingsSection("search") },
            RadialBranchItem(R.drawable.ic_notification) { onSettingsSection("notifications") },
            RadialBranchItem(R.drawable.ic_backup) { onSettingsSection("backup") },
        ),
    )
}
