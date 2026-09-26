package com.nexus.launcher.ui.widgets.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize
import com.nexus.launcher.ui.widgets.glance.NexusWeatherConditionResolver
import android.text.TextUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detailed Weather Station widget renderer replicating the multi-metric station layout:
 * - Top: Weather icon with condition, current temp, date/time, and location
 * - Middle: Daylight duration pill (sunrise to sunset) and precipitation probability pill
 * - Metrics: Humidity and wind speed indicators
 * - Bottom: 7-day forecast capsule columns with highlighted current day
 *
 * Zero Paint/Path allocations during [drawContent].
 */
class NexusWeatherStationRenderer(
    private val weatherData: WeatherData?
) : NexusWidgetRenderer() {

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryTextPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val headlineTextPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillRect = RectF()

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)

        val primaryColor = palette.textPrimary
        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#4B5563")
        } else if (isGlass) {
            Color.argb(220, 240, 240, 240)
        } else {
            tokens.textSecondary
        }

        if (weatherData == null) {
            primaryTextPaint.apply {
                color = primaryColor
                textSize = 14f * dp
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                applyLegibility(this, isGlass, palette.isLight, dp)
            }
            canvas.drawText(
                context.getString(R.string.weather_no_data),
                width / 2f,
                height / 2f,
                primaryTextPaint
            )
            return
        }

        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, width.toFloat(), height.toFloat(), dp)
        val padL = insets.left.coerceAtLeast(14f * dp)
        val padR = insets.right.coerceAtLeast(14f * dp)
        val padT = insets.top.coerceAtLeast(12f * dp)
        val padB = insets.bottom.coerceAtLeast(12f * dp)
        val availW = (width - padL - padR).coerceAtLeast(1f)
        val availH = (height - padT - padB).coerceAtLeast(1f)

        // Top Section: Icon + (Time, Condition Temp, Location)
        val topY = padT
        val iconSize = (availH * 0.26f).coerceIn(20f * dp, 44f * dp)
        val iconCx = padL + iconSize * 0.9f
        val textLeft = iconCx + iconSize * 1.1f

        // Time string e.g. "Tuesday, 11:56"
        val locale = LocaleObserver.getEffectiveLocale(context)
        val timePattern = if (android.text.format.DateFormat.is24HourFormat(context)) "EEEE, H:mm" else "EEEE, h:mm"
        val timeFormat = SimpleDateFormat(timePattern, locale)
        val rawTimeStr = timeFormat.format(Date())
        val timeStr = LocaleDigitUtils.localizeDigits(rawTimeStr, locale)

        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = (availH * 0.12f).coerceIn(9f * dp, 12f * dp)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyLegibility(this, isGlass, palette.isLight, dp)
        }
        val timeY = topY + secondaryTextPaint.textSize * 1.1f
        canvas.drawText(timeStr, textLeft, timeY, secondaryTextPaint)

        // Condition + Temperature e.g. "Cloudy 22°C"
        val condition = NexusWeatherConditionResolver.resolveCondition(context, weatherData.weatherCode)
        val tempText = "${LocaleDigitUtils.formatNumber(weatherData.currentTemp, locale)}${weatherData.unit}"
        val headline = "$condition $tempText"

        headlineTextPaint.apply {
            color = primaryColor
            textSize = (availH * 0.22f).coerceIn(13f * dp, 22f * dp)
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyLegibility(this, isGlass, palette.isLight, dp)
        }
        val maxHeadlineW = (width - padR) - textLeft - 2f * dp
        while (headlineTextPaint.measureText(headline) > maxHeadlineW && headlineTextPaint.textSize > 11f * dp) {
            headlineTextPaint.textSize -= 0.5f * dp
        }
        val finalHeadline = if (headlineTextPaint.measureText(headline) > maxHeadlineW) {
            TextUtils.ellipsize(headline, headlineTextPaint, maxHeadlineW, TextUtils.TruncateAt.END).toString()
        } else {
            headline
        }
        val headlineY = timeY + headlineTextPaint.textSize * 1.25f
        canvas.drawText(finalHeadline, textLeft, headlineY, headlineTextPaint)

        // Location e.g. "Kyiv, Ukraine"
        val place = weatherData.locationName?.takeUnless { NexusWeatherLocation.isPlaceholder(context, it) }
            ?: context.getString(R.string.weather_local_area)
        val maxPlaceW = (width - padR) - textLeft - 2f * dp
        val finalPlace = if (secondaryTextPaint.measureText(place) > maxPlaceW) {
            TextUtils.ellipsize(place, secondaryTextPaint, maxPlaceW, TextUtils.TruncateAt.END).toString()
        } else {
            place
        }
        val placeY = headlineY + secondaryTextPaint.textSize * 1.3f
        canvas.drawText(finalPlace, textLeft, placeY, secondaryTextPaint)

        // Draw top weather glyph (Sun behind cloud)
        val iconCy = (timeY + placeY) / 2f
        NexusWeatherStationDrawers.drawCompositeWeatherIcon(
            canvas, iconCx, iconCy, iconSize, weatherData.weatherCode, primaryColor, tokens.accent, dp
        )

        val remainingSpace = (height - padB) - placeY
        if (remainingSpace < 28f * dp) return

        // Middle Pills Container Styling
        val pillBgColor = if (isGlass) {
            if (palette.isLight) Color.argb(45, 0, 0, 0) else Color.argb(60, 20, 26, 32)
        } else {
            Color.argb(35, 128, 128, 128)
        }
        val pillBorderColor = if (isGlass) {
            if (palette.isLight) Color.argb(35, 0, 0, 0) else Color.argb(45, 255, 255, 255)
        } else {
            Color.argb(25, 255, 255, 255)
        }
        pillBgPaint.color = pillBgColor
        pillStrokePaint.apply {
            color = pillBorderColor
            strokeWidth = 1f * dp
        }

        val canFitForecast = remainingSpace >= 148f * dp && weatherData.daily.isNotEmpty()
        val canFitMetrics = remainingSpace >= 95f * dp
        val canFitRainPill = remainingSpace >= 58f * dp

        if (canFitForecast) {
            // Full expanded station layout:
            val pillH = (availH * 0.09f).coerceIn(24f * dp, 30f * dp)
            val pillGap = 5f * dp
            var curY = placeY + 8f * dp

            // 1. Daylight Pill
            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            NexusWeatherStationDrawers.drawDaylightPillContent(
                context, canvas, pillRect, dp, primaryColor, secondaryColor,
                weatherData.sunrise, weatherData.sunset, locale, isGlass, palette.isLight, config
            )

            // 2. Rain Pill
            curY += pillH + pillGap
            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            drawRainPillContent(context, canvas, pillRect, curY, pillH, weatherData.precipitationProbability, primaryColor, isGlass, palette.isLight, dp, config, locale)

            // 3. Metrics Row
            curY += pillH + pillGap
            val metricY = curY + 12f * dp
            drawMetricsRow(context, canvas, padL, width - padR, metricY, weatherData, secondaryColor, isGlass, palette.isLight, dp, config, locale)

            // 4. 7-Day Forecast Columns
            curY = metricY + 8f * dp
            val forecastH = (height - padB) - curY
            if (forecastH >= 50f * dp) {
                NexusWeatherStationDrawers.drawForecastColumns(
                    context, canvas, padL, curY, availW, forecastH, dp,
                    weatherData.daily.take(7), primaryColor, secondaryColor, tokens.accent,
                    isGlass, palette.isLight, config, locale
                )
            }
        } else if (canFitMetrics) {
            // Intermediate size: Daylight Pill + Rain Pill + Metrics Row (Humidity & Wind)
            // Distribute gaps to fill the entire vertical height gracefully with no empty void!
            val pillH = (availH * 0.11f).coerceIn(24f * dp, 32f * dp)
            val metricsH = 14f * dp
            val contentH = (2 * pillH) + metricsH
            val gap = ((remainingSpace - contentH) / 4f).coerceIn(4f * dp, 14f * dp)

            var curY = placeY + gap
            // 1. Daylight Pill
            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            NexusWeatherStationDrawers.drawDaylightPillContent(
                context, canvas, pillRect, dp, primaryColor, secondaryColor,
                weatherData.sunrise, weatherData.sunset, locale, isGlass, palette.isLight, config
            )

            // 2. Rain Pill
            curY += pillH + gap
            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            drawRainPillContent(context, canvas, pillRect, curY, pillH, weatherData.precipitationProbability, primaryColor, isGlass, palette.isLight, dp, config, locale)

            // 3. Metrics Row
            curY += pillH + gap
            val metricY = curY + metricsH * 0.75f
            drawMetricsRow(context, canvas, padL, width - padR, metricY, weatherData, secondaryColor, isGlass, palette.isLight, dp, config, locale)
        } else if (canFitRainPill) {
            // Short-medium size: Daylight Pill + Rain Pill, evenly spaced
            val pillH = (availH * 0.12f).coerceIn(22f * dp, 28f * dp)
            val gap = ((remainingSpace - 2 * pillH) / 3f).coerceIn(4f * dp, 10f * dp)
            var curY = placeY + gap

            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            NexusWeatherStationDrawers.drawDaylightPillContent(
                context, canvas, pillRect, dp, primaryColor, secondaryColor,
                weatherData.sunrise, weatherData.sunset, locale, isGlass, palette.isLight, config
            )

            curY += pillH + gap
            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            drawRainPillContent(context, canvas, pillRect, curY, pillH, weatherData.precipitationProbability, primaryColor, isGlass, palette.isLight, dp, config, locale)
        } else {
            // Very compact: Single Daylight Pill centered vertically in remainingSpace
            val pillH = (remainingSpace * 0.7f).coerceIn(22f * dp, 28f * dp)
            val curY = placeY + (remainingSpace - pillH) / 2f
            pillRect.set(padL, curY, width - padR, curY + pillH)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillBgPaint)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStrokePaint)
            NexusWeatherStationDrawers.drawDaylightPillContent(
                context, canvas, pillRect, dp, primaryColor, secondaryColor,
                weatherData.sunrise, weatherData.sunset, locale, isGlass, palette.isLight, config
            )
        }
    }

    private fun drawRainPillContent(
        context: Context, canvas: Canvas, rect: RectF, curY: Float, pillH: Float,
        rainProb: Int, primaryColor: Int, isGlass: Boolean, isLight: Boolean,
        dp: Float, config: NexusWidgetConfig.InstanceConfig, locale: Locale
    ) {
        val rainText = context.getString(R.string.weather_rain_format, LocaleDigitUtils.formatNumber(rainProb, locale))
        primaryTextPaint.apply {
            color = primaryColor
            textSize = (12f * dp).coerceAtMost(pillH * 0.45f)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            applyLegibility(this, isGlass, isLight, dp)
        }
        val rainIconSize = pillH * 0.35f
        val rainMidX = rect.centerX()
        val rainTextW = primaryTextPaint.measureText(rainText)
        val rainIconCx = rainMidX - (rainTextW / 2f) - 10f * dp
        val rainTextY = curY + pillH / 2f + primaryTextPaint.textSize * 0.35f
        canvas.drawText(rainText, rainMidX + (rainIconSize / 2f), rainTextY, primaryTextPaint)
        NexusWeatherIconDraw.drawWeatherIcon(
            canvas, rainIconCx, curY + pillH / 2f, rainIconSize, 61,
            primaryColor, isGlass, isLight, dp
        )
    }

    private fun drawMetricsRow(
        context: Context, canvas: Canvas, leftX: Float, rightX: Float, metricY: Float,
        weatherData: WeatherData, secondaryColor: Int, isGlass: Boolean, isLight: Boolean,
        dp: Float, config: NexusWidgetConfig.InstanceConfig, locale: Locale
    ) {
        val humText = context.getString(R.string.weather_humidity_format, LocaleDigitUtils.formatNumber(weatherData.humidity, locale))
        val windUnit = if (weatherData.unit == "°F") "mph" else "km/h"
        val windText = context.getString(R.string.weather_wind_format, LocaleDigitUtils.formatNumber(weatherData.windSpeed, locale), windUnit)

        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = (11.5f * dp).coerceAtMost((rightX - leftX) * 0.07f)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyLegibility(this, isGlass, isLight, dp)
        }
        canvas.drawText(humText, leftX + 4f * dp, metricY, secondaryTextPaint)

        secondaryTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(windText, rightX - 4f * dp, metricY, secondaryTextPaint)
    }

    private fun applyLegibility(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        }
    }
}
