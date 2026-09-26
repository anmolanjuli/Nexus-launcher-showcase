package com.nexus.launcher.data.prefs

data class NexusSettingsData(
    val homeColumns: Int = NexusDefaults.HOME_COLUMNS,
    val homeRows: Int = NexusDefaults.HOME_ROWS,
    val homePaddingLeftRightDp: Float = NexusDefaults.HOME_PADDING_LEFT_RIGHT,
    val homePaddingTopBottomDp: Float = NexusDefaults.HOME_PADDING_TOP_BOTTOM,
    val homeGapHorizontalDp: Float = NexusDefaults.HOME_GAP_HORIZONTAL,
    val homeGapVerticalDp: Float = NexusDefaults.HOME_GAP_VERTICAL,
    val homeIconSizeMultiplier: Float = NexusDefaults.HOME_ICON_SIZE_MULTIPLIER,
    val homeShowLabels: Boolean = NexusDefaults.HOME_SHOW_LABELS,
    val homeTwoLineLabels: Boolean = NexusDefaults.HOME_TWO_LINE_LABELS,
    val homeShowIndicator: Boolean = NexusDefaults.HOME_SHOW_INDICATOR,
    /** Hide the status and navigation bars on the home screen (ImmersiveModeController). */
    val immersiveMode: Boolean = NexusDefaults.IMMERSIVE_MODE,
    /** Dock on the home screen; off gives its space to the grid (DockPresence). */
    val homeShowDock: Boolean = NexusDefaults.HOME_SHOW_DOCK,
    /** How each status-row item is drawn; see ImmersiveStatusStyle. */
    val statusClockStyle: String = NexusDefaults.STATUS_CLOCK_STYLE,
    val statusNotificationStyle: String = NexusDefaults.STATUS_NOTIFICATION_STYLE,
    val statusBatteryStyle: String = NexusDefaults.STATUS_BATTERY_STYLE,
    val statusBatteryPercent: String = NexusDefaults.STATUS_BATTERY_PERCENT,
    val statusSignalStyle: String = NexusDefaults.STATUS_SIGNAL_STYLE,
    val statusWifiStyle: String = NexusDefaults.STATUS_WIFI_STYLE,
    /** The row itself: on, where, how tall, on what background, pills, fade, how far from the edges. */
    val statusBarEnabled: Boolean = NexusDefaults.STATUS_BAR_ENABLED,
    val statusBarPosition: String = NexusDefaults.STATUS_BAR_POSITION,
    val statusBarHeightDp: Int = NexusDefaults.STATUS_BAR_HEIGHT_DP,
    val statusBarBackground: String = NexusDefaults.STATUS_BAR_BACKGROUND,
    val statusBarPaddingDp: Int = NexusDefaults.STATUS_BAR_PADDING_DP,
    val statusSegmentedPills: Boolean = NexusDefaults.STATUS_SEGMENTED_PILLS,
    val statusBarFade: Boolean = NexusDefaults.STATUS_BAR_FADE,
    val statusFontKey: String = NexusDefaults.STATUS_FONT_KEY,
    val statusPillOpacity: Int = NexusDefaults.STATUS_PILL_OPACITY,
    /** Per-module extras; see ImmersiveStatusStyle. */
    val statusClockPosition: String = NexusDefaults.STATUS_CLOCK_POSITION,
    val statusDateFormat: String = NexusDefaults.STATUS_DATE_FORMAT,
    val statusMaxIcons: Int = NexusDefaults.STATUS_MAX_ICONS,
    val statusShowSilent: Boolean = NexusDefaults.STATUS_SHOW_SILENT,
    val statusGroupByApp: Boolean = NexusDefaults.STATUS_GROUP_BY_APP,
    val statusLowBatteryPercent: Int = NexusDefaults.STATUS_LOW_BATTERY_PERCENT,
    val statusChargingAnimation: Boolean = NexusDefaults.STATUS_CHARGING_ANIMATION,
    val statusShowDataType: Boolean = NexusDefaults.STATUS_SHOW_DATA_TYPE,
    val statusShowCarrier: Boolean = NexusDefaults.STATUS_SHOW_CARRIER,
    val statusShowSsid: Boolean = NexusDefaults.STATUS_SHOW_SSID,
    val statusShowBand: Boolean = NexusDefaults.STATUS_SHOW_BAND,
    /** The launcher's own notification sheet, and how long it keeps cleared notifications. */
    val notificationHistory: Boolean = NexusDefaults.NOTIFICATION_HISTORY,
    val notificationRetentionHours: Int = NexusDefaults.NOTIFICATION_RETENTION_HOURS,
    /** Items in the immersive status row (ImmersiveStatus). */
    val statusShowClock: Boolean = NexusDefaults.STATUS_SHOW_CLOCK,
    val statusShowNotifications: Boolean = NexusDefaults.STATUS_SHOW_NOTIFICATIONS,
    val statusShowWifi: Boolean = NexusDefaults.STATUS_SHOW_WIFI,
    val statusShowSignal: Boolean = NexusDefaults.STATUS_SHOW_SIGNAL,
    val statusShowBattery: Boolean = NexusDefaults.STATUS_SHOW_BATTERY,
    val drawerColumns: Int = NexusDefaults.DRAWER_COLUMNS,
    val drawerIconSizeMultiplier: Float = NexusDefaults.DRAWER_ICON_SIZE_MULTIPLIER,
    val drawerShowLabels: Boolean = NexusDefaults.DRAWER_SHOW_LABELS,
    val drawerTwoLineLabels: Boolean = NexusDefaults.DRAWER_TWO_LINE_LABELS,
    val drawerShowCategoryBar: Boolean = NexusDefaults.DRAWER_SHOW_CATEGORY_BAR,
    val drawerShowRail: Boolean = NexusDefaults.DRAWER_SHOW_RAIL,
    val drawerSortOrder: String = NexusDefaults.DRAWER_SORT_ORDER,
    val drawerShowSearchPill: Boolean = NexusDefaults.DRAWER_SHOW_SEARCH_PILL,
    val drawerSearchBarPosition: String = NexusDefaults.DRAWER_SEARCH_BAR_POSITION,
    val drawerCategoryMode: String = NexusDefaults.DRAWER_CATEGORY_MODE,
    val drawerCategoryPosition: String = NexusDefaults.DRAWER_CATEGORY_POSITION,
    val drawerLayout: String = NexusDefaults.DRAWER_LAYOUT, // legacy — kept for backup/restore compatibility only, no longer read by live code
    val drawerGridOrList: String = NexusDefaults.DRAWER_GRID_OR_LIST,
    val drawerListColumns: Int = NexusDefaults.DRAWER_LIST_COLUMNS,
    /** Spatial / Strip / List — only consumed when [drawerGridOrList] is `categories`. */
    val drawerCategoryLayout: String = NexusDefaults.DRAWER_CATEGORY_LAYOUT,
    val drawerTransition: String = NexusDefaults.DRAWER_TRANSITION,
    val backupFolderUri: String = NexusDefaults.BACKUP_FOLDER_URI,
    val accentColor: String = NexusDefaults.ACCENT_COLOR,
    val matchWallpaperColor: Boolean = NexusDefaults.MATCH_WALLPAPER_COLOR,
    val wallpaperType: String = NexusDefaults.WALLPAPER_TYPE,
    val wallpaperSolidColor: String = NexusDefaults.WALLPAPER_SOLID_COLOR,
    val wallpaperGradientStart: String = NexusDefaults.WALLPAPER_GRADIENT_START,
    val wallpaperGradientEnd: String = NexusDefaults.WALLPAPER_GRADIENT_END,
    val wallpaperGradientDirection: String = NexusDefaults.WALLPAPER_GRADIENT_DIRECTION,
    val wallpaperGalleryPath: String = NexusDefaults.WALLPAPER_GALLERY_PATH,
    val wallpaperBlur: Float = NexusDefaults.WALLPAPER_BLUR,
    val wallpaperTintColor: String = NexusDefaults.WALLPAPER_TINT_COLOR,
    val wallpaperTintStrength: Float = NexusDefaults.WALLPAPER_TINT_STRENGTH,
    val badgeStyleApp: Int = 1,
    val badgeStyleFolder: Int = 1,
    val homePageCount: Int = NexusDefaults.HOME_PAGE_COUNT,
    val pageTransition: String = NexusDefaults.PAGE_TRANSITION,
    val iconPack: String = NexusDefaults.ICON_PACK,
    val iconShape: Int = -1,
    val globalSwipeUp: String = "OPEN_APP_DRAWER",
    val globalSwipeDown: String = "OPEN_SEARCH",
    val globalTwoFingerSwipeUp: String = "NONE",
    val globalTwoFingerSwipeDown: String = "NONE",
    val homeShowFeed: Boolean = NexusDefaults.HOME_SHOW_FEED,
    val feedRefreshInterval: String = NexusDefaults.FEED_REFRESH_INTERVAL,
    val feedEInkMode: Boolean = NexusDefaults.FEED_EINK_MODE,
    val feedEInkDark: Boolean = NexusDefaults.FEED_EINK_DARK,
    val iconTheming: Boolean = NexusDefaults.ICON_THEMING,
    /** Kept for backward compat with the many `isGlobalFrostedGlassEnabled` gate call sites
     *  across widgets/folders/dock — always derived from [uiStyleMode] on write
     *  (`uiStyleMode == "FROSTED_GLASS"`), never set independently. */
    val frostedGlassEnabled: Boolean = NexusDefaults.FROSTED_GLASS_ENABLED,
    /** "NEUMORPHISM" / "FROSTED_GLASS" / "DEFAULT" — see [FrostedGlassEngine]'s UI_STYLE_*
     *  constants. The actual source of truth for the Settings > Appearance UI Style picker. */
    val uiStyleMode: String = NexusDefaults.UI_STYLE_MODE,
    val islandEnabled: Boolean = NexusDefaults.ISLAND_ENABLED,
    val islandPosition: String = NexusDefaults.ISLAND_POSITION,
    val islandCompactSize: String = NexusDefaults.ISLAND_COMPACT_SIZE,
    val islandAnimSpeed: Float = NexusDefaults.ISLAND_ANIM_SPEED,
    /** Anything an app reports as live in its own notification: downloads, routes, rides. */
    val islandTriggerActivity: Boolean = NexusDefaults.ISLAND_TRIGGER_ACTIVITY,
    val islandTriggerMusic: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerTimer: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerStopwatch: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerBatteryLow: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerCharging: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerDnd: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerBluetooth: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandTriggerCalendar: Boolean = NexusDefaults.ISLAND_TRIGGER,
    val islandWidthDp: Int = NexusDefaults.ISLAND_WIDTH_DP,
    val islandHeightDp: Int = NexusDefaults.ISLAND_HEIGHT_DP,
    val islandXOffsetDp: Int = NexusDefaults.ISLAND_X_OFFSET_DP,
    val islandYOffsetDp: Int = NexusDefaults.ISLAND_Y_OFFSET_DP,
    val islandHaptics: Boolean = NexusDefaults.ISLAND_HAPTICS,
) {
    /** Single source of truth for the drawer's actual render mode, consumed by
     *  [com.nexus.launcher.ui.canvas.CanvasSettingsApplier] to drive the live grid/list renderer
     *  and by [com.nexus.launcher.ui.settings.DrawerPreviewView] for the Settings preview — both
     *  must derive it from here rather than re-deriving from the fields independently. */
    val drawerEffectiveLayoutMode: String
        get() = when (drawerGridOrList) {
            DrawerLayoutModes.GRID -> DrawerLayoutModes.GRID
            DrawerLayoutModes.CATEGORIES -> DrawerLayoutModes.CATEGORIES
            else -> "list_$drawerListColumns"
        }
}
