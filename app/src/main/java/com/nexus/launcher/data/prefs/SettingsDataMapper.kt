package com.nexus.launcher.data.prefs

import androidx.datastore.preferences.core.Preferences

/**
 * Maps raw DataStore [Preferences] into typed [NexusSettingsData].
 * Extracted out of [SettingsRepository] to strictly enforce the 400-line file limit.
 */
object SettingsDataMapper {

    fun map(prefs: Preferences): NexusSettingsData {
        val wallpaperType = prefs[NexusSettingsKeys.WALLPAPER_TYPE] ?: NexusDefaults.WALLPAPER_TYPE
        return NexusSettingsData(
            homeColumns = (prefs[NexusSettingsKeys.HOME_COLUMNS] ?: NexusDefaults.HOME_COLUMNS)
                .coerceIn(NexusDefaults.HOME_COLUMNS_MIN, NexusDefaults.HOME_COLUMNS_MAX),
            homeRows = (prefs[NexusSettingsKeys.HOME_ROWS] ?: NexusDefaults.HOME_ROWS)
                .coerceIn(NexusDefaults.HOME_ROWS_MIN, NexusDefaults.HOME_ROWS_MAX),
            homePaddingLeftRightDp = prefs[NexusSettingsKeys.HOME_PADDING_LR] ?: NexusDefaults.HOME_PADDING_LEFT_RIGHT,
            homePaddingTopBottomDp = prefs[NexusSettingsKeys.HOME_PADDING_TB] ?: NexusDefaults.HOME_PADDING_TOP_BOTTOM,
            homeGapHorizontalDp = (prefs[NexusSettingsKeys.HOME_GAP_H] ?: NexusDefaults.HOME_GAP_HORIZONTAL)
                .coerceIn(NexusDefaults.HOME_GAP_MIN, NexusDefaults.HOME_GAP_MAX),
            homeGapVerticalDp = (prefs[NexusSettingsKeys.HOME_GAP_V] ?: NexusDefaults.HOME_GAP_VERTICAL)
                .coerceIn(NexusDefaults.HOME_GAP_MIN, NexusDefaults.HOME_GAP_MAX),
            homeIconSizeMultiplier = (prefs[NexusSettingsKeys.HOME_ICON_SIZE_MULTIPLIER] ?: NexusDefaults.HOME_ICON_SIZE_MULTIPLIER)
                .coerceIn(NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MIN, NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MAX),
            homeShowLabels = prefs[NexusSettingsKeys.HOME_SHOW_LABELS] ?: NexusDefaults.HOME_SHOW_LABELS,
            homeTwoLineLabels = prefs[NexusSettingsKeys.HOME_TWO_LINE_LABELS] ?: NexusDefaults.HOME_TWO_LINE_LABELS,
            homeShowIndicator = prefs[NexusSettingsKeys.HOME_SHOW_INDICATOR] ?: NexusDefaults.HOME_SHOW_INDICATOR,
            immersiveMode = prefs[NexusSettingsKeys.IMMERSIVE_MODE] ?: NexusDefaults.IMMERSIVE_MODE,
            homeShowDock = prefs[NexusSettingsKeys.HOME_SHOW_DOCK] ?: NexusDefaults.HOME_SHOW_DOCK,
            statusClockStyle = prefs[NexusSettingsKeys.STATUS_CLOCK_STYLE] ?: NexusDefaults.STATUS_CLOCK_STYLE,
            statusNotificationStyle = prefs[NexusSettingsKeys.STATUS_NOTIFICATION_STYLE] ?: NexusDefaults.STATUS_NOTIFICATION_STYLE,
            statusBatteryStyle = prefs[NexusSettingsKeys.STATUS_BATTERY_STYLE] ?: NexusDefaults.STATUS_BATTERY_STYLE,
            statusBatteryPercent = prefs[NexusSettingsKeys.STATUS_BATTERY_PERCENT] ?: NexusDefaults.STATUS_BATTERY_PERCENT,
            statusSignalStyle = prefs[NexusSettingsKeys.STATUS_SIGNAL_STYLE] ?: NexusDefaults.STATUS_SIGNAL_STYLE,
            statusWifiStyle = prefs[NexusSettingsKeys.STATUS_WIFI_STYLE] ?: NexusDefaults.STATUS_WIFI_STYLE,
            statusBarEnabled = prefs[NexusSettingsKeys.STATUS_BAR_ENABLED] ?: NexusDefaults.STATUS_BAR_ENABLED,
            statusBarPosition = prefs[NexusSettingsKeys.STATUS_BAR_POSITION] ?: NexusDefaults.STATUS_BAR_POSITION,
            statusBarHeightDp = (prefs[NexusSettingsKeys.STATUS_BAR_HEIGHT_DP] ?: NexusDefaults.STATUS_BAR_HEIGHT_DP)
                .coerceIn(24, 48),
            statusBarBackground = prefs[NexusSettingsKeys.STATUS_BAR_BACKGROUND] ?: NexusDefaults.STATUS_BAR_BACKGROUND,
            statusBarPaddingDp = (prefs[NexusSettingsKeys.STATUS_BAR_PADDING_DP] ?: NexusDefaults.STATUS_BAR_PADDING_DP)
                .coerceIn(0, 24),
            statusSegmentedPills = prefs[NexusSettingsKeys.STATUS_SEGMENTED_PILLS]
                ?: NexusDefaults.STATUS_SEGMENTED_PILLS,
            statusBarFade = prefs[NexusSettingsKeys.STATUS_BAR_FADE] ?: NexusDefaults.STATUS_BAR_FADE,
            statusFontKey = prefs[NexusSettingsKeys.STATUS_FONT_KEY] ?: NexusDefaults.STATUS_FONT_KEY,
            statusPillOpacity = (prefs[NexusSettingsKeys.STATUS_PILL_OPACITY] ?: NexusDefaults.STATUS_PILL_OPACITY)
                .coerceIn(10, 90),
            statusClockPosition = prefs[NexusSettingsKeys.STATUS_CLOCK_POSITION] ?: NexusDefaults.STATUS_CLOCK_POSITION,
            statusDateFormat = prefs[NexusSettingsKeys.STATUS_DATE_FORMAT] ?: NexusDefaults.STATUS_DATE_FORMAT,
            statusMaxIcons = (prefs[NexusSettingsKeys.STATUS_MAX_ICONS] ?: NexusDefaults.STATUS_MAX_ICONS)
                .coerceIn(1, 10),
            statusShowSilent = prefs[NexusSettingsKeys.STATUS_SHOW_SILENT] ?: NexusDefaults.STATUS_SHOW_SILENT,
            statusGroupByApp = prefs[NexusSettingsKeys.STATUS_GROUP_BY_APP] ?: NexusDefaults.STATUS_GROUP_BY_APP,
            statusLowBatteryPercent = (prefs[NexusSettingsKeys.STATUS_LOW_BATTERY_PERCENT]
                ?: NexusDefaults.STATUS_LOW_BATTERY_PERCENT).coerceIn(5, 30),
            statusChargingAnimation = prefs[NexusSettingsKeys.STATUS_CHARGING_ANIMATION] ?: NexusDefaults.STATUS_CHARGING_ANIMATION,
            statusShowDataType = prefs[NexusSettingsKeys.STATUS_SHOW_DATA_TYPE] ?: NexusDefaults.STATUS_SHOW_DATA_TYPE,
            statusShowCarrier = prefs[NexusSettingsKeys.STATUS_SHOW_CARRIER] ?: NexusDefaults.STATUS_SHOW_CARRIER,
            statusShowSsid = prefs[NexusSettingsKeys.STATUS_SHOW_SSID] ?: NexusDefaults.STATUS_SHOW_SSID,
            statusShowBand = prefs[NexusSettingsKeys.STATUS_SHOW_BAND] ?: NexusDefaults.STATUS_SHOW_BAND,
            notificationHistory = prefs[NexusSettingsKeys.NOTIFICATION_HISTORY] ?: NexusDefaults.NOTIFICATION_HISTORY,
            notificationRetentionHours = (prefs[NexusSettingsKeys.NOTIFICATION_RETENTION_HOURS]
                ?: NexusDefaults.NOTIFICATION_RETENTION_HOURS)
                .coerceIn(com.nexus.launcher.service.NotificationHistory.MIN_RETENTION_HOURS,
                    com.nexus.launcher.service.NotificationHistory.MAX_RETENTION_HOURS),
            statusShowClock = prefs[NexusSettingsKeys.STATUS_SHOW_CLOCK] ?: NexusDefaults.STATUS_SHOW_CLOCK,
            statusShowNotifications = prefs[NexusSettingsKeys.STATUS_SHOW_NOTIFICATIONS] ?: NexusDefaults.STATUS_SHOW_NOTIFICATIONS,
            statusShowWifi = prefs[NexusSettingsKeys.STATUS_SHOW_WIFI] ?: NexusDefaults.STATUS_SHOW_WIFI,
            statusShowSignal = prefs[NexusSettingsKeys.STATUS_SHOW_SIGNAL] ?: NexusDefaults.STATUS_SHOW_SIGNAL,
            statusShowBattery = prefs[NexusSettingsKeys.STATUS_SHOW_BATTERY] ?: NexusDefaults.STATUS_SHOW_BATTERY,
            drawerColumns = prefs[NexusSettingsKeys.DRAWER_COLUMNS] ?: NexusDefaults.DRAWER_COLUMNS,
            drawerIconSizeMultiplier = prefs[NexusSettingsKeys.DRAWER_ICON_SIZE_MULTIPLIER] ?: NexusDefaults.DRAWER_ICON_SIZE_MULTIPLIER,
            drawerShowLabels = prefs[NexusSettingsKeys.DRAWER_SHOW_LABELS] ?: NexusDefaults.DRAWER_SHOW_LABELS,
            drawerTwoLineLabels = prefs[NexusSettingsKeys.DRAWER_TWO_LINE_LABELS] ?: NexusDefaults.DRAWER_TWO_LINE_LABELS,
            drawerShowCategoryBar = prefs[NexusSettingsKeys.DRAWER_SHOW_CATEGORY_BAR] ?: NexusDefaults.DRAWER_SHOW_CATEGORY_BAR,
            drawerShowRail = prefs[NexusSettingsKeys.DRAWER_SHOW_RAIL] ?: NexusDefaults.DRAWER_SHOW_RAIL,
            drawerSortOrder = prefs[NexusSettingsKeys.DRAWER_SORT_ORDER] ?: NexusDefaults.DRAWER_SORT_ORDER,
            drawerShowSearchPill = prefs[NexusSettingsKeys.DRAWER_SHOW_SEARCH_PILL] ?: NexusDefaults.DRAWER_SHOW_SEARCH_PILL,
            drawerSearchBarPosition = prefs[NexusSettingsKeys.DRAWER_SEARCH_BAR_POSITION] ?: NexusDefaults.DRAWER_SEARCH_BAR_POSITION,
            drawerCategoryMode = prefs[NexusSettingsKeys.DRAWER_CATEGORY_MODE] ?: NexusDefaults.DRAWER_CATEGORY_MODE,
            drawerCategoryPosition = prefs[NexusSettingsKeys.DRAWER_CATEGORY_POSITION] ?: NexusDefaults.DRAWER_CATEGORY_POSITION,
            drawerLayout = prefs[NexusSettingsKeys.DRAWER_LAYOUT] ?: NexusDefaults.DRAWER_LAYOUT,
            drawerGridOrList = run {
                val raw = prefs[NexusSettingsKeys.DRAWER_GRID_OR_LIST] ?: NexusDefaults.DRAWER_GRID_OR_LIST
                if (raw in DrawerLayoutModes.GRID_OR_LIST) raw else NexusDefaults.DRAWER_GRID_OR_LIST
            },
            drawerListColumns = (prefs[NexusSettingsKeys.DRAWER_LIST_COLUMNS] ?: NexusDefaults.DRAWER_LIST_COLUMNS).coerceIn(1, 2),
            drawerCategoryLayout = run {
                val raw = prefs[NexusSettingsKeys.DRAWER_CATEGORY_LAYOUT]
                    ?: NexusDefaults.DRAWER_CATEGORY_LAYOUT
                if (raw in DrawerLayoutModes.CATEGORY_LAYOUTS) raw
                else NexusDefaults.DRAWER_CATEGORY_LAYOUT
            },
            drawerTransition = run {
                val raw = prefs[NexusSettingsKeys.DRAWER_TRANSITION] ?: NexusDefaults.DRAWER_TRANSITION
                if (raw in setOf("default", "cube", "zoom", "tilt", "stack")) raw else NexusDefaults.DRAWER_TRANSITION
            },
            backupFolderUri = prefs[NexusSettingsKeys.BACKUP_FOLDER_URI] ?: NexusDefaults.BACKUP_FOLDER_URI,
            accentColor = prefs[NexusSettingsKeys.ACCENT_COLOR] ?: NexusDefaults.ACCENT_COLOR,
            matchWallpaperColor = prefs[NexusSettingsKeys.MATCH_WALLPAPER_COLOR] ?: NexusDefaults.MATCH_WALLPAPER_COLOR,
            wallpaperType = wallpaperType,
            wallpaperSolidColor = prefs[NexusSettingsKeys.WALLPAPER_SOLID_COLOR] ?: NexusDefaults.WALLPAPER_SOLID_COLOR,
            wallpaperGradientStart = prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_START] ?: NexusDefaults.WALLPAPER_GRADIENT_START,
            wallpaperGradientEnd = prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_END] ?: NexusDefaults.WALLPAPER_GRADIENT_END,
            wallpaperGradientDirection = prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_DIRECTION] ?: NexusDefaults.WALLPAPER_GRADIENT_DIRECTION,
            wallpaperGalleryPath = prefs[NexusSettingsKeys.WALLPAPER_GALLERY_PATH] ?: NexusDefaults.WALLPAPER_GALLERY_PATH,
            wallpaperBlur = prefs[NexusSettingsKeys.WALLPAPER_BLUR] ?: NexusDefaults.WALLPAPER_BLUR,
            wallpaperTintColor = prefs[NexusSettingsKeys.WALLPAPER_TINT_COLOR] ?: NexusDefaults.WALLPAPER_TINT_COLOR,
            wallpaperTintStrength = prefs[NexusSettingsKeys.WALLPAPER_TINT_STRENGTH] ?: NexusDefaults.WALLPAPER_TINT_STRENGTH,
            badgeStyleApp = prefs[NexusSettingsKeys.BADGE_STYLE_APP] ?: 1,
            badgeStyleFolder = prefs[NexusSettingsKeys.BADGE_STYLE_FOLDER] ?: 1,
            homePageCount = prefs[NexusSettingsKeys.HOME_PAGE_COUNT] ?: NexusDefaults.HOME_PAGE_COUNT,
            pageTransition = prefs[NexusSettingsKeys.PAGE_TRANSITION] ?: NexusDefaults.PAGE_TRANSITION,
            iconPack = prefs[NexusSettingsKeys.ICON_PACK] ?: NexusDefaults.ICON_PACK,
            iconShape = prefs[NexusSettingsKeys.ICON_SHAPE] ?: -1,
            globalSwipeUp = prefs[NexusSettingsKeys.GLOBAL_SWIPE_UP] ?: "OPEN_APP_DRAWER",
            globalSwipeDown = prefs[NexusSettingsKeys.GLOBAL_SWIPE_DOWN] ?: "OPEN_SEARCH",
            globalTwoFingerSwipeUp = prefs[NexusSettingsKeys.GLOBAL_TWO_FINGER_SWIPE_UP] ?: "NONE",
            globalTwoFingerSwipeDown = prefs[NexusSettingsKeys.GLOBAL_TWO_FINGER_SWIPE_DOWN] ?: "NONE",
            homeShowFeed = prefs[NexusSettingsKeys.HOME_SHOW_FEED] ?: NexusDefaults.HOME_SHOW_FEED,
            feedRefreshInterval = prefs[NexusSettingsKeys.FEED_REFRESH_INTERVAL] ?: NexusDefaults.FEED_REFRESH_INTERVAL,
            feedEInkMode = prefs[NexusSettingsKeys.FEED_EINK_MODE] ?: NexusDefaults.FEED_EINK_MODE,
            feedEInkDark = prefs[NexusSettingsKeys.FEED_EINK_DARK] ?: NexusDefaults.FEED_EINK_DARK,
            iconTheming = prefs[NexusSettingsKeys.ICON_THEMING] ?: NexusDefaults.ICON_THEMING,
            frostedGlassEnabled = prefs[NexusSettingsKeys.FROSTED_GLASS_ENABLED] ?: NexusDefaults.FROSTED_GLASS_ENABLED,
            uiStyleMode = prefs[NexusSettingsKeys.UI_STYLE_MODE] ?: NexusDefaults.UI_STYLE_MODE,
            islandEnabled = prefs[NexusSettingsKeys.ISLAND_ENABLED] ?: NexusDefaults.ISLAND_ENABLED,
            islandPosition = prefs[NexusSettingsKeys.ISLAND_POSITION] ?: NexusDefaults.ISLAND_POSITION,
            islandCompactSize = prefs[NexusSettingsKeys.ISLAND_COMPACT_SIZE] ?: NexusDefaults.ISLAND_COMPACT_SIZE,
            islandAnimSpeed = (prefs[NexusSettingsKeys.ISLAND_ANIM_SPEED] ?: NexusDefaults.ISLAND_ANIM_SPEED)
                .coerceIn(0.5f, 1.5f),
            islandTriggerActivity = prefs[NexusSettingsKeys.ISLAND_TRIGGER_ACTIVITY] ?: NexusDefaults.ISLAND_TRIGGER_ACTIVITY,
            islandTriggerMusic = prefs[NexusSettingsKeys.ISLAND_TRIGGER_MUSIC] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerTimer = prefs[NexusSettingsKeys.ISLAND_TRIGGER_TIMER] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerStopwatch = prefs[NexusSettingsKeys.ISLAND_TRIGGER_STOPWATCH] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerBatteryLow = prefs[NexusSettingsKeys.ISLAND_TRIGGER_BATTERY_LOW] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerCharging = prefs[NexusSettingsKeys.ISLAND_TRIGGER_CHARGING] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerDnd = prefs[NexusSettingsKeys.ISLAND_TRIGGER_DND] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerBluetooth = prefs[NexusSettingsKeys.ISLAND_TRIGGER_BLUETOOTH] ?: NexusDefaults.ISLAND_TRIGGER,
            islandTriggerCalendar = prefs[NexusSettingsKeys.ISLAND_TRIGGER_CALENDAR] ?: NexusDefaults.ISLAND_TRIGGER,
            islandWidthDp = (prefs[NexusSettingsKeys.ISLAND_WIDTH_DP] ?: NexusDefaults.ISLAND_WIDTH_DP)
                .coerceIn(50, 260),
            islandHeightDp = (prefs[NexusSettingsKeys.ISLAND_HEIGHT_DP] ?: NexusDefaults.ISLAND_HEIGHT_DP)
                .coerceIn(20, 44),
            islandXOffsetDp = (prefs[NexusSettingsKeys.ISLAND_X_OFFSET_DP] ?: NexusDefaults.ISLAND_X_OFFSET_DP)
                .coerceIn(-100, 100),
            islandYOffsetDp = (prefs[NexusSettingsKeys.ISLAND_Y_OFFSET_DP] ?: NexusDefaults.ISLAND_Y_OFFSET_DP)
                .coerceIn(-25, 35),
            islandHaptics = prefs[NexusSettingsKeys.ISLAND_HAPTICS] ?: NexusDefaults.ISLAND_HAPTICS,
        )
    }
}
