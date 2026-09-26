package com.nexus.launcher.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object NexusSettingsKeys {
    val HOME_COLUMNS = intPreferencesKey("home_columns")
    val HOME_ROWS = intPreferencesKey("home_rows")
    val HOME_PADDING_LR = floatPreferencesKey("home_padding_lr")
    val HOME_PADDING_TB = floatPreferencesKey("home_padding_tb")
    val HOME_GAP_H = floatPreferencesKey("home_gap_h")
    val HOME_GAP_V = floatPreferencesKey("home_gap_v")
    val HOME_ICON_SIZE_MULTIPLIER = floatPreferencesKey("home_icon_size_multiplier")
    val HOME_SHOW_LABELS = booleanPreferencesKey("home_show_labels")
    val HOME_TWO_LINE_LABELS = booleanPreferencesKey("home_two_line_labels")
    val HOME_SHOW_INDICATOR = booleanPreferencesKey("home_show_indicator")
    val IMMERSIVE_MODE = booleanPreferencesKey("immersive_mode")
    val HOME_SHOW_DOCK = booleanPreferencesKey("home_show_dock")
    val STATUS_CLOCK_STYLE = stringPreferencesKey("status_clock_style")
    val STATUS_NOTIFICATION_STYLE = stringPreferencesKey("status_notification_style")
    val STATUS_BATTERY_STYLE = stringPreferencesKey("status_battery_style")
    val STATUS_BATTERY_PERCENT = stringPreferencesKey("status_battery_percent")
    val STATUS_SIGNAL_STYLE = stringPreferencesKey("status_signal_style")
    val STATUS_WIFI_STYLE = stringPreferencesKey("status_wifi_style")

    // The status row itself: whether it is drawn at all, where, how tall and on what.
    val STATUS_BAR_ENABLED = booleanPreferencesKey("status_bar_enabled")
    val STATUS_BAR_POSITION = stringPreferencesKey("status_bar_position")
    val STATUS_BAR_HEIGHT_DP = androidx.datastore.preferences.core.intPreferencesKey("status_bar_height_dp")
    val STATUS_BAR_BACKGROUND = stringPreferencesKey("status_bar_background")
    val STATUS_BAR_PADDING_DP = androidx.datastore.preferences.core.intPreferencesKey("status_bar_padding_dp")
    val STATUS_SEGMENTED_PILLS = booleanPreferencesKey("status_segmented_pills")
    val STATUS_BAR_FADE = booleanPreferencesKey("status_bar_fade")
    val STATUS_FONT_KEY = stringPreferencesKey("status_font_key")
    val STATUS_PILL_OPACITY = intPreferencesKey("status_pill_opacity")

    // Per-module extras.
    val STATUS_CLOCK_POSITION = stringPreferencesKey("status_clock_position")
    val STATUS_DATE_FORMAT = stringPreferencesKey("status_date_format")
    val STATUS_MAX_ICONS = androidx.datastore.preferences.core.intPreferencesKey("status_max_icons")
    val STATUS_SHOW_SILENT = booleanPreferencesKey("status_show_silent")
    val STATUS_GROUP_BY_APP = booleanPreferencesKey("status_group_by_app")
    val STATUS_LOW_BATTERY_PERCENT = androidx.datastore.preferences.core.intPreferencesKey("status_low_battery_percent")
    val STATUS_CHARGING_ANIMATION = booleanPreferencesKey("status_charging_animation")
    val STATUS_SHOW_DATA_TYPE = booleanPreferencesKey("status_show_data_type")
    val STATUS_SHOW_CARRIER = booleanPreferencesKey("status_show_carrier")
    val STATUS_SHOW_SSID = booleanPreferencesKey("status_show_ssid")
    val STATUS_SHOW_BAND = booleanPreferencesKey("status_show_band")
    val NOTIFICATION_HISTORY = booleanPreferencesKey("notification_history")
    val NOTIFICATION_RETENTION_HOURS = androidx.datastore.preferences.core.intPreferencesKey("notification_retention_hours")
    val STATUS_SHOW_CLOCK = booleanPreferencesKey("status_show_clock")
    val STATUS_SHOW_NOTIFICATIONS = booleanPreferencesKey("status_show_notifications")
    val STATUS_SHOW_WIFI = booleanPreferencesKey("status_show_wifi")
    val STATUS_SHOW_SIGNAL = booleanPreferencesKey("status_show_signal")
    val STATUS_SHOW_BATTERY = booleanPreferencesKey("status_show_battery")
    val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
    val DRAWER_ICON_SIZE_MULTIPLIER = floatPreferencesKey("drawer_icon_size_multiplier")
    val DRAWER_SHOW_LABELS = booleanPreferencesKey("drawer_show_labels")
    val DRAWER_TWO_LINE_LABELS = booleanPreferencesKey("drawer_two_line_labels")
    val DRAWER_SHOW_CATEGORY_BAR = booleanPreferencesKey("drawer_show_category_bar")
    val DRAWER_SHOW_RAIL = booleanPreferencesKey("drawer_show_rail")
    val DRAWER_SORT_ORDER = stringPreferencesKey("drawer_sort_order")
    val DRAWER_SHOW_SEARCH_PILL = booleanPreferencesKey("drawer_show_search_pill")
    val DRAWER_SEARCH_BAR_POSITION = stringPreferencesKey("drawer_search_bar_position")
    val DRAWER_CATEGORY_MODE = stringPreferencesKey("drawer_category_mode")
    val DRAWER_CATEGORY_POSITION = stringPreferencesKey("drawer_category_position")
    val DRAWER_LAYOUT = stringPreferencesKey("drawer_layout")
    val DRAWER_GRID_OR_LIST = stringPreferencesKey("drawer_grid_or_list")
    val DRAWER_LIST_COLUMNS = intPreferencesKey("drawer_list_columns")
    /** Spatial / Strip / List presentation when [DRAWER_GRID_OR_LIST] is `categories`. */
    val DRAWER_CATEGORY_LAYOUT = stringPreferencesKey("drawer_category_layout")
    val DRAWER_TRANSITION = stringPreferencesKey("drawer_transition")
    /** Guards the one-time [SettingsRepository.migrateDrawerLayoutIfNeeded] split of the legacy
     *  [DRAWER_LAYOUT] value into the three keys above — never set outside that function. */
    val DRAWER_LAYOUT_MIGRATED_V1 = booleanPreferencesKey("drawer_layout_migrated_v1")
    /** Guards the one-time [MinimalUiCleanup] removal of the deleted Minimal UI preference keys —
     *  never set outside that function. */
    val LEGACY_MINIMAL_CLEANUP_DONE = booleanPreferencesKey("legacy_minimal_cleanup_done")
    /** SAF tree URI of the folder backups are written to and listed from. Empty = not chosen. */
    val BACKUP_FOLDER_URI = stringPreferencesKey("backup_folder_uri")
    val ACCENT_COLOR = stringPreferencesKey("accent_color")
    val MATCH_WALLPAPER_COLOR = booleanPreferencesKey("match_wallpaper_color")
    val WALLPAPER_TYPE = stringPreferencesKey("wallpaper_type")
    val WALLPAPER_SOLID_COLOR = stringPreferencesKey("wallpaper_solid_color")
    val WALLPAPER_GRADIENT_START = stringPreferencesKey("wallpaper_gradient_start")
    val WALLPAPER_GRADIENT_END = stringPreferencesKey("wallpaper_gradient_end")
    val WALLPAPER_GRADIENT_DIRECTION = stringPreferencesKey("wallpaper_gradient_direction")
    val WALLPAPER_GALLERY_PATH = stringPreferencesKey("wallpaper_gallery_path")
    val WALLPAPER_BLUR = floatPreferencesKey("wallpaper_blur")
    val WALLPAPER_TINT_COLOR = stringPreferencesKey("wallpaper_tint_color")
    val WALLPAPER_TINT_STRENGTH = floatPreferencesKey("wallpaper_tint_strength")
    val BADGE_STYLE_APP = intPreferencesKey("badge_style_app_int")
    val BADGE_STYLE_FOLDER = intPreferencesKey("badge_style_folder_int")
    val HOME_PAGE_COUNT = intPreferencesKey("home_page_count")
    val PAGE_TRANSITION = stringPreferencesKey("page_transition")
    val ICON_PACK = stringPreferencesKey("icon_pack")
    val ICON_SHAPE = intPreferencesKey("icon_shape_int")
    val GLOBAL_SWIPE_UP = stringPreferencesKey("global_swipe_up")
    val GLOBAL_SWIPE_DOWN = stringPreferencesKey("global_swipe_down")
    val GLOBAL_TWO_FINGER_SWIPE_UP = stringPreferencesKey("global_two_finger_swipe_up")
    val GLOBAL_TWO_FINGER_SWIPE_DOWN = stringPreferencesKey("global_two_finger_swipe_down")
    val HOME_SHOW_FEED = booleanPreferencesKey("home_show_feed")
    val FEED_REFRESH_INTERVAL = stringPreferencesKey("feed_refresh_interval")
    val FEED_EINK_MODE = booleanPreferencesKey("feed_eink_mode")
    val FEED_EINK_DARK = booleanPreferencesKey("feed_eink_dark")
    val ICON_THEMING = booleanPreferencesKey("icon_theming")
    val FROSTED_GLASS_ENABLED = booleanPreferencesKey("frosted_glass_enabled")
    val UI_STYLE_MODE = stringPreferencesKey("ui_style_mode")

    val ISLAND_ENABLED = booleanPreferencesKey("nexus_island_enabled")
    val ISLAND_POSITION = stringPreferencesKey("nexus_island_position")
    val ISLAND_COMPACT_SIZE = stringPreferencesKey("nexus_island_compact_size")
    val ISLAND_ANIM_SPEED = floatPreferencesKey("nexus_island_anim_speed")
    val ISLAND_TRIGGER_ACTIVITY = booleanPreferencesKey("nexus_island_trigger_activity")
    val ISLAND_TRIGGER_MUSIC = booleanPreferencesKey("nexus_island_trigger_music")
    val ISLAND_TRIGGER_TIMER = booleanPreferencesKey("nexus_island_trigger_timer")
    val ISLAND_TRIGGER_STOPWATCH = booleanPreferencesKey("nexus_island_trigger_stopwatch")
    val ISLAND_TRIGGER_BATTERY_LOW = booleanPreferencesKey("nexus_island_trigger_battery_low")
    val ISLAND_TRIGGER_CHARGING = booleanPreferencesKey("nexus_island_trigger_charging")
    val ISLAND_TRIGGER_DND = booleanPreferencesKey("nexus_island_trigger_dnd")
    val ISLAND_TRIGGER_BLUETOOTH = booleanPreferencesKey("nexus_island_trigger_bluetooth")
    val ISLAND_TRIGGER_CALENDAR = booleanPreferencesKey("nexus_island_trigger_calendar")
    val ISLAND_WIDTH_DP = intPreferencesKey("nexus_island_width_dp")
    val ISLAND_HEIGHT_DP = intPreferencesKey("nexus_island_height_dp")
    val ISLAND_X_OFFSET_DP = intPreferencesKey("nexus_island_x_offset_dp")
    val ISLAND_Y_OFFSET_DP = intPreferencesKey("nexus_island_y_offset_dp")
    val ISLAND_HAPTICS = booleanPreferencesKey("nexus_island_haptics")
}

