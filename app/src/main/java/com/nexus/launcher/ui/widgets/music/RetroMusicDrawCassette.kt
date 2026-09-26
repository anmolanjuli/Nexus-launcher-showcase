package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the 1990s Cassette Tape Deck style for the Retro Music widget.
 * Features a cream paper label (no gradients), dual spinning reels (2 RPM rotation during playback),
 * a 3-digit mechanical tape counter [ 0 4 2 ], and skeuomorphic transport keys.
 */
object RetroMusicDrawCassette {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val stripeRedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripeBluePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val counterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val counterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SERIF
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
    private val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.SANS_SERIF
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
    private val sidePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
        isSubpixelText = true
    }
    private val counterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }

    private val rectCache = RectF()
    private val wellRect = RectF()
    private val artRect = RectF()
    private val pathCache = Path()

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
        colors: RetroMusicPalette.CassetteColors,
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
            RetroCassetteExpandedDraw.drawExpanded(
                canvas, width, height, dp, title, artist, art, isPlaying,
                progress, posStr, durStr, cfg, colors, surfaceMode, palette,
                appWidgetId
            )
            return
        }

        // 1. Cream Paper Label Background (Authentic surface mode only)
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.color = colors.labelBg
            canvas.drawRect(0f, 0f, width, height, bgPaint)

            // Outer cassette border
            borderPaint.color = colors.labelBorder
            borderPaint.strokeWidth = 2f * dp
            canvas.drawRoundRect(2f * dp, 2f * dp, width - 2f * dp, height - 2f * dp, 4f * dp, 4f * dp, borderPaint)

            // Top vintage accent stripes (Red & Blue bands)
            val stripeH = 3f * dp
            stripeRedPaint.color = colors.stripeRed
            canvas.drawRect(4f * dp, 4f * dp, width - 4f * dp, 4f * dp + stripeH, stripeRedPaint)
            stripeBluePaint.color = colors.stripeBlue
            canvas.drawRect(4f * dp, 4f * dp + stripeH, width - 4f * dp, 4f * dp + stripeH * 2f, stripeBluePaint)
        }

        if (tier == RetroMusicTier.TIER_1_TINY || isTiny) {
            drawTiny(canvas, width, height, dp, isPlaying, progress, colors, surfaceMode, palette)
            return
        }

        val padding = 12f * dp
        val bottomCtrlH = 46f * dp
        val ctrlY = height - 24f * dp

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        val primaryText = if (isCustomSurface && !palette!!.isLight) palette.textPrimary else colors.textPrimary
        val secondaryText = if (isCustomSurface && !palette!!.isLight) palette.textSecondary else colors.textSecondary

        // 2. Vintage "SIDE A" indicator
        sidePaint.color = secondaryText
        sidePaint.textSize = (8.5f * dp).toInt().toFloat()
        canvas.drawText("SIDE A", (width - padding - 4f * dp).toInt().toFloat(), (padding + 10f * dp).toInt().toFloat(), sidePaint)

        // 3. Track Title & Artist (Handwritten/Typewritten style)
        val isTier3 = tier == RetroMusicTier.TIER_3_COMFORTABLE || isMedium
        val showArt = cfg.showAlbumArt && art != null && (isTier3 || height >= 105f * dp)
        val targetArt = if (isTier3) 64f * dp else 42f * dp
        val artSize = if (showArt) minOf(targetArt, height - bottomCtrlH - padding * 2f).coerceAtLeast(36f * dp) else 0f
        val contentLeft = if (showArt) padding + artSize + 8f * dp else padding
        val availW = (width - padding - contentLeft).coerceAtLeast(10f)

        if (showArt) {
            artRect.set(padding, padding + 4f * dp, padding + artSize, padding + 4f * dp + artSize)
            canvas.drawBitmap(art!!, null, artRect, artBitmapPaint)
        }

        titlePaint.color = primaryText
        titlePaint.textSize = ((if (isLarge) 13.5f else 11.5f) * dp).toInt().toFloat()
        val titleY = (padding + (if (showArt) 18f else 14f) * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title, titlePaint, availW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, contentLeft.toInt().toFloat(), titleY, titlePaint)

        artistPaint.color = secondaryText
        artistPaint.textSize = (9.5f * dp).toInt().toFloat()
        val artistY = (titleY + 13f * dp).toInt().toFloat()
        val artistEllipsized = TextUtils.ellipsize(artist, artistPaint, availW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(artistEllipsized, contentLeft.toInt().toFloat(), artistY, artistPaint)

        // Paper lined rule below text
        linePaint.color = if (isCustomSurface) (if (!palette!!.isLight) 0x40FFFFFF else 0x40000000) else colors.labelBorder
        linePaint.alpha = 70
        linePaint.strokeWidth = 0.75f * dp
        val lineY = artistY + 5f * dp
        canvas.drawLine(contentLeft, lineY, width - padding, lineY, linePaint)

        // 4. Center Spool Well & Dual Spinning Reels
        val wellTop = lineY + 6f * dp
        val wellBottom = if (cfg.showProgressBar && !isCompact) ctrlY - 20f * dp else ctrlY - 16f * dp
        if (wellBottom - wellTop >= 28f * dp) {
            val wellW = minOf(width - padding * 2f, 160f * dp)
            val wellLeft = if (showArt) contentLeft else (width - wellW) / 2f
            wellRect.set(wellLeft, wellTop, wellLeft + wellW, wellBottom)
            RetroCassetteReelDraw.drawSpoolWellWithReels(canvas, wellRect, dp, isPlaying, colors)
        }

        // 5. A length of tape across the shell: the counter reads as a count, not a position.
        if (cfg.showProgressBar && !isCompact) {
            RetroProgressBar.drawTape(
                canvas, padding, width - padding, wellBottom + 3f * dp, dp, progress, colors,
            )
        } else if (cfg.showProgressBar) {
            // Compact leaves only a couple of dp between the reels and the keys: a hairline.
            RetroProgressBar.drawSlim(
                canvas, padding, width - padding, wellBottom + 2f * dp, dp, progress,
                colors.spoolWellBg, colors.stripeRed,
            )
        }

        // 6. Mechanical Tape Counter [ 0 4 2 ]
        val counterW = 34f * dp
        val counterH = 14f * dp
        val counterX = width - padding - counterW
        val counterY = (wellTop + 2f * dp).coerceAtLeast(titleY - 8f * dp)
        drawTapeCounter(canvas, counterX, counterY, counterW, counterH, dp, progress, colors)

        // 7. Skeuomorphic Chrome Transport Keys
        RetroCassetteTransportDraw.drawCassetteTransport(canvas, width, ctrlY, dp, isPlaying, colors, surfaceMode, palette)
    }

    private fun drawTiny(
        canvas: Canvas,
        w: Float,
        h: Float,
        dp: Float,
        isPlaying: Boolean,
        progress: Float,
        colors: RetroMusicPalette.CassetteColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        val cx = (w / 2f).toInt().toFloat()
        val cy = (h / 2f).toInt().toFloat()
        val reelR = minOf(w, h) * 0.32f

        // Single spinning cassette reel
        val angle = if (isPlaying) ((SystemClock.uptimeMillis() % 30000L) / 30000f) * 360f else 0f
        RetroCassetteReelDraw.drawSingleReel(canvas, cx, cy, reelR, angle, dp, colors)

        // Small Play/Pause in center hub
        val iconR = reelR * 0.40f
        RetroCassetteTransportDraw.drawTinyPlayPause(canvas, cx, cy, iconR, dp, isPlaying, colors, surfaceMode, palette)

        // Progress border tick
        borderPaint.color = colors.stripeRed
        borderPaint.strokeWidth = 2.5f * dp
        rectCache.set(cx - reelR - 4f * dp, cy - reelR - 4f * dp, cx + reelR + 4f * dp, cy + reelR + 4f * dp)
        canvas.drawArc(rectCache, -90f, 360f * progress.coerceIn(0f, 1f), false, borderPaint)
    }

    private fun drawTapeCounter(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, dp: Float, progress: Float, colors: RetroMusicPalette.CassetteColors) {
        rectCache.set(x, y, x + w, y + h)
        counterBgPaint.color = colors.counterBg
        canvas.drawRoundRect(rectCache, 2f * dp, 2f * dp, counterBgPaint)
        counterBorderPaint.color = colors.labelBorder
        counterBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(rectCache, 2f * dp, 2f * dp, counterBorderPaint)

        val countVal = (progress.coerceIn(0f, 1f) * 999f).toInt()
        val countStr = String.format(java.util.Locale.US, "%03d", countVal)

        counterPaint.color = colors.counterText
        counterPaint.textSize = (9.5f * dp).toInt().toFloat()
        canvas.drawText(countStr, rectCache.centerX().toInt().toFloat(), (y + h - 3.5f * dp).toInt().toFloat(), counterPaint)
    }
}
