package com.nexus.launcher.ui.widgets.music

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the analog VU meter, decorative receiver dials, and transport switches
 * for the 1970s Hi-Fi Amplifier style music widget.
 */
object RetroAmplifierControlsDraw {

    private val vuBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vuBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val segActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segDimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scaleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val chromeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val chromeFacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chromeBevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dialTickPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rectCache = RectF()
    private val btnRect = RectF()
    private val pathCache = Path()

    fun drawAnalogVuMeter(
        canvas: Canvas,
        rect: RectF,
        dp: Float,
        progress: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.AmplifierColors
    ) {
        vuBgPaint.color = colors.vuMeterBg
        canvas.drawRoundRect(rect, 3f * dp, 3f * dp, vuBgPaint)

        vuBorderPaint.color = colors.amberDim
        vuBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(rect, 3f * dp, 3f * dp, vuBorderPaint)

        val totalSegments = 16
        val bounce = if (isPlaying) (RetroMusicVisualizerData.vuLeft - 0.25f) * 0.15f else 0f
        val activeFraction = (progress + bounce).coerceIn(0f, 1f)
        val activeCount = (activeFraction * totalSegments).toInt().coerceIn(0, totalSegments)

        val segGap = 2f * dp
        val padX = 6f * dp
        val segAreaW = rect.width() - padX * 2f
        val segW = ((segAreaW - segGap * (totalSegments - 1)) / totalSegments).coerceAtLeast(2f * dp)
        val segTop = rect.top + 3f * dp
        val segBottom = rect.bottom - 3f * dp

        for (i in 0 until totalSegments) {
            val sx = rect.left + padX + i * (segW + segGap)
            rectCache.set(sx, segTop, sx + segW, segBottom)

            // Segment color: 0..9 Green, 10..12 Amber, 13..15 Red
            val (baseColor, dimColor) = when {
                i >= 13 -> Pair(0xFFFF3333.toInt(), 0x33FF3333.toInt()) // Red Peak
                i >= 10 -> Pair(colors.amberText, colors.amberDim)        // Amber Warning
                else -> Pair(0xFF44CC44.toInt(), 0x2544CC44.toInt())    // Green Nominal
            }

            if (i < activeCount) {
                segActivePaint.color = baseColor
                canvas.drawRect(rectCache, segActivePaint)
            } else {
                segDimPaint.color = dimColor
                canvas.drawRect(rectCache, segDimPaint)
            }
        }
    }

    fun drawDecorativeDial(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        dp: Float,
        label: String,
        colors: RetroMusicPalette.AmplifierColors
    ) {
        // Outer graduated scale ticks
        dialTickPaint.color = colors.amberDim
        dialTickPaint.strokeWidth = 1f * dp
        val tickRadius = r + 4f * dp
        for (a in 140..400 step 30) {
            val rad = Math.toRadians(a.toDouble())
            val x1 = (cx + cos(rad) * (r + 1.5f * dp)).toFloat()
            val y1 = (cy + sin(rad) * (r + 1.5f * dp)).toFloat()
            val x2 = (cx + cos(rad) * tickRadius).toFloat()
            val y2 = (cy + sin(rad) * tickRadius).toFloat()
            canvas.drawLine(x1, y1, x2, y2, dialTickPaint)
        }

        // Heavy aluminum dial body
        chromeRingPaint.color = colors.chromeRing
        chromeRingPaint.strokeWidth = 2f * dp
        canvas.drawCircle(cx, cy, r, chromeRingPaint)

        chromeFacePaint.color = colors.chromeFace
        canvas.drawCircle(cx, cy, r - 1f * dp, chromeFacePaint)

        // White position pointer
        dialTickPaint.color = Color.WHITE
        dialTickPaint.strokeWidth = 1.5f * dp
        canvas.drawLine(cx, cy - r * 0.2f, cx, cy - r * 0.85f, dialTickPaint)

        // Text label
        scaleTextPaint.color = colors.amberDim
        scaleTextPaint.textSize = (6.5f * dp).toInt().toFloat()
        canvas.drawText(label, cx.toInt().toFloat(), (cy + r + 8f * dp).toInt().toFloat(), scaleTextPaint)
    }

