package com.nexus.launcher.ui.widgets.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Renders deeply recessed tactile pill grooves and theme-matched progress fills
 * with zero Paint allocations during render passes.
 */
class ProgressDrawBars {

    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val wellBounds = RectF()
    private val fillBounds = RectF()
    private val groovePath = Path()

    // Shader caches to prevent allocation per render pass
    private var cachedShadowShader: LinearGradient? = null
    private var cachedShadowKey = ""

    private fun getOrCreateShadowShader(top: Float, bottom: Float, color: Int): LinearGradient {
        val key = "$top,$bottom,$color"
        if (key != cachedShadowKey || cachedShadowShader == null) {
            cachedShadowShader = LinearGradient(0f, top, 0f, bottom, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cachedShadowKey = key
        }
        return cachedShadowShader!!
    }

    fun drawTrackSlot(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        track: ProgressPresets.CalculatedTrack,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        showSubtitle: Boolean = false,
        isHeroLayout: Boolean = false
    ) {
        val labelColor = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        val subColor = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary

        // Theme token matched fill color (no harsh neon accent)
        val fillColor = if (!track.colorHex.isNullOrBlank()) {
            try { Color.parseColor(track.colorHex) } catch (_: Exception) { labelColor }
        } else {
            labelColor
        }

        val labelSize = if (isHeroLayout) (13f * dp).coerceIn(11f * dp, 16f * dp) else (11.5f * dp).coerceIn(9f * dp, 13f * dp)
        val badgeSize = if (isHeroLayout) (13f * dp).coerceIn(11f * dp, 16f * dp) else (11.5f * dp).coerceIn(9f * dp, 13f * dp)

        // 1. Top Header: Label (Left) + Badge (Right)
        labelPaint.typeface = Typeface.DEFAULT_BOLD
        labelPaint.textSize = labelSize
        labelPaint.color = labelColor
        labelPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(track.label, x, y + labelSize, labelPaint)

        badgePaint.typeface = Typeface.DEFAULT_BOLD
        badgePaint.textSize = badgeSize
        badgePaint.color = labelColor
        badgePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(track.badgeText, x + w, y + badgeSize, badgePaint)

        // 2. Groove Dimensions
        val textSpacing = (6f * dp).coerceAtMost(h * 0.18f)
        val barTop = y + labelSize + textSpacing
        val barH = if (isHeroLayout) (16f * dp).coerceIn(12f * dp, 22f * dp) else (12f * dp).coerceIn(8f * dp, 16f * dp)
        val barR = barH / 2f
        wellBounds.set(x, barTop, x + w, barTop + barH)

        // 3. Render Debossed Sunken Groove
        groovePath.reset()
        groovePath.addRoundRect(wellBounds, barR, barR, Path.Direction.CW)

        if (isNeumorphic && palette != null) {
            // Base sunken debossed well color
            wellPaint.color = palette.debossedBg
            canvas.drawPath(groovePath, wellPaint)

            // Inner top shadow gradient for tactile depth
            val shadowColor = if (palette.isLight) Color.argb(100, 100, 90, 80) else Color.argb(130, 0, 0, 0)
            shadowPaint.shader = getOrCreateShadowShader(barTop, barTop + (barH * 0.75f), shadowColor)
            canvas.drawPath(groovePath, shadowPaint)

            // Inner bottom specular highlight edge
            val highlightColor = if (palette.isLight) Color.argb(160, 255, 255, 255) else Color.argb(40, 255, 255, 255)
            bevelPaint.strokeWidth = 1f * dp
            bevelPaint.color = highlightColor
            bevelPaint.shader = null
            canvas.drawPath(groovePath, bevelPaint)
        } else {
            wellPaint.color = ColorUtils.setAlphaComponent(tokens.surface, 0x99)
            canvas.drawPath(groovePath, wellPaint)
            bevelPaint.strokeWidth = 1f * dp
            bevelPaint.color = tokens.divider
            bevelPaint.shader = null
            canvas.drawPath(groovePath, bevelPaint)
        }

        // 4. Progress Fill Bar (inset pill inside the groove)
        val inset = (2f * dp).coerceAtMost(barH * 0.2f)
        val innerH = barH - inset * 2f
        val innerR = innerH / 2f
        val availableW = w - inset * 2f
        val fillW = (availableW * track.progressFraction).coerceIn(0f, availableW)

        if (fillW >= innerH * 0.6f) {
            fillBounds.set(x + inset, barTop + inset, x + inset + fillW, barTop + barH - inset)
            fillPaint.color = fillColor
            canvas.drawRoundRect(fillBounds, innerR, innerR, fillPaint)
        }

        // 5. Secondary Subtitle if space allows
        if (showSubtitle && (h - (barTop + barH - y)) >= (12f * dp)) {
            val subY = barTop + barH + (11f * dp)
            subPaint.typeface = Typeface.DEFAULT
            subPaint.textSize = (9f * dp).coerceIn(7.5f * dp, 11f * dp)
            subPaint.color = subColor
            subPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(track.secondaryText, x, subY, subPaint)
        }
    }
}
