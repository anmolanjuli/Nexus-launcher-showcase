package com.nexus.launcher.ui.widgets.weather

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetSizeHelper
import com.nexus.launcher.ui.widgets.WidgetSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusWeatherWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_LOCALE_CHANGED,
            com.nexus.launcher.locale.LocaleController.ACTION_APP_LOCALE_CHANGED -> {
                NexusWeatherRepository.clearCachedPlaceName(context)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(android.content.ComponentName(context, this::class.java))
                for (id in ids) {
                    updateWidget(context, appWidgetManager, id, null)
                }
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val ids = appWidgetManager.getAppWidgetIds(android.content.ComponentName(context, this::class.java))
                for (id in ids) {
                    updateWidget(context, appWidgetManager, id, null)
                }
            }
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
        val widgetContext = com.nexus.launcher.locale.LocaleObserver.wrapContext(context)
        val opts = options ?: appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 100)
        val minHeightDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 80)
        
        val size = NexusWidgetSizeHelper.resolve(minWidthDp, minHeightDp)
        val config = NexusWidgetConfig.read(widgetContext, appWidgetId)
        
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val layoutId = if (!hasLocationPermission) {
            com.nexus.launcher.R.layout.widget_nexus_permission_canvas
        } else when (size) {
            WidgetSize.TINY -> com.nexus.launcher.R.layout.widget_weather_tiny
            WidgetSize.SMALL -> com.nexus.launcher.R.layout.widget_weather_small
            WidgetSize.MEDIUM -> com.nexus.launcher.R.layout.widget_weather_medium
            WidgetSize.LARGE -> com.nexus.launcher.R.layout.widget_weather_large
        }
        
        val views = RemoteViews(context.packageName, layoutId)
        val density = widgetContext.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(1)

        if (!hasLocationPermission) {
            val bmp = createPermissionFallbackBitmap(widgetContext, widthPx, heightPx, density)
            views.setImageViewBitmap(com.nexus.launcher.R.id.widget_canvas, bmp)
            views.setOnClickPendingIntent(
                com.nexus.launcher.R.id.widget_canvas,
                NexusWeatherPermission.clickPendingIntent(context, appWidgetId)
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // We have permission, fetch weather and render
        CoroutineScope(Dispatchers.Main).launch {
            val weatherData = NexusWeatherRepository.getWeather(widgetContext)
            val bmp = NexusWeatherRenderer(weatherData).render(
                widgetContext, widthPx, heightPx, config, minWidthDp, minHeightDp
            )
            views.setImageViewBitmap(com.nexus.launcher.R.id.widget_canvas, bmp)
            
            val quickSettingsIntent = Intent(context, NexusWeatherQuickSettingsActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                appWidgetId,
                quickSettingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(com.nexus.launcher.R.id.widget_canvas, pi)
            
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
        canvas.drawText(context.getString(com.nexus.launcher.R.string.weather_enable_location), w / 2f, h / 2f, textPaint)
        return bmp
    }
}
