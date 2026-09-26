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

/**
 * Fluid, grid-locked storage meters and anti-collision battery/thermal bars.
 * Strictly zero allocations in draw calls.
 */
class PerformanceDrawBars {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thermalTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barBounds = RectF()
    private val fillBounds = RectF()
    private val boltPath = Path()

    fun drawStorageBar(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        usedPercent: Int,
        freeBytes: Long,
        totalBytes: Long,
        accentColor: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null,
        showSubtext: Boolean = true
    ) {
        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        val totalGb = totalBytes / (1024f * 1024f * 1024f)

        // 1. Large Bold Free Space Headline
        val tfBold = resolveTypeface(context, config, Typeface.BOLD)
        headlinePaint.typeface = tfBold
        val headlineSize = if (showSubtext) {
            (height * 0.36f).coerceIn(13f * dp, 22f * dp)
        } else {
            (height * 0.44f).coerceIn(12f * dp, 18f * dp)
        }
        headlinePaint.textSize = headlineSize
        headlinePaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        headlinePaint.textAlign = Paint.Align.LEFT

        val headlineText = context.getString(com.nexus.launcher.R.string.performance_free, String.format("%.1f GB", freeGb))
        val headlineY = y + headlineSize
        canvas.drawText(headlineText, x, headlineY, headlinePaint)

        // 2. Subtitle: Storage total info (only if showSubtext is true)
        var contentBottom = headlineY
        if (showSubtext) {
            val tfNormal = resolveTypeface(context, config, Typeface.NORMAL)
            subTextPaint.typeface = tfNormal
            subTextPaint.textSize = (height * 0.22f).coerceIn(9f * dp, 12f * dp)
            subTextPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
            subTextPaint.textAlign = Paint.Align.LEFT

            val subText = context.getString(R.string.performance_storage) + " • " + context.getString(com.nexus.launcher.R.string.performance_total, String.format("%.0f GB", totalGb))
            val subY = headlineY + subTextPaint.textSize + 3f * dp
            canvas.drawText(subText, x, subY, subTextPaint)
            contentBottom = subY
        }

        // 3. Thick 6dp Progress Bar anchored at the bottom of the quadrant
        val barH = (6f * dp).coerceIn(5f * dp, 7.5f * dp)
        val barY = (y + height - barH).coerceAtLeast(contentBottom + 4f * dp)
        val corner = barH / 2f
        barBounds.set(x, barY, x + width, barY + barH)

        trackPaint.color = if (isNeumorphic && palette != null) {
            ColorUtils.setAlphaComponent(palette.textSecondary, 0x2E)
        } else {
            ColorUtils.setAlphaComponent(tokens.divider, 0x55)
        }
        canvas.drawRoundRect(barBounds, corner, corner, trackPaint)

        // Bar Fill
        val fillW = (width * (usedPercent.coerceIn(0, 100) / 100f)).coerceAtLeast(barH)
        fillBounds.set(x, barY, x + fillW, barY + barH)
        fillPaint.color = if (usedPercent >= 90) Color.parseColor("#FF5252") else accentColor
        canvas.drawRoundRect(fillBounds, corner, corner, fillPaint)
    }

    fun drawBatteryAndThermalStrip(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        batteryPercent: Int,
        isCharging: Boolean,
        tempC: Float?,
        thermalLevel: ThermalLevel,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null,
        showThermalLabel: Boolean = true
    ) {
        val midY = y + height * 0.5f
        val tfBold = resolveTypeface(context, config, Typeface.BOLD)

        // 1. Right-Aligned Thermal Text Label (only if showThermalLabel is true and not unknown)
        val hasThermal = showThermalLabel && thermalLevel != ThermalLevel.UNKNOWN
        var thermalText = ""
        var thermalColor = tokens.accent
        var thermalW = 0f

        if (hasThermal) {
            val pair = when (thermalLevel) {
                ThermalLevel.NORMAL -> context.getString(R.string.performance_thermal_normal) to tokens.accent
                ThermalLevel.WARM -> context.getString(R.string.performance_thermal_light) to Color.parseColor("#FFA726")
                ThermalLevel.HOT -> context.getString(R.string.performance_thermal_moderate) to Color.parseColor("#FF7043")
                ThermalLevel.SEVERE -> context.getString(R.string.performance_thermal_severe) to Color.parseColor("#FF5252")
                ThermalLevel.UNKNOWN -> "" to tokens.accent
            }
            thermalText = pair.first
            thermalColor = pair.second

            thermalTextPaint.typeface = tfBold
            thermalTextPaint.textSize = (height * 0.38f).coerceIn(10f * dp, 14f * dp)
            thermalTextPaint.color = thermalColor
            thermalW = thermalTextPaint.measureText(thermalText) + 8f * dp
            canvas.drawText(thermalText, x + width, midY + thermalTextPaint.textSize * 0.35f, thermalTextPaint)
        }

        // 2. Battery Left Info
        headlinePaint.typeface = tfBold
        headlinePaint.textSize = (height * 0.40f).coerceIn(11f * dp, 16f * dp)
        headlinePaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        headlinePaint.textAlign = Paint.Align.LEFT

        var curX = x
        if (isCharging) {
            val boltSize = (11f * dp).coerceIn(9f * dp, 14f * dp)
            drawChargingBolt(canvas, curX, midY - boltSize * 0.5f, boltSize, tokens.accent)
            curX += boltSize + 3f * dp
        }

        val availForBattery = (width - (curX - x) - thermalW).coerceAtLeast(0f)
        val fullBattText = "$batteryPercent%" + if (tempC != null) " • ${String.format("%.1f", tempC)}°C" else ""
        val fullBattW = headlinePaint.measureText(fullBattText)

        val battText = if (fullBattW <= availForBattery || tempC == null) {
            fullBattText
        } else {
            "$batteryPercent%"
        }

        canvas.drawText(battText, curX, midY + headlinePaint.textSize * 0.35f, headlinePaint)
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
