package com.nexus.launcher.ui.widgets.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.nexus.launcher.typography.AppFontFamily
import com.nexus.launcher.typography.CanvasTypographyHelper
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Shared pre-allocated drawing utilities for Nexus Battery widgets.
 * Zero per-frame allocations for paths, paints, and typeface lookups.
 */
object NexusBatteryDrawUtils {

    private val boltPath = Path()
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shimmerRect = RectF()

    fun drawLightningBolt(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        boltPath.reset()
        val halfW = size * 0.35f
        val halfH = size * 0.55f

        boltPath.moveTo(cx + halfW * 0.2f, cy - halfH)
        boltPath.lineTo(cx - halfW, cy + halfH * 0.05f)
        boltPath.lineTo(cx - halfW * 0.1f, cy + halfH * 0.05f)
        boltPath.lineTo(cx - halfW * 0.3f, cy + halfH)
        boltPath.lineTo(cx + halfW, cy - halfH * 0.15f)
        boltPath.lineTo(cx + halfW * 0.1f, cy - halfH * 0.15f)
        boltPath.close()

        boltPaint.color = color
        canvas.drawPath(boltPath, boltPaint)
    }

    fun drawVerticalShimmer(canvas: Canvas, rect: RectF, trackW: Float, stateColor: Int, phase: Float) {
        val effectivePhase = if (phase > 0f) phase else ((System.currentTimeMillis() % 1400L) / 1400f)
        val shimmerH = (rect.height() * 0.35f).coerceIn(10f, 32f)
        val shimmerY = rect.bottom - (rect.height() * effectivePhase)

        shimmerRect.set(rect.left, shimmerY - shimmerH, rect.right, shimmerY)
        shimmerPaint.color = stateColor
        shimmerPaint.alpha = 200

        canvas.save()
        canvas.clipRect(rect)
        canvas.drawRoundRect(shimmerRect, trackW / 2f, trackW / 2f, shimmerPaint)
        canvas.restore()
    }

    fun applyLegibility(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadowColor = if (isLight) Color.argb(40, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        }
    }

    fun getTypeface(context: Context, config: NexusWidgetConfig.InstanceConfig, style: Int): Typeface {
        val fontKey = config.fontFamily
        if (!fontKey.isNullOrEmpty() && fontKey != "default") {
            try {
                val family = AppFontFamily.fromKey(fontKey)
                if (family.fontResId != null) {
                    val tf = ResourcesCompat.getFont(context, family.fontResId)
                    if (tf != null) return tf
                } else if (family.familyName != null) {
                    return Typeface.create(family.familyName, style)
                }
            } catch (_: Exception) {}
        }
        return CanvasTypographyHelper.getTypeface(context, style)
    }
}
