package com.nexus.launcher.ui.widgets.performance

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * AppWidgetProvider for the Device Performance Widget.
 */
class NexusPerformanceWidgetProvider : AppWidgetProvider() {

    private val renderer = NexusPerformanceRenderer()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context.packageName, javaClass.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
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
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle?) {
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        val config = NexusWidgetConfig.read(context, appWidgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_performance_canvas)

        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        val bmp = renderer.render(context, widthPx, heightPx, config, minWidthDp, minHeightDp, -1f)
        views.setImageViewBitmap(R.id.widget_canvas, bmp)

        views.setOnClickPendingIntent(R.id.touch_cpu, PerformanceTapRouter.createCpuPendingIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.touch_ram, PerformanceTapRouter.createRamPendingIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.touch_storage, PerformanceTapRouter.createStoragePendingIntent(context, appWidgetId))
        views.setOnClickPendingIntent(R.id.touch_battery, PerformanceTapRouter.createBatteryPendingIntent(context, appWidgetId))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
