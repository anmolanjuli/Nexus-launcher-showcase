package com.nexus.launcher.data.prefs

import androidx.datastore.preferences.core.MutablePreferences

/**
 * Writes a full [NexusSettingsData] snapshot into DataStore preferences — the Backup/Restore
 * write path. Extracted out of [SettingsRepository.applySnapshot] purely to keep that file under
 * the 400-line limit; no behavior change.
 */
object SettingsSnapshotWriter {

    /**
     * @param includeWallpaperAndPages writes the wallpaper treatment and page count too.
     *   Off for the Settings draft/Apply path, which reads its draft once and would otherwise
     *   revert a wallpaper the user changed from WallpaperSheet while that draft was open. On
     *   for backup restore, where the snapshot is the whole intended state.
     *   [NexusSettingsKeys.BACKUP_FOLDER_URI] is never written from a snapshot — it points at a
     *   folder on this device and must survive restoring someone else's backup.
     */
    fun write(
        prefs: MutablePreferences,
        s: NexusSettingsData,
        includeWallpaperAndPages: Boolean = false
    ) {
        prefs[NexusSettingsKeys.HOME_COLUMNS] = s.homeColumns.coerceIn(
            NexusDefaults.HOME_COLUMNS_MIN, NexusDefaults.HOME_COLUMNS_MAX
        )
        prefs[NexusSettingsKeys.HOME_ROWS] = s.homeRows.coerceIn(
            NexusDefaults.HOME_ROWS_MIN, NexusDefaults.HOME_ROWS_MAX
        )
        prefs[NexusSettingsKeys.HOME_PADDING_LR] = s.homePaddingLeftRightDp.coerceIn(-4f, 64f)
        prefs[NexusSettingsKeys.HOME_PADDING_TB] = s.homePaddingTopBottomDp.coerceIn(-32f, 64f)
        prefs[NexusSettingsKeys.HOME_GAP_H] = s.homeGapHorizontalDp.coerceIn(
            NexusDefaults.HOME_GAP_MIN, NexusDefaults.HOME_GAP_MAX
        )
        prefs[NexusSettingsKeys.HOME_GAP_V] = s.homeGapVerticalDp.coerceIn(
            NexusDefaults.HOME_GAP_MIN, NexusDefaults.HOME_GAP_MAX
        )
        prefs[NexusSettingsKeys.HOME_ICON_SIZE_MULTIPLIER] = s.homeIconSizeMultiplier.coerceIn(
            NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MIN,
            NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MAX
        )
        prefs[NexusSettingsKeys.HOME_SHOW_LABELS] = s.homeShowLabels
        prefs[NexusSettingsKeys.HOME_TWO_LINE_LABELS] = s.homeTwoLineLabels
        prefs[NexusSettingsKeys.HOME_SHOW_INDICATOR] = s.homeShowIndicator
        prefs[NexusSettingsKeys.IMMERSIVE_MODE] = s.immersiveMode
        prefs[NexusSettingsKeys.HOME_SHOW_DOCK] = s.homeShowDock
        prefs[NexusSettingsKeys.STATUS_CLOCK_STYLE] = s.statusClockStyle
        prefs[NexusSettingsKeys.STATUS_NOTIFICATION_STYLE] = s.statusNotificationStyle
        prefs[NexusSettingsKeys.STATUS_BATTERY_STYLE] = s.statusBatteryStyle
        prefs[NexusSettingsKeys.STATUS_BATTERY_PERCENT] = s.statusBatteryPercent
        prefs[NexusSettingsKeys.STATUS_SIGNAL_STYLE] = s.statusSignalStyle
        prefs[NexusSettingsKeys.STATUS_WIFI_STYLE] = s.statusWifiStyle
        prefs[NexusSettingsKeys.STATUS_BAR_ENABLED] = s.statusBarEnabled
        prefs[NexusSettingsKeys.STATUS_BAR_POSITION] = s.statusBarPosition
        prefs[NexusSettingsKeys.STATUS_BAR_HEIGHT_DP] = s.statusBarHeightDp
        prefs[NexusSettingsKeys.STATUS_BAR_BACKGROUND] = s.statusBarBackground
        prefs[NexusSettingsKeys.STATUS_BAR_PADDING_DP] = s.statusBarPaddingDp
        prefs[NexusSettingsKeys.STATUS_SEGMENTED_PILLS] = s.statusSegmentedPills
        prefs[NexusSettingsKeys.STATUS_BAR_FADE] = s.statusBarFade
        prefs[NexusSettingsKeys.STATUS_FONT_KEY] = s.statusFontKey
        prefs[NexusSettingsKeys.STATUS_PILL_OPACITY] = s.statusPillOpacity
        prefs[NexusSettingsKeys.STATUS_CLOCK_POSITION] = s.statusClockPosition
        prefs[NexusSettingsKeys.STATUS_DATE_FORMAT] = s.statusDateFormat
        prefs[NexusSettingsKeys.STATUS_MAX_ICONS] = s.statusMaxIcons
        prefs[NexusSettingsKeys.STATUS_SHOW_SILENT] = s.statusShowSilent
        prefs[NexusSettingsKeys.STATUS_GROUP_BY_APP] = s.statusGroupByApp
        prefs[NexusSettingsKeys.STATUS_LOW_BATTERY_PERCENT] = s.statusLowBatteryPercent
        prefs[NexusSettingsKeys.STATUS_CHARGING_ANIMATION] = s.statusChargingAnimation
        prefs[NexusSettingsKeys.STATUS_SHOW_DATA_TYPE] = s.statusShowDataType
        prefs[NexusSettingsKeys.STATUS_SHOW_CARRIER] = s.statusShowCarrier
        prefs[NexusSettingsKeys.STATUS_SHOW_SSID] = s.statusShowSsid
        prefs[NexusSettingsKeys.STATUS_SHOW_BAND] = s.statusShowBand
        prefs[NexusSettingsKeys.NOTIFICATION_HISTORY] = s.notificationHistory
        prefs[NexusSettingsKeys.NOTIFICATION_RETENTION_HOURS] = s.notificationRetentionHours
        prefs[NexusSettingsKeys.STATUS_SHOW_CLOCK] = s.statusShowClock
        prefs[NexusSettingsKeys.STATUS_SHOW_NOTIFICATIONS] = s.statusShowNotifications
        prefs[NexusSettingsKeys.STATUS_SHOW_WIFI] = s.statusShowWifi
        prefs[NexusSettingsKeys.STATUS_SHOW_SIGNAL] = s.statusShowSignal
        prefs[NexusSettingsKeys.STATUS_SHOW_BATTERY] = s.statusShowBattery
        prefs[NexusSettingsKeys.HOME_SHOW_FEED] = s.homeShowFeed
        prefs[NexusSettingsKeys.FEED_REFRESH_INTERVAL] = s.feedRefreshInterval
        prefs[NexusSettingsKeys.FEED_EINK_MODE] = s.feedEInkMode
        prefs[NexusSettingsKeys.FEED_EINK_DARK] = s.feedEInkDark
        prefs[NexusSettingsKeys.DRAWER_COLUMNS] = s.drawerColumns.coerceIn(2, 10)
        prefs[NexusSettingsKeys.DRAWER_ICON_SIZE_MULTIPLIER] = s.drawerIconSizeMultiplier.coerceIn(0.4f, 0.85f)
        prefs[NexusSettingsKeys.DRAWER_SHOW_LABELS] = s.drawerShowLabels
        prefs[NexusSettingsKeys.DRAWER_TWO_LINE_LABELS] = s.drawerTwoLineLabels
        prefs[NexusSettingsKeys.DRAWER_SHOW_CATEGORY_BAR] = s.drawerShowCategoryBar
        prefs[NexusSettingsKeys.DRAWER_SHOW_RAIL] = s.drawerShowRail
        prefs[NexusSettingsKeys.DRAWER_SORT_ORDER] =
            if (s.drawerSortOrder in setOf("az", "za", "new")) s.drawerSortOrder else NexusDefaults.DRAWER_SORT_ORDER
        prefs[NexusSettingsKeys.DRAWER_SHOW_SEARCH_PILL] = s.drawerShowSearchPill
        prefs[NexusSettingsKeys.DRAWER_CATEGORY_MODE] =
            if (s.drawerCategoryMode in setOf("in_pill", "dropdown", "strip")) s.drawerCategoryMode
            else NexusDefaults.DRAWER_CATEGORY_MODE
        prefs[NexusSettingsKeys.DRAWER_CATEGORY_POSITION] =
            if (s.drawerCategoryPosition in setOf("top", "bottom")) s.drawerCategoryPosition
            else NexusDefaults.DRAWER_CATEGORY_POSITION
        prefs[NexusSettingsKeys.DRAWER_SEARCH_BAR_POSITION] =
            if (s.drawerSearchBarPosition in setOf("top", "bottom")) s.drawerSearchBarPosition
            else NexusDefaults.DRAWER_SEARCH_BAR_POSITION
        prefs[NexusSettingsKeys.DRAWER_LAYOUT] =
            if (s.drawerLayout in setOf("grid", "list", "list_1", "list_2")) s.drawerLayout else NexusDefaults.DRAWER_LAYOUT
        prefs[NexusSettingsKeys.DRAWER_GRID_OR_LIST] =
            if (s.drawerGridOrList in DrawerLayoutModes.GRID_OR_LIST) s.drawerGridOrList
            else NexusDefaults.DRAWER_GRID_OR_LIST
        prefs[NexusSettingsKeys.DRAWER_LIST_COLUMNS] = s.drawerListColumns.coerceIn(1, 2)
        prefs[NexusSettingsKeys.DRAWER_CATEGORY_LAYOUT] =
            if (s.drawerCategoryLayout in DrawerLayoutModes.CATEGORY_LAYOUTS) s.drawerCategoryLayout
            else NexusDefaults.DRAWER_CATEGORY_LAYOUT
        prefs[NexusSettingsKeys.DRAWER_TRANSITION] =
            if (s.drawerTransition in setOf("default", "cube", "zoom", "tilt", "stack")) s.drawerTransition
            else NexusDefaults.DRAWER_TRANSITION
        // A restored backup always carries an explicit NexusSettingsData snapshot (never a bare
        // legacy blob), so the migration flag is set here too — otherwise the next cold start's
        // migrateDrawerLayoutIfNeeded() would overwrite these just-restored values by re-deriving
        // from the also-restored legacy drawerLayout field.
        prefs[NexusSettingsKeys.DRAWER_LAYOUT_MIGRATED_V1] = true
        prefs[NexusSettingsKeys.ACCENT_COLOR] = s.accentColor
        prefs[NexusSettingsKeys.BADGE_STYLE_APP] = s.badgeStyleApp
        prefs[NexusSettingsKeys.BADGE_STYLE_FOLDER] = s.badgeStyleFolder
        prefs[NexusSettingsKeys.PAGE_TRANSITION] = s.pageTransition
        prefs[NexusSettingsKeys.ICON_PACK] = s.iconPack
        prefs[NexusSettingsKeys.ICON_SHAPE] = s.iconShape
        prefs[NexusSettingsKeys.GLOBAL_SWIPE_UP] = s.globalSwipeUp
        prefs[NexusSettingsKeys.GLOBAL_SWIPE_DOWN] = s.globalSwipeDown
        prefs[NexusSettingsKeys.GLOBAL_TWO_FINGER_SWIPE_UP] = s.globalTwoFingerSwipeUp
        prefs[NexusSettingsKeys.GLOBAL_TWO_FINGER_SWIPE_DOWN] = s.globalTwoFingerSwipeDown
        prefs[NexusSettingsKeys.ICON_THEMING] = s.iconTheming
        prefs[NexusSettingsKeys.FROSTED_GLASS_ENABLED] = s.frostedGlassEnabled
        prefs[NexusSettingsKeys.UI_STYLE_MODE] = s.uiStyleMode
        prefs[NexusSettingsKeys.ISLAND_ENABLED] = s.islandEnabled
        prefs[NexusSettingsKeys.ISLAND_POSITION] =
            if (s.islandPosition in setOf("center", "left", "right")) s.islandPosition
            else NexusDefaults.ISLAND_POSITION
        prefs[NexusSettingsKeys.ISLAND_COMPACT_SIZE] =
            if (s.islandCompactSize in setOf("small", "medium", "large")) s.islandCompactSize
            else NexusDefaults.ISLAND_COMPACT_SIZE
        prefs[NexusSettingsKeys.ISLAND_ANIM_SPEED] = s.islandAnimSpeed.coerceIn(0.5f, 1.5f)
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_ACTIVITY] = s.islandTriggerActivity
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_MUSIC] = s.islandTriggerMusic
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_TIMER] = s.islandTriggerTimer
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_STOPWATCH] = s.islandTriggerStopwatch
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_BATTERY_LOW] = s.islandTriggerBatteryLow
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_CHARGING] = s.islandTriggerCharging
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_DND] = s.islandTriggerDnd
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_BLUETOOTH] = s.islandTriggerBluetooth
        prefs[NexusSettingsKeys.ISLAND_TRIGGER_CALENDAR] = s.islandTriggerCalendar
        prefs[NexusSettingsKeys.ISLAND_WIDTH_DP] = s.islandWidthDp.coerceIn(50, 260)
        prefs[NexusSettingsKeys.ISLAND_HEIGHT_DP] = s.islandHeightDp.coerceIn(20, 44)
        prefs[NexusSettingsKeys.ISLAND_X_OFFSET_DP] = s.islandXOffsetDp.coerceIn(-100, 100)
        prefs[NexusSettingsKeys.ISLAND_Y_OFFSET_DP] = s.islandYOffsetDp.coerceIn(-25, 35)
        prefs[NexusSettingsKeys.ISLAND_HAPTICS] = s.islandHaptics
        if (includeWallpaperAndPages) {
            prefs[NexusSettingsKeys.HOME_PAGE_COUNT] = s.homePageCount.coerceAtLeast(1)
            prefs[NexusSettingsKeys.MATCH_WALLPAPER_COLOR] = s.matchWallpaperColor
            prefs[NexusSettingsKeys.WALLPAPER_TYPE] = s.wallpaperType
            prefs[NexusSettingsKeys.WALLPAPER_SOLID_COLOR] = s.wallpaperSolidColor
            prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_START] = s.wallpaperGradientStart
            prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_END] = s.wallpaperGradientEnd
            prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_DIRECTION] = s.wallpaperGradientDirection
            prefs[NexusSettingsKeys.WALLPAPER_GALLERY_PATH] = s.wallpaperGalleryPath
            prefs[NexusSettingsKeys.WALLPAPER_BLUR] = s.wallpaperBlur
            prefs[NexusSettingsKeys.WALLPAPER_TINT_COLOR] = s.wallpaperTintColor
            prefs[NexusSettingsKeys.WALLPAPER_TINT_STRENGTH] = s.wallpaperTintStrength
        }
    }
}
