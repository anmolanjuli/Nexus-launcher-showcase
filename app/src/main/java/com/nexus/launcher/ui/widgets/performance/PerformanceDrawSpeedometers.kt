package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import kotlin.math.cos
import kotlin.math.sin

/**
 * Automotive Speedometer gauges for Storage, Temperature (multi-zone), and Battery fuel cells.
 * Strictly zero allocations in draw passes.
 */
class PerformanceDrawSpeedometers {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val needleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arcBounds = RectF()
    private val blockBounds = RectF()
    private val needlePath = Path()
    private val boltPath = Path()

    /** Renders radial Storage Speedometer with discrete radial tick bars and needle pointer. */
    fun drawStorageSpeedometer(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        usedPercent: Int,
        freeBytes: Long,
        totalBytes: Long,
        accentColor: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null
    ) {
        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        val totalGb = totalBytes / (1024f * 1024f * 1024f)

        // 1. Inset well background
        val wellRadius = radius + 2f * dp
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        if (isNeumorphic && palette != null) {
            NexusNeumorphicDraw.drawDebossedWell(canvas, arcBounds, radius, palette, dp)
        } else {
            trackPaint.style = Paint.Style.FILL
            trackPaint.color = ColorUtils.setAlphaComponent(0x000000, 0x38)
            canvas.drawCircle(cx, cy, wellRadius, trackPaint)

            trackPaint.style = Paint.Style.STROKE
            trackPaint.strokeWidth = 1f * dp
            trackPaint.color = ColorUtils.setAlphaComponent(tokens.divider, 0x48)
            canvas.drawCircle(cx, cy, wellRadius, trackPaint)
        }

        // 2. Discrete Radial Tick Bars (16 ticks spanning 240 degrees from 150° to 390°)
        val totalTicks = 18
        val startAngle = 150f
        val sweepAngle = 240f
        val angleStep = sweepAngle / (totalTicks - 1)
        val activeTickCount = (totalTicks * (usedPercent.coerceIn(0, 100) / 100f)).toInt()

        val outerR = radius - 4f * dp
        val innerR = radius - 11f * dp
        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeCap = Paint.Cap.ROUND
        trackPaint.strokeWidth = (2.2f * dp).coerceIn(1.8f * dp, 3.2f * dp)

        for (i in 0 until totalTicks) {
            val angleDeg = startAngle + i * angleStep
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val cosA = cos(angleRad).toFloat()
            val sinA = sin(angleRad).toFloat()

            val x1 = cx + innerR * cosA
            val y1 = cy + innerR * sinA
            val x2 = cx + outerR * cosA
            val y2 = cy + outerR * sinA

            val isActive = i <= activeTickCount
            if (isActive) {
                trackPaint.color = if (usedPercent >= 90) Color.parseColor("#FF5252") else accentColor
            } else {
                trackPaint.color = if (isNeumorphic && palette != null) {
                    ColorUtils.setAlphaComponent(palette.textSecondary, 0x33)
                } else {
                    ColorUtils.setAlphaComponent(tokens.divider, 0x44)
                }
            }
            canvas.drawLine(x1, y1, x2, y2, trackPaint)
        }

        // 3. Speedometer Needle
        val needleAngleDeg = startAngle + (usedPercent.coerceIn(0, 100) / 100f) * sweepAngle
        val needleRad = Math.toRadians(needleAngleDeg.toDouble())
        val needleLength = radius - 7f * dp
        val perpRad = needleRad + Math.PI / 2.0
        val baseWidth = 3f * dp

        val tipX = cx + needleLength * cos(needleRad).toFloat()
        val tipY = cy + needleLength * sin(needleRad).toFloat()
        val baseLeftX = cx + baseWidth * cos(perpRad).toFloat()
        val baseLeftY = cy + baseWidth * sin(perpRad).toFloat()
        val baseRightX = cx - baseWidth * cos(perpRad).toFloat()
        val baseRightY = cy - baseWidth * sin(perpRad).toFloat()

        needlePath.reset()
        needlePath.moveTo(tipX, tipY)
        needlePath.lineTo(baseLeftX, baseLeftY)
        needlePath.lineTo(baseRightX, baseRightY)
        needlePath.close()

        val needleColor = if (usedPercent >= 90) Color.parseColor("#FF5252") else accentColor
        needlePaint.color = needleColor
        canvas.drawPath(needlePath, needlePaint)

        // Center needle hub
        val hubRadius = 4.5f * dp
        needlePaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        canvas.drawCircle(cx, cy, hubRadius, needlePaint)

        // 4. Center / Bottom Storage Text
        val tfBold = resolveTypeface(context, config, Typeface.BOLD)
        textBoldPaint.typeface = tfBold
        textBoldPaint.textSize = (radius * 0.32f).coerceIn(10f * dp, 16f * dp)
        textBoldPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        textBoldPaint.textAlign = Paint.Align.CENTER

        val freeStr = String.format("%.1f", freeGb)
        val freeHeadline = context.getString(R.string.performance_free, "${freeStr}G")
        val headlineY = cy + (radius * 0.44f)
        canvas.drawText(freeHeadline, cx, headlineY, textBoldPaint)

        val tfNormal = resolveTypeface(context, config, Typeface.NORMAL)
        textNormalPaint.typeface = tfNormal
        textNormalPaint.textSize = (radius * 0.22f).coerceIn(8f * dp, 12f * dp)
        textNormalPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
        textNormalPaint.textAlign = Paint.Align.CENTER

        val totalStr = String.format("%.0f GB", totalGb)
        val totalLabel = context.getString(R.string.performance_total, totalStr)
        val subY = headlineY + textNormalPaint.textSize * 1.25f
        canvas.drawText(totalLabel, cx, subY, textNormalPaint)
    }

