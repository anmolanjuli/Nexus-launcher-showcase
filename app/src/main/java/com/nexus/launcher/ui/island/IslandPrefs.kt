package com.nexus.launcher.ui.island

import com.nexus.launcher.data.prefs.NexusSettingsData

object IslandPrefs {
    fun triggerEnabled(settings: NexusSettingsData, kind: IslandKind): Boolean = when (kind) {
        IslandKind.CALL -> true
        IslandKind.ACTIVITY -> settings.islandTriggerActivity
        IslandKind.MUSIC -> settings.islandTriggerMusic
        IslandKind.TIMER -> settings.islandTriggerTimer
        IslandKind.STOPWATCH -> settings.islandTriggerStopwatch
        IslandKind.BATTERY_LOW -> settings.islandTriggerBatteryLow
        IslandKind.CHARGING -> settings.islandTriggerCharging
        IslandKind.DND -> settings.islandTriggerDnd
        IslandKind.BLUETOOTH -> settings.islandTriggerBluetooth
        IslandKind.CALENDAR -> settings.islandTriggerCalendar
    }

    val priority: List<IslandKind> = listOf(
        IslandKind.CALL,
        IslandKind.ACTIVITY,
        IslandKind.MUSIC,
        IslandKind.TIMER,
        IslandKind.STOPWATCH,
        IslandKind.CALENDAR,
        IslandKind.CHARGING,
        IslandKind.BATTERY_LOW,
        IslandKind.BLUETOOTH,
        IslandKind.DND,
    )
}
