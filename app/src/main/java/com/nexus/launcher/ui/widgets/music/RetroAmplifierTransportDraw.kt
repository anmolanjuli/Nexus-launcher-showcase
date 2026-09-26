package com.nexus.launcher.ui.widgets.music

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles expanded transport toggles, volume knob, and chassis screws for the
 * 1970s Hi-Fi Amplifier retro music widget.
 */
object RetroAmplifierTransportDraw {

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knobRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scalePaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rectCache = RectF()
    private val pathCache = Path()

    fun drawAmplifierTransportRow(
        canvas: Canvas, cxCenter: Float, cy: Float, dp: Float, isPlaying: Boolean,
        colors: RetroMusicPalette.AmplifierColors, surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val cxCenterR = cxCenter.toInt().toFloat()
        val cyR = cy.toInt().toFloat()
        val offset = (68f * dp).toInt().toFloat()
        val btnW = (54f * dp).toInt().toFloat()
        val playBtnW = (64f * dp).toInt().toFloat()
        val btnH = (26f * dp).toInt().toFloat()

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && palette != null) palette.textPrimary else colors.amberText

        drawAmplifierButton(canvas, cxCenterR - offset - btnW / 2f, cyR - btnH / 2f, btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxCenterR - offset, cyR, 7f * dp, false)

        drawAmplifierButton(canvas, cxCenterR - playBtnW / 2f, cyR - btnH / 2f, playBtnW, btnH, dp, colors, surfaceMode, palette)
        if (isPlaying) drawPauseIcon(canvas, cxCenterR, cyR, 7.5f * dp) else drawPlayIcon(canvas, cxCenterR, cyR, 7.5f * dp)

        drawAmplifierButton(canvas, cxCenterR + offset - btnW / 2f, cyR - btnH / 2f, btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxCenterR + offset, cyR, 7f * dp, true)
    }

    fun drawMasterVolumeDial(
        canvas: Canvas, cx: Float, cy: Float, radius: Float,
        dp: Float, isPlaying: Boolean, colors: RetroMusicPalette.AmplifierColors
    ) {
        knobRimPaint.color = colors.chromeRing
        knobRimPaint.strokeWidth = 2f * dp
        canvas.drawCircle(cx, cy, radius, knobRimPaint)

        knobPaint.color = colors.chromeFace
        canvas.drawCircle(cx, cy, radius - 1.5f * dp, knobPaint)

        knobRimPaint.color = colors.amberDim
        knobRimPaint.strokeWidth = 1f * dp
        canvas.drawCircle(cx, cy, radius * 0.65f, knobRimPaint)

        val angleDeg = if (isPlaying) 45.0 else -30.0
        val rad = Math.toRadians(angleDeg)
        tickPaint.color = colors.amberText
        tickPaint.strokeWidth = 2f * dp
        canvas.drawLine(
            (cx + cos(rad) * radius * 0.2f).toFloat(), (cy + sin(rad) * radius * 0.2f).toFloat(),
            (cx + cos(rad) * (radius - 3f * dp)).toFloat(), (cy + sin(rad) * (radius - 3f * dp)).toFloat(),
            tickPaint
        )

        scalePaint.color = colors.amberDim
        scalePaint.textSize = (7f * dp).toInt().toFloat()
        canvas.drawText("VOL", cx.toInt().toFloat(), (cy + radius + 8f * dp).toInt().toFloat(), scalePaint)
    }

    fun drawScrew(canvas: Canvas, cx: Float, cy: Float, dp: Float, colors: RetroMusicPalette.AmplifierColors) {
        val r = 4f * dp
        screwPaint.color = colors.screwHead
        canvas.drawCircle(cx, cy, r, screwPaint)
        screwSlotPaint.color = colors.faceplateBottom
        screwSlotPaint.strokeWidth = 1f * dp
        canvas.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, screwSlotPaint)
    }

    private fun drawAmplifierButton(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        dp: Float, colors: RetroMusicPalette.AmplifierColors,
        surfaceMode: RetroMusicConfig.SurfaceMode, palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        rectCache.set(x, y, x + w, y + h)
        val cornerRadius = 4f * dp
        when {
            surfaceMode == RetroMusicConfig.SurfaceMode.NEUMORPHIC && palette != null -> {
                NexusNeumorphicDraw.drawRaisedRoundRect(canvas, rectCache, cornerRadius, palette, dp)
            }
            surfaceMode == RetroMusicConfig.SurfaceMode.FROSTED && palette != null -> {
                knobPaint.color = if (palette.isLight) android.graphics.Color.argb(35, 0, 0, 0) else android.graphics.Color.argb(45, 255, 255, 255)
                knobPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, knobPaint)
                knobRimPaint.color = if (palette.isLight) android.graphics.Color.argb(40, 0, 0, 0) else android.graphics.Color.argb(60, 255, 255, 255)
                knobRimPaint.strokeWidth = 1f * dp
                knobRimPaint.style = Paint.Style.STROKE
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, knobRimPaint)
            }
            else -> {
                knobPaint.color = colors.chromeFace
                knobPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, knobPaint)
                knobRimPaint.color = colors.chromeRing
                knobRimPaint.strokeWidth = 1f * dp
                knobRimPaint.style = Paint.Style.STROKE
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, knobRimPaint)
            }
        }
    }

    private fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        pathCache.reset()
        pathCache.moveTo(cx - r * 0.55f, cy - r)
        pathCache.lineTo(cx + r, cy)
        pathCache.lineTo(cx - r * 0.55f, cy + r)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val barW = r * 0.45f
        canvas.drawRect(cx - r * 0.8f, cy - r, cx - r * 0.8f + barW, cy + r, iconPaint)
        canvas.drawRect(cx + r * 0.8f - barW, cy - r, cx + r * 0.8f, cy + r, iconPaint)
    }

    private fun drawSkipIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, isNext: Boolean) {
        val barW = r * 0.35f
        val dir = if (isNext) 1f else -1f
        canvas.drawRect(cx + (if (isNext) r - barW else -r), cy - r * 0.9f, cx + (if (isNext) r else -r + barW), cy + r * 0.9f, iconPaint)
        pathCache.reset()
        pathCache.moveTo(cx - dir * r * 0.8f, cy - r * 0.9f)
        pathCache.lineTo(cx + dir * (r - barW), cy)
        pathCache.lineTo(cx - dir * r * 0.8f, cy + r * 0.9f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }
}
