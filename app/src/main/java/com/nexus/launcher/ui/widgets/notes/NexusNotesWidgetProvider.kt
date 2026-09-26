package com.nexus.launcher.ui.widgets.notes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetSizeHelper

/**
 * AppWidgetProvider for the first-party Nexus Notes widget.
 * Manages rendering, lifecycle updates, and tap-to-edit routing.
 */
class NexusNotesWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val thisAppWidget = ComponentName(context.packageName, javaClass.name)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)

        when (intent.action) {
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
            NotesDataStore.delete(context, id)
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle?) {
        val widgetContext = LocaleObserver.wrapContext(context)
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        val size = NexusWidgetSizeHelper.resolve(minWidthDp, minHeightDp)
        val config = NexusWidgetConfig.read(widgetContext, appWidgetId)
        val note = NotesDataStore.read(widgetContext, appWidgetId)

        val density = widgetContext.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        val bmp = NexusNotesRenderer(note).render(
            widgetContext, widthPx, heightPx, config, minWidthDp, minHeightDp
        )

        val views = RemoteViews(context.packageName, R.layout.widget_notes_canvas)
        views.setImageViewBitmap(R.id.widget_canvas, bmp)

        // Tap opens NotesEditSheet in MainActivity
        val tapIntent = com.nexus.launcher.ui.HomeRoute.to(context).apply {
            putExtra("action_open_notes_edit", true)
            putExtra("notes_widget_id", appWidgetId)
        }

        val pi = PendingIntent.getActivity(
            context,
            0x400000 + appWidgetId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_canvas, pi)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
