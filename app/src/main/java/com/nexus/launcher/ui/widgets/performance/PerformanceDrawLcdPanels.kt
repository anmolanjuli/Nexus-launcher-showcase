package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Rectangular LCD Cockpit Displays for maximum space utilization.
 * Features debossed rounded-rect pods, digital readouts, and illuminated LED segmented bars.
 * Strictly zero GC allocations in draw passes.
 */
class PerformanceDrawLcdPanels {

    private val helper = PerformanceLcdDrawHelper()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cardBounds = RectF()
    private val blockBounds = RectF()

    /** Draws rectangular LCD Pod for CPU. */
    fun drawCpuPanel(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        percent: Int,
        accent: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig?
    ) {
        val corner = (10f * dp).coerceIn(6f * dp, 14f * dp)
        helper.drawPodBackground(canvas, x, y, w, h, corner, tokens, palette, isNeumorphic, dp)

        val padX = (8f * dp).coerceIn(5f * dp, 12f * dp)
        val padY = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val innerW = w - padX * 2f

        // 1. Header Row
        val tfHeader = helper.resolveTypeface(context, config, Typeface.NORMAL)
        headerPaint.typeface = tfHeader
        headerPaint.textSize = (h * 0.18f).coerceIn(8.5f * dp, 12f * dp)
        headerPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
        headerPaint.textAlign = Paint.Align.LEFT
        val headerY = y + padY + headerPaint.textSize
        canvas.drawText(context.getString(R.string.performance_cpu), x + padX, headerY, headerPaint)

        // 2. Hero Readout with proportional auto-shrink
        val tfBold = helper.resolveTypeface(context, config, Typeface.BOLD)
        headlinePaint.typeface = tfBold
        var heroSize = (h * 0.42f).coerceIn(14f * dp, 32f * dp)
        headlinePaint.textSize = heroSize
        val heroText = "$percent%"
        while (headlinePaint.measureText(heroText) > innerW && heroSize > 9f * dp) {
            heroSize -= 1f * dp
            headlinePaint.textSize = heroSize
        }
        headlinePaint.color = if (percent >= 90) Color.parseColor("#FF5252") else if (percent >= 75) Color.parseColor("#FFA726") else (if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary)
        headlinePaint.textAlign = Paint.Align.LEFT
        val heroY = headerY + headlinePaint.textSize + 2f * dp
        canvas.drawText(heroText, x + padX, heroY, headlinePaint)

        // 3. LED Segmented Horizon Bar
        val barH = (5f * dp).coerceIn(3.5f * dp, 7f * dp)
        val barY = y + h - padY - barH
        helper.drawSegmentedBar(canvas, x + padX, barY, innerW, barH, percent, accent, tokens, palette, isNeumorphic, dp)
    }

    /** Draws rectangular LCD Pod for RAM. */
    fun drawRamPanel(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        ramUsedBytes: Long,
        ramTotalBytes: Long,
        ramPercent: Int,
        accent: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig?
    ) {
        val corner = (10f * dp).coerceIn(6f * dp, 14f * dp)
        helper.drawPodBackground(canvas, x, y, w, h, corner, tokens, palette, isNeumorphic, dp)

        val padX = (8f * dp).coerceIn(5f * dp, 12f * dp)
        val padY = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val innerW = w - padX * 2f

        val ramGb = ramUsedBytes / (1024f * 1024f * 1024f)

        // 1. Header Row
        val tfHeader = helper.resolveTypeface(context, config, Typeface.NORMAL)
        headerPaint.typeface = tfHeader
        headerPaint.textSize = (h * 0.18f).coerceIn(8.5f * dp, 12f * dp)
        headerPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
        headerPaint.textAlign = Paint.Align.LEFT
        val headerY = y + padY + headerPaint.textSize
        canvas.drawText(context.getString(R.string.performance_ram), x + padX, headerY, headerPaint)

        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$ramPercent%", x + w - padX, headerY, headerPaint)

        // 2. Hero Readout with proportional auto-shrink
        val tfBold = helper.resolveTypeface(context, config, Typeface.BOLD)
        headlinePaint.typeface = tfBold
        var heroSize = (h * 0.40f).coerceIn(13f * dp, 30f * dp)
        headlinePaint.textSize = heroSize
        val ramStr = String.format("%.1f GB", ramGb)
        while (headlinePaint.measureText(ramStr) > innerW && heroSize > 9f * dp) {
            heroSize -= 1f * dp
            headlinePaint.textSize = heroSize
        }
        headlinePaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        headlinePaint.textAlign = Paint.Align.LEFT
        val heroY = headerY + headlinePaint.textSize + 2f * dp
        canvas.drawText(ramStr, x + padX, heroY, headlinePaint)

        // 3. LED Segmented Horizon Bar
        val barH = (5f * dp).coerceIn(3.5f * dp, 7f * dp)
        val barY = y + h - padY - barH
        helper.drawSegmentedBar(canvas, x + padX, barY, innerW, barH, ramPercent, accent, tokens, palette, isNeumorphic, dp)
    }

