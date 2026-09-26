package com.nexus.launcher.ui.widgets.glance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.format.DateFormat
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusNeumorphicPaletteResolver
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize
import com.nexus.launcher.ui.widgets.weather.WeatherData
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Renders the Clock + Weather + Calendar ("Nexus Glance") all-in-one widget.
 * Features:
 * - Bold digital clock (HH:mm)
 * - Localized full date (e.g., "Wednesday, September 9")
 * - Translucent capsule pill with a procedural 4-pointed sparkle star and weather condition
 */
class NexusGlanceRenderer(
    private val weatherData: WeatherData? = null
) : NexusWidgetRenderer() {

    companion object {
        const val STYLE_MINIMAL = 0
        const val STYLE_ZEN_PILL = 1
        const val STYLE_EDITORIAL = 2
        const val STYLE_STATION = 3
    }

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sparklePath = Path()
    private val pillRect = RectF()
    private val secondPillRect = RectF()

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val locale = LocaleObserver.getEffectiveLocale(context)
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val palette = NexusNeumorphicDraw.resolvePalette(context, config.themeMode)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive) &&
            !config.isBorderless

        // Theme-harmonized default colors
        val primaryColor = if (isNeumorphic) palette.textPrimary else tokens.textPrimary
        val secondaryColor = if (isNeumorphic) palette.textSecondary else tokens.textSecondary
        val accentColor = if (isNeumorphic) palette.accent else tokens.accent

        sparklePaint.color = accentColor

        // Formatted Time
        val is24h = DateFormat.is24HourFormat(context)
        val timePattern = if (is24h) "HH:mm" else "hh:mm"
        val timeFormat = SimpleDateFormat(timePattern, locale)
        val timeStr = LocaleDigitUtils.localizeDigits(timeFormat.format(Date()), locale)

        // Formatted Date
        val datePattern = when (size) {
            WidgetSize.TINY -> "MMM d"
            WidgetSize.SMALL -> "EEE, MMM d"
            else -> "EEEE, MMMM d"
        }
        val dateFormat = SimpleDateFormat(datePattern, locale)
        val dateStr = LocaleDigitUtils.localizeDigits(dateFormat.format(Date()), locale)

        // Weather Data
        val tempValue = weatherData?.currentTemp ?: 23
        val unitStr = weatherData?.unit ?: "°C"
        val conditionStr = if (weatherData != null) {
            NexusWeatherConditionResolver.resolveCondition(context, weatherData.weatherCode)
        } else {
            context.getString(R.string.weather_condition_clear)
        }
        val formattedTemp = "${LocaleDigitUtils.formatNumber(tempValue, locale)}$unitStr"

        when (config.clockStyle) {
            STYLE_ZEN_PILL -> NexusGlanceStyleDrawers.drawZenPillStyle(
                context, canvas, width, height, dp, config, tokens, palette, isGlass, isNeumorphic,
                primaryColor, secondaryColor, accentColor, timeStr, dateStr, formattedTemp, conditionStr
            )
            STYLE_EDITORIAL -> NexusGlanceStyleDrawers.drawEditorialStyle(
                context, canvas, width, height, dp, config, tokens, palette, isGlass, isNeumorphic,
                primaryColor, secondaryColor, accentColor, locale, is24h, dateStr, formattedTemp, conditionStr
            )
            STYLE_STATION -> NexusGlanceStyleDrawers.drawStationStyle(
                context, canvas, width, height, dp, config, tokens, palette, isGlass, isNeumorphic,
                primaryColor, secondaryColor, accentColor, timeStr, dateStr, formattedTemp, conditionStr, locale, weatherData
            )
            else -> drawMinimalStyle(
                context, canvas, width, height, size, dp, config, tokens, palette, isGlass, isNeumorphic,
                primaryColor, secondaryColor, accentColor, timeStr, dateStr, formattedTemp, conditionStr
            )
        }
    }

    private fun drawMinimalStyle(
        context: Context, canvas: Canvas, w: Int, h: Int, size: WidgetSize, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, tokens: com.nexus.launcher.theme.NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette, isGlass: Boolean, isNeumorphic: Boolean,
        primaryColor: Int, secondaryColor: Int, accentColor: Int,
        timeStr: String, dateStr: String, formattedTemp: String, conditionStr: String
    ) {
        val cx = w / 2f
        if (size == WidgetSize.TINY) {
            // Compact 2-row layout: Large centered time, small weather badge
            clockPaint.apply {
                color = primaryColor
                textSize = minOf(h * 0.46f, 32f * dp)
                typeface = getTypeface(context, config, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                applyTextLegibility(this, isGlass, palette.isLight, dp)
            }
            canvas.drawText(timeStr, cx, h * 0.52f, clockPaint)

            weatherPaint.apply {
                color = secondaryColor
                textSize = minOf(h * 0.26f, 13f * dp)
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$formattedTemp · $conditionStr", cx, h * 0.82f, weatherPaint)
            return
        }

        // Standard 3-Tier Centered Presentation
        val clockTextSize = (h * 0.36f).coerceIn(26f * dp, 48f * dp)
        clockPaint.apply {
            color = primaryColor
            textSize = clockTextSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val clockY = h * 0.38f + clockTextSize * 0.32f
        canvas.drawText(timeStr, cx, clockY, clockPaint)

        val dateTextSize = (h * 0.14f).coerceIn(12f * dp, 16f * dp)
        datePaint.apply {
            color = secondaryColor
            textSize = dateTextSize
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val dateY = clockY + dateTextSize * 1.55f
        canvas.drawText(dateStr, cx, dateY, datePaint)

        // Centered Weather Capsule Pill
        val weatherText = context.getString(R.string.glance_pill_text_format, formattedTemp, conditionStr)
        val pillHeight = (h * 0.22f).coerceIn(22f * dp, 30f * dp)
        val pillRadius = pillHeight / 2f
        val weatherTextSize = (pillHeight * 0.52f).coerceIn(11f * dp, 14f * dp)

        weatherPaint.apply {
            color = primaryColor
            textSize = weatherTextSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val textWidth = weatherPaint.measureText(weatherText)
        val sparkleRadius = pillHeight * 0.24f
        val pillPadX = 12f * dp
        val iconTextGap = 6f * dp
        val pillWidth = pillPadX + sparkleRadius * 2f + iconTextGap + textWidth + pillPadX

        val pillLeft = cx - pillWidth / 2f
        val pillTop = dateY + 10f * dp
        pillRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        drawPillBackground(canvas, pillRect, pillRadius, isGlass, isNeumorphic, palette, dp)

        val sparkleCx = pillRect.left + pillPadX + sparkleRadius
        val sparkleCy = pillRect.centerY()
        drawSparkleStar(canvas, sparkleCx, sparkleCy, sparkleRadius)

        val weatherTextX = sparkleCx + sparkleRadius + iconTextGap
        val weatherTextY = pillRect.centerY() + weatherTextSize * 0.35f
        canvas.drawText(weatherText, weatherTextX, weatherTextY, weatherPaint)
    }

    private fun drawPillBackground(
        canvas: Canvas, rect: RectF, radius: Float, isGlass: Boolean,
        isNeumorphic: Boolean, palette: NexusNeumorphicDraw.SoftPalette, dp: Float
    ) {
        val pillBgAlpha = if (isGlass) 40 else if (palette.isLight) 22 else 45
        pillBgPaint.color = if (palette.isLight) {
            Color.argb(pillBgAlpha, 0, 0, 0)
        } else {
            Color.argb(pillBgAlpha, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, radius, radius, pillBgPaint)

        if (isGlass || isNeumorphic) {
            pillStrokePaint.strokeWidth = 1f * dp
            pillStrokePaint.color = if (palette.isLight) Color.argb(28, 0, 0, 0) else Color.argb(32, 255, 255, 255)
            canvas.drawRoundRect(rect, radius, radius, pillStrokePaint)
        }
    }

    private fun drawSparkleStar(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        sparklePath.reset()
        sparklePath.moveTo(cx, cy - r)
        sparklePath.quadTo(cx, cy, cx + r, cy)
        sparklePath.quadTo(cx, cy, cx, cy + r)
        sparklePath.quadTo(cx, cy, cx - r, cy)
        sparklePath.quadTo(cx, cy, cx, cy - r)
        sparklePath.close()
        canvas.drawPath(sparklePath, sparklePaint)
    }

    private fun applyTextLegibility(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        } else {
            paint.clearShadowLayer()
        }
    }
}
