package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.widgets.battery.NexusBatteryRenderer
import com.nexus.launcher.ui.widgets.calendar.CalendarEvent
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarRenderer
import com.nexus.launcher.ui.widgets.clock.NexusClockRenderer
import com.nexus.launcher.ui.widgets.music.NexusMusicRenderer
import com.nexus.launcher.ui.widgets.weather.DailyForecast
import com.nexus.launcher.ui.widgets.weather.HourlyForecast
import com.nexus.launcher.ui.widgets.weather.NexusWeatherRenderer
import com.nexus.launcher.ui.widgets.weather.WeatherData
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance preview cache and renderer for first-party Nexus widgets in the widget picker.
 * Renders live Neumorphic preview bitmaps matching current theme tokens and caches them in memory.
 */
object NexusWidgetPreviewCache {

    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun getPreview(
        context: Context,
        entry: WidgetProviderEntry,
        targetW: Float,
        targetH: Float
    ): Bitmap? {
        val dp = context.resources.displayMetrics.density
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val providerClass = entry.info?.provider?.className ?: entry.nexusKind ?: ""
        val wPx = targetW.toInt().coerceAtLeast(1)
        val hPx = targetH.toInt().coerceAtLeast(1)
        val styleKey = when {
            com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled -> "FROSTED"
            com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled -> "DEFAULT"
            else -> "NEUMORPHIC"
        }
        val cacheKey = "${providerClass}_${styleKey}_${tokens.bg}_${tokens.surface}_${tokens.accent}_${wPx}_${hPx}"

        cache[cacheKey]?.let { return it }

        val bmp = renderLivePreview(context, entry, wPx, hPx, dp, tokens)
        if (bmp != null) {
            cache[cacheKey] = bmp
        }
        return bmp
    }

