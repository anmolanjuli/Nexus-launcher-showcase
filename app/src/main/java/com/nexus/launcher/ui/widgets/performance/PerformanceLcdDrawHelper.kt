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
 * Common draw helpers for rectangular LCD Cockpit panels.
 * Strictly zero GC allocations in draw passes.
 */
class PerformanceLcdDrawHelper {

    private val panelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val battStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val cardBounds = RectF()
    private val blockBounds = RectF()
    private val boltPath = Path()

    fun drawPodBackground(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        corner: Float,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float
    ) {
        cardBounds.set(x, y, x + w, y + h)
        if (isNeumorphic && palette != null) {
            NexusNeumorphicDraw.drawDebossedWell(canvas, cardBounds, corner, palette, dp)
        } else {
            panelBgPaint.style = Paint.Style.FILL
            val isLight = palette?.isLight == true
            panelBgPaint.color = if (isLight) Color.argb(20, 0, 0, 0) else ColorUtils.setAlphaComponent(0x000000, 0x33)
            canvas.drawRoundRect(cardBounds, corner, corner, panelBgPaint)

            borderPaint.strokeWidth = 1f * dp
            borderPaint.color = if (isLight) Color.argb(35, 0, 0, 0) else ColorUtils.setAlphaComponent(tokens.divider, 0x44)
            canvas.drawRoundRect(cardBounds, corner, corner, borderPaint)
        }
    }

    fun drawSegmentedBar(
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
        dp: Float
    ) {
        val count = 12
        val gap = 2f * dp
        val segW = (w - (count - 1) * gap) / count
        val active = ((percent.coerceIn(0, 100) / 100f) * count).toInt()
        val corner = 1.5f * dp
        val color = if (percent >= 90) Color.parseColor("#FF5252") else if (percent >= 75) Color.parseColor("#FFA726") else accent

        for (i in 0 until count) {
            val sx = x + i * (segW + gap)
            blockBounds.set(sx, y, sx + segW, y + h)
            if (i < active) {
                fillPaint.color = color
            } else {
                fillPaint.color = if (isNeumorphic && palette != null) {
                    ColorUtils.setAlphaComponent(palette.textSecondary, 0x24)
                } else {
                    ColorUtils.setAlphaComponent(tokens.divider, 0x33)
                }
            }
            canvas.drawRoundRect(blockBounds, corner, corner, fillPaint)
        }
    }

    fun drawTinyBatteryIcon(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        percent: Int,
        isCharging: Boolean,
        accent: Int,
        tokens: NexusColorTokens,
        dp: Float
    ) {
        val stroke = 1f * dp
        battStrokePaint.strokeWidth = stroke
        battStrokePaint.color = tokens.textSecondary

        val bodyW = w - 2.5f * dp
        val bodyCorner = 1.5f * dp
        cardBounds.set(x, y, x + bodyW, y + h)
        canvas.drawRoundRect(cardBounds, bodyCorner, bodyCorner, battStrokePaint)

        // Terminal nipple on right
        val tipW = 1.5f * dp
        val tipH = h * 0.44f
        val tipY = y + (h - tipH) / 2f
        cardBounds.set(x + bodyW, tipY, x + bodyW + tipW, tipY + tipH)
        fillPaint.color = tokens.textSecondary
        canvas.drawRect(cardBounds, fillPaint)

        if (isCharging) {
            drawChargingBolt(canvas, x + 2f * dp, y + 1f * dp, h - 2f * dp, accent)
        } else {
            val fillMargin = 2f * dp
            val maxFillW = bodyW - fillMargin * 2f
            val curFillW = (maxFillW * (percent.coerceIn(0, 100) / 100f)).coerceAtLeast(1f * dp)
            cardBounds.set(x + fillMargin, y + fillMargin, x + fillMargin + curFillW, y + h - fillMargin)
            fillPaint.color = if (percent <= 15) Color.parseColor("#FF5252") else if (percent <= 30) Color.parseColor("#FFA726") else accent
            canvas.drawRoundRect(cardBounds, 0.5f * dp, 0.5f * dp, fillPaint)
        }
    }

    fun resolveTypeface(context: Context, config: NexusWidgetConfig.InstanceConfig?, weight: Int): Typeface {
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
