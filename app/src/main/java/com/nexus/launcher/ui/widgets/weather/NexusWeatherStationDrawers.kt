package com.nexus.launcher.ui.widgets.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Drawing routines for the Detailed Weather Station widget:
 * - Daylight sunrise/sunset pill with live duration
 * - 7-day forecast capsule columns with today highlight
 * - Horizon sun icon and composite weather glyphs
 *
 * Zero Paint/Path allocations during drawing.
 */
object NexusWeatherStationDrawers {

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val sunFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val colRect = RectF()
    private val arcRect = RectF()

    fun drawDaylightPillContent(
        context: Context, canvas: Canvas, rect: RectF, dp: Float,
        primaryColor: Int, secondaryColor: Int,
        sunriseStr: String?, sunsetStr: String?, locale: Locale,
        isGlass: Boolean, isLight: Boolean, config: NexusWidgetConfig.InstanceConfig
    ) {
        val cy = rect.centerY()
        val textY = cy + (4f * dp)
        val textPad = 10f * dp

        // Sunrise (left)
        val sRise = sunriseStr ?: "5:00 am"
        primaryTextPaint.apply {
            color = primaryColor
            textSize = 11f * dp
            typeface = getTypeface(context, config, Typeface.NORMAL)
            applyLegibility(this, isGlass, isLight, dp)
        }
        val sunriseIconCx = rect.left + textPad + 8f * dp
        drawHorizonSunIcon(canvas, sunriseIconCx, cy, 7f * dp, isRise = true, color = primaryColor, dp = dp)

        // Sunset (right)
        val sSet = sunsetStr ?: "8:00 pm"
        val sunsetIconCx = rect.right - textPad - 8f * dp
        drawHorizonSunIcon(canvas, sunsetIconCx, cy, 7f * dp, isRise = false, color = primaryColor, dp = dp)

        // Auto-scale sunrise/sunset font if space between icons is constrained
        val availableTextW = (sunsetIconCx - 14f * dp) - (sunriseIconCx + 14f * dp)
        while (primaryTextPaint.measureText(sRise) + primaryTextPaint.measureText(sSet) + 8f * dp > availableTextW && primaryTextPaint.textSize > 8f * dp) {
            primaryTextPaint.textSize -= 0.5f * dp
        }

        primaryTextPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(sRise, sunriseIconCx + 14f * dp, textY, primaryTextPaint)

        primaryTextPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(sSet, sunsetIconCx - 14f * dp, textY, primaryTextPaint)

        // Duration (center) - only draw if sufficient room exists between sunrise and sunset
        val durationStr = calculateDuration(context, sunriseStr, sunsetStr, locale)
        secondaryTextPaint.apply {
            color = secondaryColor
            textSize = 10.5f * dp
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            applyLegibility(this, isGlass, isLight, dp)
        }
        val sunriseRight = sunriseIconCx + 14f * dp + primaryTextPaint.measureText(sRise)
        val sunsetLeft = sunsetIconCx - 14f * dp - primaryTextPaint.measureText(sSet)
        val centerAvailableW = sunsetLeft - sunriseRight
        if (centerAvailableW >= secondaryTextPaint.measureText(durationStr) + 10f * dp) {
            canvas.drawText(durationStr, rect.centerX(), textY, secondaryTextPaint)
        }
    }