    private fun renderLivePreview(
        context: Context,
        entry: WidgetProviderEntry,
        wPx: Int,
        hPx: Int,
        dp: Float,
        tokens: NexusColorTokens
    ): Bitmap? {
        val providerClass = entry.info?.provider?.className ?: ""
        val nexusKind = entry.nexusKind ?: ""

        val isGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val isDefault = com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled
        val bgMode = when {
            isGlass -> NexusWidgetConfig.BG_GLASS
            isDefault -> NexusWidgetConfig.BG_SOLID
            else -> NexusWidgetConfig.BG_NEUMORPHIC
        }
        val accentHex = String.format("#%06X", 0xFFFFFF and tokens.accent)

        val minWDp = (wPx / dp).toInt().coerceAtLeast(1)
        val minHDp = (hPx / dp).toInt().coerceAtLeast(1)
        val config = NexusWidgetConfig.InstanceConfig(
            appWidgetId = -1,
            backgroundMode = bgMode,
            backgroundOpacity = if (isGlass) 0.65f else 1.0f,
            accentColor = accentHex,
            shapeStyle = 1,
            calendarViewMode = "MONTHLY",
            showCountWhenSmall = false,
            cornerRadius = 16,
            isExpressive = false,
            clockStyle = 0
        )

        return when {
            providerClass.contains("NexusGlanceWidgetProvider") || nexusKind == "glance" -> {
                val mockWeather = createMockWeatherData(context)
                val pWidth = maxOf(wPx, (180 * dp).toInt())
                val pHeight = maxOf(hPx, (90 * dp).toInt())
                val pMinW = (pWidth / dp).toInt()
                val pMinH = (pHeight / dp).toInt()
                com.nexus.launcher.ui.widgets.glance.NexusGlanceRenderer(mockWeather).render(context, pWidth, pHeight, config, pMinW, pMinH, -1f)
            }
            providerClass.contains("NexusMusicWidgetProvider") || nexusKind == "music" -> {
                NexusMusicRenderer().render(context, wPx, hPx, config, minWDp, minHDp, 0.42f)
            }
            providerClass.contains("NexusWeatherWidgetProvider") || nexusKind == "weather" -> {
                val mockWeather = createMockWeatherData(context)
                NexusWeatherRenderer(mockWeather).render(context, wPx, hPx, config, minWDp, minHDp, -1f)
            }
            providerClass.contains("NexusCalendarWidgetProvider") || nexusKind == "calendar" -> {
                val mockEvents = createMockCalendarEvents(context)
                NexusCalendarRenderer(mockEvents).render(context, wPx, hPx, config, minWDp, minHDp, -1f)
            }
            providerClass.contains("NexusClockWidgetProvider") || nexusKind == "clock" -> {
                NexusClockRenderer().render(context, wPx, hPx, config, minWDp, minHDp, -1f)
            }
            providerClass.contains("NexusPerformanceWidgetProvider") || nexusKind == "performance" -> {
                val pWidth = maxOf(wPx, (200 * dp).toInt())
                val pHeight = maxOf(hPx, (120 * dp).toInt())
                val pMinW = (pWidth / dp).toInt()
                val pMinH = (pHeight / dp).toInt()
                com.nexus.launcher.ui.widgets.performance.NexusPerformanceRenderer().render(context, pWidth, pHeight, config, pMinW, pMinH, -1f)
            }
            providerClass.contains("NexusProgressWidgetProvider") || nexusKind == "progress" -> {
                val pWidth = maxOf(wPx, (180 * dp).toInt())
                val pHeight = maxOf(hPx, (110 * dp).toInt())
                val pMinW = (pWidth / dp).toInt()
                val pMinH = (pHeight / dp).toInt()
                com.nexus.launcher.ui.widgets.progress.NexusProgressRenderer().render(context, pWidth, pHeight, config, pMinW, pMinH, -1f)
            }
            providerClass.contains("NexusAgendaWidgetProvider") || nexusKind == "agenda" -> {
                val mockEvents = createMockCalendarEvents(context)
                val pWidth = maxOf(wPx, (160 * dp).toInt())
                val pHeight = maxOf(hPx, (110 * dp).toInt())
                val pMinW = (pWidth / dp).toInt()
                val pMinH = (pHeight / dp).toInt()
                com.nexus.launcher.ui.widgets.agenda.NexusAgendaRenderer(mockEvents).render(context, pWidth, pHeight, config, pMinW, pMinH, -1f)
            }
            providerClass.contains("NexusNotesWidgetProvider") || nexusKind == "notes" -> {
                val mockNote = com.nexus.launcher.ui.widgets.notes.NoteData(
                    title = context.getString(com.nexus.launcher.R.string.widget_preview_note_title),
                    body = context.getString(com.nexus.launcher.R.string.widget_preview_note_body),
                    urgency = com.nexus.launcher.ui.widgets.notes.NoteData.URGENCY_SCHEDULE,
                    status = com.nexus.launcher.ui.widgets.notes.NoteData.STATUS_WIP,
                    progress = 65,
                    updatedAt = System.currentTimeMillis()
                )
                val pWidth = maxOf(wPx, (160 * dp).toInt())
                val pHeight = maxOf(hPx, (110 * dp).toInt())
                val pMinW = (pWidth / dp).toInt()
                val pMinH = (pHeight / dp).toInt()
                com.nexus.launcher.ui.widgets.notes.NexusNotesRenderer(mockNote).render(context, pWidth, pHeight, config, pMinW, pMinH, -1f)
            }
            providerClass.contains("NexusBatteryWidgetProvider") || nexusKind == "battery" -> {
                val pWidth = maxOf(wPx, (160 * dp).toInt())
                val pHeight = maxOf(hPx, (110 * dp).toInt())
                val pMinW = (pWidth / dp).toInt()
                val pMinH = (pHeight / dp).toInt()
                val mockConsumers = com.nexus.launcher.ui.widgets.battery.NexusBatteryUsageHelper.createMockConsumers(context)
                val mockBattery = com.nexus.launcher.ui.widgets.battery.BatterySnapshot(
                    percent = 82,
                    isCharging = true,
                    statusText = context.getString(R.string.battery_status_charging),
                    topConsumers = mockConsumers
                )
                NexusBatteryRenderer(mockBattery).render(context, pWidth, pHeight, config, pMinW, pMinH, -1f)
            }
            providerClass.contains("NexusSearchWidgetProvider") || nexusKind == "search" -> {
                renderSearchPreview(context, wPx, hPx, dp, tokens)
            }
            nexusKind.startsWith("shortcut_box") || nexusKind.startsWith("app_box") -> {
                renderBoxPreview(context, wPx, hPx, dp, tokens)
            }
            entry.isLivingMosaic || nexusKind.startsWith("living_mosaic") -> {
                val (sx, sy) = NexusWidgetKinds.mosaicSpan(nexusKind)
                renderMosaicPreview(wPx, hPx, dp, sx, sy, tokens)
            }
            else -> null
        }
    }