object NexusDefaults {
    const val HOME_COLUMNS_MIN = 4
    const val HOME_COLUMNS_MAX = 12
    const val HOME_ROWS_MIN = 5
    const val HOME_ROWS_MAX = 15
    const val HOME_COLUMNS = 6
    const val HOME_ROWS = 10
    const val HOME_PADDING_LEFT_RIGHT = 0f
    const val HOME_PADDING_TOP_BOTTOM = 0f
    const val HOME_GAP_HORIZONTAL = 0f
    const val HOME_GAP_VERTICAL = 0f
    const val HOME_GAP_MIN = 0f
    const val HOME_GAP_MAX = 64f
    const val HOME_ICON_SIZE_MULTIPLIER = 0.65f
    const val HOME_ICON_SIZE_MULTIPLIER_MIN = 0.4f
    const val HOME_ICON_SIZE_MULTIPLIER_MAX = 1.0f
    const val HOME_SHOW_LABELS = true
    const val HOME_TWO_LINE_LABELS = false
    const val HOME_SHOW_INDICATOR = true
    const val IMMERSIVE_MODE = false
    const val HOME_SHOW_DOCK = true
    const val STATUS_CLOCK_STYLE = "system"
    const val STATUS_NOTIFICATION_STYLE = "count"
    const val STATUS_BATTERY_STYLE = "bar"
    const val STATUS_BATTERY_PERCENT = "beside"
    const val STATUS_SIGNAL_STYLE = "bars"
    const val STATUS_WIFI_STYLE = "arcs"

