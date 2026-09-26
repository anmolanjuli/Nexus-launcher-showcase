package com.nexus.launcher.ui.island

enum class IslandKind {
    CALL,
    /** Anything an app is doing right now, read from its ongoing notification. */
    ACTIVITY,
    MUSIC,
    TIMER,
    STOPWATCH,
    BATTERY_LOW,
    CHARGING,
    DND,
    BLUETOOTH,
    CALENDAR,
}

enum class IslandShape {
    DORMANT,
    COMPACT,
    EXPANDED,
    MULTI,
}
