package com.nexus.launcher.ui.widgets.weather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal object NexusWeatherPermission {
    const val ACTION_REQUEST = "com.nexus.launcher.ACTION_REQUEST_LOCATION_PERMISSION"
    const val REQUEST_CODE = 127

    fun clickPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = com.nexus.launcher.ui.HomeRoute.to(context, ACTION_REQUEST).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            0x300000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val weatherIds = manager.getAppWidgetIds(
            ComponentName(context, NexusWeatherWidgetProvider::class.java)
        )
        if (weatherIds.isNotEmpty()) {
            context.sendBroadcast(Intent(context, NexusWeatherWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, weatherIds)
            })
        }
        val glanceIds = manager.getAppWidgetIds(
            ComponentName(context, com.nexus.launcher.ui.widgets.glance.NexusGlanceWidgetProvider::class.java)
        )
        if (glanceIds.isNotEmpty()) {
            context.sendBroadcast(Intent(context, com.nexus.launcher.ui.widgets.glance.NexusGlanceWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, glanceIds)
            })
        }
    }
}