    fun drawForecastColumns(
        context: Context, canvas: Canvas, left: Float, top: Float, totalW: Float, totalH: Float,
        dp: Float, dailyList: List<DailyForecast>, primaryColor: Int, secondaryColor: Int,
        accentColor: Int, isGlass: Boolean, isLight: Boolean,
        config: NexusWidgetConfig.InstanceConfig, locale: Locale
    ) {
        val count = dailyList.size
        val gap = 4f * dp
        val colW = (totalW - gap * (count - 1)) / count.toFloat()
        val showLowTemp = totalH >= 86f * dp

        dailyList.forEachIndexed { i, day ->
            val colLeft = left + i * (colW + gap)
            colRect.set(colLeft, top, colLeft + colW, top + totalH)

            val isToday = (i == 0)
            pillBgPaint.color = if (isToday) {
                Color.argb(55, 235, 120, 80) // warm highlight tint as in screenshot
            } else if (isGlass) {
                if (isLight) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255)
            } else {
                Color.argb(25, 128, 128, 128)
            }
            canvas.drawRoundRect(colRect, colW / 2f, colW / 2f, pillBgPaint)

            // Day label
            val dayLabel = day.dayName
            primaryTextPaint.apply {
                color = if (isToday) primaryColor else secondaryColor
                textSize = (10f * dp).coerceAtMost(colW * 0.35f)
                typeface = getTypeface(context, config, if (isToday) Typeface.BOLD else Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                applyLegibility(this, isGlass, isLight, dp)
            }
            val dayY = if (showLowTemp) top + totalH * 0.20f else top + totalH * 0.26f
            canvas.drawText(dayLabel, colRect.centerX(), dayY, primaryTextPaint)

            // Weather icon
            val iconCy = if (showLowTemp) top + totalH * 0.44f else top + totalH * 0.52f
            val iconSize = (colW * 0.32f).coerceIn(8f * dp, 14f * dp)
            NexusWeatherIconDraw.drawWeatherIcon(
                canvas, colRect.centerX(), iconCy, iconSize, day.weatherCode,
                if (isToday) primaryColor else secondaryColor, isGlass, isLight, dp
            )

            // High temp
            val highStr = "${LocaleDigitUtils.formatNumber(day.highTemp, locale)}°"
            primaryTextPaint.apply {
                color = primaryColor
                textSize = (11f * dp).coerceAtMost(colW * 0.38f)
                typeface = getTypeface(context, config, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val highY = if (showLowTemp) top + totalH * 0.69f else top + totalH * 0.82f
            canvas.drawText(highStr, colRect.centerX(), highY, primaryTextPaint)

            // Low temp - only drawn if vertical room permits to prevent bottom border clipping
            if (showLowTemp) {
                val lowStr = "${LocaleDigitUtils.formatNumber(day.lowTemp, locale)}°"
                secondaryTextPaint.apply {
                    color = secondaryColor
                    textSize = (10f * dp).coerceAtMost(colW * 0.34f)
                    typeface = getTypeface(context, config, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                }
                val lowY = top + totalH * 0.88f
                canvas.drawText(lowStr, colRect.centerX(), lowY, secondaryTextPaint)
            }
        }
    }

    fun calculateDuration(context: Context, sunriseStr: String?, sunsetStr: String?, locale: Locale): String {
        if (sunriseStr.isNullOrBlank() || sunsetStr.isNullOrBlank()) {
            return "-- " + context.getString(
                R.string.weather_daylight_format,
                LocaleDigitUtils.formatNumber(15, locale),
                LocaleDigitUtils.formatNumber(32, locale)
            ) + " --"
        }
        return try {
            val f24 = SimpleDateFormat("H:mm", Locale.US)
            val f12 = SimpleDateFormat("h:mm a", Locale.US)
            val s1 = try { f12.parse(sunriseStr) } catch (_: Exception) { f24.parse(sunriseStr) }
            val s2 = try { f12.parse(sunsetStr) } catch (_: Exception) { f24.parse(sunsetStr) }
            if (s1 != null && s2 != null) {
                var diffMs = s2.time - s1.time
                if (diffMs < 0) diffMs += 24 * 3600 * 1000L
                val hours = (diffMs / (3600 * 1000L)).toInt()
                val mins = ((diffMs % (3600 * 1000L)) / (60 * 1000L)).toInt()
                val dText = context.getString(
                    R.string.weather_daylight_format,
                    LocaleDigitUtils.formatNumber(hours, locale),
                    LocaleDigitUtils.formatNumber(mins, locale)
                )
                "-- $dText --"
            } else {
                "-- 15 h 32 m --"
            }
        } catch (_: Exception) {
            "-- 15 h 32 m --"
        }
    }

    fun drawHorizonSunIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, isRise: Boolean, color: Int, dp: Float) {
        iconStrokePaint.apply {
            this.color = color
            strokeWidth = 1.5f * dp
        }
        // Horizon line
        canvas.drawLine(cx - radius * 1.3f, cy, cx + radius * 1.3f, cy, iconStrokePaint)
        // Sun arc
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcRect, 180f, 180f, false, iconStrokePaint)
        // Arrow pointing up (sunrise) or down (sunset)
        val arrowY = if (isRise) cy + radius * 0.6f else cy + radius * 0.8f
        val arrowLen = radius * 0.5f
        if (isRise) {
            canvas.drawLine(cx, arrowY, cx, arrowY - arrowLen, iconStrokePaint)
            canvas.drawLine(cx - 2.5f * dp, arrowY - arrowLen + 2.5f * dp, cx, arrowY - arrowLen, iconStrokePaint)
            canvas.drawLine(cx + 2.5f * dp, arrowY - arrowLen + 2.5f * dp, cx, arrowY - arrowLen, iconStrokePaint)
        } else {
            canvas.drawLine(cx, arrowY - arrowLen, cx, arrowY, iconStrokePaint)
            canvas.drawLine(cx - 2.5f * dp, arrowY - 2.5f * dp, cx, arrowY, iconStrokePaint)
            canvas.drawLine(cx + 2.5f * dp, arrowY - 2.5f * dp, cx, arrowY, iconStrokePaint)
        }
    }

    fun drawCompositeWeatherIcon(
        canvas: Canvas, cx: Float, cy: Float, size: Float,
        weatherCode: Int, primaryColor: Int, accentColor: Int, dp: Float
    ) {
        sunFillPaint.color = Color.rgb(255, 160, 30)
        canvas.drawCircle(cx + size * 0.22f, cy - size * 0.18f, size * 0.32f, sunFillPaint)
        NexusWeatherIconDraw.drawWeatherIcon(canvas, cx, cy, size * 0.65f, weatherCode, primaryColor, false, false, dp)
    }

    private fun applyLegibility(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        }
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
