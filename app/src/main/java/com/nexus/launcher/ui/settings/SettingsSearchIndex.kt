package com.nexus.launcher.ui.settings

import com.nexus.launcher.R

/**
 * Every individual setting the settings search can find, page by page.
 *
 * Search opens the owning page and [SettingsSearchReveal] finds the row by the label it shows,
 * so an entry only names the row's title string — the pages carry no ids or tags for it. When a
 * row is added, renamed or moved to another page, change its entry here too; a stale entry still
 * opens the right page, it just cannot point at the row.
 */
object SettingsSearchIndex {

    private const val HOME = 0
    private const val DRAWER = 1
    private const val THEME = 2
    private const val NOTIFICATIONS = 3
    private const val GESTURES = 4
    private const val BACKUP = 5
    private const val SEARCH = 6
    private const val ICONS = 7
    private const val TYPOGRAPHY = 8
    private const val DOCK = SettingsHubCatalog.PAGER_DOCK_DIALOG
    private const val IMMERSIVE = 11
    private const val ISLAND = 12

    private fun c(titleRes: Int, page: Int, subtitleRes: Int = 0, keywordRes: Int = 0) =
        SettingsSearchControl(titleRes, page, keywordRes, subtitleRes)

    val controls: List<SettingsSearchControl> = listOf(
        // Home screen
        c(R.string.home_settings_grid_layout_title, HOME, keywordRes = R.string.settings_search_kw_columns),
        c(R.string.home_settings_columns, HOME, R.string.home_settings_columns_subtitle),
        c(R.string.home_settings_rows, HOME, R.string.home_settings_rows_subtitle),
        c(R.string.home_settings_icon_size, HOME, R.string.home_settings_icon_size_subtitle, R.string.settings_search_kw_icon_size),
        c(R.string.home_settings_app_label, HOME, R.string.home_settings_app_label_subtitle),
        c(R.string.settings_two_line_labels, HOME, R.string.settings_two_line_labels_subtitle, R.string.settings_search_kw_two_line),
        c(R.string.home_settings_page_indicator, HOME, R.string.home_settings_page_indicator_subtitle),
        c(R.string.home_settings_show_dock, HOME, R.string.home_settings_show_dock_subtitle),
        c(R.string.home_settings_screen_spacing_title, HOME, keywordRes = R.string.settings_search_kw_spacing),
        c(R.string.home_settings_padding_horizontal, HOME, R.string.home_settings_padding_horizontal_subtitle),
        c(R.string.home_settings_padding_vertical, HOME, R.string.home_settings_padding_vertical_subtitle),
        c(R.string.home_settings_gap_horizontal, HOME, R.string.home_settings_gap_horizontal_subtitle),
        c(R.string.home_settings_gap_vertical, HOME, R.string.home_settings_gap_vertical_subtitle),
        c(R.string.home_settings_transitions_title, HOME),
        c(R.string.home_settings_page_transition, HOME, keywordRes = R.string.settings_search_kw_transitions),
        c(R.string.home_settings_reduce_motion, HOME, R.string.home_settings_reduce_motion_subtitle),
        c(R.string.home_settings_transition_speed, HOME, R.string.home_settings_transition_speed_subtitle),
        c(R.string.home_settings_news_feed_title, HOME),
        c(R.string.home_settings_show_feed, HOME, R.string.home_settings_show_feed_subtitle),

        // Immersive Mode (status row)
        c(R.string.home_settings_immersive, IMMERSIVE, R.string.home_settings_immersive_subtitle, R.string.settings_search_kw_immersive),
        c(R.string.home_settings_status_info, IMMERSIVE, R.string.home_settings_status_info_subtitle, R.string.settings_search_kw_immersive),
        c(R.string.status_bar_enabled, IMMERSIVE, R.string.status_bar_enabled_subtitle),
        c(R.string.status_bar_height, IMMERSIVE),
        c(R.string.status_bar_background, IMMERSIVE),
        c(R.string.status_pill_opacity, IMMERSIVE, R.string.status_pill_opacity_subtitle),
        c(R.string.status_segmented_pills, IMMERSIVE, R.string.status_segmented_pills_subtitle),
        c(R.string.status_bar_fade, IMMERSIVE, R.string.status_bar_fade_subtitle),
        c(R.string.status_bar_font, IMMERSIVE, R.string.status_font_follow_subtitle),

        // App drawer
        c(R.string.drawer_settings_layout_title, DRAWER, R.string.drawer_settings_layout_subtitle),
        c(R.string.drawer_grid_or_list_label, DRAWER),
        c(R.string.home_settings_columns, DRAWER),
        c(R.string.home_settings_icon_size, DRAWER, keywordRes = R.string.settings_search_kw_icon_size),
        c(R.string.home_settings_app_label, DRAWER),
        c(R.string.settings_two_line_labels, DRAWER, keywordRes = R.string.settings_search_kw_two_line),
        c(R.string.drawer_sort_order_label, DRAWER),
        c(R.string.drawer_settings_search_navigation_title, DRAWER),
        c(R.string.drawer_show_search_pill_label, DRAWER),
        c(R.string.drawer_search_bar_position_label, DRAWER),
        c(R.string.drawer_categories_label, DRAWER),
        c(R.string.drawer_category_style_label, DRAWER),
        c(R.string.drawer_category_position_label, DRAWER),
        c(R.string.drawer_side_rail_label, DRAWER),
        c(R.string.drawer_settings_hidden_apps_title, DRAWER, R.string.drawer_settings_hidden_apps_subtitle, R.string.settings_search_kw_hidden),

        // Dock (a sheet, not a page)
        c(R.string.dock_settings_height, DOCK, keywordRes = R.string.settings_search_kw_dock),
        c(R.string.dock_settings_icon_size, DOCK),
        c(R.string.dock_settings_max_icons, DOCK),
        c(R.string.dock_settings_corner_radius, DOCK),
        c(R.string.dock_settings_show_labels, DOCK),
        c(R.string.dock_settings_font_size, DOCK),
        c(R.string.dock_settings_show_search, DOCK),
        c(R.string.dock_settings_opacity, DOCK),
        c(R.string.dock_bg_solid_color, DOCK),
        c(R.string.expressive_gradient, DOCK),
        c(R.string.folder_edit_glass_refraction, DOCK),

        // Theme
        c(R.string.theme_mode_label, THEME, keywordRes = R.string.settings_search_kw_theme),
        c(R.string.settings_section_calm_palette, THEME),
        c(R.string.settings_section_accent_color, THEME, keywordRes = R.string.settings_search_kw_accent),
        c(R.string.settings_section_background_layer, THEME),
        c(R.string.settings_section_color_accessibility, THEME),
        c(R.string.settings_ui_option, THEME, R.string.settings_ui_option_subtitle),
        c(R.string.frost_capture_title, THEME, R.string.frost_capture_subtitle),

        // Icons
        c(R.string.settings_global_appearance, ICONS),
        c(R.string.icon_shape_row_title, ICONS, keywordRes = R.string.settings_search_kw_icons),
        c(R.string.settings_icons_pack_label, ICONS, keywordRes = R.string.settings_search_kw_icons),
        c(R.string.settings_adaptive_styling, ICONS, R.string.settings_adaptive_styling_subtitle),
        c(R.string.settings_themed_icons, ICONS, R.string.settings_themed_icons_subtitle),

        // Typography
        c(R.string.settings_section_font, TYPOGRAPHY, keywordRes = R.string.settings_search_kw_type),
        c(R.string.font_custom_section, TYPOGRAPHY),
        c(R.string.settings_section_language, TYPOGRAPHY, keywordRes = R.string.settings_search_kw_type),

        // Nexus Island
        c(R.string.island_enable, ISLAND, R.string.island_enable_subtitle, R.string.settings_search_kw_island),
        c(R.string.island_position, ISLAND),
        c(R.string.island_compact_size, ISLAND),
        c(R.string.island_anim_speed, ISLAND, R.string.island_anim_speed_subtitle),
        c(R.string.island_triggers_title, ISLAND, keywordRes = R.string.settings_search_kw_island),

        // Gestures
        c(R.string.gestures_one_finger_title, GESTURES, keywordRes = R.string.settings_search_kw_gestures),
        c(R.string.gesture_swipe_up, GESTURES),
        c(R.string.gesture_swipe_down, GESTURES),
        c(R.string.gestures_two_finger_title, GESTURES),
        c(R.string.gesture_two_finger_swipe_up, GESTURES),
        c(R.string.gesture_two_finger_swipe_down, GESTURES),

        // Search
        c(R.string.search_micro_actions_title, SEARCH, R.string.search_micro_actions_desc),
        c(R.string.search_calc_title, SEARCH, R.string.search_calc_desc),
        c(R.string.search_conv_title, SEARCH, R.string.search_conv_desc),
        c(R.string.search_sources_title, SEARCH, R.string.search_sources_desc),
        c(R.string.search_contacts_title, SEARCH, R.string.search_contacts_desc),
        c(R.string.search_web_title, SEARCH, R.string.search_web_desc),
        c(R.string.search_maps_title, SEARCH, R.string.search_maps_desc),

        // Notifications
        c(R.string.badge_header_title, NOTIFICATIONS, keywordRes = R.string.settings_search_kw_badges),
        c(R.string.badge_app_icon_title, NOTIFICATIONS),
        c(R.string.badge_folder_title, NOTIFICATIONS),
        c(R.string.settings_section_badge_color, NOTIFICATIONS),
        c(R.string.system_permissions_title, NOTIFICATIONS, keywordRes = R.string.settings_search_kw_notifications),
        c(R.string.notification_access_title, NOTIFICATIONS),
        c(R.string.storage_access_title, NOTIFICATIONS),
        c(R.string.app_usage_title, NOTIFICATIONS),
        c(R.string.location_access_title, NOTIFICATIONS),
        c(R.string.accessibility_service_title, NOTIFICATIONS),

        // Backup and restore
        c(R.string.backup_location_title, BACKUP, keywordRes = R.string.settings_search_kw_backup),
        c(R.string.backup_create_action, BACKUP, keywordRes = R.string.settings_search_kw_backup),
        c(R.string.import_nova_title, BACKUP, keywordRes = R.string.settings_search_kw_nova),
    )
}