    fun drawAmplifierTransport(
        canvas: Canvas,
        w: Float,
        cy: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.AmplifierColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val cxCenter = (w / 2f).toInt().toFloat()
        val offset = (minOf(w * 0.28f, 66f * dp)).toInt().toFloat()
        val cxPrev = cxCenter - offset
        val cxNext = cxCenter + offset
        val cyRounded = cy.toInt().toFloat()

        val knobR = 17f * dp
        val playR = 20f * dp

        val iconColor = if (surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null && palette.isLight) {
            palette.textPrimary
        } else {
            colors.amberText
        }

        // Prev Switch
        drawSingleSwitch(canvas, cxPrev, cyRounded, knobR, dp, surfaceMode, palette, colors)
        drawPrevIcon(canvas, cxPrev, cyRounded, knobR * 0.46f, iconColor)

        // Play/Pause Switch
        drawSingleSwitch(canvas, cxCenter, cyRounded, playR, dp, surfaceMode, palette, colors, isPrimary = true)
        if (isPlaying) {
            drawPauseIcon(canvas, cxCenter, cyRounded, playR * 0.50f, iconColor)
        } else {
            drawPlayIcon(canvas, cxCenter, cyRounded, playR * 0.50f, iconColor)
        }

        // Next Switch
        drawSingleSwitch(canvas, cxNext, cyRounded, knobR, dp, surfaceMode, palette, colors)
        drawNextIcon(canvas, cxNext, cyRounded, knobR * 0.46f, iconColor)
    }

    private fun drawSingleSwitch(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        dp: Float,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?,
        colors: RetroMusicPalette.AmplifierColors,
        isPrimary: Boolean = false
    ) {
        when {
            surfaceMode == RetroMusicConfig.SurfaceMode.NEUMORPHIC && palette != null -> {
                NexusNeumorphicDraw.drawNeumorphicRoundButton(canvas, cx, cy, r, palette, dp)
            }
            surfaceMode == RetroMusicConfig.SurfaceMode.FROSTED && palette != null -> {
                chromeFacePaint.color = if (palette.isLight) Color.argb(35, 0, 0, 0) else Color.argb(45, 255, 255, 255)
                chromeFacePaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r, chromeFacePaint)

                chromeRingPaint.color = if (palette.isLight) Color.argb(40, 0, 0, 0) else Color.argb(60, 255, 255, 255)
                chromeRingPaint.strokeWidth = 1f * dp
                chromeRingPaint.style = Paint.Style.STROKE
                canvas.drawCircle(cx, cy, r, chromeRingPaint)
            }
            else -> {
                // Chrome toggle collar
                chromeRingPaint.color = colors.chromeRing
                chromeRingPaint.style = Paint.Style.STROKE
                chromeRingPaint.strokeWidth = if (isPrimary) 2.5f * dp else 1.8f * dp
                canvas.drawCircle(cx, cy, r, chromeRingPaint)

                chromeFacePaint.color = colors.chromeFace
                chromeFacePaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r - 1.2f * dp, chromeFacePaint)

                chromeBevelPaint.color = 0x40FFFFFF
                chromeBevelPaint.strokeWidth = 1f * dp
                canvas.drawCircle(cx, cy - 0.5f * dp, r - 2f * dp, chromeBevelPaint)
            }
        }
    }

    private fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        pathCache.reset()
        pathCache.moveTo(cx - r * 0.55f, cy - r * 0.75f)
        pathCache.lineTo(cx + r * 0.75f, cy)
        pathCache.lineTo(cx - r * 0.55f, cy + r * 0.75f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        val barW = r * 0.30f
        val gap = r * 0.24f
        val h = r * 0.72f
        canvas.drawRect(cx - gap - barW, cy - h, cx - gap, cy + h, iconPaint)
        canvas.drawRect(cx + gap, cy - h, cx + gap + barW, cy + h, iconPaint)
    }

    private fun drawPrevIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        val barW = r * 0.24f
        canvas.drawRect(cx - r * 0.75f, cy - r * 0.65f, cx - r * 0.75f + barW, cy + r * 0.65f, iconPaint)
        pathCache.reset()
        pathCache.moveTo(cx + r * 0.65f, cy - r * 0.65f)
        pathCache.lineTo(cx - r * 0.35f, cy)
        pathCache.lineTo(cx + r * 0.65f, cy + r * 0.65f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }

    private fun drawNextIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        iconPaint.color = color
        val barW = r * 0.24f
        canvas.drawRect(cx + r * 0.75f - barW, cy - r * 0.65f, cx + r * 0.75f, cy + r * 0.65f, iconPaint)
        pathCache.reset()
        pathCache.moveTo(cx - r * 0.65f, cy - r * 0.65f)
        pathCache.lineTo(cx + r * 0.35f, cy)
        pathCache.lineTo(cx - r * 0.65f, cy + r * 0.65f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }
}
