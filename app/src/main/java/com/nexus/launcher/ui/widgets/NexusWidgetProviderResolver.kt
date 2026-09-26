package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo

/** Resolves synthetic Nexus widget entries to their installed [AppWidgetProviderInfo]. */
object NexusWidgetProviderResolver {

    fun resolve(manager: AppWidgetManager, entry: WidgetProviderEntry): AppWidgetProviderInfo? {
        val targetClass = when (entry.nexusKind) {
            "performance" -> "com.nexus.launcher.ui.widgets.performance.NexusPerformanceWidgetProvider"
            "music" -> "com.nexus.launcher.ui.widgets.music.NexusMusicWidgetProvider"
            "weather" -> "com.nexus.launcher.ui.widgets.weather.NexusWeatherWidgetProvider"
            "calendar" -> "com.nexus.launcher.ui.widgets.calendar.NexusCalendarWidgetProvider"
            "clock" -> "com.nexus.launcher.ui.widgets.clock.NexusClockWidgetProvider"
            "search" -> "com.nexus.launcher.search.ui.NexusSearchWidgetProvider"
            "notes" -> "com.nexus.launcher.ui.widgets.notes.NexusNotesWidgetProvider"
            "agenda" -> "com.nexus.launcher.ui.widgets.agenda.NexusAgendaWidgetProvider"
            "battery" -> "com.nexus.launcher.ui.widgets.battery.NexusBatteryWidgetProvider"
            else -> null
        } ?: return null
        return manager.installedProviders.find { it.provider.className == targetClass }
    }
}
