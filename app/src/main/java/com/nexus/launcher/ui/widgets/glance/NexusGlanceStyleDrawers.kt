package com.nexus.launcher.ui.widgets.glance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.weather.WeatherData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creative style drawer routines for Glance widget (Zen Pill, Editorial Stacked, and Station styles).
 * Zero Paint allocations during rendering.
 */
object NexusGlanceStyleDrawers {

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val weatherPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sparklePath = Path()
    private val pillRect = RectF()
    private val secondPillRect = RectF()

    fun drawZenPillStyle(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette, isGlass: Boolean, isNeumorphic: Boolean,
        primaryColor: Int, secondaryColor: Int, accentColor: Int,
        timeStr: String, dateStr: String, formattedTemp: String, conditionStr: String
    ) {
        val cx = w / 2f
        val clockTextSize = (h * 0.38f).coerceIn(28f * dp, 50f * dp)
        clockPaint.apply {
            color = primaryColor
            textSize = clockTextSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val clockY = h * 0.40f + clockTextSize * 0.32f
        canvas.drawText(timeStr, cx, clockY, clockPaint)

        val pillHeight = (h * 0.24f).coerceIn(22f * dp, 32f * dp)
        val pillRadius = pillHeight / 2f
        val textSize = (pillHeight * 0.50f).coerceIn(11f * dp, 13f * dp)

        datePaint.apply {
            color = secondaryColor
            this.textSize = textSize
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        weatherPaint.apply {
            color = primaryColor
            this.textSize = textSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        sparklePaint.color = accentColor

        val weatherText = "$formattedTemp · $conditionStr"
        val wTextWidth = weatherPaint.measureText(weatherText)
        val dTextWidth = datePaint.measureText(dateStr)

        val padX = 10f * dp
        val gap = 8f * dp
        val sparkleR = pillHeight * 0.22f
        val wPillW = padX + sparkleR * 2f + 4f * dp + wTextWidth + padX
        val dPillW = padX + dTextWidth + padX
        val totalW = dPillW + gap + wPillW

        val pillTop = clockY + 12f * dp
        val startX = cx - totalW / 2f

        // Date pill (left)
        pillRect.set(startX, pillTop, startX + dPillW, pillTop + pillHeight)
        drawPillBackground(canvas, pillRect, pillRadius, isGlass, isNeumorphic, palette, dp)
        canvas.drawText(dateStr, pillRect.centerX(), pillRect.centerY() + textSize * 0.35f, datePaint)

        // Weather pill (right)
        val wLeft = startX + dPillW + gap
        secondPillRect.set(wLeft, pillTop, wLeft + wPillW, pillTop + pillHeight)
        drawPillBackground(canvas, secondPillRect, pillRadius, isGlass, isNeumorphic, palette, dp)

        val sCx = secondPillRect.left + padX + sparkleR
        val sCy = secondPillRect.centerY()
        drawSparkleStar(canvas, sCx, sCy, sparkleR)
        canvas.drawText(weatherText, sCx + sparkleR + 4f * dp, secondPillRect.centerY() + textSize * 0.35f, weatherPaint)
    }

    fun drawEditorialStyle(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette, isGlass: Boolean, isNeumorphic: Boolean,
        primaryColor: Int, secondaryColor: Int, accentColor: Int,
        locale: Locale, is24h: Boolean, dateStr: String, formattedTemp: String, conditionStr: String
    ) {
        val pad = (w * 0.08f).coerceIn(16f * dp, 24f * dp)
        val hoursFmt = SimpleDateFormat(if (is24h) "HH" else "hh", locale)
        val minsFmt = SimpleDateFormat("mm", locale)
        val now = Date()
        val hours = LocaleDigitUtils.localizeDigits(hoursFmt.format(now), locale)
        val mins = LocaleDigitUtils.localizeDigits(minsFmt.format(now), locale)

        val stackTextSize = (h * 0.42f).coerceIn(32f * dp, 56f * dp)
        clockPaint.apply {
            color = primaryColor
            textSize = stackTextSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }

        val leftX = pad
        val topY = h * 0.44f
        canvas.drawText(hours, leftX, topY, clockPaint)
        canvas.drawText(mins, leftX, topY + stackTextSize * 0.92f, clockPaint)

        val rightX = leftX + clockPaint.measureText("00") + 16f * dp
        val upperDate = dateStr.uppercase(locale)

        datePaint.apply {
            color = accentColor
            textSize = 12f * dp
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.08f
        }
        canvas.drawText(upperDate, rightX, h * 0.36f, datePaint)

        weatherPaint.apply {
            color = primaryColor
            textSize = 20f * dp
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(formattedTemp, rightX, h * 0.58f, weatherPaint)

        secondaryPaint.apply {
            color = secondaryColor
            textSize = 12f * dp
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(conditionStr, rightX, h * 0.74f, secondaryPaint)
    }

    fun drawStationStyle(
        context: Context, canvas: Canvas, w: Int, h: Int, dp: Float,
        config: NexusWidgetConfig.InstanceConfig, tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette, isGlass: Boolean, isNeumorphic: Boolean,
        primaryColor: Int, secondaryColor: Int, accentColor: Int,
        timeStr: String, dateStr: String, formattedTemp: String, conditionStr: String,
        locale: Locale, weatherData: WeatherData?
    ) {
        val cx = w / 2f
        val clockTextSize = (h * 0.32f).coerceIn(24f * dp, 44f * dp)
        clockPaint.apply {
            color = primaryColor
            textSize = clockTextSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            applyTextLegibility(this, isGlass, palette.isLight, dp)
        }
        val clockY = h * 0.34f + clockTextSize * 0.30f
        canvas.drawText(timeStr, cx, clockY, clockPaint)

        val dateTextSize = 12f * dp
        datePaint.apply {
            color = secondaryColor
            textSize = dateTextSize
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val dateY = clockY + dateTextSize * 1.50f
        canvas.drawText(dateStr, cx, dateY, datePaint)

        val highTemp = weatherData?.highTemp ?: 26
        val lowTemp = weatherData?.lowTemp ?: 16
        val unit = weatherData?.unit ?: "°"
        val highStr = "${LocaleDigitUtils.formatNumber(highTemp, locale)}$unit"
        val lowStr = "${LocaleDigitUtils.formatNumber(lowTemp, locale)}$unit"
        val rangeStr = context.getString(com.nexus.launcher.R.string.glance_high_low, highStr, lowStr)

        val pillHeight = (h * 0.24f).coerceIn(24f * dp, 32f * dp)
        val pillRadius = pillHeight / 2f
        val pillTextSize = (pillHeight * 0.48f).coerceIn(11f * dp, 13f * dp)

        weatherPaint.apply {
            color = primaryColor
            textSize = pillTextSize
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        secondaryPaint.apply {
            color = secondaryColor
            textSize = pillTextSize
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        sparklePaint.color = accentColor

        val mainWeatherText = "$formattedTemp $conditionStr"
        val mainW = weatherPaint.measureText(mainWeatherText)
        val rangeW = secondaryPaint.measureText(rangeStr)
        val padX = 12f * dp
        val gap = 10f * dp
        val sparkleR = pillHeight * 0.22f
        val pillWidth = padX + sparkleR * 2f + 6f * dp + mainW + gap + rangeW + padX

        val pillLeft = cx - pillWidth / 2f
        val pillTop = dateY + 10f * dp
        pillRect.set(pillLeft, pillTop, pillLeft + pillWidth, pillTop + pillHeight)

        drawPillBackground(canvas, pillRect, pillRadius, isGlass, isNeumorphic, palette, dp)

        val sCx = pillRect.left + padX + sparkleR
        val sCy = pillRect.centerY()
        drawSparkleStar(canvas, sCx, sCy, sparkleR)

        val weatherX = sCx + sparkleR + 6f * dp
        val textY = pillRect.centerY() + pillTextSize * 0.35f
        canvas.drawText(mainWeatherText, weatherX, textY, weatherPaint)
        canvas.drawText(rangeStr, weatherX + mainW + gap, textY, secondaryPaint)
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

    private fun getTypeface(context: Context, config: NexusWidgetConfig.InstanceConfig, weight: Int): Typeface {
        val fontKey = config.fontFamily
        if (!fontKey.isNullOrEmpty() && fontKey != "default") {
            try {
                val family = com.nexus.launcher.typography.AppFontFamily.fromKey(fontKey)
                if (family.fontResId != null) {
                    val tf = androidx.core.content.res.ResourcesCompat.getFont(context, family.fontResId)
                    if (tf != null) return tf
                } else if (family.familyName != null) {
                    return Typeface.create(family.familyName, weight)
                }
            } catch (_: Exception) {}
        }
        return com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, weight)
    }
}
