package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Tier 4 (Expanded, 4x2+) renderer for the 1990s Cassette Tape Deck style.
 * Fills 100% of the widget canvas with an authentic compact cassette chassis.
 * Static body, screws, paper label, and spool well are cached into a bitmap per widget.
 * Spool rotation and tape hiss meter are driven by [RetroMusicVisualizerData] at 15 FPS.
 */
object RetroCassetteExpandedDraw {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chassisBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stripeRedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripeBluePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SERIF; textAlign = Paint.Align.LEFT; isSubpixelText = true }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; textAlign = Paint.Align.LEFT; isSubpixelText = true }
    private val tracklistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT; isSubpixelText = true }
    private val sidePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.RIGHT; isSubpixelText = true }
    private val counterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; isSubpixelText = true }
    private val meterLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT; isSubpixelText = true }

    private val spoolWellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tapePackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hissSegPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val labelRect = RectF()
    private val spoolRect = RectF()
    private val counterRect = RectF()
    private val rectCache = RectF()

    fun drawExpanded(
        canvas: Canvas,
        w: Float,
        h: Float,
        dp: Float,
        title: String,
        artist: String,
        art: Bitmap?,
        isPlaying: Boolean,
        progress: Float,
        posStr: String,
        durStr: String,
        cfg: RetroMusicConfig,
        colors: RetroMusicPalette.CassetteColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?,
        appWidgetId: Int = 0
    ) {
        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        val padding = 10f * dp
        val labelMarginX = padding + 6f * dp
        val labelMarginTop = padding + 4f * dp
        val progressBand = if (cfg.showProgressBar) RetroProgressBar.TAPE_BAND_DP * dp else 0f
        val bottomCtrlH = 50f * dp + progressBand
        val labelBottom = h - bottomCtrlH - 6f * dp
        val ctrlY = h - 26f * dp

        val topH = labelBottom - labelMarginTop
        val spoolW = minOf((w - labelMarginX * 2f) * 0.44f, 150f * dp).coerceAtLeast(60f * dp)
        val spoolH = minOf(topH * 0.72f, 62f * dp)
        val spoolLeft = w - labelMarginX - spoolW - 8f * dp
        val spoolTop = labelMarginTop + (topH - spoolH) / 2f
        val spoolR = RectF(spoolLeft, spoolTop, spoolLeft + spoolW, spoolTop + spoolH)

        val counterW = 44f * dp
        val counterH = 16f * dp
        val counterLeft = spoolLeft + (spoolW - counterW) / 2f
        val counterTop = spoolTop - counterH - 3f * dp
        val counterR = RectF(counterLeft, counterTop, counterLeft + counterW, counterTop + counterH)

        val hissX = labelMarginX + 12f * dp
        val hissY = labelBottom - 18f * dp
        val hissW = (spoolLeft - hissX - 14f * dp).coerceAtLeast(40f * dp)
        val hissH = 9f * dp

        // 1. Static Cassette Hardware & Label Layer (Cached per widget and track)
        val cacheKey = "cassette_${title}_${artist}_${surfaceMode}_${palette?.isLight}_${cfg.showProgressBar}_${w.toInt()}_${h.toInt()}"
        val staticBmp = RetroMusicStaticCache.getOrRender(appWidgetId, w.toInt(), h.toInt(), cacheKey) { staticCanvas ->
            drawStaticCassette(
                staticCanvas, w, h, dp, title, artist, cfg, colors,
                surfaceMode, palette, isCustomSurface, padding, labelMarginX, labelMarginTop,
                labelBottom, spoolR, counterR, hissX, hissY
            )
        }
        canvas.drawBitmap(staticBmp, 0f, 0f, null)

        // 2. Dynamic Spool Compartment & Rotating Reels (15 FPS, read from RetroMusicVisualizerData)
        drawDynamicSpools(canvas, spoolR, dp, isPlaying, progress, colors)

        // 3. Dynamic Mechanical Tape Counter
        drawTapeCounter(canvas, counterR, dp, progress, colors)

        // 4. Dynamic Tape Hiss / Saturation LED Meter
        drawTapeHissMeter(canvas, hissX, hissY, hissW, hissH, dp, isPlaying, progress, colors)

        // 5. The tape itself as the progress bar. The spools and the counter both move with the
        // track, but neither of them reads as "how far through" at a glance.
        if (cfg.showProgressBar) {
            RetroProgressBar.drawTape(
                canvas, labelMarginX, w - labelMarginX, labelBottom + 3f * dp, dp, progress, colors,
            )
        }

        // 6. Chrome Piano Key Transport Controls
        RetroCassetteTransportDraw.drawCassetteTransportExpanded(canvas, (w / 2f).toInt().toFloat(), ctrlY, dp, isPlaying, colors, surfaceMode, palette)
    }

    private fun drawStaticCassette(
        canvas: Canvas, w: Float, h: Float, dp: Float,
        title: String, artist: String, cfg: RetroMusicConfig,
        colors: RetroMusicPalette.CassetteColors, surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?, isCustomSurface: Boolean,
        padding: Float, labelMarginX: Float, labelMarginTop: Float, labelBottom: Float,
        spoolR: RectF, counterR: RectF, hissX: Float, hissY: Float
    ) {
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.color = 0xFF1C1A18.toInt()
            canvas.drawRoundRect(0f, 0f, w, h, 6f * dp, 6f * dp, bgPaint)
            chassisBorderPaint.color = 0xFF353028.toInt()
            chassisBorderPaint.strokeWidth = 2f * dp
            canvas.drawRoundRect(1f * dp, 1f * dp, w - 1f * dp, h - 1f * dp, 5f * dp, 5f * dp, chassisBorderPaint)

            RetroCassetteTransportDraw.drawScrew(canvas, padding, padding, dp)
            RetroCassetteTransportDraw.drawScrew(canvas, w - padding, padding, dp)
            RetroCassetteTransportDraw.drawScrew(canvas, padding, h - padding, dp)
            RetroCassetteTransportDraw.drawScrew(canvas, w - padding, h - padding, dp)
        }

        labelRect.set(labelMarginX, labelMarginTop, w - labelMarginX, labelBottom)
        labelBgPaint.color = if (isCustomSurface) (if (!palette!!.isLight) 0x22FFFFFF else 0x18000000) else colors.labelBg
        canvas.drawRoundRect(labelRect, 4f * dp, 4f * dp, labelBgPaint)
        labelBorderPaint.color = if (isCustomSurface) 0x40FFFFFF else colors.labelBorder
        labelBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(labelRect, 4f * dp, 4f * dp, labelBorderPaint)

        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            stripeRedPaint.color = colors.stripeRed
            stripeBluePaint.color = colors.stripeBlue
            val stripeH = 3f * dp
            canvas.drawRect(labelRect.left + 4f * dp, labelRect.top + 5f * dp, labelRect.right - 4f * dp, labelRect.top + 5f * dp + stripeH, stripeRedPaint)
            canvas.drawRect(labelRect.left + 4f * dp, labelRect.top + 9f * dp, labelRect.right - 4f * dp, labelRect.top + 9f * dp + stripeH, stripeBluePaint)
        }

        val primaryText = if (isCustomSurface && palette != null) palette.textPrimary else colors.textPrimary
        val secondaryText = if (isCustomSurface && palette != null) palette.textSecondary else colors.textSecondary

        val metaLeft = labelRect.left + 12f * dp
        val metaW = (spoolR.left - metaLeft - 10f * dp).coerceAtLeast(10f)

        sidePaint.color = secondaryText
        sidePaint.textSize = (9f * dp).toInt().toFloat()
        canvas.drawText("SIDE A  C-90", (labelRect.right - 10f * dp).toInt().toFloat(), (labelRect.top + 22f * dp).toInt().toFloat(), sidePaint)

        titlePaint.color = primaryText
        titlePaint.textSize = (13.5f * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title, titlePaint, metaW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, metaLeft.toInt().toFloat(), (labelRect.top + 26f * dp).toInt().toFloat(), titlePaint)

        artistPaint.color = secondaryText
        artistPaint.textSize = (10f * dp).toInt().toFloat()
        val artistEllipsized = TextUtils.ellipsize(artist, artistPaint, metaW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(artistEllipsized, metaLeft.toInt().toFloat(), (labelRect.top + 41f * dp).toInt().toFloat(), artistPaint)

        rulePaint.color = if (isCustomSurface) 0x30FFFFFF else 0x25000000
        rulePaint.strokeWidth = 1f * dp
        canvas.drawLine(metaLeft, labelRect.top + 47f * dp, metaLeft + metaW, labelRect.top + 47f * dp, rulePaint)
        canvas.drawLine(metaLeft, labelRect.top + 58f * dp, metaLeft + metaW, labelRect.top + 58f * dp, rulePaint)

        // Spool cutout frame
        spoolWellPaint.color = colors.spoolWellBg
        canvas.drawRoundRect(spoolR, 4f * dp, 4f * dp, spoolWellPaint)
        strokePaint.color = colors.labelBorder
        strokePaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(spoolR, 4f * dp, 4f * dp, strokePaint)

        // Tape bridge
        val bridgeW = spoolR.width() * 0.40f
        val bridgeH = spoolR.height() * 0.35f
        spoolWellPaint.color = 0x50000000
        canvas.drawRoundRect(spoolR.centerX() - bridgeW / 2f, spoolR.centerY() - bridgeH / 2f, spoolR.centerX() + bridgeW / 2f, spoolR.centerY() + bridgeH / 2f, 2f * dp, 2f * dp, spoolWellPaint)

        // Counter housing
        spoolWellPaint.color = 0xFF141210.toInt()
        canvas.drawRoundRect(counterR, 2f * dp, 2f * dp, spoolWellPaint)
        canvas.drawRoundRect(counterR, 2f * dp, 2f * dp, strokePaint)

        meterLabelPaint.color = secondaryText
        meterLabelPaint.textSize = (7.5f * dp).toInt().toFloat()
        canvas.drawText("PEAK LEVEL dB", (hissX + 3f * dp).toInt().toFloat(), (hissY - 2f * dp).toInt().toFloat(), meterLabelPaint)
    }

    private fun drawDynamicSpools(
        canvas: Canvas, rect: RectF, dp: Float, isPlaying: Boolean, progress: Float, colors: RetroMusicPalette.CassetteColors
    ) {
        val reelR = (rect.height() * 0.38f).coerceIn(12f * dp, 26f * dp)
        val cy = rect.centerY()
        val cxLeft = rect.left + rect.width() * 0.28f
        val cxRight = rect.right - rect.width() * 0.28f
        val reelAngle = if (isPlaying) RetroMusicVisualizerData.reelAngle else 0f

        val leftTapeR = reelR * (1.1f - progress * 0.45f)
        val rightTapeR = reelR * (0.65f + progress * 0.45f)

        tapePackPaint.color = 0xFF2A1C16.toInt()
        canvas.drawCircle(cxLeft, cy, leftTapeR, tapePackPaint)
        canvas.drawCircle(cxRight, cy, rightTapeR, tapePackPaint)

        RetroCassetteReelDraw.drawSingleReel(canvas, cxLeft, cy, reelR * 0.7f, reelAngle, dp, colors)
        RetroCassetteReelDraw.drawSingleReel(canvas, cxRight, cy, reelR * 0.7f, reelAngle, dp, colors)
    }

    private fun drawTapeCounter(canvas: Canvas, rect: RectF, dp: Float, progress: Float, colors: RetroMusicPalette.CassetteColors) {
        val countVal = (progress * 999).toInt()
        val countStr = String.format(java.util.Locale.US, "%03d", countVal)
        counterPaint.color = colors.counterText
        counterPaint.textSize = (8.5f * dp).toInt().toFloat()
        canvas.drawText("[ $countStr ]", rect.centerX().toInt().toFloat(), (rect.centerY() + 3.2f * dp).toInt().toFloat(), counterPaint)
    }

    private fun drawTapeHissMeter(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        dp: Float, isPlaying: Boolean, progress: Float, colors: RetroMusicPalette.CassetteColors
    ) {
        spoolWellPaint.color = 0xFF181614.toInt()
        rectCache.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rectCache, 2f * dp, 2f * dp, spoolWellPaint)

        val count = 14
        val gap = 1.5f * dp
        val segW = (w - (count - 1) * gap - 4f * dp) / count
        val activeFraction = if (isPlaying) (progress * 0.4f + RetroMusicVisualizerData.hissLevel * 0.6f) else 0.2f
        val active = (activeFraction * count).toInt().coerceIn(1, count)

        for (i in 0 until count) {
            val sx = x + 2f * dp + i * (segW + gap)
            val color = when {
                i >= count - 2 -> 0xFFFF3333.toInt()
                i >= count - 5 -> 0xFFFFCC00.toInt()
                else -> 0xFF33CC33.toInt()
            }
            hissSegPaint.color = if (i < active) color else 0xFF2A2622.toInt()
            canvas.drawRect(sx, y + 2f * dp, sx + segW, y + h - 2f * dp, hissSegPaint)
        }
    }
}
