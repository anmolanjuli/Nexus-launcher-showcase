package com.nexus.launcher.ui.widgets.calendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal object NexusCalendarPermission {
    const val ACTION_REQUEST = "com.nexus.launcher.ACTION_REQUEST_CALENDAR_PERMISSION"
    const val REQUEST_CODE = 126

    fun clickPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = com.nexus.launcher.ui.HomeRoute.to(context, ACTION_REQUEST).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            0x100000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, NexusCalendarWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        context.sendBroadcast(Intent(context, NexusCalendarWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        })
    }
}
