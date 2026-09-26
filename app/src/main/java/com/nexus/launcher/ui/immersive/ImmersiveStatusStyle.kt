package com.nexus.launcher.ui.immersive

import com.nexus.launcher.data.prefs.NexusSettingsData

/**
 * How each item in the immersive status row is drawn (Settings → Home → Status info).
 *
 * Stored as short strings rather than enum ordinals: they go into preferences and backups, and a
 * reordered enum would silently change what a saved value means. Anything unrecognised falls
 * back to the first option, so a value from a newer build never leaves the row blank.
 */
data class ImmersiveStatusStyle(
    val clock: String = CLOCK_SYSTEM,
    val notifications: String = NOTIFICATIONS_COUNT,
    val battery: String = BATTERY_BAR,
    val batteryPercent: String = PERCENT_BESIDE,
    val signal: String = SIGNAL_BARS,
    val wifi: String = WIFI_ARCS,
    val clockPosition: String = POSITION_LEFT,
    val dateFormat: String = DATE_SHORT,
    val maxIcons: Int = 3,
    val showSilent: Boolean = true,
    val groupByApp: Boolean = false,
    val lowBatteryPercent: Int = 20,
    val chargingAnimation: Boolean = true,
    val showDataType: Boolean = false,
    val showCarrier: Boolean = false,
    val showSsid: Boolean = false,
    val showBand: Boolean = false,
    val segmentedPills: Boolean = true,
    val barFade: Boolean = false,
    val barAtBottom: Boolean = false,
    val fontKey: String = FONT_FOLLOW,
    val pillOpacity: Int = 40,
) {
    companion object {
        const val CLOCK_SYSTEM = "system"
        const val CLOCK_12H = "12h"
        const val CLOCK_24H = "24h"
        const val CLOCK_SECONDS = "seconds"
        const val CLOCK_DATE = "date"

        const val NOTIFICATIONS_COUNT = "count"
        const val NOTIFICATIONS_ICONS = "icons"
        const val NOTIFICATIONS_DOT = "dot"

        const val BATTERY_BAR = "bar"
        const val BATTERY_RING = "ring"
        const val BATTERY_TEXT = "text"

        const val PERCENT_BESIDE = "beside"
        const val PERCENT_INSIDE = "inside"
        const val PERCENT_OFF = "off"

        const val SIGNAL_BARS = "bars"
        const val SIGNAL_ARC = "arc"
        const val SIGNAL_DOTS = "dots"

        const val WIFI_ARCS = "arcs"
        const val WIFI_BARS = "bars"
        const val WIFI_DOT = "dot"

        const val POSITION_LEFT = "left"
        const val POSITION_CENTER = "center"
        const val POSITION_RIGHT = "right"

        const val DATE_SHORT = "short"
        const val DATE_LONG = "long"
        const val DATE_WEEKDAY = "weekday"

        const val BAR_TOP = "top"
        const val BAR_BOTTOM = "bottom"

        const val BACKGROUND_TRANSPARENT = "transparent"
        const val BACKGROUND_FROSTED = "frosted"
        const val BACKGROUND_SOLID = "solid"
        const val FONT_FOLLOW = "follow"

        val CLOCK_OPTIONS = listOf(CLOCK_SYSTEM, CLOCK_12H, CLOCK_24H, CLOCK_SECONDS, CLOCK_DATE)
        val NOTIFICATION_OPTIONS = listOf(NOTIFICATIONS_COUNT, NOTIFICATIONS_ICONS, NOTIFICATIONS_DOT)
        val BATTERY_OPTIONS = listOf(BATTERY_BAR, BATTERY_RING, BATTERY_TEXT)
        val PERCENT_OPTIONS = listOf(PERCENT_BESIDE, PERCENT_INSIDE, PERCENT_OFF)
        val SIGNAL_OPTIONS = listOf(SIGNAL_BARS, SIGNAL_ARC, SIGNAL_DOTS)
        val WIFI_OPTIONS = listOf(WIFI_ARCS, WIFI_BARS, WIFI_DOT)
        val POSITION_OPTIONS = listOf(POSITION_LEFT, POSITION_CENTER, POSITION_RIGHT)
        val DATE_OPTIONS = listOf(DATE_SHORT, DATE_LONG, DATE_WEEKDAY)
        val BAR_POSITION_OPTIONS = listOf(BAR_TOP, BAR_BOTTOM)
        val BACKGROUND_OPTIONS = listOf(BACKGROUND_TRANSPARENT, BACKGROUND_FROSTED, BACKGROUND_SOLID)

        fun of(settings: NexusSettingsData) = ImmersiveStatusStyle(
            clock = pick(settings.statusClockStyle, CLOCK_OPTIONS),
            notifications = pick(settings.statusNotificationStyle, NOTIFICATION_OPTIONS),
            battery = pick(settings.statusBatteryStyle, BATTERY_OPTIONS),
            batteryPercent = pick(settings.statusBatteryPercent, PERCENT_OPTIONS),
            signal = pick(settings.statusSignalStyle, SIGNAL_OPTIONS),
            wifi = pick(settings.statusWifiStyle, WIFI_OPTIONS),
            clockPosition = pick(settings.statusClockPosition, POSITION_OPTIONS),
            dateFormat = pick(settings.statusDateFormat, DATE_OPTIONS),
            maxIcons = settings.statusMaxIcons.coerceIn(1, 10),
            showSilent = settings.statusShowSilent,
            groupByApp = settings.statusGroupByApp,
            lowBatteryPercent = settings.statusLowBatteryPercent.coerceIn(5, 30),
            chargingAnimation = settings.statusChargingAnimation,
            showDataType = settings.statusShowDataType,
            showCarrier = settings.statusShowCarrier,
            showSsid = settings.statusShowSsid,
            showBand = settings.statusShowBand,
            segmentedPills = settings.statusSegmentedPills,
            barFade = settings.statusBarFade,
            barAtBottom = settings.statusBarPosition == BAR_BOTTOM,
            fontKey = settings.statusFontKey.ifBlank { FONT_FOLLOW },
            pillOpacity = settings.statusPillOpacity.coerceIn(10, 90),
        )

        private fun pick(value: String, options: List<String>): String =
            if (value in options) value else options.first()
    }
}
