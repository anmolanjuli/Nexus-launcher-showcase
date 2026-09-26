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
 * Renders the 1970s Hi-Fi Amplifier receiver style for the Retro Music widget.
 * Features a brushed metal faceplate, amber backlit display, analog multi-segment VU meter,
 * decorative Hi-Fi receiver dials, and vintage chrome toggle switches.
 */
object RetroMusicDrawAmplifier {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val displayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val displayBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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

    private val displayRect = RectF()
    private val vuRect = RectF()
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
        colors: RetroMusicPalette.AmplifierColors,
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
            RetroAmplifierExpandedDraw.drawExpanded(
                canvas, width, height, dp, title, artist, art, isPlaying,
                progress, posStr, durStr, cfg, colors, surfaceMode, palette,
                appWidgetId
            )
            return
        }

        // 1. Brushed Metal Faceplate (Authentic surface mode only)
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            val shader = LinearGradient(0f, 0f, 0f, height, colors.faceplateTop, colors.faceplateBottom, Shader.TileMode.CLAMP)
            bgPaint.shader = shader
            canvas.drawRect(0f, 0f, width, height, bgPaint)
            bgPaint.shader = null

            // Metallic outer rim
            rimPaint.color = colors.chromeRing
            rimPaint.strokeWidth = 2f * dp
            canvas.drawRect(1f * dp, 1f * dp, width - 1f * dp, height - 1f * dp, rimPaint)

            // Corner Screws
            val screwInset = 7f * dp
            val screwR = 3.5f * dp
            drawScrew(canvas, screwInset, screwInset, screwR, dp, colors)
            drawScrew(canvas, width - screwInset, screwInset, screwR, dp, colors)
            drawScrew(canvas, screwInset, height - screwInset, screwR, dp, colors)
            drawScrew(canvas, width - screwInset, height - screwInset, screwR, dp, colors)
        }

        if (tier == RetroMusicTier.TIER_1_TINY || isTiny) {
            drawTiny(canvas, width, height, dp, isPlaying, progress, colors, cfg)
            return
        }

        val padding = 12f * dp
        val bottomCtrlH = 46f * dp
        val ctrlY = height - 24f * dp

        // 2. Amber Backlit Display
        val isTier3 = tier == RetroMusicTier.TIER_3_COMFORTABLE || isMedium
        val showArt = cfg.showAlbumArt && (art != null || isTier3 || height >= 105f * dp)
        val targetArtSize = if (isTier3) 72f * dp else 48f * dp
        val artSize = if (showArt) minOf(targetArtSize, height - bottomCtrlH - padding * 2f).coerceAtLeast(36f * dp) else 0f
        val displayLeft = if (showArt) padding + artSize + 8f * dp else padding

        // Allow room for decorative dial on the right if wide enough (>= 220dp)
        val showDial = (isLarge || isMedium) && (width >= 220f * dp)
        val dialW = if (showDial) 48f * dp else 0f
        val displayRight = width - padding - dialW

        val displayTop = padding
        val vuHeight = 12f * dp
        val vuSpacing = 6f * dp
        val displayBottom = if (cfg.showProgressBar && !isCompact) height - bottomCtrlH - padding - vuHeight - vuSpacing else ctrlY - 20f * dp

        displayRect.set(displayLeft, displayTop, displayRight, displayBottom)
        displayBgPaint.color = colors.displayBg
        canvas.drawRoundRect(displayRect, 4f * dp, 4f * dp, displayBgPaint)

        // Amber warm glow
        glowPaint.color = colors.amberGlow
        canvas.drawRoundRect(displayRect, 4f * dp, 4f * dp, glowPaint)

        displayBorderPaint.color = colors.amberDim
        displayBorderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(displayRect, 4f * dp, 4f * dp, displayBorderPaint)

        // 3. Album Art
        if (showArt) {
            artRect.set(padding, displayTop, padding + artSize, displayTop + artSize)
            if (art != null) {
                canvas.drawBitmap(art, null, artRect, artBitmapPaint)
            } else {
                displayBgPaint.color = 0xFF181208.toInt()
                canvas.drawRoundRect(artRect, 4f * dp, 4f * dp, displayBgPaint)
                RetroAmplifierControlsDraw.drawDecorativeDial(canvas, artRect.centerX(), artRect.centerY(), artSize * 0.32f, dp, "HI-FI", colors)
            }
            displayBorderPaint.color = colors.chromeRing
            displayBorderPaint.strokeWidth = 1.5f * dp
            canvas.drawRoundRect(artRect, 4f * dp, 4f * dp, displayBorderPaint)
        }

        // 4. Decorative Receiver Dial (Right Side)
        if (showDial) {
            val dialCx = width - padding - dialW / 2f
            val dialCy = (displayTop + displayBottom) / 2f
            RetroAmplifierControlsDraw.drawDecorativeDial(canvas, dialCx, dialCy, 16f * dp, dp, "LEVEL", colors)
        }

        // 5. Amber Typography
        val textPaddingX = 8f * dp
        val textAvailW = (displayRight - displayLeft - textPaddingX * 2f).coerceAtLeast(10f)

        titlePaint.color = colors.amberText
        titlePaint.textSize = ((if (isLarge) 13.5f else 11.5f) * dp).toInt().toFloat()
        val titleY = (displayTop + (if (isLarge) 17f else 14f) * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title, titlePaint, textAvailW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, (displayLeft + textPaddingX).toInt().toFloat(), titleY, titlePaint)

        if (displayBottom - displayTop >= 28f * dp) {
            artistPaint.color = colors.amberDim
            artistPaint.textSize = (9.5f * dp).toInt().toFloat()
            val artistY = (titleY + 13f * dp).toInt().toFloat()
            val artistEllipsized = TextUtils.ellipsize(artist, artistPaint, textAvailW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(artistEllipsized, (displayLeft + textPaddingX).toInt().toFloat(), artistY, artistPaint)
        }

        // 6. Analog VU Meter, and the track position under it
        if (cfg.showProgressBar && !isCompact) {
            val vuTop = displayBottom + vuSpacing
            vuRect.set(padding, vuTop, width - padding, vuTop + vuHeight)
            RetroAmplifierControlsDraw.drawAnalogVuMeter(canvas, vuRect, dp, progress, isPlaying, colors)
            // The VU meter reads as level, not position — so where the track is says so plainly.
            RetroProgressBar.drawSlim(
                canvas, padding, width - padding, vuRect.bottom + 3f * dp, dp, progress,
                colors.displayBg, colors.amberText,
            )
        } else if (cfg.showProgressBar) {
            // Compact has no room for the meter, but it has room for a line.
            RetroProgressBar.drawSlim(
                canvas, padding, width - padding, displayBottom + 3f * dp, dp, progress,
                colors.displayBg, colors.amberText,
            )
        }

        // 7. Transport Switches / Knobs
        RetroAmplifierControlsDraw.drawAmplifierTransport(canvas, width, ctrlY, dp, isPlaying, colors, surfaceMode, palette)
    }

    private fun drawTiny(
        canvas: Canvas,
        w: Float,
        h: Float,
        dp: Float,
        isPlaying: Boolean,
        progress: Float,
        colors: RetroMusicPalette.AmplifierColors,
        cfg: RetroMusicConfig,
    ) {
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.32f

        RetroAmplifierControlsDraw.drawDecorativeDial(canvas, cx, cy, radius, dp, if (isPlaying) "PLAY" else "PAUSE", colors)
        // No room for a bar at 1x1, so the dial wears the progress as a ring.
        if (cfg.showProgressBar) {
            RetroProgressBar.drawRing(
                canvas, cx, cy, radius + 4f * dp, dp, progress, colors.displayBg, colors.amberText,
            )
        }
    }

    private fun drawScrew(canvas: Canvas, cx: Float, cy: Float, r: Float, dp: Float, colors: RetroMusicPalette.AmplifierColors) {
        screwPaint.color = colors.screwHead
        canvas.drawCircle(cx, cy, r, screwPaint)
        screwSlotPaint.color = 0xFF2A2A2A.toInt()
        screwSlotPaint.strokeWidth = 1f * dp
        canvas.drawLine(cx - r * 0.6f, cy - r * 0.6f, cx + r * 0.6f, cy + r * 0.6f, screwSlotPaint)
    }
}
