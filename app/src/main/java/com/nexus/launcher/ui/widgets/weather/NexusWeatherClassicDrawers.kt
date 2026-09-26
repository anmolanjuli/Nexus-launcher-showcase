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
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset

/**
 * Drawer functions for the Classic Weather widget tiers (Icon-only, Compact, Standard, Expanded).
 * All Paint and Rect allocations are pre-allocated class fields (zero per-frame allocations).
 */
object NexusWeatherClassicDrawers {

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wellRect = RectF()

    fun drawIconOnly(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, palette: NexusNeumorphicDraw.SoftPalette,
        isGlass: Boolean, primaryColor: Int, weatherData: WeatherData
    ) {
        primaryTextPaint.apply {
            color = primaryColor
            textSize = minOf(h * 0.45f, 32f * dp)
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val iconSize = minOf(h * 0.28f, 16f * dp)
        val textY = h / 2f + primaryTextPaint.textSize * 0.35f
        canvas.drawText(formatTemp(context, weatherData.currentTemp), w * 0.42f, textY, primaryTextPaint)
        NexusWeatherIconDraw.drawWeatherIcon(canvas, w * 0.76f, h / 2f, iconSize, weatherData.weatherCode, primaryColor, isGlass, palette.isLight, dp)
    }

    fun drawCompact(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, palette: NexusNeumorphicDraw.SoftPalette,
        isGlass: Boolean, primaryColor: Int, secondaryColor: Int, weatherData: WeatherData
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val rightMargin = insets.right
        val topMargin = h / 2f + 4f * dp

        primaryTextPaint.apply {
            color = primaryColor
            textSize = (h * 0.32f).coerceIn(24f * dp, 34f * dp)
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = 11f * dp
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }

        canvas.drawText(formatTemp(context, weatherData.currentTemp), leftMargin, topMargin, primaryTextPaint)

        val place = locationLabel(context, weatherData.locationName)
        canvas.drawText(place, leftMargin, topMargin + 18f * dp, secondaryTextPaint)

        // Weather Icon on right
        val iconSize = (h * 0.28f).coerceIn(14f * dp, 22f * dp)
        val iconX = w - rightMargin - iconSize
        val iconY = h / 2f
        NexusWeatherIconDraw.drawWeatherIcon(canvas, iconX, iconY, iconSize, weatherData.weatherCode, primaryColor, isGlass, palette.isLight, dp)
    }

    fun drawStandard(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, palette: NexusNeumorphicDraw.SoftPalette,
        isGlass: Boolean, primaryColor: Int, secondaryColor: Int, weatherData: WeatherData
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val rightMargin = insets.right
        val availW = w - leftMargin - rightMargin
        val availH = h - insets.top - insets.bottom

        val topMargin = insets.top + (availH * 0.22f).coerceIn(24f * dp, 38f * dp)

        // Weather icon top-right
        val iconSize = (availH * 0.16f).coerceIn(18f * dp, 26f * dp)
        val iconCx = w - rightMargin - iconSize * 0.8f
        val iconCy = topMargin - iconSize * 0.35f
        NexusWeatherIconDraw.drawWeatherIcon(canvas, iconCx, iconCy, iconSize, weatherData.weatherCode, primaryColor, isGlass, palette.isLight, dp)

        val tempStr = formatTemp(context, weatherData.currentTemp)
        primaryTextPaint.apply {
            color = primaryColor
            textSize = (availH * 0.24f).coerceIn(26f * dp, 40f * dp)
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val maxTempW = iconCx - iconSize * 0.6f - leftMargin
        while (primaryTextPaint.measureText(tempStr) > maxTempW && primaryTextPaint.textSize > 18f * dp) {
            primaryTextPaint.textSize -= 1f * dp
        }
        canvas.drawText(tempStr, leftMargin, topMargin, primaryTextPaint)

        val metaY = topMargin + (availH * 0.09f).coerceIn(16f * dp, 20f * dp)
        val labels = LocaleObserver.wrapContext(context)
        val highLabel = labels.getString(R.string.weather_high_label)
        val lowLabel = labels.getString(R.string.weather_low_label)
        val highTempStr = formatNumber(labels, weatherData.highTemp)
        val lowTempStr = formatNumber(labels, weatherData.lowTemp)
        val hlText = labels.getString(R.string.weather_high_low, highLabel, highTempStr, lowLabel, lowTempStr)

        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = (availH * 0.07f).coerceIn(10f * dp, 12f * dp)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        canvas.drawText(hlText, leftMargin, metaY, secondaryTextPaint)

        val place = locationLabel(labels, weatherData.locationName)
        secondaryTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(place, w - rightMargin, metaY, secondaryTextPaint)

        // Hourly strip centered vertically in remaining space
        val remainingH = (h - insets.bottom) - metaY
        if (remainingH >= 45f * dp && weatherData.hourly.isNotEmpty()) {
            val divY = metaY + (remainingH * 0.16f).coerceIn(8f * dp, 14f * dp)
            dividerPaint.apply {
                color = if (isGlass) {
                    if (palette.isLight) Color.argb(40, 0, 0, 0) else Color.argb(65, 255, 255, 255)
                } else {
                    Color.argb(40, 128, 128, 128)
                }
                strokeWidth = 1f * dp
            }
            canvas.drawLine(leftMargin, divY, w - rightMargin, divY, dividerPaint)

            // Dynamic hour count based on width: 3 to 6 hours
            val numHours = (availW / (48f * dp)).toInt().coerceIn(3, weatherData.hourly.size.coerceAtMost(6))
            val colW = availW / numHours.toFloat()

            // Center strip vertically in remaining space below divider
            val stripAreaH = (h - insets.bottom) - divY
            val stripCenterY = divY + stripAreaH / 2f

            if (config.backgroundMode == NexusWidgetConfig.BG_NEUMORPHIC) {
                wellRect.set(leftMargin - 4f * dp, stripCenterY - 26f * dp, w - rightMargin + 4f * dp, stripCenterY + 26f * dp)
                NexusNeumorphicDraw.drawDebossedWell(canvas, wellRect, 6f * dp, palette, dp)
            }

            secondaryTextPaint.apply {
                color = secondaryColor
                textSize = (stripAreaH * 0.18f).coerceIn(9f * dp, 11f * dp)
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                applyTextLegibility(this, isGlass, palette.isLight, dp)
            }
            primaryTextPaint.apply {
                color = primaryColor
                textSize = (stripAreaH * 0.22f).coerceIn(11f * dp, 13f * dp)
                typeface = getTypeface(context, config, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                applyTextLegibility(this, isGlass, palette.isLight, dp)
            }

            val hourlyIconSize = (stripAreaH * 0.20f).coerceIn(7f * dp, 11f * dp)
            weatherData.hourly.take(numHours).forEachIndexed { i, hourly ->
                val cx = leftMargin + colW * i + colW / 2f
                canvas.drawText(hourly.time, cx, stripCenterY - hourlyIconSize - 3f * dp, secondaryTextPaint)
                NexusWeatherIconDraw.drawWeatherIcon(canvas, cx, stripCenterY, hourlyIconSize, hourly.weatherCode, primaryColor, isGlass, palette.isLight, dp)
                canvas.drawText(formatTemp(context, hourly.temp), cx, stripCenterY + hourlyIconSize + primaryTextPaint.textSize * 0.85f, primaryTextPaint)
            }
        }
    }

    fun drawExpanded(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, palette: NexusNeumorphicDraw.SoftPalette,
        isGlass: Boolean, primaryColor: Int, secondaryColor: Int, weatherData: WeatherData
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val rightMargin = insets.right
        val availH = h - insets.top - insets.bottom

        val topMargin = insets.top + (availH * 0.20f).coerceIn(28f * dp, 44f * dp)

        // Weather Icon top-right
        val iconSize = (availH * 0.16f).coerceIn(20f * dp, 30f * dp)
        val iconCx = w - rightMargin - iconSize * 0.8f
        val iconCy = topMargin - iconSize * 0.35f
        NexusWeatherIconDraw.drawWeatherIcon(canvas, iconCx, iconCy, iconSize, weatherData.weatherCode, primaryColor, isGlass, palette.isLight, dp)

        // Large Current Temp
        val tempStr = formatTemp(context, weatherData.currentTemp)
        primaryTextPaint.apply {
            color = primaryColor
            textSize = (availH * 0.22f).coerceIn(28f * dp, 48f * dp)
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val maxTempW = iconCx - iconSize * 0.6f - leftMargin
        while (primaryTextPaint.measureText(tempStr) > maxTempW && primaryTextPaint.textSize > 20f * dp) {
            primaryTextPaint.textSize -= 1f * dp
        }
        canvas.drawText(tempStr, leftMargin, topMargin, primaryTextPaint)

        // Subtitle row: High/Low on left, Location on right
        val metaY = topMargin + (availH * 0.08f).coerceIn(16f * dp, 22f * dp)
        val highLabel = context.getString(R.string.weather_high_label)
        val lowLabel = context.getString(R.string.weather_low_label)
        val highTempStr = formatNumber(context, weatherData.highTemp)
        val lowTempStr = formatNumber(context, weatherData.lowTemp)
        val hlText = context.getString(R.string.weather_high_low, highLabel, highTempStr, lowLabel, lowTempStr)

        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = (availH * 0.06f).coerceIn(10f * dp, 13f * dp)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        canvas.drawText(hlText, leftMargin, metaY, secondaryTextPaint)

        val place = locationLabel(context, weatherData.locationName)
        secondaryTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(place, w - rightMargin, metaY, secondaryTextPaint)

        // Divider
        val divY = metaY + 12f * dp
        dividerPaint.apply {
            color = if (isGlass) {
                if (palette.isLight) Color.argb(40, 0, 0, 0) else Color.argb(65, 255, 255, 255)
            } else {
                Color.argb(40, 128, 128, 128)
            }
            strokeWidth = 1f * dp
        }
        canvas.drawLine(leftMargin, divY, w - rightMargin, divY, dividerPaint)

        // Dynamic Daily Forecast Rows: distributed evenly across remaining height
        val remainingH = (h - insets.bottom) - divY - 6f * dp
        val rowCount = (remainingH / (25f * dp)).toInt().coerceIn(3, weatherData.daily.size.coerceAtMost(7))
        val rowH = remainingH / rowCount.toFloat()

        primaryTextPaint.apply {
            color = primaryColor
            textSize = (rowH * 0.44f).coerceIn(11f * dp, 14f * dp)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = primaryTextPaint.textSize
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }

        weatherData.daily.take(rowCount).forEachIndexed { i, day ->
            val rowCenterY = divY + (i + 0.5f) * rowH
            val textBaseline = rowCenterY + primaryTextPaint.textSize * 0.35f

            // Day label e.g. "Today", "Sat"
            canvas.drawText(day.dayName, leftMargin, textBaseline, primaryTextPaint)

            // Condition icon centered
            val iconDailySize = (rowH * 0.35f).coerceIn(7f * dp, 11f * dp)
            NexusWeatherIconDraw.drawWeatherIcon(canvas, w / 2f, rowCenterY, iconDailySize, day.weatherCode, primaryColor, isGlass, palette.isLight, dp)

            // High / Low temp e.g. "27° / 18°"
            val dayHighStr = formatNumber(context, day.highTemp)
            val dayLowStr = formatNumber(context, day.lowTemp)
            val hlForecast = context.getString(R.string.weather_forecast_high_low, dayHighStr, dayLowStr)
            canvas.drawText(hlForecast, w - rightMargin, textBaseline, secondaryTextPaint)
        }
    }

    private fun applyTextLegibility(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        }
    }

    private fun formatNumber(context: Context, value: Int): String {
        val locale = LocaleObserver.getEffectiveLocale(context)
        return LocaleDigitUtils.formatNumber(value, locale)
    }

    private fun formatTemp(context: Context, value: Int): String {
        return "${formatNumber(context, value)}°"
    }

    private fun locationLabel(context: Context, name: String?): String {
        val labels = LocaleObserver.wrapContext(context)
        return name?.takeUnless { NexusWeatherLocation.isPlaceholder(labels, it) }
            ?: labels.getString(R.string.weather_local_area)
    }

    private fun getTypeface(context: Context, config: NexusWidgetConfig.InstanceConfig, style: Int): Typeface {
        val fontKey = config.fontFamily
        if (!fontKey.isNullOrEmpty() && fontKey != "default") {
            try {
                val family = com.nexus.launcher.typography.AppFontFamily.fromKey(fontKey)
                if (family.fontResId != null) {
                    val tf = androidx.core.content.res.ResourcesCompat.getFont(context, family.fontResId)
                    if (tf != null) return tf
                } else if (family.familyName != null) {
                    return Typeface.create(family.familyName, style)
                }
            } catch (_: Exception) {}
        }
        return com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, style)
    }
}