    fun createMockWeatherData(context: Context): WeatherData {
        val unitStr = if (com.nexus.launcher.ui.widgets.weather.NexusWeatherRepository.getTemperatureUnit(context) == "fahrenheit") "°F" else "°C"
        val isFahr = unitStr == "°F"
        val currentT = if (isFahr) 72 else 22
        return WeatherData(
            currentTemp = currentT,
            apparentTemp = currentT,
            weatherCode = 2,
            highTemp = if (isFahr) 78 else 25,
            lowTemp = if (isFahr) 64 else 16,
            unit = unitStr,
            timestamp = System.currentTimeMillis(),
            daily = listOf(
                DailyForecast(if (isFahr) 78 else 25, if (isFahr) 64 else 16, 2, context.getString(R.string.weather_today)),
                DailyForecast(if (isFahr) 80 else 26, if (isFahr) 65 else 17, 1, previewDay(1)),
                DailyForecast(if (isFahr) 76 else 24, if (isFahr) 62 else 15, 61, previewDay(2)),
                DailyForecast(if (isFahr) 74 else 23, if (isFahr) 60 else 14, 3, previewDay(3)),
                DailyForecast(if (isFahr) 77 else 24, if (isFahr) 63 else 16, 0, previewDay(4)),
                DailyForecast(if (isFahr) 79 else 26, if (isFahr) 64 else 17, 1, previewDay(5)),
                DailyForecast(if (isFahr) 81 else 27, if (isFahr) 66 else 18, 2, previewDay(6))
            ),
            hourly = listOf(
                HourlyForecast(previewHour(12), currentT, 2),
                HourlyForecast(previewHour(13), currentT + 2, 2),
                HourlyForecast(previewHour(14), currentT + 3, 1)
            ),
            locationName = context.getString(R.string.weather_local_area),
            humidity = 68,
            windSpeed = if (isFahr) 8 else 14,
            sunrise = previewTime(context, 5, 12),
            sunset = previewTime(context, 20, 34),
            precipitationProbability = 15
        )
    }

    fun createMockCalendarEvents(context: Context): List<CalendarEvent> {
        return listOf(
            CalendarEvent(
                title = context.getString(R.string.mock_event_title),
                startMs = System.currentTimeMillis() + 3600000L,
                endMs = System.currentTimeMillis() + 7200000L,
                color = Color.parseColor("#7EB8D4"),
                isAllDay = false
            )
        )
    }

    private fun renderSearchPreview(context: Context, w: Int, h: Int, dp: Float, tokens: NexusColorTokens): Bitmap {
        return NexusWidgetPreviewDrawers.renderSearchPreview(context, w, h, dp, tokens)
    }

    private fun renderMosaicPreview(w: Int, h: Int, dp: Float, spanX: Int, spanY: Int, tokens: NexusColorTokens): Bitmap {
        return NexusWidgetPreviewDrawers.renderMosaicPreview(w, h, dp, spanX, spanY, tokens)
    }

    private fun renderBoxPreview(context: Context, w: Int, h: Int, dp: Float, tokens: NexusColorTokens): Bitmap {
        return NexusWidgetPreviewDrawers.renderBoxPreview(context, w, h, dp, tokens)
    }

    fun clear() {
        cache.clear()
    }

    /** Short weekday [daysAhead] from today, in the app language. */
    private fun previewDay(daysAhead: Int): String =
        java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
            .format(java.util.Date(System.currentTimeMillis() + daysAhead * 86_400_000L))

    private fun previewHour(hour: Int): String = previewClock(hour, 0,
        android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "j"))

    private fun previewTime(context: Context, hour: Int, minute: Int): String = previewClock(hour, minute,
        if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm"
        else android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "hmm a"))

    private fun previewClock(hour: Int, minute: Int, pattern: String): String {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
        }
        return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(cal.time)
    }
}
