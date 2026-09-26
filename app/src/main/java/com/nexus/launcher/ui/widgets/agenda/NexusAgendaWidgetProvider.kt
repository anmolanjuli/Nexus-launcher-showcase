package com.nexus.launcher.ui.widgets.agenda

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetSizeHelper
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarPermission
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AppWidgetProvider for Nexus Agenda Widget.
 * Handles minute ticks, date/time/locale updates, and direct event tap routing.
 */
class NexusAgendaWidgetProvider : AppWidgetProvider() {

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
        }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle?) {
        val widgetContext = com.nexus.launcher.locale.LocaleObserver.wrapContext(context)
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)

        val size = NexusWidgetSizeHelper.resolve(minWidthDp, minHeightDp)
        val config = NexusWidgetConfig.read(widgetContext, appWidgetId)
        val hasCalendarPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

        val density = widgetContext.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        if (!hasCalendarPermission) {
            val views = RemoteViews(context.packageName, R.layout.widget_nexus_permission_canvas)
            val bmp = createPermissionFallbackBitmap(widgetContext, widthPx, heightPx, density)
            views.setImageViewBitmap(R.id.widget_canvas, bmp)
            views.setOnClickPendingIntent(
                R.id.widget_canvas,
                NexusCalendarPermission.clickPendingIntent(context, appWidgetId)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        val handler = CoroutineExceptionHandler { _, _ -> }
        CoroutineScope(Dispatchers.Main + handler).launch {
            val events = NexusCalendarRepository.getAgendaEvents(widgetContext, config.agendaRange)
            val bmp = NexusAgendaRenderer(events).render(
                widgetContext, widthPx, heightPx, config, minWidthDp, minHeightDp
            )
            val views = RemoteViews(context.packageName, R.layout.widget_agenda_canvas)
            views.setImageViewBitmap(R.id.widget_canvas, bmp)

            // Tap widget opens Calendar app today schedule
            val timeMs = System.currentTimeMillis()
            val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(timeMs.toString()).build()
            val calendarIntent = Intent(Intent.ACTION_VIEW).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val pi = PendingIntent.getActivity(
                context,
                0x300000 + appWidgetId,
                calendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_canvas, pi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createPermissionFallbackBitmap(context: Context, w: Int, h: Int, dp: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#80000000") }
        canvas.drawRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 16f * dp, 16f * dp, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * dp
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(context.getString(R.string.calendar_enable_access), w / 2f, h / 2f, textPaint)
        return bmp
    }
}
