package com.nexus.launcher.ui.widgets.progress

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
 * AppWidgetProvider for the First-Party Progress Bar Widget.
 */
class NexusProgressWidgetProvider : AppWidgetProvider() {

    private val renderer = NexusProgressRenderer()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context.packageName, javaClass.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        when (intent.action) {
            Intent.ACTION_TIME_TICK,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
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
            ProgressTapRouter.ACTION_PROGRESS_TAP_SLOT -> {
                val widgetId = intent.getIntExtra(ProgressTapRouter.EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val slotIndex = intent.getIntExtra(ProgressTapRouter.EXTRA_SLOT_INDEX, 0)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val openIntent = com.nexus.launcher.ui.HomeRoute.to(context).apply {
                        putExtra("action_open_progress_settings", true)
                        putExtra("progress_widget_id", widgetId)
                        putExtra("progress_slot_index", slotIndex)
                    }
                    context.startActivity(openIntent)
                }
            }
            ProgressTapRouter.ACTION_PROGRESS_TAP_BG -> {
                val widgetId = intent.getIntExtra(ProgressTapRouter.EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val openIntent = com.nexus.launcher.ui.HomeRoute.to(context).apply {
                        putExtra("action_open_progress_settings", true)
                        putExtra("progress_widget_id", widgetId)
                        putExtra("progress_slot_index", -1)
                    }
                    context.startActivity(openIntent)
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
            ProgressDataStore.deleteWidget(context, id)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle?) {
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        val config = NexusWidgetConfig.read(context, appWidgetId)
        val views = RemoteViews(context.packageName, R.layout.widget_progress_canvas)

        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        val bmp = renderer.render(context, widthPx, heightPx, config, minWidthDp, minHeightDp, -1f)
        views.setImageViewBitmap(R.id.widget_canvas, bmp)

        views.setOnClickPendingIntent(R.id.touch_slot_0, ProgressTapRouter.createSlotPendingIntent(context, appWidgetId, 0))
        views.setOnClickPendingIntent(R.id.touch_slot_1, ProgressTapRouter.createSlotPendingIntent(context, appWidgetId, 1))
        views.setOnClickPendingIntent(R.id.touch_slot_2, ProgressTapRouter.createSlotPendingIntent(context, appWidgetId, 2))

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
