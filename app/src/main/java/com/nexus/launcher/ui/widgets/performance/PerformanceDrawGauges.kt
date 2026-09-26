package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Renders circular arc gauges for CPU, RAM, and system loads with inset anchored wells.
 * Strictly zero allocations in draw calls.
 */
class PerformanceDrawGauges {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val wellFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wellRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val centerValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val gaugeBounds = RectF()

    fun drawCircularGauge(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        percent: Int,
        valueText: String,
        labelText: String,
        accentColor: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig? = null,
        strokeWidthDp: Float? = null
    ) {
        val strokePx = if (strokeWidthDp != null) strokeWidthDp * dp else (radius * 0.16f).coerceIn(3.5f * dp, 8f * dp)
        val arcRadius = radius - strokePx / 2f
        gaugeBounds.set(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)

        // 1. Inset well with 1dp rim shadow (anchored to material)
        val wellRadius = radius + 2f * dp
        if (isNeumorphic && palette != null) {
            NexusNeumorphicDraw.drawDebossedWell(canvas, gaugeBounds, arcRadius, palette, dp)
        } else {
            wellFillPaint.color = ColorUtils.setAlphaComponent(0x000000, 0x38)
            canvas.drawCircle(cx, cy, wellRadius, wellFillPaint)

            wellRimPaint.strokeWidth = 1f * dp
            wellRimPaint.color = ColorUtils.setAlphaComponent(tokens.divider, 0x48)
            canvas.drawCircle(cx, cy, wellRadius, wellRimPaint)
        }

        // 2. Background Track
        trackPaint.strokeWidth = strokePx
        trackPaint.color = if (isNeumorphic && palette != null) {
            ColorUtils.setAlphaComponent(palette.textSecondary, 0x33)
        } else {
            ColorUtils.setAlphaComponent(tokens.divider, 0x66)
        }
        canvas.drawArc(gaugeBounds, 135f, 270f, false, trackPaint)

        // 3. Dynamic Value Arc
        val sweepAngle = (percent.coerceIn(0, 100) / 100f) * 270f
        if (sweepAngle > 0.5f) {
            val gaugeColor = when {
                percent >= 90 -> Color.parseColor("#FF5252") // Alert Red
                percent >= 75 -> Color.parseColor("#FFA726") // Warning Amber
                else -> accentColor
            }
            arcPaint.strokeWidth = strokePx
            arcPaint.color = gaugeColor
            canvas.drawArc(gaugeBounds, 135f, sweepAngle, false, arcPaint)
        }

        // 4. Center Primary Value Text
        val tf = resolveTypeface(context, config, Typeface.BOLD)
        centerValPaint.typeface = tf
        centerValPaint.textSize = (radius * 0.46f).coerceIn(11f * dp, 32f * dp)
        centerValPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary

        val valY = cy - (radius * 0.04f) + (centerValPaint.textSize * 0.35f)
        canvas.drawText(valueText, cx, valY, centerValPaint)

        // 5. Secondary Label (CPU, RAM, etc.)
        val labelTf = resolveTypeface(context, config, Typeface.NORMAL)
        labelPaint.typeface = labelTf
        labelPaint.textSize = (radius * 0.26f).coerceIn(8f * dp, 14f * dp)
        labelPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary

        val labelY = cy + (radius * 0.44f)
        canvas.drawText(labelText, cx, labelY, labelPaint)
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