    /** Multi-zone color temperature bar with needle pointer and exact degrees. */
    fun drawThermalSpeedometerBar(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        tempC: Float?,
        thermalLevel: ThermalLevel,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null
    ) {
        val currentTemp = tempC ?: 34.0f
        val tfBold = resolveTypeface(context, config, Typeface.BOLD)

        // 1. Temperature Value Text on Left
        textBoldPaint.typeface = tfBold
        textBoldPaint.textSize = (height * 0.42f).coerceIn(10f * dp, 15f * dp)
        textBoldPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        textBoldPaint.textAlign = Paint.Align.LEFT

        val tempText = if (tempC != null) String.format("%.1f°C", tempC) else "34.0°C"
        val textY = y + textBoldPaint.textSize
        canvas.drawText(tempText, x, textY, textBoldPaint)

        // 2. Status Label on Right
        val (thermalLabel, statusColor) = when {
            currentTemp < 35f -> context.getString(R.string.performance_thermal_normal) to Color.parseColor("#4CAF50")
            currentTemp < 45f -> context.getString(R.string.performance_thermal_light) to Color.parseColor("#FFA726")
            else -> context.getString(R.string.performance_thermal_moderate) to Color.parseColor("#FF5252")
        }

        textBoldPaint.textAlign = Paint.Align.RIGHT
        textBoldPaint.color = statusColor
        canvas.drawText(thermalLabel, x + width, textY, textBoldPaint)

        // 3. Multi-Zone Thermal Bar (20°C .. 60°C range)
        val barY = textY + 6f * dp
        val barH = (5.5f * dp).coerceIn(4f * dp, 7f * dp)
        val corner = barH / 2f
        val minTemp = 20f
        val maxTemp = 60f
        val normalizedTemp = ((currentTemp - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)

        // Background track with 3 colored zones
        val zone1W = width * ((35f - 20f) / 40f) // 20-35 Green zone
        val zone2W = width * ((45f - 35f) / 40f) // 35-45 Yellow zone
        val zone3W = width - zone1W - zone2W     // 45-60 Red zone

        fillPaint.style = Paint.Style.FILL

        // Green Zone
        fillPaint.color = ColorUtils.setAlphaComponent(Color.parseColor("#4CAF50"), 0x55)
        blockBounds.set(x, barY, x + zone1W, barY + barH)
        canvas.drawRoundRect(blockBounds, corner, corner, fillPaint)

        // Yellow Zone
        fillPaint.color = ColorUtils.setAlphaComponent(Color.parseColor("#FFA726"), 0x55)
        blockBounds.set(x + zone1W, barY, x + zone1W + zone2W, barY + barH)
        canvas.drawRect(blockBounds, fillPaint)

        // Red Zone
        fillPaint.color = ColorUtils.setAlphaComponent(Color.parseColor("#FF5252"), 0x55)
        blockBounds.set(x + zone1W + zone2W, barY, x + width, barY + barH)
        canvas.drawRoundRect(blockBounds, corner, corner, fillPaint)

        // 4. Pointer Needle / Marker
        val needleX = (x + width * normalizedTemp).coerceIn(x + 3f * dp, x + width - 3f * dp)
        val needleW = 4f * dp
        val needleH = 4.5f * dp
        needlePath.reset()
        needlePath.moveTo(needleX, barY + barH + 1f * dp)
        needlePath.lineTo(needleX - needleW, barY + barH + 1f * dp + needleH)
        needlePath.lineTo(needleX + needleW, barY + barH + 1f * dp + needleH)
        needlePath.close()

        needlePaint.color = statusColor
        canvas.drawPath(needlePath, needlePaint)
    }

    /** Automotive graphical segmented fuel-cell battery gauge. */
    fun drawFuelCellBatteryMeter(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        batteryPercent: Int,
        isCharging: Boolean,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null
    ) {
        val midY = y + height * 0.5f
        val tfBold = resolveTypeface(context, config, Typeface.BOLD)

        // 1. Left Battery Text + Charging Bolt
        textBoldPaint.typeface = tfBold
        textBoldPaint.textSize = (height * 0.44f).coerceIn(10f * dp, 15f * dp)
        textBoldPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        textBoldPaint.textAlign = Paint.Align.LEFT

        var curX = x
        if (isCharging) {
            val boltSize = (11f * dp).coerceIn(9f * dp, 14f * dp)
            drawChargingBolt(canvas, curX, midY - boltSize * 0.5f, boltSize, tokens.accent)
            curX += boltSize + 3f * dp
        }

        val battText = "$batteryPercent%"
        val textY = midY + textBoldPaint.textSize * 0.35f
        canvas.drawText(battText, curX, textY, textBoldPaint)
        curX += textBoldPaint.measureText(battText) + 8f * dp

        // 2. Segmented Fuel Cells (7 discrete energy bars)
        val meterW = (width - (curX - x)).coerceAtLeast(30f * dp)
        val cellCount = 7
        val cellGap = 2.5f * dp
        val cellW = (meterW - (cellCount - 1) * cellGap) / cellCount
        val cellH = (8f * dp).coerceIn(6f * dp, 12f * dp)
        val cellY = midY - cellH * 0.5f

        val activeCells = ((batteryPercent.coerceIn(0, 100) / 100f) * cellCount).toInt().coerceIn(1, cellCount)
        val activeColor = when {
            batteryPercent <= 15 -> Color.parseColor("#FF5252") // Red
            batteryPercent <= 30 -> Color.parseColor("#FFA726") // Amber
            else -> tokens.accent                               // Accent / Teal
        }

        for (i in 0 until cellCount) {
            val cx = curX + i * (cellW + cellGap)
            blockBounds.set(cx, cellY, cx + cellW, cellY + cellH)
            val corner = 1.5f * dp

            if (i < activeCells) {
                fillPaint.color = activeColor
                canvas.drawRoundRect(blockBounds, corner, corner, fillPaint)
            } else {
                fillPaint.color = if (isNeumorphic && palette != null) {
                    ColorUtils.setAlphaComponent(palette.textSecondary, 0x22)
                } else {
                    ColorUtils.setAlphaComponent(tokens.divider, 0x33)
                }
                canvas.drawRoundRect(blockBounds, corner, corner, fillPaint)
            }
        }
    }

    private fun resolveTypeface(context: Context, config: NexusWidgetConfig.InstanceConfig?, weight: Int): Typeface {
        val fontKey = config?.fontFamily
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

    private fun drawChargingBolt(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        iconPaint.color = color
        boltPath.reset()
        boltPath.moveTo(x + size * 0.55f, y)
        boltPath.lineTo(x + size * 0.15f, y + size * 0.58f)
        boltPath.lineTo(x + size * 0.45f, y + size * 0.58f)
        boltPath.lineTo(x + size * 0.35f, y + size)
        boltPath.lineTo(x + size * 0.85f, y + size * 0.42f)
        boltPath.lineTo(x + size * 0.55f, y + size * 0.42f)
        boltPath.close()
        canvas.drawPath(boltPath, iconPaint)
    }
}
