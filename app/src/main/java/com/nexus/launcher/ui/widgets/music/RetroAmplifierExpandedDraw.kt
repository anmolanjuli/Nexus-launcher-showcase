package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Tier 4 (Expanded, 4x2+) renderer for the 1970s Hi-Fi Amplifier receiver style.
 * Fills 100% of the widget canvas with authentic audio component hardware.
 * Static faceplate, screws, album art, amber display, and tone sliders are cached
 * into a single bitmap.
 * The dual VU meter needles are computed off-thread by [RetroMusicVisualizerData]
 * and rendered at 15 FPS without main-thread trigonometric overhead.
 */
object RetroAmplifierExpandedDraw {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val displayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val displayBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SERIF; textAlign = Paint.Align.LEFT; isSubpixelText = true }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; textAlign = Paint.Align.LEFT; isSubpixelText = true }
    private val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT; isSubpixelText = true }
    private val scalePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; isSubpixelText = true }

    private val vuBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val vuBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val segActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segDimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sliderTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sliderTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val artRect = RectF()
    private val displayRect = RectF()
    private val vuRect = RectF()
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
        colors: RetroMusicPalette.AmplifierColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?,
        appWidgetId: Int = 0
    ) {
        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        val padding = 10f * dp
        // A band across the faceplate for the tuning scale, between the hardware and the keys.
        val progressBand = if (cfg.showProgressBar) RetroProgressBar.TUNING_BAND_DP * dp else 0f
        val bottomH = 52f * dp + progressBand
        val artSize = minOf(h - bottomH - padding * 2.5f, 120f * dp).coerceAtLeast(56f * dp)
        val showArt = cfg.showAlbumArt
        val artLeft = padding + 6f * dp
        val artTop = padding + 4f * dp
        val contentLeft = if (showArt) artLeft + artSize + 10f * dp else padding + 6f * dp
        val topH = artSize

        val totalAvailW = w - contentLeft - padding - 6f * dp
        val vuW = minOf(totalAvailW * 0.38f, 120f * dp).coerceAtLeast(46f * dp)
        val metaW = totalAvailW - vuW - 8f * dp
        val metaLeft = contentLeft
        val displayH = topH * 0.58f

        displayRect.set(metaLeft, artTop, metaLeft + metaW, artTop + displayH)
        val vuLeft = displayRect.right + 8f * dp
        vuRect.set(vuLeft, artTop, vuLeft + vuW, artTop + topH)

        val ctrlY = h - 26f * dp
        val knobSize = minOf(bottomH * 1.15f, 72f * dp)
        val knobX = w - padding - knobSize / 2f - 6f * dp

        // 1. Static Hardware Chassis (Brushed aluminum, screws, art, display, sliders, VU housing)
        val artHash = art?.hashCode() ?: 0
        val cacheKey = "amp_${title}_${artist}_${showArt}_${artHash}_${surfaceMode}_${palette?.isLight}_${cfg.showProgressBar}_${w.toInt()}_${h.toInt()}"
        val staticBmp = RetroMusicStaticCache.getOrRender(appWidgetId, w.toInt(), h.toInt(), cacheKey) { staticCanvas ->
            drawStaticAmplifier(
                staticCanvas, w, h, dp, title, artist, art, cfg, colors,
                surfaceMode, palette, isCustomSurface, padding, artSize, showArt,
                artLeft, artTop, contentLeft, metaLeft, metaW, topH, displayH,
                vuRect
            )
        }
        canvas.drawBitmap(staticBmp, 0f, 0f, null)

        // 2. Dynamic Analog VU Meter (15 FPS, read from RetroMusicVisualizerData)
        val channelH = (vuRect.height() - 20f * dp) / 2f
        val vuLeftVal = if (isPlaying) RetroMusicVisualizerData.vuLeft else 0.25f
        val vuRightVal = if (isPlaying) RetroMusicVisualizerData.vuRight else 0.25f
        drawSingleChannelMeter(canvas, vuRect.left + 5f * dp, vuRect.top + 14f * dp, vuRect.width() - 10f * dp, channelH - 2f * dp, dp, vuLeftVal, colors, "L")
        drawSingleChannelMeter(canvas, vuRect.left + 5f * dp, vuRect.top + 14f * dp + channelH, vuRect.width() - 10f * dp, channelH - 2f * dp, dp, vuRightVal, colors, "R")

        // 3. Where the track is, on the faceplate's own tuning scale — this is the only thing
        // here that says how far through the song you are; the VU meters read as level.
        if (cfg.showProgressBar) {
            RetroProgressBar.drawTuning(
                canvas,
                left = padding + 10f * dp,
                right = w - padding - 10f * dp,
                top = artTop + topH + 5f * dp,
                dp = dp,
                progress = progress,
                posStr = posStr,
                durStr = durStr,
                colors = colors,
            )
        }

        // 4. Dynamic Master Volume Dial & Transport Controls
        RetroAmplifierTransportDraw.drawMasterVolumeDial(canvas, knobX, ctrlY, knobSize / 2f, dp, isPlaying, colors)
        val transRight = knobX - knobSize / 2f - 12f * dp
        val transLeft = padding + 10f * dp
        RetroAmplifierTransportDraw.drawAmplifierTransportRow(canvas, transLeft + (transRight - transLeft) / 2f, ctrlY, dp, isPlaying, colors, surfaceMode, palette)
    }

    private fun drawStaticAmplifier(
        canvas: Canvas, w: Float, h: Float, dp: Float,
        title: String, artist: String, art: Bitmap?,
        cfg: RetroMusicConfig, colors: RetroMusicPalette.AmplifierColors,
        surfaceMode: RetroMusicConfig.SurfaceMode, palette: NexusNeumorphicDraw.SoftPalette?,
        isCustomSurface: Boolean, padding: Float, artSize: Float, showArt: Boolean,
        artLeft: Float, artTop: Float, contentLeft: Float, metaLeft: Float, metaW: Float,
        topH: Float, displayH: Float, vuR: RectF
    ) {
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(colors.chromeFace, colors.faceplateTop, colors.faceplateBottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            bgPaint.shader = null

            rimPaint.color = colors.chromeFace
            rimPaint.strokeWidth = 2f * dp
            canvas.drawRect(1f * dp, 1f * dp, w - 1f * dp, h - 1f * dp, rimPaint)

            RetroAmplifierTransportDraw.drawScrew(canvas, padding, padding, dp, colors)
            RetroAmplifierTransportDraw.drawScrew(canvas, w - padding, padding, dp, colors)
            RetroAmplifierTransportDraw.drawScrew(canvas, padding, h - padding, dp, colors)
            RetroAmplifierTransportDraw.drawScrew(canvas, w - padding, h - padding, dp, colors)
        }

        if (showArt) {
            artRect.set(artLeft, artTop, artLeft + artSize, artTop + artSize)
            if (art != null) {
                canvas.drawBitmap(art, null, artRect, artBitmapPaint)
            } else {
                displayBgPaint.color = colors.displayBg
                canvas.drawRoundRect(artRect, 4f * dp, 4f * dp, displayBgPaint)
            }
            displayBorderPaint.color = colors.chromeFace
            displayBorderPaint.strokeWidth = 1.5f * dp
            canvas.drawRoundRect(artRect, 4f * dp, 4f * dp, displayBorderPaint)
        }

        displayBgPaint.color = if (isCustomSurface) (if (!palette!!.isLight) 0x22FFFFFF else 0x18000000) else colors.displayBg
        canvas.drawRoundRect(displayRect, 4f * dp, 4f * dp, displayBgPaint)
        displayBorderPaint.color = colors.amberDim
        displayBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(displayRect, 4f * dp, 4f * dp, displayBorderPaint)

        val textAvailW = (displayRect.width() - 14f * dp).coerceAtLeast(10f)
        val primaryText = if (isCustomSurface && palette != null) palette.textPrimary else colors.amberText
        val secondaryText = if (isCustomSurface && palette != null) palette.textSecondary else colors.amberDim

        sourcePaint.color = secondaryText
        sourcePaint.textSize = (8.5f * dp).toInt().toFloat()
        canvas.drawText("STEREO HI-FI 44.1kHz • AUX-1", (displayRect.left + 7f * dp).toInt().toFloat(), (displayRect.top + 13f * dp).toInt().toFloat(), sourcePaint)

        titlePaint.color = primaryText
        titlePaint.textSize = (13.5f * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title, titlePaint, textAvailW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, (displayRect.left + 7f * dp).toInt().toFloat(), (displayRect.top + 31f * dp).toInt().toFloat(), titlePaint)

        artistPaint.color = secondaryText
        artistPaint.textSize = (10f * dp).toInt().toFloat()
        val artistEllipsized = TextUtils.ellipsize(artist, artistPaint, textAvailW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(artistEllipsized, (displayRect.left + 7f * dp).toInt().toFloat(), (displayRect.top + 46f * dp).toInt().toFloat(), artistPaint)

        val eqTop = displayRect.bottom + 6f * dp
        val eqBottom = artTop + topH
        drawToneSliders(canvas, metaLeft, eqTop, metaW, eqBottom - eqTop, dp, colors, primaryText, secondaryText)

        // VU meter housing
        vuBgPaint.color = colors.vuMeterBg
        canvas.drawRoundRect(vuR, 4f * dp, 4f * dp, vuBgPaint)
        vuBorderPaint.color = colors.amberDim
        vuBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(vuR, 4f * dp, 4f * dp, vuBorderPaint)

        scalePaint.color = colors.amberDim
        scalePaint.textSize = (7.5f * dp).toInt().toFloat()
        canvas.drawText("VU  dB", vuR.centerX().toInt().toFloat(), (vuR.top + 10f * dp).toInt().toFloat(), scalePaint)
    }

    private fun drawSingleChannelMeter(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        dp: Float, activeFraction: Float, colors: RetroMusicPalette.AmplifierColors, label: String
    ) {
        scalePaint.color = colors.amberDim
        scalePaint.textSize = (7.5f * dp).toInt().toFloat()
        canvas.drawText(label, (x + 4f * dp).toInt().toFloat(), (y + h / 2f + 2.5f * dp).toInt().toFloat(), scalePaint)

        val barLeft = x + 12f * dp
        val barW = w - 16f * dp
        val totalSegs = 14
        val gap = 1.5f * dp
        val segW = (barW - (totalSegs - 1) * gap) / totalSegs
        val activeSegs = (activeFraction * totalSegs).toInt().coerceIn(1, totalSegs)

        for (i in 0 until totalSegs) {
            val sx = barLeft + i * (segW + gap)
            rectCache.set(sx, y + 2f * dp, sx + segW, y + h - 2f * dp)
            val (baseColor, dimColor) = when {
                i >= 11 -> Pair(0xFFFF3333.toInt(), 0x33FF3333.toInt())
                i >= 8 -> Pair(colors.amberText, colors.amberDim)
                else -> Pair(0xFF44CC44.toInt(), 0x2544CC44.toInt())
            }

            if (i < activeSegs) {
                segActivePaint.color = baseColor
                canvas.drawRoundRect(rectCache, 1f * dp, 1f * dp, segActivePaint)
            } else {
                segDimPaint.color = dimColor
                canvas.drawRoundRect(rectCache, 1f * dp, 1f * dp, segDimPaint)
            }
        }
    }

    private fun drawToneSliders(
        canvas: Canvas, left: Float, top: Float, width: Float, height: Float,
        dp: Float, colors: RetroMusicPalette.AmplifierColors, primaryText: Int, secondaryText: Int
    ) {
        val labels = arrayOf("BASS", "MID", "TREBLE")
        val values = floatArrayOf(0.65f, 0.50f, 0.72f)
        val colW = width / 3f

        for (i in 0 until 3) {
            val cx = left + i * colW + colW / 2f
            scalePaint.color = secondaryText
            scalePaint.textSize = 7f * dp
            canvas.drawText(labels[i], cx, top + 8f * dp, scalePaint)

            val trackTop = top + 12f * dp
            val trackH = (height - 18f * dp).coerceAtLeast(10f * dp)
            val trackBottom = trackTop + trackH

            sliderTickPaint.color = colors.faceplateBottom
            canvas.drawLine(cx, trackTop, cx, trackBottom, sliderTickPaint)

            val midY = trackTop + trackH / 2f
            sliderTickPaint.color = colors.amberDim
            canvas.drawLine(cx - 5f * dp, midY, cx + 5f * dp, midY, sliderTickPaint)

            val thumbY = trackBottom - values[i] * trackH
            rectCache.set(cx - 7f * dp, thumbY - 3f * dp, cx + 7f * dp, thumbY + 3f * dp)
            sliderTrackPaint.color = colors.chromeFace
            canvas.drawRoundRect(rectCache, 1.5f * dp, 1.5f * dp, sliderTrackPaint)
            sliderTickPaint.color = colors.amberText
            canvas.drawLine(cx - 7f * dp, thumbY, cx + 7f * dp, thumbY, sliderTickPaint)
        }
    }
}
