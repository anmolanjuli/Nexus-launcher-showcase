package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Forces every currently-bound first-party widget instance to re-render its own baked RemoteViews
 * content.
 *
 * Batches and staggers updates across frames (50ms per widget) so that changing themes,
 * surface modes, or frosted glass settings does not spike CPU and drop frames by re-rendering
 * all widgets simultaneously.
 */
object NexusWidgetGlobalRefresh {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRefreshRunnable: Runnable? = null

    private val FIRST_PARTY_PROVIDER_CLASSES = listOf(
        "com.nexus.launcher.ui.widgets.music.NexusMusicWidgetProvider",
        "com.nexus.launcher.ui.widgets.weather.NexusWeatherWidgetProvider",
        "com.nexus.launcher.ui.widgets.calendar.NexusCalendarWidgetProvider",
        "com.nexus.launcher.ui.widgets.clock.NexusClockWidgetProvider",
        "com.nexus.launcher.ui.widgets.performance.NexusPerformanceWidgetProvider",
        "com.nexus.launcher.ui.widgets.progress.NexusProgressWidgetProvider",
        "com.nexus.launcher.ui.widgets.agenda.NexusAgendaWidgetProvider",
        "com.nexus.launcher.ui.widgets.notes.NexusNotesWidgetProvider",
        "com.nexus.launcher.ui.widgets.battery.NexusBatteryWidgetProvider",
        "com.nexus.launcher.ui.widgets.glance.NexusGlanceWidgetProvider",
        "com.nexus.launcher.search.ui.NexusSearchWidgetProvider"
    )

    fun refreshAllGlassCapableWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val allIds = mutableListOf<Int>()

        for (providerClass in FIRST_PARTY_PROVIDER_CLASSES) {
            val ids = try {
                appWidgetManager.getAppWidgetIds(ComponentName(context.packageName, providerClass))
            } catch (_: Exception) {
                continue
            }
            for (id in ids) {
                allIds.add(id)
            }
        }
        if (allIds.isEmpty()) return

        // Cancel previous in-flight stagger to avoid duplicate work
        pendingRefreshRunnable?.let { handler.removeCallbacks(it) }

        var index = 0
        val staggerRunnable = object : Runnable {
            override fun run() {
                if (index < allIds.size) {
                    val id = allIds[index++]
                    val intent = Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED").apply {
                        putExtra("appWidgetId", id)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(intent)

                    if (index < allIds.size) {
                        handler.postDelayed(this, 50L) // Stagger across frames (50ms gap)
                    } else {
                        pendingRefreshRunnable = null
                    }
                }
            }
        }
        pendingRefreshRunnable = staggerRunnable
        handler.post(staggerRunnable)
    }
}
