package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumConfig

object SettingsHubCatalog {

    const val PAGE_COUNT = 13

    /**
     * Hub target that is not a pager page.
     *
     * Dock settings live in a bottom sheet rather than a fragment - it was only reachable by
     * long-pressing the dock, so it had no presence in Settings at all. [DockSettingsDialog]
     * takes its live `DockLayout` as an optional argument and falls back to its own preview, so
     * it works perfectly well opened from here with no dock on screen.
     */
    const val PAGER_DOCK_DIALOG = -1

    /**
     * Hub listing order. `pagerIndex` is the fixed position in [SettingsPagerAdapter]; this list
     * is the *presentation* order, which is why the two no longer run in step.
     *
     * Regrouped 2026-09-09: Gestures and Search were filed under "Look", but neither is about
     * appearance - both describe how the launcher behaves - and Notifications sat in System
     * despite being the same kind of thing. Behavior now holds all three, Appearance holds only
     * what changes how the launcher looks, and System is left for the genuinely
     * device-level items.
     */
    private val allPages: List<SettingsHubPage> = listOf(
        SettingsHubPage(
            pagerIndex = 0,
            titleRes = R.string.settings_section_home_screen,
            subtitleRes = R.string.settings_hub_home_subtitle,
            iconRes = R.drawable.ic_home,
            group = SettingsHubGroup.LAYOUT,
            keywordRes = R.string.settings_search_kw_home,
        ),
        SettingsHubPage(
            pagerIndex = 1,
            titleRes = R.string.settings_section_app_drawer,
            subtitleRes = R.string.settings_hub_drawer_subtitle,
            iconRes = R.drawable.ic_drawermode,
            group = SettingsHubGroup.LAYOUT,
            keywordRes = R.string.settings_search_kw_drawer,
        ),
        SettingsHubPage(
            pagerIndex = PAGER_DOCK_DIALOG,
            titleRes = R.string.settings_hub_dock_title,
            subtitleRes = R.string.settings_hub_dock_subtitle,
            iconRes = R.drawable.ic_dock_tray,
            group = SettingsHubGroup.LAYOUT,
            keywordRes = R.string.settings_search_kw_dock,
        ),
        SettingsHubPage(
            pagerIndex = 11,
            titleRes = R.string.settings_hub_immersive_title,
            subtitleRes = R.string.settings_hub_immersive_subtitle,
            iconRes = R.drawable.ic_status_bar,
            group = SettingsHubGroup.LAYOUT,
            keywordRes = R.string.settings_search_kw_immersive,
        ),
        SettingsHubPage(
            pagerIndex = 2,
            titleRes = R.string.settings_hub_theme_title,
            subtitleRes = R.string.settings_hub_theme_subtitle,
            iconRes = R.drawable.ic_palette_theme,
            group = SettingsHubGroup.APPEARANCE,
            keywordRes = R.string.settings_search_kw_theme,
        ),
        SettingsHubPage(
            pagerIndex = 7,
            titleRes = R.string.settings_section_icons,
            subtitleRes = R.string.settings_hub_icons_subtitle,
            iconRes = R.drawable.ic_icon_tile,
            group = SettingsHubGroup.APPEARANCE,
            keywordRes = R.string.settings_search_kw_icons,
        ),
        SettingsHubPage(
            pagerIndex = 8,
            titleRes = R.string.settings_hub_typography_title,
            subtitleRes = R.string.settings_hub_typography_subtitle,
            iconRes = R.drawable.ic_fonts,
            group = SettingsHubGroup.APPEARANCE,
            keywordRes = R.string.settings_search_kw_type,
        ),
        SettingsHubPage(
            pagerIndex = 12,
            titleRes = R.string.settings_hub_island_title,
            subtitleRes = R.string.settings_hub_island_subtitle,
            iconRes = R.drawable.ic_island,
            group = SettingsHubGroup.APPEARANCE,
            keywordRes = R.string.settings_search_kw_island,
        ),
        SettingsHubPage(
            pagerIndex = 4,
            titleRes = R.string.settings_page_gestures,
            subtitleRes = R.string.settings_hub_gestures_subtitle,
            iconRes = R.drawable.ic_gesture,
            group = SettingsHubGroup.BEHAVIOUR,
            keywordRes = R.string.settings_search_kw_gestures,
        ),
        SettingsHubPage(
            pagerIndex = 6,
            titleRes = R.string.settings_page_search,
            subtitleRes = R.string.settings_hub_search_subtitle,
            iconRes = R.drawable.outline_manage_search_24,
            group = SettingsHubGroup.BEHAVIOUR,
            keywordRes = R.string.settings_search_kw_search,
        ),
        SettingsHubPage(
            pagerIndex = 3,
            titleRes = R.string.settings_hub_notifications_title,
            subtitleRes = R.string.settings_hub_notifications_subtitle,
            iconRes = R.drawable.ic_notification,
            group = SettingsHubGroup.BEHAVIOUR,
            keywordRes = R.string.settings_search_kw_notifications,
        ),
        SettingsHubPage(
            pagerIndex = 10,
            titleRes = R.string.premium_page_title,
            subtitleRes = R.string.premium_page_subtitle,
            iconRes = R.drawable.ic_bookmark_filled,
            group = SettingsHubGroup.SYSTEM,
            keywordRes = R.string.settings_search_kw_premium,
        ),
        SettingsHubPage(
            pagerIndex = 9,
            titleRes = R.string.licenses_page_title,
            subtitleRes = R.string.licenses_page_subtitle,
            iconRes = R.drawable.ic_info,
            group = SettingsHubGroup.SYSTEM,
            keywordRes = R.string.settings_search_kw_licenses,
        ),
        SettingsHubPage(
            pagerIndex = 5,
            titleRes = R.string.settings_hub_backup_title,
            subtitleRes = R.string.settings_hub_backup_subtitle,
            iconRes = R.drawable.ic_backup,
            group = SettingsHubGroup.SYSTEM,
            keywordRes = R.string.settings_search_kw_backup,
        ),
    )

