package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.widgets.agenda.NexusAgendaWidgetProvider
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarWidgetProvider
import com.nexus.launcher.ui.widgets.clock.NexusClockWidgetProvider
import com.nexus.launcher.ui.widgets.glance.NexusGlanceWidgetProvider
import com.nexus.launcher.ui.widgets.performance.NexusPerformanceWidgetProvider
import com.nexus.launcher.ui.widgets.progress.NexusProgressWidgetProvider
import com.nexus.launcher.ui.widgets.weather.NexusWeatherWidgetProvider

/**
 * Dynamically listens for system time ticks and power/screen events to dispatch
 * updates to first-party Nexus AppWidgets in real time.
 *
 * Manifest-declared receivers cannot receive ACTION_TIME_TICK or ACTION_SCREEN_ON
 * on modern Android, leaving clocks and gauges static until an explicit event.
 */
object WidgetRealTimeUpdateDispatcher {

    @Volatile
    private var isRegistered = false
    private var appContext: Context? = null

    /** The last charge state a battery broadcast was acted on for: level, status, plug. */
    private var lastPowerState: String? = null

    private val timeTickProviders = arrayOf(
        NexusClockWidgetProvider::class.java,
        NexusGlanceWidgetProvider::class.java,
        NexusPerformanceWidgetProvider::class.java,
        NexusProgressWidgetProvider::class.java,
        NexusAgendaWidgetProvider::class.java
    )

    private val timeChangeProviders = arrayOf(
        NexusClockWidgetProvider::class.java,
        NexusGlanceWidgetProvider::class.java,
        NexusPerformanceWidgetProvider::class.java,
        NexusProgressWidgetProvider::class.java,
        NexusAgendaWidgetProvider::class.java,
        NexusCalendarWidgetProvider::class.java,
        NexusWeatherWidgetProvider::class.java
    )

    private val performanceProviders = arrayOf(
        NexusPerformanceWidgetProvider::class.java,
        NexusGlanceWidgetProvider::class.java
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_SCREEN_ON -> {
                    dispatchTo(context, timeTickProviders)
                }
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED,
                "android.intent.action.TIME_SET" -> {
                    dispatchTo(context, timeChangeProviders)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    // The system sends this for temperature and voltage as well as charge, so
                    // it can arrive every few seconds while charging. Only a change the widgets
                    // would actually draw differently is worth a re-render of both of them.
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                        .coerceAtLeast(1)
                    val state = "${level * 100 / scale}|" +
                        "${intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)}|" +
                        "${intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)}"
                    if (state != lastPowerState) {
                        lastPowerState = state
                        dispatchTo(context, performanceProviders, sourceIntent = intent)
                    }
                }
                Intent.ACTION_POWER_CONNECTED,
                Intent.ACTION_POWER_DISCONNECTED -> {
                    lastPowerState = null
                    dispatchTo(context, performanceProviders, sourceIntent = intent)
                }
            }
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (!isRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction("android.intent.action.TIME_SET")
            }
            try {
                ContextCompat.registerReceiver(
                    app,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isRegistered = true
            } catch (_: Exception) {}
        }
        dispatchImmediateTick(app)
    }

    fun stop(context: Context) {
        if (isRegistered) {
            try {
                (appContext ?: context.applicationContext).unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }

    fun dispatchImmediateTick(context: Context) {
        dispatchTo(context, timeTickProviders)
    }

    private fun dispatchTo(
        context: Context,
        providers: Array<out Class<*>>,
        sourceIntent: Intent? = null
    ) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        for (providerClass in providers) {
            try {
                val component = ComponentName(context, providerClass)
                val ids = manager.getAppWidgetIds(component)
                if (ids != null && ids.isNotEmpty()) {
                    // 1. Direct in-process invocation: instantaneous, zero IPC delay, immune to broadcast security restrictions
                    try {
                        val provider = providerClass.getDeclaredConstructor().newInstance() as? AppWidgetProvider
                        provider?.onUpdate(context, manager, ids)
                    } catch (_: Exception) {
                        // 2. Fallback to standard ACTION_APPWIDGET_UPDATE (safe, public, non-protected broadcast)
                        val intent = Intent(context, providerClass).apply {
                            this.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            sourceIntent?.extras?.let { putExtras(it) }
                        }
                        context.sendBroadcast(intent)
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
