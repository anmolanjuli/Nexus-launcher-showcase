package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Tier 4 (Expanded, 4x2+) renderer for the 1999 Winamp MP3 Player widget.
 * Fills 100% of the widget canvas with an authentic Winamp chassis.
 * Static elements (bevels, art, LCD frame, metadata tags) are cached as a bitmap per widget.
 * Only the spectrum analyzer (from [RetroMusicVisualizerData]), progress fill, and transport
 * buttons update at 15 FPS.
 */
object RetroMusicDrawWinampExpanded {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelLightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screenBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spectrumGreenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spectrumYellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFCC00.toInt() }
    private val spectrumRedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3333.toInt() }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val artBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun monoPaint(align: Paint.Align = Paint.Align.LEFT) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        textAlign = align
        isSubpixelText = true
    }
    private val bannerPaint = monoPaint()
    private val titlePaint = monoPaint()
    private val artistPaint = monoPaint()
    private val timePaint = monoPaint()
    private val tagPaint = monoPaint(Paint.Align.CENTER)

    private val screenRect = RectF()
    private val artRect = RectF()
    private val spectrumRect = RectF()
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
        colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?,
        appWidgetId: Int = 0
    ) {
        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        val padding = 10f * dp
        val bottomCtrlH = 50f * dp
        val ctrlY = h - 26f * dp
        val topAreaH = (h - bottomCtrlH - padding * 2.5f).coerceAtLeast(60f * dp)
        val artSize = minOf(topAreaH, 120f * dp).coerceAtLeast(50f * dp)
        val showArt = cfg.showAlbumArt
        val artLeft = padding
        val artTop = padding
        val contentLeft = if (showArt) artLeft + artSize + 8f * dp else padding

        val screenLeft = contentLeft
        val screenTop = padding
        val screenRight = w - padding
        val screenBottom = artTop + artSize
        val specW = minOf((screenRight - screenLeft) * 0.28f, 85f * dp).coerceAtLeast(36f * dp)
        val specLeft = screenRight - specW - 6f * dp
        val specTop = screenTop + 6f * dp
        val specBottom = screenBottom - 6f * dp

        val barY = screenBottom + 8f * dp
        val timeW = 34f * dp
        val barLeft = padding + timeW + 4f * dp
        val barRight = w - padding - timeW - 4f * dp
        val barW = (barRight - barLeft).coerceAtLeast(20f)
        val barH = 7f * dp

        // 1. Static Backdrop Layer (Cached per widget and track)
        val artHash = art?.hashCode() ?: 0
        val cacheKey = "winamp_${title}_${artist}_${showArt}_${artHash}_${surfaceMode}_${palette?.isLight}_${w.toInt()}_${h.toInt()}"
        val staticBmp = RetroMusicStaticCache.getOrRender(appWidgetId, w.toInt(), h.toInt(), cacheKey) { staticCanvas ->
            drawStaticWinamp(
                staticCanvas, w, h, dp, title, artist, art, cfg, colors,
                surfaceMode, palette, isCustomSurface, padding, artSize, showArt,
                artLeft, artTop, screenLeft, screenTop, screenRight, screenBottom,
                specLeft, specW, barLeft, barY, barW, barH
            )
        }
        canvas.drawBitmap(staticBmp, 0f, 0f, null)

        // 2. Dynamic Spectrum Analyzer (15 FPS, read from RetroMusicVisualizerData)
        spectrumRect.set(specLeft, specTop, specLeft + specW, specBottom)
        drawSpectrumAnalyzer(canvas, spectrumRect, dp, isPlaying, colors)

        // 3. Dynamic Progress Fill & Timestamps
        val primaryText = if (isCustomSurface && palette != null) palette.textPrimary else colors.screenText
        timePaint.color = primaryText
        timePaint.textSize = (9f * dp).toInt().toFloat()
        canvas.drawText(posStr, padding.toInt().toFloat(), (barY + 7f * dp).toInt().toFloat(), timePaint)
        canvas.drawText(durStr, (w - padding - timeW + 4f * dp).toInt().toFloat(), (barY + 7f * dp).toInt().toFloat(), timePaint)
        drawActiveProgressBar(canvas, barLeft, barY, barW, barH, dp, progress, colors)

        // 4. Transport Controls
        RetroWinampTransportDraw.drawWinampTransportExpanded(canvas, w, ctrlY, dp, isPlaying, colors, surfaceMode, palette)
    }

    private fun drawStaticWinamp(
        canvas: Canvas, w: Float, h: Float, dp: Float,
        title: String, artist: String, art: Bitmap?,
        cfg: RetroMusicConfig, colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode, palette: NexusNeumorphicDraw.SoftPalette?,
        isCustomSurface: Boolean, padding: Float, artSize: Float, showArt: Boolean,
        artLeft: Float, artTop: Float, screenLeft: Float, screenTop: Float, screenRight: Float, screenBottom: Float,
        specLeft: Float, specW: Float, barLeft: Float, barY: Float, barW: Float, barH: Float
    ) {
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.color = colors.bg
            canvas.drawRect(0f, 0f, w, h, bgPaint)
            drawBevel(canvas, 0f, 0f, w, h, dp, colors.bevelLight, colors.bevelDark, false)
        }

        if (showArt) {
            artRect.set(artLeft, artTop, artLeft + artSize, artTop + artSize)
            if (art != null) {
                canvas.drawBitmap(art, null, artRect, artBitmapPaint)
            } else {
                screenBgPaint.color = 0xFF101410.toInt()
                canvas.drawRect(artRect, screenBgPaint)
                drawPlaceholderArt(canvas, artRect, dp, colors)
            }
            artBorderPaint.color = colors.bevelShadow
            artBorderPaint.strokeWidth = 1.5f * dp
            canvas.drawRect(artRect, artBorderPaint)
            drawBevel(canvas, artRect.left, artRect.top, artRect.right, artRect.bottom, dp, colors.bevelShadow, colors.bevelLight, true)
        }

        screenRect.set(screenLeft, screenTop, screenRight, screenBottom)
        screenBgPaint.color = if (isCustomSurface) (if (!palette!!.isLight) 0x22FFFFFF else 0x18000000) else colors.screenBg
        canvas.drawRect(screenRect, screenBgPaint)
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            drawBevel(canvas, screenRect.left, screenRect.top, screenRect.right, screenRect.bottom, dp, colors.bevelShadow, colors.bevelLight, true)
        }

        val textAvailW = (specLeft - screenLeft - 12f * dp).coerceAtLeast(10f)
        val primaryText = if (isCustomSurface && palette != null) palette.textPrimary else colors.screenText
        val secondaryText = if (isCustomSurface && palette != null) palette.textSecondary else colors.screenTextDim

        bannerPaint.color = secondaryText
        bannerPaint.textSize = (8.5f * dp).toInt().toFloat()
        canvas.drawText("WINAMP NOW PLAYING • 320 KBPS", (screenLeft + 8f * dp).toInt().toFloat(), (screenTop + 14f * dp).toInt().toFloat(), bannerPaint)

        titlePaint.color = primaryText
        titlePaint.textSize = (13.5f * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title.uppercase(), titlePaint, textAvailW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, (screenLeft + 8f * dp).toInt().toFloat(), (screenTop + 32f * dp).toInt().toFloat(), titlePaint)

        artistPaint.color = secondaryText
        artistPaint.textSize = (10f * dp).toInt().toFloat()
        val artistEllipsized = TextUtils.ellipsize(artist.uppercase(), artistPaint, textAvailW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(artistEllipsized, (screenLeft + 8f * dp).toInt().toFloat(), (screenTop + 48f * dp).toInt().toFloat(), artistPaint)

        if (screenBottom - screenTop >= 70f * dp) {
            drawAuxiliaryBadges(canvas, screenLeft + 8f * dp, screenTop + 58f * dp, dp, colors)
        }

        // Draw segmented bar inactive slots
        val count = 28
        val gap = 1.5f * dp
        val segW = (barW - (count - 1) * gap) / count
        barBgPaint.color = 0xFF142014.toInt()
        for (i in 0 until count) {
            val sx = barLeft + i * (segW + gap)
            canvas.drawRect(sx, barY, sx + segW, barY + barH, barBgPaint)
        }
    }

    private fun drawSpectrumAnalyzer(
        canvas: Canvas,
        rect: RectF,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.WinampColors
    ) {
        val barCount = 12
        val gap = 2f * dp
        val bw = (rect.width() - (barCount - 1) * gap) / barCount

        spectrumGreenPaint.color = colors.screenText

        for (i in 0 until barCount) {
            val bx = rect.left + i * (bw + gap)
            val wave = if (isPlaying) RetroMusicVisualizerData.getSpectrumBar(i) else 0.15f
            val barH = (rect.height() * wave).coerceIn(4f * dp, rect.height())

            val segments = (barH / (3f * dp)).toInt().coerceAtLeast(1)
            for (s in 0 until segments) {
                val segY = rect.bottom - (s + 1) * 3f * dp
                val frac = s.toFloat() / segments
                val paint = when {
                    frac > 0.8f -> spectrumRedPaint
                    frac > 0.5f -> spectrumYellowPaint
                    else -> spectrumGreenPaint
                }
                canvas.drawRect(bx, segY, bx + bw, segY + 2.2f * dp, paint)
            }
        }
    }

    private fun drawActiveProgressBar(
        canvas: Canvas,
        left: Float, top: Float, width: Float, height: Float,
        dp: Float, progress: Float, colors: RetroMusicPalette.WinampColors
    ) {
        val count = 28
        val gap = 1.5f * dp
        val segW = (width - (count - 1) * gap) / count
        val activeCount = (progress * count).toInt().coerceIn(0, count)

        barActivePaint.color = colors.screenText
        for (i in 0 until activeCount) {
            val sx = left + i * (segW + gap)
            canvas.drawRect(sx, top, sx + segW, top + height, barActivePaint)
        }
    }

    private fun drawAuxiliaryBadges(canvas: Canvas, left: Float, top: Float, dp: Float, colors: RetroMusicPalette.WinampColors) {
        val badges = arrayOf("EQ", "PL", "SHUF", "REP")
        var cx = left
        val bw = 24f * dp
        val bh = 13f * dp
        tagPaint.textSize = 7.5f * dp
        tagPaint.color = colors.screenTextDim

        for (i in badges.indices) {
            rectCache.set(cx, top, cx + bw, top + bh)
            btnBgPaint.color = 0xFF222822.toInt()
            canvas.drawRoundRect(rectCache, 2f * dp, 2f * dp, btnBgPaint)
            btnBorderPaint.color = colors.bevelDark
            btnBorderPaint.strokeWidth = 1f * dp
            canvas.drawRoundRect(rectCache, 2f * dp, 2f * dp, btnBorderPaint)
            canvas.drawText(badges[i], cx + bw / 2f, top + 9.5f * dp, tagPaint)
            cx += bw + 4f * dp
        }
    }

    private fun drawPlaceholderArt(canvas: Canvas, rect: RectF, dp: Float, colors: RetroMusicPalette.WinampColors) {
        val barCount = 7
        val bw = (rect.width() - (barCount + 1) * 2f * dp) / barCount
        barActivePaint.color = colors.screenText
        for (b in 0 until barCount) {
            val bh = rect.height() * (0.3f + 0.35f * (b % 3))
            val bx = rect.left + 2f * dp + b * (bw + 2f * dp)
            canvas.drawRect(bx, rect.bottom - 2f * dp - bh, bx + bw, rect.bottom - 2f * dp, barActivePaint)
        }
    }

    private fun drawBevel(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        dp: Float, light: Int, shadow: Int, sunken: Boolean
    ) {
        val stroke = 1f * dp
        bevelLightPaint.color = if (sunken) shadow else light
        bevelDarkPaint.color = if (sunken) light else shadow
        canvas.drawRect(l, t, r, t + stroke, bevelLightPaint)
        canvas.drawRect(l, t, l + stroke, b, bevelLightPaint)
        canvas.drawRect(l, b - stroke, r, b, bevelDarkPaint)
        canvas.drawRect(r - stroke, t, r, b, bevelDarkPaint)
    }
}
