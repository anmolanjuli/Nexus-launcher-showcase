package com.nexus.launcher.ui.widgets.music

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
 * Renders the 1999 Winamp MP3 Player style for the Retro Music widget.
 * Features chrome-bevelled 3D edges, monospace pixel typography, a green segmented
 * progress bar with 20 tick segments, 3D embossed transport buttons, and framed album art.
 */
object RetroMusicDrawWinamp {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelLightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screenBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segActivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val segInactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val artBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
    private val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
        isSubpixelText = true
    }

    private val rectCache = RectF()
    private val screenRect = RectF()
    private val segRect = RectF()
    private val artRect = RectF()

    fun draw(
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
        cfg: RetroMusicConfig,
        colors: RetroMusicPalette.WinampColors,
        isTiny: Boolean,
        isCompact: Boolean,
        isMedium: Boolean,
        isLarge: Boolean,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null,
        appWidgetId: Int = 0
    ) {
        val width = w.toFloat()
        val height = h.toFloat()
        val wDp = width / dp
        val hDp = height / dp
        val tier = RetroMusicTier.resolve(wDp, hDp)

        if (tier == RetroMusicTier.TIER_4_EXPANDED || isLarge) {
            RetroMusicDrawWinampExpanded.drawExpanded(
                canvas, width, height, dp, title, artist, art, isPlaying,
                progress, posStr, durStr, cfg, colors, surfaceMode, palette,
                appWidgetId
            )
            return
        }

        // 1. Base Bevelled Background (Authentic surface mode only)
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.color = colors.bg
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            drawRaisedBevel(canvas, 0f, 0f, width, height, dp, colors.bevelLight, colors.bevelMid, colors.bevelDark, colors.bevelShadow)
        }

        if (tier == RetroMusicTier.TIER_1_TINY || isTiny) {
            drawTiny(canvas, width, height, dp, isPlaying, progress, colors, surfaceMode, palette)
            return
        }

        val padding = 8f * dp
        val bottomCtrlH = 46f * dp
        val ctrlY = height - 24f * dp

        // 2. Display Window (Recessed 3D box)
        val isTier3 = tier == RetroMusicTier.TIER_3_COMFORTABLE || isMedium
        val showArt = cfg.showAlbumArt && (art != null || isTier3 || height >= 105f * dp)
        val targetArtSize = if (isTier3) 72f * dp else 48f * dp
        val artSize = if (showArt) minOf(targetArtSize, height - bottomCtrlH - padding * 2f).coerceAtLeast(36f * dp) else 0f
        val displayLeft = if (showArt) padding * 2f + artSize else padding
        val displayRight = width - padding
        val displayTop = padding
        val displayBottom = if (cfg.showProgressBar && !isCompact) height - bottomCtrlH - padding else ctrlY - 20f * dp

        screenRect.set(displayLeft, displayTop, displayRight, displayBottom)
        screenBgPaint.color = colors.screenBg
        canvas.drawRect(screenRect, screenBgPaint)
        drawSunkenBevel(canvas, screenRect.left, screenRect.top, screenRect.right, screenRect.bottom, dp, colors.bevelShadow, colors.bevelDark, colors.bevelLight)

        // 3. Album Art
        if (showArt) {
            val artTop = displayTop
            artRect.set(padding, artTop, padding + artSize, artTop + artSize)
            if (art != null) {
                canvas.drawBitmap(art, null, artRect, artBitmapPaint)
            } else {
                // Retro visualizer placeholder when no art
                screenBgPaint.color = 0xFF101410.toInt()
                canvas.drawRect(artRect, screenBgPaint)
                val barCount = 7
                val bw = (artSize - (barCount + 1) * 2f * dp) / barCount
                segActivePaint.color = colors.screenText
                segActivePaint.style = Paint.Style.FILL
                for (b in 0 until barCount) {
                    val bh = artSize * (0.3f + 0.5f * (kotlin.math.sin(b * 1.1 + 1.0).toFloat().coerceIn(0f, 1f)))
                    val bx = padding + 2f * dp + b * (bw + 2f * dp)
                    val by = artTop + artSize - 2f * dp - bh
                    canvas.drawRect(bx, by, bx + bw, artTop + artSize - 2f * dp, segActivePaint)
                }
            }
            artBorderPaint.color = colors.bevelShadow
            artBorderPaint.strokeWidth = 1.5f * dp
            canvas.drawRect(artRect, artBorderPaint)
            drawSunkenBevel(canvas, artRect.left, artRect.top, artRect.right, artRect.bottom, dp, colors.bevelShadow, colors.bevelDark, colors.bevelLight)
        }

        // 4. Text inside LCD Window
        val textPaddingX = 6f * dp
        val textAvailW = (displayRight - displayLeft - textPaddingX * 2f).coerceAtLeast(10f)

        titlePaint.color = colors.screenText
        titlePaint.textSize = ((if (isTier3) 12.5f else 11f) * dp).roundToInt().toFloat()
        val titleY = (displayTop + (if (isTier3) 15f else 14f) * dp).roundToInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title.uppercase(), titlePaint, textAvailW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, (displayLeft + textPaddingX).roundToInt().toFloat(), titleY, titlePaint)

        if (displayBottom - displayTop >= 28f * dp) {
            artistPaint.color = colors.screenTextDim
            artistPaint.textSize = (9.5f * dp).roundToInt().toFloat()
            val artistY = (titleY + 13f * dp).roundToInt().toFloat()
            val artistEllipsized = TextUtils.ellipsize(artist.uppercase(), artistPaint, textAvailW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(artistEllipsized, (displayLeft + textPaddingX).roundToInt().toFloat(), artistY, artistPaint)

            if (isTier3 && displayRight - displayLeft > 120f * dp) {
                infoPaint.color = colors.screenText
                infoPaint.textSize = (8.5f * dp).roundToInt().toFloat()
                val rateStr = "$posStr / $durStr"
                canvas.drawText(rateStr, (displayRight - textPaddingX).roundToInt().toFloat(), artistY, infoPaint)
            }
        }

        // 5. Segmented Progress Bar
        if (cfg.showProgressBar && !isCompact) {
            val barY = displayBottom + 4f * dp
            val barH = 6f * dp
            val barW = width - padding * 2f
            drawSegmentedProgressBar(canvas, padding, barY, barW, barH, dp, progress, colors)
        }

        // 6. Transport Buttons
        RetroWinampTransportDraw.drawWinampTransport(canvas, width, ctrlY, dp, isPlaying, colors, surfaceMode, palette)
    }

    private fun drawTiny(
        canvas: Canvas,
        w: Float,
        h: Float,
        dp: Float,
        isPlaying: Boolean,
        progress: Float,
        colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        val cx = (w / 2f).roundToInt().toFloat()
        val cy = (h / 2f).roundToInt().toFloat()
        val btnSize = (minOf(w, h) * 0.58f).roundToInt().toFloat()
        RetroWinampTransportDraw.drawTinyPlayPause(canvas, cx, cy, btnSize, dp, isPlaying, colors, surfaceMode, palette)

        // Progress border tick arc around button
        val ringR = minOf(w, h) * 0.42f
        segActivePaint.color = colors.screenText
        segActivePaint.style = Paint.Style.STROKE
        segActivePaint.strokeWidth = (2.5f * dp).roundToInt().toFloat()
        val sweep = 360f * progress.coerceIn(0f, 1f)
        rectCache.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        canvas.drawArc(rectCache, -90f, sweep, false, segActivePaint)
        segActivePaint.style = Paint.Style.FILL
    }

    private fun drawSegmentedProgressBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        dp: Float,
        progress: Float,
        colors: RetroMusicPalette.WinampColors
    ) {
        val totalSegments = 20
        val gap = 1.25f * dp
        val segW = (w - (totalSegments - 1) * gap) / totalSegments
        val activeCount = (progress.coerceIn(0f, 1f) * totalSegments).toInt()

        segActivePaint.color = colors.screenText
        segActivePaint.style = Paint.Style.FILL
        segInactivePaint.color = 0xFF002A00.toInt()
        segInactivePaint.style = Paint.Style.FILL

        for (i in 0 until totalSegments) {
            val sx = x + i * (segW + gap)
            segRect.set(sx, y, sx + segW, y + h)
            val paint = if (i < activeCount) segActivePaint else segInactivePaint
            canvas.drawRect(segRect, paint)
        }
    }

    private fun drawRaisedBevel(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, dp: Float, cLight: Int, cMid: Int, cDark: Int, cShadow: Int) {
        val bw = 1.25f * dp
        bevelLightPaint.color = cLight
        canvas.drawRect(l, t, r, t + bw, bevelLightPaint)
        canvas.drawRect(l, t, l + bw, b, bevelLightPaint)

        bevelDarkPaint.color = cShadow
        canvas.drawRect(l, b - bw, r, b, bevelDarkPaint)
        canvas.drawRect(r - bw, t, r, b, bevelDarkPaint)
    }

    private fun drawSunkenBevel(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, dp: Float, cShadow: Int, cDark: Int, cLight: Int) {
        val bw = 1.25f * dp
        bevelDarkPaint.color = cShadow
        canvas.drawRect(l, t, r, t + bw, bevelDarkPaint)
        canvas.drawRect(l, t, l + bw, b, bevelDarkPaint)

        bevelLightPaint.color = cLight
        canvas.drawRect(l, b - bw, r, b, bevelLightPaint)
        canvas.drawRect(r - bw, t, r, b, bevelLightPaint)
    }
}
