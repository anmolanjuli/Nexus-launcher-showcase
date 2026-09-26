package com.nexus.launcher.search.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

class NexusSearchWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            "com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED" -> {
                val widgetId = intent.getIntExtra("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val info = appWidgetManager.getAppWidgetInfo(widgetId)
                    if (info?.provider?.className == this::class.java.name) {
                        updateWidget(context, appWidgetManager, widgetId, null)
                    }
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
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 56)
        
        val prefs = context.getSharedPreferences(NexusWidgetConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val config = NexusWidgetConfig.read(context, appWidgetId, defaultShapeStyle = 11)
        if (!prefs.contains("shape_style_$appWidgetId")) {
            NexusWidgetConfig.write(context, config)
        }
        
        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        val bmp = NexusSearchWidgetRenderer().render(
            context, widthPx, heightPx, config, minWidthDp, minHeightDp
        )

        val views = RemoteViews(context.packageName, R.layout.widget_nexus_search)
        views.setImageViewBitmap(R.id.widget_canvas, bmp)
        
        // Pending intent to trigger search
        val intent = com.nexus.launcher.ui.HomeRoute.to(context, "com.nexus.launcher.ACTION_OPEN_SEARCH")
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        views.setOnClickPendingIntent(R.id.widget_canvas, pendingIntent)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