    /** Draws rectangular LCD Pod for Storage. */
    fun drawStoragePanel(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        usedPercent: Int,
        freeBytes: Long,
        totalBytes: Long,
        accent: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig?
    ) {
        val corner = (10f * dp).coerceIn(6f * dp, 14f * dp)
        helper.drawPodBackground(canvas, x, y, w, h, corner, tokens, palette, isNeumorphic, dp)

        val padX = (8f * dp).coerceIn(5f * dp, 12f * dp)
        val padY = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val innerW = w - padX * 2f

        val freeGb = freeBytes / (1024f * 1024f * 1024f)
        val totalGb = totalBytes / (1024f * 1024f * 1024f)

        // 1. Header Row
        val tfHeader = helper.resolveTypeface(context, config, Typeface.NORMAL)
        headerPaint.typeface = tfHeader
        headerPaint.textSize = (h * 0.18f).coerceIn(8.5f * dp, 12f * dp)
        headerPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
        headerPaint.textAlign = Paint.Align.LEFT
        val headerY = y + padY + headerPaint.textSize
        canvas.drawText(context.getString(R.string.performance_storage), x + padX, headerY, headerPaint)

        headerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("${String.format("%.0f", totalGb)} GB", x + w - padX, headerY, headerPaint)

        // 2. Hero Readout with proportional auto-shrink to prevent overflow
        val tfBold = helper.resolveTypeface(context, config, Typeface.BOLD)
        headlinePaint.typeface = tfBold
        var heroSize = (h * 0.38f).coerceIn(12f * dp, 28f * dp)
        headlinePaint.textSize = heroSize

        val fullFree = context.getString(com.nexus.launcher.R.string.performance_free, String.format("%.1f GB", freeGb))
        val shortFree = context.getString(com.nexus.launcher.R.string.performance_free, String.format("%.1fG", freeGb))
        var freeStr = fullFree

        while (headlinePaint.measureText(freeStr) > innerW && heroSize > 9f * dp) {
            if (freeStr == fullFree && headlinePaint.measureText(shortFree) <= innerW) {
                freeStr = shortFree
            } else {
                heroSize -= 1f * dp
                headlinePaint.textSize = heroSize
            }
        }

        headlinePaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        headlinePaint.textAlign = Paint.Align.LEFT
        val heroY = headerY + headlinePaint.textSize + 2f * dp
        canvas.drawText(freeStr, x + padX, heroY, headlinePaint)

        // 3. Thick Progress Bar
        val barH = (5f * dp).coerceIn(3.5f * dp, 7f * dp)
        val barY = y + h - padY - barH
        val barCorner = barH / 2f
        cardBounds.set(x + padX, barY, x + padX + innerW, barY + barH)

        trackPaint.color = if (isNeumorphic && palette != null) {
            ColorUtils.setAlphaComponent(palette.textSecondary, 0x2E)
        } else {
            ColorUtils.setAlphaComponent(tokens.divider, 0x48)
        }
        canvas.drawRoundRect(cardBounds, barCorner, barCorner, trackPaint)

        val fillW = (innerW * (usedPercent.coerceIn(0, 100) / 100f)).coerceAtLeast(barH)
        blockBounds.set(x + padX, barY, x + padX + fillW, barY + barH)
        fillPaint.color = if (usedPercent >= 90) Color.parseColor("#FF5252") else accent
        canvas.drawRoundRect(blockBounds, barCorner, barCorner, fillPaint)
    }

