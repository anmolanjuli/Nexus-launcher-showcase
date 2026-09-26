package com.nexus.launcher.ui.widgets.clock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.RemoteViews
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

class NexusClockWidgetProvider : AppWidgetProvider() {

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
            Intent.ACTION_LOCALE_CHANGED,
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

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            NexusWidgetConfig.delete(context, id)
            LaCrosseClockConfig.delete(context, id)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle?) {
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 100)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)

        val config = NexusWidgetConfig.read(context, appWidgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_clock_canvas)

        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        val bmp = NexusClockRenderer().render(context, widthPx, heightPx, config, minWidthDp, minHeightDp, -1f)
        views.setImageViewBitmap(R.id.widget_canvas, bmp)

        // Launch Alarm / Clock App on widget tap
        val clockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fallbackIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
            ?: context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
            ?: clockIntent

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val piClock = PendingIntent.getActivity(context, appWidgetId, fallbackIntent, piFlags)

        if (config.clockStyle == NexusClockRenderer.STYLE_LACROSSE_LCD) {
            val lacrosseCfg = LaCrosseClockConfig.read(context, appWidgetId)
            val cachedTemp = if (lacrosseCfg.showTemp) {
                com.nexus.launcher.ui.widgets.weather.NexusWeatherRepository.getCachedTemperature(context)
            } else null

            if (cachedTemp != null) {
                views.setViewVisibility(R.id.lacrosse_click_overlay, android.view.View.VISIBLE)
                views.setOnClickPendingIntent(R.id.click_zone_time, piClock)
                views.setOnClickPendingIntent(R.id.click_zone_date, piClock)

                val weatherIntent = Intent(context, com.nexus.launcher.ui.widgets.weather.NexusWeatherQuickSettingsActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val piWeather = PendingIntent.getActivity(
                    context,
                    0x620000 + appWidgetId,
                    weatherIntent,
                    piFlags
                )
                views.setOnClickPendingIntent(R.id.click_zone_temp, piWeather)
            } else {
                views.setViewVisibility(R.id.lacrosse_click_overlay, android.view.View.GONE)
            }
        } else {
            views.setViewVisibility(R.id.lacrosse_click_overlay, android.view.View.GONE)
        }

        views.setOnClickPendingIntent(R.id.widget_canvas, piClock)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
