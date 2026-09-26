package com.nexus.launcher.ui.widgets.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

import kotlin.math.roundToInt

/**
 * Tier 4 (Expanded, 4x2+) renderer for the modern Nexus / Glass music widget.
 * Minimalist, calm, non-distracting layout:
 * - Album art (scaled up to 120dp) with rounded glass corners
 * - Clean metadata (track title and artist name only)
 * - Full-width progress bar with dual numerical timestamps
 * - Tactile glass / neumorphic transport triad (Prev, Play/Pause, Next)
 */
object NexusMusicDrawExpanded {

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.LEFT
    }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.LEFT
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawExpanded(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        title: String,
        artist: String,
        art: Bitmap?,
        isPlaying: Boolean,
        progress: Float,
        posStr: String,
        durStr: String,
        palette: NexusNeumorphicDraw.SoftPalette,
        highContrastSecondary: Int,
        isNeumorphic: Boolean,
        isGlass: Boolean
    ) {
        val width = w.toFloat()
        val height = h.toFloat()

        val paddingX = (12f * dp).coerceAtMost(width * 0.08f).roundToInt().toFloat()
        val paddingTop = (10f * dp).coerceAtMost(height * 0.08f).roundToInt().toFloat()
        val paddingBottom = (10f * dp).coerceAtMost(height * 0.08f).roundToInt().toFloat()

        // 1. Adaptive Transport Controls (Prev, Play/Pause, Next)
        val baseCenterR = (26f * dp).coerceAtMost(height * 0.20f).coerceAtLeast(16f * dp)
        val baseSideR = (baseCenterR * 0.78f).coerceAtLeast(13f * dp)

        val sideMargin = (12f * dp).coerceAtLeast(8f * dp)
        val maxAllowedSpan = (width / 2f - sideMargin).coerceAtLeast(16f * dp)
        val standardSpan = 78f * dp
        val hScale = if (maxAllowedSpan < standardSpan) {
            (maxAllowedSpan / standardSpan).coerceIn(0.55f, 1.0f)
        } else {
            1.0f
        }

        val maxR = ((maxAllowedSpan - 6f * dp) / 2.6f).coerceAtLeast(8f * dp)
        val centerR = minOf(baseCenterR * hScale, maxR * 1.25f).roundToInt().toFloat()
        val sideR = minOf(baseSideR * hScale, maxR).roundToInt().toFloat()

        val minOffset = centerR + sideR + 4f * dp
        val maxOffset = (maxAllowedSpan - sideR).coerceAtLeast(minOffset)
        val targetOffset = 66f * dp * hScale
        val offset = targetOffset.coerceIn(minOffset, maxOffset).roundToInt().toFloat()

        val ctrlY = (height - paddingBottom - centerR).roundToInt().toFloat()
        val cxCenter = (width / 2f).roundToInt().toFloat()

        // 2. Guaranteed non-colliding Progress Bar clearance above Play button
        val thumbR = (4.5f * dp).roundToInt().toFloat()
        val playTop = ctrlY - centerR
        val gapBarToPlay = (7f * dp).roundToInt().toFloat()
        val maxBarY = (playTop - thumbR - gapBarToPlay).roundToInt().toFloat()

        // 3. Scaled Album Art (strictly above progress bar)
        val artTop = (paddingTop + 2f * dp).roundToInt().toFloat()
        val artLeft = (paddingX + 2f * dp).roundToInt().toFloat()
        val gapArtToBar = (7f * dp).roundToInt().toFloat()
        val maxArtH = (maxBarY - gapArtToBar - artTop).roundToInt().toFloat()
        val artSize = minOf(120f * dp, maxArtH.toFloat()).coerceAtLeast(24f * dp).roundToInt().toFloat()

        if (maxArtH >= 24f * dp) {
            val cornerR = (artSize * 0.16f).coerceIn(6f * dp, 16f * dp).roundToInt().toFloat()
            NexusMusicDrawIcons.drawArtwork(canvas, art, artLeft, artTop, artSize, cornerR, palette)
        }

        val actualArtBottom = if (maxArtH >= 24f * dp) artTop + artSize else artTop
        val availableBarRange = maxBarY - (actualArtBottom + gapArtToBar)
        val barY = if (availableBarRange > 0f) {
            (actualArtBottom + gapArtToBar + availableBarRange * 0.35f).roundToInt().toFloat().coerceAtMost(maxBarY)
        } else {
            maxBarY
        }

        // 4. Metadata Block (Title and Artist)
        val contentLeft = if (maxArtH >= 24f * dp) {
            (artLeft + artSize + 12f * dp).roundToInt().toFloat()
        } else {
            artLeft
        }
        val contentRight = (width - paddingX - 4f * dp).roundToInt().toFloat()
        val contentW = (contentRight - contentLeft).coerceAtLeast(10f)

        titlePaint.color = palette.textPrimary
        val titleTextSize = (minOf(16f * dp, maxOf(12f * dp, height * 0.12f))).roundToInt().toFloat()
        titlePaint.textSize = titleTextSize
        titlePaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        artistPaint.color = highContrastSecondary
        val artistTextSize = (minOf(13f * dp, maxOf(10f * dp, height * 0.09f))).roundToInt().toFloat()
        artistPaint.textSize = artistTextSize
        artistPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        if (isGlass) {
            val shadowColor = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(120, 0, 0, 0)
            titlePaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
            artistPaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
        } else {
            titlePaint.clearShadowLayer()
            artistPaint.clearShadowLayer()
        }

        val metaCenterY = if (maxArtH >= 24f * dp) {
            (artTop + artSize / 2f).roundToInt().toFloat()
        } else {
            ((artTop + barY) / 2f).roundToInt().toFloat()
        }

        val hasArtist = artist.isNotEmpty() && (height >= 105f * dp || maxArtH < 24f * dp)
        if (hasArtist) {
            val titleY = (metaCenterY - 2f * dp).roundToInt().toFloat()
            val titleEllipsized = TextUtils.ellipsize(title, titlePaint, contentW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(titleEllipsized, contentLeft, titleY, titlePaint)

            val artistY = (metaCenterY + artistTextSize + 4f * dp).roundToInt().toFloat()
            val artistEllipsized = TextUtils.ellipsize(artist, artistPaint, contentW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(artistEllipsized, contentLeft, artistY, artistPaint)
        } else {
            val titleY = (metaCenterY + titleTextSize * 0.35f).roundToInt().toFloat()
            val titleEllipsized = TextUtils.ellipsize(title, titlePaint, contentW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(titleEllipsized, contentLeft, titleY, titlePaint)
        }

        // 5. Full-Width Progress Bar with Time Readouts
        val timeFontSize = (minOf(9.5f * dp, maxOf(8f * dp, height * 0.075f))).roundToInt().toFloat()
        timePaint.color = highContrastSecondary
        timePaint.textSize = timeFontSize
        val timeW = timePaint.measureText("00:00").coerceAtLeast(26f * dp).roundToInt().toFloat()

        val textBaselineY = (barY + timeFontSize * 0.38f).roundToInt().toFloat()
        canvas.drawText(posStr, paddingX, textBaselineY, timePaint)
        canvas.drawText(durStr, (width - paddingX - timeW).roundToInt().toFloat(), textBaselineY, timePaint)

        val barLeft = (paddingX + timeW + 6f * dp).roundToInt().toFloat()
        val barRight = (width - paddingX - timeW - 6f * dp).roundToInt().toFloat()
        val barW = (barRight - barLeft).coerceAtLeast(16f)
        drawGlassProgressBar(canvas, barLeft, barY, barW, dp, progress, palette, thumbR)

        // 6. Transport Controls
        val cxPrev = cxCenter - offset
        val cxNext = cxCenter + offset
        NexusMusicDrawIcons.drawHorizontalTriad(
            canvas,
            cxPrev,
            cxCenter,
            cxNext,
            ctrlY,
            sideR,
            centerR,
            isPlaying,
            dp,
            palette,
            isNeumorphic
        )
    }

    private fun drawGlassProgressBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        dp: Float,
        progress: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        thumbR: Float
    ) {
        val rx = x.roundToInt().toFloat()
        val ry = y.roundToInt().toFloat()
        val rw = w.roundToInt().toFloat()

        trackPaint.color = palette.debossedBg
        trackPaint.strokeWidth = (3.5f * dp).roundToInt().toFloat()
        trackPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(rx, ry, rx + rw, ry, trackPaint)

        fillPaint.color = palette.textPrimary
        fillPaint.strokeWidth = (3.5f * dp).roundToInt().toFloat()
        fillPaint.strokeCap = Paint.Cap.ROUND
        val activeW = (rw * progress.coerceIn(0f, 1f)).coerceIn(0f, rw).roundToInt().toFloat()
        if (activeW > 0f) {
            canvas.drawLine(rx, ry, rx + activeW, ry, fillPaint)
        }

        thumbPaint.color = palette.textPrimary
        canvas.drawCircle(rx + activeW, ry, thumbR, thumbPaint)
    }
}
