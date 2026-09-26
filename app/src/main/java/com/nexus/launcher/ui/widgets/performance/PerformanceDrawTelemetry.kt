package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Renders stacked real-time telemetry line graphs (Nothing-inspired system stats).
 * Strictly zero allocations in draw calls.
 */
class PerformanceDrawTelemetry {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rowBounds = RectF()
    private val chartBounds = RectF()
    private val linePath = Path()
    private val fillPath = Path()

    fun drawTelemetry(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        snap: PerformanceSnapshot,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null
    ) {
        val cpuHist = PerformanceHistoryBuffer.getCpuHistory(context)
        val ramHist = PerformanceHistoryBuffer.getRamHistory(context)
        val batHist = PerformanceHistoryBuffer.getBatHistory(context)
        val netHist = PerformanceHistoryBuffer.getNetHistory(context)

        val rowCount = when {
            height >= (160 * dp) -> 4
            height >= (85 * dp) -> 2
            else -> 1
        }

        val gap = (6f * dp).coerceAtMost(height * 0.04f)
        val rowH = (height - gap * (rowCount - 1)) / rowCount

        val tfBold = resolveTypeface(context, config, Typeface.BOLD)
        val tfNormal = resolveTypeface(context, config, Typeface.NORMAL)

        // Row 1: CPU
        var curY = y
        drawGraphRow(
            canvas, x, curY, width, rowH,
            title = "CPU",
            valueText = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(snap.cpuUsagePercent, context),
            history = cpuHist,
            lineColor = Color.parseColor("#FF5252"),
            tokens = tokens, palette = palette, isNeumorphic = isNeumorphic,
            dp = dp, tfBold = tfBold, tfNormal = tfNormal
        )

        if (rowCount >= 2) {
            curY += rowH + gap
            drawGraphRow(
                canvas, x, curY, width, rowH,
                title = "RAM",
                valueText = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(snap.ramPercent, context),
                history = ramHist,
                lineColor = Color.parseColor("#4CAF50"),
                tokens = tokens, palette = palette, isNeumorphic = isNeumorphic,
                dp = dp, tfBold = tfBold, tfNormal = tfNormal
            )
        }

        if (rowCount >= 4) {
            curY += rowH + gap
            drawGraphRow(
                canvas, x, curY, width, rowH,
                title = "BAT",
                valueText = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(snap.batteryPercent, context),
                history = batHist,
                lineColor = Color.parseColor("#0099FF"),
                tokens = tokens, palette = palette, isNeumorphic = isNeumorphic,
                dp = dp, tfBold = tfBold, tfNormal = tfNormal
            )

            curY += rowH + gap
            val speedText = formatSpeed(snap.networkSpeedBytesPerSec)
            drawGraphRow(
                canvas, x, curY, width, rowH,
                title = "NET",
                valueText = speedText,
                history = netHist,
                lineColor = Color.parseColor("#00E5FF"),
                tokens = tokens, palette = palette, isNeumorphic = isNeumorphic,
                dp = dp, tfBold = tfBold, tfNormal = tfNormal
            )
        }
    }

    private fun drawGraphRow(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        title: String,
        valueText: String,
        history: FloatArray,
        lineColor: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        tfBold: Typeface,
        tfNormal: Typeface
    ) {
        val headerH = (14f * dp).coerceAtMost(h * 0.35f)
        val chartH = (h - headerH - 3f * dp).coerceAtLeast(12f * dp)

        // 1. Header Labels with safe squircle margins & high-contrast clarity
        val labelMarginX = (6f * dp).coerceIn(4f * dp, 10f * dp)
        labelPaint.typeface = tfBold
        labelPaint.textSize = (headerH * 0.90f).coerceIn(9f * dp, 14f * dp)
        labelPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(title, x + labelMarginX, y + labelPaint.textSize, labelPaint)

        valuePaint.typeface = tfBold
        valuePaint.textSize = (headerH * 0.90f).coerceIn(9f * dp, 14f * dp)
        valuePaint.color = lineColor
        valuePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(valueText, x + w - labelMarginX, y + valuePaint.textSize, valuePaint)

        // 2. Chart Background Well & Bounds
        val chartY = y + headerH + 3f * dp
        chartBounds.set(x, chartY, x + w, chartY + chartH)
        val corner = 4f * dp

        cardBgPaint.color = if (isNeumorphic && palette != null) {
            ColorUtils.setAlphaComponent(palette.textSecondary, 0x14)
        } else {
            ColorUtils.setAlphaComponent(tokens.surface, 0x55)
        }
        canvas.drawRoundRect(chartBounds, corner, corner, cardBgPaint)

        // 3. Subtle Horizontal Grid Lines
        gridPaint.strokeWidth = 1f * dp
        gridPaint.color = ColorUtils.setAlphaComponent(tokens.divider, 0x33)
        for (i in 1..3) {
            val gridY = chartY + (chartH * i / 4f)
            canvas.drawLine(x, gridY, x + w, gridY, gridPaint)
        }

        // 4. Time-series Curve and Area Fill
        if (history.isEmpty()) return
        val count = history.size
        val stepX = w / (count - 1).coerceAtLeast(1)

        linePath.reset()
        fillPath.reset()

        val bottomY = chartY + chartH
        fillPath.moveTo(x, bottomY)

        for (i in 0 until count) {
            val px = x + i * stepX
            val norm = (history[i].coerceIn(0f, 100f) / 100f)
            // Clamp top/bottom margins inside chart
            val py = bottomY - (norm * (chartH - 4f * dp)) - 2f * dp

            if (i == 0) {
                linePath.moveTo(px, py)
                fillPath.lineTo(px, py)
            } else {
                linePath.lineTo(px, py)
                fillPath.lineTo(px, py)
            }
        }

        fillPath.lineTo(x + w, bottomY)
        fillPath.close()

        fillPaint.color = ColorUtils.setAlphaComponent(lineColor, 0x24)
        canvas.drawPath(fillPath, fillPaint)

        linePaint.strokeWidth = (2f * dp).coerceIn(1.5f * dp, 3f * dp)
        linePaint.color = lineColor
        canvas.drawPath(linePath, linePaint)
    }

    private fun formatSpeed(speedBytesPerSec: Float): String {
        return when {
            speedBytesPerSec < 1024f -> String.format("%.0f B/s", speedBytesPerSec)
            speedBytesPerSec < 1024f * 1024f -> String.format("%.0f KB/s", speedBytesPerSec / 1024f)
            else -> String.format("%.1f MB/s", speedBytesPerSec / (1024f * 1024f))
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
}
