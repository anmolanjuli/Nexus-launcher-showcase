package com.nexus.launcher.ui.widgets

/** Synthetic Nexus picker entries (not AppWidget providers). */
object NexusWidgetKinds {
    const val LIVING_MOSAIC_2X2 = "living_mosaic_2x2"
    const val LIVING_MOSAIC_3X3 = "living_mosaic_3x3"
    const val SHORTCUT_BOX_2X2 = "shortcut_box_2x2"
    const val SHORTCUT_BOX_2X3 = "shortcut_box_2x3"
    const val APP_BOX_2X2 = "app_box_2x2"
    const val LIVE_APPS_3X3 = "live_apps_3x3"
    const val LIVE_APPS_2X2 = "live_apps_2x2"

    fun isLivingMosaic(kind: String?): Boolean =
        kind == LIVING_MOSAIC_2X2 || kind == LIVING_MOSAIC_3X3

    fun isShortcutBox(kind: String?): Boolean =
        kind == SHORTCUT_BOX_2X2 || kind == SHORTCUT_BOX_2X3

    fun isAppBox(kind: String?): Boolean =
        kind == APP_BOX_2X2

    fun isLiveApps(kind: String?): Boolean =
        kind == LIVE_APPS_3X3 || kind == LIVE_APPS_2X2

    fun isSyntheticKind(kind: String?): Boolean =
        isLivingMosaic(kind) || isShortcutBox(kind) || isAppBox(kind) || isLiveApps(kind)

    fun mosaicSpan(kind: String?): Pair<Int, Int> = when (kind) {
        LIVING_MOSAIC_2X2 -> 2 to 2
        LIVING_MOSAIC_3X3 -> 3 to 3
        SHORTCUT_BOX_2X2 -> 2 to 2
        SHORTCUT_BOX_2X3 -> 2 to 3
        APP_BOX_2X2 -> 2 to 2
        LIVE_APPS_3X3 -> 3 to 3
        LIVE_APPS_2X2 -> 2 to 2
        else -> 2 to 2
    }
}
