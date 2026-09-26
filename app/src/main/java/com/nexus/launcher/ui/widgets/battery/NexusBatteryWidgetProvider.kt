package com.nexus.launcher.ui.widgets.battery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.RemoteViews
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleController
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * AppWidgetProvider for the Nexus Battery Widget.
 * Listens for system battery events, power connection state, locale changes, and config updates.
 */
class NexusBatteryWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BATTERY_CHANGED,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_LOCALE_CHANGED,
            LocaleController.ACTION_APP_LOCALE_CHANGED -> {
                updateAllWidgets(context)
            }
            "com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED" -> {
                val widgetId = intent.getIntExtra("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info?.provider?.className == this::class.java.name) {
                        updateWidget(context, appWidgetManager, widgetId, null)
                    }
                } else {
                    updateAllWidgets(context)
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

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, this::class.java))
        for (id in ids) {
            updateWidget(context, appWidgetManager, id, null)
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
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 100)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)

        val config = NexusWidgetConfig.read(widgetContext, appWidgetId)
        val density = widgetContext.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        // Drawn inside the launcher's own process: a throw here does not fail one widget, it
        // takes down the home screen — and a home app that crashes can lose its default status.
        // A drawing bug skips this update and is logged instead.
        val bitmap = try {
            NexusBatteryRenderer().render(
                context = widgetContext,
                widthPx = widthPx,
                heightPx = heightPx,
                config = config,
                minWidthDp = minWidthDp,
                minHeightDp = minHeightDp,
                progress = -1f
            )
        } catch (e: RuntimeException) {
            android.util.Log.e("NexusBattery", "Render failed at ${minWidthDp}x${minHeightDp}dp", e)
            return
        }

        val views = RemoteViews(context.packageName, R.layout.widget_battery_canvas)
        views.setImageViewBitmap(R.id.widget_canvas, bitmap)

        // PendingIntent to Battery Usage Settings
        val tapIntent = createBatterySettingsIntent(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_canvas, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createBatterySettingsIntent(context: Context): Intent {
        val batteryUsageIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (batteryUsageIntent.resolveActivity(context.packageManager) != null) {
            return batteryUsageIntent
        }
        return Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
