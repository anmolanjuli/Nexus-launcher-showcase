package com.nexus.launcher.ui.widgets.glance

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.HomeRoute
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.weather.NexusWeatherPermission
import com.nexus.launcher.ui.widgets.weather.NexusWeatherQuickSettingsActivity
import com.nexus.launcher.ui.widgets.weather.NexusWeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AppWidgetProvider for the all-in-one Clock + Weather + Calendar ("Glance") widget.
 * Features 3-tier click targets:
 * - Clock tap -> Alarm / Clock app
 * - Date tap -> Calendar app
 * - Weather pill tap -> Weather quick settings (or permission request via HomeRoute)
 */
class NexusGlanceWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context.packageName, javaClass.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            "android.intent.action.TIME_SET",
            "com.nexus.launcher.ACTION_APP_LOCALE_CHANGED" -> {
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id, null)
                }
            }
            "com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED" -> {
                val widgetId = intent.getIntExtra("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && appWidgetIds.contains(widgetId)) {
                    updateWidget(context, appWidgetManager, widgetId, null)
                }
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, null)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            NexusWidgetConfig.delete(context, id)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle?
    ) {
        val widgetContext = LocaleObserver.wrapContext(context)
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)

        val config = NexusWidgetConfig.read(widgetContext, appWidgetId)
        val density = widgetContext.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        val hasLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // 1. Synchronous initial render: ensures the widget never presents an empty canvas on drop
        renderAndApply(
            context, appWidgetManager, appWidgetId, widgetContext, config,
            widthPx, heightPx, minWidthDp, minHeightDp, hasLocationPermission, null
        )

        // 2. Asynchronous live weather update if location permission is granted
        if (hasLocationPermission) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val weatherData = NexusWeatherRepository.getWeather(widgetContext)
                    if (weatherData != null) {
                        renderAndApply(
                            context, appWidgetManager, appWidgetId, widgetContext, config,
                            widthPx, heightPx, minWidthDp, minHeightDp, hasLocationPermission, weatherData
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun renderAndApply(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        widgetContext: Context,
        config: NexusWidgetConfig.InstanceConfig,
        widthPx: Int,
        heightPx: Int,
        minWidthDp: Int,
        minHeightDp: Int,
        hasLocationPermission: Boolean,
        weatherData: com.nexus.launcher.ui.widgets.weather.WeatherData?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_glance_canvas)
        val bmp = NexusGlanceRenderer(weatherData).render(
            widgetContext, widthPx, heightPx, config, minWidthDp, minHeightDp, -1f
        )
        views.setImageViewBitmap(R.id.widget_canvas, bmp)

        // Click Zone 1: Clock (Top) -> Opens Alarm / Clock App
        val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val deskClockPkg = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
            ?: context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
            ?: clockIntent
        val piClock = PendingIntent.getActivity(
            context,
            0x500000 + appWidgetId,
            deskClockPkg,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.click_zone_clock, piClock)

        // Click Zone 2: Date (Middle) -> Opens Calendar App
        val calUri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
        val calIntent = Intent(Intent.ACTION_VIEW, calUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val deskCalPkg = context.packageManager.getLaunchIntentForPackage("com.google.android.calendar")
            ?: context.packageManager.getLaunchIntentForPackage("com.android.calendar")
            ?: calIntent
        val piCalendar = PendingIntent.getActivity(
            context,
            0x510000 + appWidgetId,
            deskCalPkg,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.click_zone_date, piCalendar)

        // Click Zone 3: Weather Pill (Bottom) -> Weather Settings or Permission Request
        val piWeather = if (!hasLocationPermission) {
            val permIntent = HomeRoute.to(context, NexusWeatherPermission.ACTION_REQUEST).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            PendingIntent.getActivity(
                context,
                0x520000 + appWidgetId,
                permIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            val weatherIntent = Intent(context, NexusWeatherQuickSettingsActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            PendingIntent.getActivity(
                context,
                0x520000 + appWidgetId,
                weatherIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        views.setOnClickPendingIntent(R.id.click_zone_weather, piWeather)
        views.setOnClickPendingIntent(R.id.widget_canvas, piClock)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