    /** Draws rectangular LCD Pod for Battery & Thermal with strict 8dp vertical spacing. */
    fun drawBatteryThermalPanel(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        batteryPercent: Int,
        isCharging: Boolean,
        tempC: Float?,
        thermalLevel: ThermalLevel,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig?
    ) {
        val corner = (10f * dp).coerceIn(6f * dp, 14f * dp)
        helper.drawPodBackground(canvas, x, y, w, h, corner, tokens, palette, isNeumorphic, dp)

        val padX = (8f * dp).coerceIn(5f * dp, 12f * dp)
        val padY = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val innerW = w - padX * 2f

        // Strict 6-8dp vertical padding between Temperature and Battery lines
        val verticalGap = (7f * dp).coerceIn(5f * dp, 9f * dp)
        val availableH = h - padY * 2f - verticalGap
        val halfH = (availableH / 2f).coerceAtLeast(10f * dp)

        val currentTemp = tempC ?: 34.0f
        val tfBold = helper.resolveTypeface(context, config, Typeface.BOLD)

        // ───────────────── TOP SECTION: TEMPERATURE ─────────────────
        val topY = y + padY
        val tfHeader = helper.resolveTypeface(context, config, Typeface.NORMAL)
        headerPaint.typeface = tfHeader
        headerPaint.textSize = (halfH * 0.44f).coerceIn(8.5f * dp, 12f * dp)
        headerPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
        headerPaint.textAlign = Paint.Align.LEFT
        val tempTitleY = topY + headerPaint.textSize
        val tempStr = String.format("%.1f°C", currentTemp)
        canvas.drawText(tempStr, x + padX, tempTitleY, headerPaint)

        val (thermalLabel, statusColor) = when {
            currentTemp < 35f -> context.getString(R.string.performance_thermal_normal) to Color.parseColor("#4CAF50")
            currentTemp < 45f -> context.getString(R.string.performance_thermal_light) to Color.parseColor("#FFA726")
            else -> context.getString(R.string.performance_thermal_moderate) to Color.parseColor("#FF5252")
        }
        headerPaint.textAlign = Paint.Align.RIGHT
        headerPaint.color = statusColor
        canvas.drawText(thermalLabel, x + w - padX, tempTitleY, headerPaint)

        // Thermal multi-zone bar
        val thermBarH = (3.5f * dp).coerceIn(2.5f * dp, 5f * dp)
        val thermBarY = tempTitleY + 3.5f * dp
        val thermCorner = thermBarH / 2f

        val z1 = innerW * (15f / 40f)
        val z2 = innerW * (10f / 40f)
        fillPaint.style = Paint.Style.FILL

        fillPaint.color = ColorUtils.setAlphaComponent(Color.parseColor("#4CAF50"), 0x66)
        blockBounds.set(x + padX, thermBarY, x + padX + z1, thermBarY + thermBarH)
        canvas.drawRoundRect(blockBounds, thermCorner, thermCorner, fillPaint)

        fillPaint.color = ColorUtils.setAlphaComponent(Color.parseColor("#FFA726"), 0x66)
        blockBounds.set(x + padX + z1, thermBarY, x + padX + z1 + z2, thermBarY + thermBarH)
        canvas.drawRect(blockBounds, fillPaint)

        fillPaint.color = ColorUtils.setAlphaComponent(Color.parseColor("#FF5252"), 0x66)
        blockBounds.set(x + padX + z1 + z2, thermBarY, x + padX + innerW, thermBarY + thermBarH)
        canvas.drawRoundRect(blockBounds, thermCorner, thermCorner, fillPaint)

        // ───────────────── BOTTOM SECTION: BATTERY ─────────────────
        // [Tiny Battery Icon] ── [Segmented Fuel Cells] ── [Percentage %]
        val battRowY = topY + halfH + verticalGap

        // 1. Right: Battery percentage text
        headlinePaint.typeface = tfBold
        headlinePaint.textSize = (halfH * 0.50f).coerceIn(9f * dp, 13f * dp)
        headlinePaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        headlinePaint.textAlign = Paint.Align.RIGHT
        val battText = "$batteryPercent%"
        val battTextW = headlinePaint.measureText(battText)
        val battTextY = battRowY + (halfH + headlinePaint.textSize * 0.7f) / 2f
        canvas.drawText(battText, x + w - padX, battTextY, headlinePaint)

        // 2. Left: Tiny Battery Icon
        val iconW = (13f * dp).coerceIn(10f * dp, 16f * dp)
        val iconH = (7.5f * dp).coerceIn(6f * dp, 10f * dp)
        val iconY = battRowY + (halfH - iconH) / 2f
        helper.drawTinyBatteryIcon(canvas, x + padX, iconY, iconW, iconH, batteryPercent, isCharging, tokens.accent, tokens, dp)

        // 3. Center: Segmented Fuel Cells between icon and percentage
        val fuelStartX = x + padX + iconW + 4f * dp
        val fuelEndX = x + w - padX - battTextW - 5f * dp
        val fuelW = fuelEndX - fuelStartX

        if (fuelW >= 18f * dp) {
            val cellCount = 6
            val cellGap = 1.5f * dp
            val cellW = (fuelW - (cellCount - 1) * cellGap) / cellCount
            val cellH = (5.5f * dp).coerceIn(4f * dp, 8f * dp)
            val cellY = battRowY + (halfH - cellH) / 2f
            val activeCells = ((batteryPercent.coerceIn(0, 100) / 100f) * cellCount).toInt().coerceIn(1, cellCount)
            val cellColor = if (batteryPercent <= 15) Color.parseColor("#FF5252") else if (batteryPercent <= 30) Color.parseColor("#FFA726") else tokens.accent

            for (i in 0 until cellCount) {
                val cx = fuelStartX + i * (cellW + cellGap)
                blockBounds.set(cx, cellY, cx + cellW, cellY + cellH)
                val cCorner = 1f * dp
                if (i < activeCells) {
                    fillPaint.color = cellColor
                } else {
                    fillPaint.color = if (isNeumorphic && palette != null) ColorUtils.setAlphaComponent(palette.textSecondary, 0x22) else ColorUtils.setAlphaComponent(tokens.divider, 0x33)
                }
                canvas.drawRoundRect(blockBounds, cCorner, cCorner, fillPaint)
            }
        }
    }
}