    /**
     * The pages a user can reach.
     *
     * Premium drops out entirely until Play can sell it — a page whose only purpose is to take
     * money it cannot take has nothing to say. Everything downstream (the group listings, the jump
     * sheet, search) reads this rather than the raw list, so hiding it here hides it everywhere.
     */
    val pages: List<SettingsHubPage> =
        if (PremiumConfig.BILLING_LIVE) allPages
        else allPages.filter { it.titleRes != R.string.premium_page_title }

    /** Falls back to the first page for an unknown index - notably [PAGER_DOCK_DIALOG], which
     *  is never actually shown in the pager. */
    fun pageAt(pagerIndex: Int): SettingsHubPage =
        pages.firstOrNull { it.pagerIndex == pagerIndex } ?: pages.first()

    fun pagesIn(group: SettingsHubGroup): List<SettingsHubPage> =
        pages.filter { it.group == group }

    /**
     * Pages match on title, subtitle and keywords. Settings come from [SettingsSearchIndex] and
     * are ranked: a name that starts with the query, then one that contains it, then a match
     * anywhere in its subtitle, keywords or page name. Every word typed has to match.
     */
    fun search(context: Context, rawQuery: String): SettingsSearchResult {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) {
            return SettingsSearchResult(emptyList(), emptyList())
        }
        val terms = query.split(' ').filter { it.isNotBlank() }
        val reachable = pages.map { it.pagerIndex }.toSet()
        val controls = SettingsSearchIndex.controls
            .filter { it.pagerIndex in reachable }
            .mapNotNull { control -> scoreControl(context, control, query, terms)?.let { control to it } }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { context.getString(it.titleRes).lowercase() to it.pagerIndex }
            .take(MAX_CONTROL_RESULTS)
        return SettingsSearchResult(
            pages = pages.filter { page ->
                val haystack = listOf(page.titleRes, page.subtitleRes, page.keywordRes)
                    .joinToString(" ") { context.getString(it) }.lowercase()
                terms.all { haystack.contains(it) }
            },
            controls = controls,
        )
    }

    private fun scoreControl(context: Context, control: SettingsSearchControl, query: String, terms: List<String>): Int? {
        val title = context.getString(control.titleRes).lowercase()
        if (title.startsWith(query)) return 3
        if (title.contains(query)) return 2
        val haystack = listOf(control.titleRes, control.subtitleRes, control.keywordRes, pageAt(control.pagerIndex).titleRes)
            .filter { it != 0 }
            .joinToString(" ") { context.getString(it) }
            .lowercase()
        return if (terms.all { haystack.contains(it) }) 1 else null
    }

    /** Deep-link section names (`SettingsActivity.EXTRA_SECTION`) to pager positions; -1 if unknown. */
    fun sectionIndex(section: String?): Int = when (section?.lowercase()) {
        "home", "homescreen" -> 0
        "drawer" -> 1
        "theme" -> 2
        "notifications", "notification" -> 3
        "gestures", "gesture" -> 4
        "backup", "backup_restore" -> 5
        "search" -> 6
        "icons", "icon" -> 7
        "typography", "font", "fonts" -> 8
        "licenses", "license", "oss" -> 9
        "premium", "subscribe", "upgrade" -> 10
        "immersive", "status", "statusbar" -> 11
        "island", "nexusisland", "dynamicisland" -> 12
        else -> -1
    }

    private const val MAX_CONTROL_RESULTS = 30
}