    /** Defaults chosen so an existing status row looks exactly as it did before these settings. */
    const val STATUS_BAR_ENABLED = true
    const val STATUS_BAR_POSITION = "top"
    const val STATUS_BAR_HEIGHT_DP = 28
    const val STATUS_BAR_BACKGROUND = "transparent"
    const val STATUS_BAR_PADDING_DP = 0
    const val STATUS_SEGMENTED_PILLS = true
    const val STATUS_BAR_FADE = false
    const val STATUS_FONT_KEY = "follow"
    const val STATUS_PILL_OPACITY = 40
    const val STATUS_CLOCK_POSITION = "left"
    const val STATUS_DATE_FORMAT = "short"
    const val STATUS_MAX_ICONS = 3
    const val STATUS_SHOW_SILENT = true
    const val STATUS_GROUP_BY_APP = false
    const val STATUS_LOW_BATTERY_PERCENT = 20
    const val STATUS_CHARGING_ANIMATION = true
    const val STATUS_SHOW_DATA_TYPE = false
    const val STATUS_SHOW_CARRIER = false
    const val STATUS_SHOW_SSID = false
    const val STATUS_SHOW_BAND = false
    const val NOTIFICATION_HISTORY = false
    const val NOTIFICATION_RETENTION_HOURS = 24
    const val STATUS_SHOW_CLOCK = true
    const val STATUS_SHOW_NOTIFICATIONS = true
    const val STATUS_SHOW_WIFI = true
    const val STATUS_SHOW_SIGNAL = true
    const val STATUS_SHOW_BATTERY = true
    const val DRAWER_COLUMNS = 6
    const val DRAWER_ICON_SIZE_MULTIPLIER = 0.65f
    const val DRAWER_SHOW_LABELS = true
    const val DRAWER_TWO_LINE_LABELS = false
    const val DRAWER_SHOW_CATEGORY_BAR = true
    const val DRAWER_SHOW_RAIL = true
    const val DRAWER_SORT_ORDER = "az" // az | za | new
    const val DRAWER_SHOW_SEARCH_PILL = true
    const val DRAWER_SEARCH_BAR_POSITION = "top" // top | bottom
    const val DRAWER_CATEGORY_MODE = "in_pill" // in_pill | dropdown | strip
    const val DRAWER_CATEGORY_POSITION = "top" // top | bottom
    const val DRAWER_LAYOUT = "grid" // grid | list — legacy, superseded by the two below
    const val DRAWER_GRID_OR_LIST = "grid" // grid | list | categories
    const val DRAWER_LIST_COLUMNS = 1 // 1 | 2
    const val DRAWER_CATEGORY_LAYOUT = "spatial" // spatial | strip | list
    const val DRAWER_TRANSITION = "default" // default | cube | zoom | tilt | stack
    const val LEGACY_MINIMAL_CLEANUP_DONE = false
    const val BACKUP_FOLDER_URI = ""
    /** Harbor — see AccentColorPicker.PRESETS for why these colours. */
    const val ACCENT_COLOR = "#4F8DA6"
    const val MATCH_WALLPAPER_COLOR = false
    const val WALLPAPER_TYPE = "system"
    const val WALLPAPER_SOLID_COLOR = "#0D1117"
    val WALLPAPER_GRADIENT_START = "#240b36"
    val WALLPAPER_GRADIENT_END = "#c31432"
    val WALLPAPER_GRADIENT_DIRECTION = "top_bottom"
    const val WALLPAPER_GALLERY_PATH = ""
    const val WALLPAPER_BLUR = 0f
    const val WALLPAPER_TINT_COLOR = "#00000000"
    const val WALLPAPER_TINT_STRENGTH = 0f
    const val BADGE_STYLE = "dot"
    const val HOME_PAGE_COUNT = 1
    const val PAGE_TRANSITION = "slide"
    const val ICON_PACK = "none"
    const val ICON_SHAPE = -1
    const val HOME_SHOW_FEED = true
    const val FEED_REFRESH_INTERVAL = "1h"
    const val FEED_EINK_MODE = false
    const val FEED_EINK_DARK = false
    const val ICON_THEMING = false
    const val FROSTED_GLASS_ENABLED = false
    /** One of [com.nexus.launcher.ui.glass.FrostedGlassEngine]'s UI_STYLE_* constants. */
    const val UI_STYLE_MODE = "NEUMORPHISM"

    const val ISLAND_ENABLED = false
    const val ISLAND_POSITION = "center"
    const val ISLAND_COMPACT_SIZE = "medium"
    const val ISLAND_ANIM_SPEED = 1f
    const val ISLAND_TRIGGER = true
    const val ISLAND_TRIGGER_ACTIVITY = true
    const val ISLAND_WIDTH_DP = 110
    const val ISLAND_HEIGHT_DP = 28
    const val ISLAND_X_OFFSET_DP = 0
    const val ISLAND_Y_OFFSET_DP = 0
    const val ISLAND_HAPTICS = true
}
