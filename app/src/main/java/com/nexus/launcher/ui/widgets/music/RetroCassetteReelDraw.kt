package com.nexus.launcher.ui.widgets.music

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.sin

/**
 * Handles rendering the spinning reels and spool well for the Cassette style music widget.
 * Animates the 6-toothed reel sprockets at 2 RPM during active playback.
 */
object RetroCassetteReelDraw {

    private val spoolWellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spoolWellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val spoolHubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spoolTeethPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bridgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawSpoolWellWithReels(
        canvas: Canvas,
        rect: RectF,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.CassetteColors
    ) {
        // Dark spool compartment cutout
        spoolWellPaint.color = colors.spoolWellBg
        spoolWellPaint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, spoolWellPaint)

        spoolWellBorderPaint.color = colors.labelBorder
        spoolWellBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(rect, 4f * dp, 4f * dp, spoolWellBorderPaint)

        val reelR = (rect.height() * 0.40f).coerceIn(8f * dp, 18f * dp)
        val cy = rect.centerY()
        val cxLeft = rect.left + rect.width() * 0.28f
        val cxRight = rect.right - rect.width() * 0.28f

        // 2 RPM continuous rotation pre-computed on background thread
        val reelAngle = if (isPlaying) RetroMusicVisualizerData.reelAngle else 0f

        // Left Reel
        drawSingleReel(canvas, cxLeft, cy, reelR, reelAngle, dp, colors)
        // Right Reel
        drawSingleReel(canvas, cxRight, cy, reelR, reelAngle, dp, colors)

        // Center magnetic tape bridge window
        val bridgeW = (cxRight - cxLeft) * 0.45f
        val bridgeH = reelR * 0.8f
        bridgePaint.color = 0x60000000
        canvas.drawRoundRect(
            rect.centerX() - bridgeW / 2f,
            cy - bridgeH / 2f,
            rect.centerX() + bridgeW / 2f,
            cy + bridgeH / 2f,
            2f * dp,
            2f * dp,
            bridgePaint
        )
    }

    fun drawSingleReel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        angleDeg: Float,
        dp: Float,
        colors: RetroMusicPalette.CassetteColors
    ) {
        // Center hub
        spoolHubPaint.color = colors.spoolHub
        canvas.drawCircle(cx, cy, r, spoolHubPaint)

        // 6-toothed spoke sprockets
        spoolTeethPaint.color = colors.spoolTeeth
        spoolTeethPaint.strokeWidth = 2f * dp
        spoolTeethPaint.style = Paint.Style.STROKE

        val radBase = Math.toRadians(angleDeg.toDouble())
        for (i in 0 until 6) {
            val rad = radBase + i * (Math.PI / 3.0)
            val x1 = cx + (cos(rad) * r * 0.35f).toFloat()
            val y1 = cy + (sin(rad) * r * 0.35f).toFloat()
            val x2 = cx + (cos(rad) * r * 0.90f).toFloat()
            val y2 = cy + (sin(rad) * r * 0.90f).toFloat()
            canvas.drawLine(x1, y1, x2, y2, spoolTeethPaint)
        }

        // Inner center hole
        spoolWellPaint.color = colors.spoolWellBg
        spoolWellPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, r * 0.30f, spoolWellPaint)
    }
}
