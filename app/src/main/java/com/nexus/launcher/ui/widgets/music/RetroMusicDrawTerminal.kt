package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Renders the 1980s CRT Terminal style for the Retro Music widget.
 * Features a 3-row strictly non-overlapping hierarchy:
 * - Row 1: Album art (left) + Metadata block (right)
 * - Row 2: ASCII progress bar across full width
 * - Row 3: Transport controls (Prev, Play/Pause, Next at all sizes >= 2x1)
 * Uses muted CRT phosphor green (#5FBF5F) and 90% opacity off-white metadata.
 */
object RetroMusicDrawTerminal {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dimBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val promptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
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
    private val asciiBarPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
    private val btnTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }

    private val rectCache = RectF()
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
        colors: RetroMusicPalette.TerminalColors,
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
            RetroTerminalExpandedDraw.drawExpanded(
                canvas, width, height, dp, title, artist, art, isPlaying,
                progress, posStr, durStr, cfg, colors, surfaceMode, palette,
                appWidgetId
            )
            return
        }

        // 1. Background (Authentic surface mode only)
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.color = colors.bg
            canvas.drawRect(0f, 0f, width, height, bgPaint)

            // CRT Screen Outline
            borderPaint.color = colors.borderGreen
            borderPaint.strokeWidth = 1.5f * dp
            val inset = 4f * dp
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 6f * dp, 6f * dp, borderPaint)
        }

        if (tier == RetroMusicTier.TIER_1_TINY || isTiny) {
            drawTiny(canvas, width, height, dp, isPlaying, progress, colors)
            return
        }

        val padding = if (isCompact) 8f * dp else 12f * dp
        val row3H = if (isCompact) 22f * dp else 26f * dp
        val row2H = if (cfg.showProgressBar) (if (isCompact) 13f * dp else 16f * dp) else 0f
        val gap = 4f * dp

        // Row 3 (Bottom): Transport Controls
        val ctrlY = height - padding - row3H / 2f

        // Row 2: ASCII Progress Bar
        val row2Y = if (cfg.showProgressBar) ctrlY - row3H / 2f - gap - 2f * dp else ctrlY - row3H / 2f
        val row2Top = if (cfg.showProgressBar) row2Y - row2H else row2Y

        // Row 1: Available vertical space above Row 2
        val row1Top = padding
        val row1Bottom = row2Top - gap
        val availRow1H = (row1Bottom - row1Top).coerceAtLeast(10f)

        // Only show thumbnail if vertical space can accommodate it cleanly without crowding metadata
        val isTier3 = tier == RetroMusicTier.TIER_3_COMFORTABLE || isMedium
        val canFitThumbnail = cfg.showAlbumArt && availRow1H >= 38f * dp && (height >= 105f * dp || isTier3)
        val targetArt = if (isTier3) 72f * dp else 48f * dp
        val artSize = if (canFitThumbnail) minOf(targetArt, availRow1H).coerceIn(36f * dp, 76f * dp) else 0f

        val contentLeft = if (canFitThumbnail) padding + artSize + 8f * dp else padding
        val contentRight = width - padding
        val availW = (contentRight - contentLeft).coerceAtLeast(10f)

        // --- ROW 1: Album Art (left) + Metadata block (right) ---
        if (canFitThumbnail) {
            artRect.set(padding, row1Top, padding + artSize, row1Top + artSize)
            if (art != null) {
                canvas.drawBitmap(art, null, artRect, artBitmapPaint)
            } else {
                val cx = artRect.centerX()
                val cy = artRect.centerY()
                btnTextPaint.color = colors.phosphorDim
                btnTextPaint.textSize = (14f * dp).toInt().toFloat()
                canvas.drawText("[♪]", cx.toInt().toFloat(), (cy + 5f * dp).toInt().toFloat(), btnTextPaint)
            }
            artBorderPaint.color = colors.borderGreen
            artBorderPaint.strokeWidth = 1f * dp
            canvas.drawRect(artRect, artBorderPaint)
        }

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        val metadataTextColor = if (isCustomSurface && palette!!.isLight) palette.textPrimary else colors.textOffWhite
        val artistTextColor = if (isCustomSurface && palette!!.isLight) palette.textSecondary else ((colors.textOffWhite and 0x00FFFFFF) or (0xB3 shl 24))
        val activeGreen = if (isCustomSurface && palette!!.isLight) 0xFF2D752D.toInt() else colors.phosphorGreen

        // Header Line: > SYS.AUDIO//PLAYING (Muted Green)
        promptPaint.color = activeGreen
        promptPaint.textSize = ((if (isLarge) 9.5f else if (isCompact) 7.5f else 8.5f) * dp).toInt().toFloat()
        val promptY = (row1Top + promptPaint.textSize + 2f * dp).toInt().toFloat()
        canvas.drawText("> SYS.AUDIO//PLAYING", contentLeft.toInt().toFloat(), promptY, promptPaint)

        // Title Line (Off-White or Dark in light theme)
        titlePaint.color = metadataTextColor
        titlePaint.textSize = ((if (isLarge) 13f else if (isCompact) 10.5f else 11.5f) * dp).toInt().toFloat()
        val titleY = (promptY + titlePaint.textSize + (if (isCompact) 2f else 4f) * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title.uppercase(), titlePaint, availW - 14f * dp, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, contentLeft.toInt().toFloat(), titleY, titlePaint)

        // Blinking Cursor (Muted Green, 500ms cycle)
        val isCursorOn = (SystemClock.uptimeMillis() / 500L) % 2L == 0L
        if (isCursorOn) {
            val titleW = titlePaint.measureText(titleEllipsized)
            val cursorW = 6f * dp
            val cursorH = titlePaint.textSize
            cursorPaint.color = activeGreen
            canvas.drawRect(contentLeft + titleW + 2f * dp, titleY - cursorH + 2f * dp, contentLeft + titleW + 2f * dp + cursorW, titleY + 2f * dp, cursorPaint)
        }

        // Artist Line (Off-White with subtle dim)
        if (availRow1H >= 38f * dp) {
            artistPaint.color = artistTextColor
            artistPaint.textSize = ((if (isLarge) 10f else if (isCompact) 8.5f else 9f) * dp).toInt().toFloat()
            val artistY = (titleY + artistPaint.textSize + 2f * dp).toInt().toFloat()
            val artistEllipsized = TextUtils.ellipsize(artist.uppercase(), artistPaint, availW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(artistEllipsized, contentLeft.toInt().toFloat(), artistY, artistPaint)
        }

        // --- ROW 2: ASCII Progress Bar (strictly below Row 1, across full width) ---
        if (cfg.showProgressBar) {
            drawAsciiProgressBar(canvas, padding, row2Y, width - padding * 2f, dp, progress, posStr, durStr, colors, isLarge, isCompact)
        }

        // --- ROW 3: Transport Controls (Prev, Play/Pause, Next at all sizes >= 2x1) ---
        drawTerminalTransport(canvas, width, ctrlY, row3H, dp, isPlaying, cfg, colors, isCompact, isLarge, surfaceMode, palette)
    }

    private fun drawTiny(canvas: Canvas, w: Float, h: Float, dp: Float, isPlaying: Boolean, progress: Float, colors: RetroMusicPalette.TerminalColors) {
        val cx = (w / 2f).toInt().toFloat()
        val cy = (h / 2f).toInt().toFloat()

        btnTextPaint.color = colors.phosphorGreen
        btnTextPaint.textSize = (15f * dp).toInt().toFloat()
        val label = if (isPlaying) "[ || ]" else "[ > ]"
        canvas.drawText(label, cx, cy + 5f * dp, btnTextPaint)

        val r = minOf(w, h) * 0.44f
        borderPaint.color = colors.borderGreen
        borderPaint.strokeWidth = 1.5f * dp
        rectCache.set(cx - r, cy - r, cx + r, cy + r)
        val sweep = 360f * progress.coerceIn(0f, 1f)
        canvas.drawArc(rectCache, -90f, sweep, false, borderPaint)
    }

    private fun drawAsciiProgressBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        dp: Float,
        progress: Float,
        posStr: String,
        durStr: String,
        colors: RetroMusicPalette.TerminalColors,
        isLarge: Boolean,
        isCompact: Boolean
    ) {
        val totalBlocks = if (isCompact) 14 else if (isLarge) 24 else 18
        val filledBlocks = (progress.coerceIn(0f, 1f) * totalBlocks).toInt().coerceIn(0, totalBlocks)
        val emptyBlocks = totalBlocks - filledBlocks

        val sb = StringBuilder("[")
        for (i in 0 until filledBlocks) sb.append("█")
        for (i in 0 until emptyBlocks) sb.append("░")
        sb.append("]")

        if (!isCompact) {
            sb.append(" ").append(posStr).append("/").append(durStr)
        }

        asciiBarPaint.color = colors.phosphorGreen
        asciiBarPaint.textSize = ((if (isLarge) 9.5f else if (isCompact) 7.5f else 8.5f) * dp).toInt().toFloat()
        canvas.drawText(sb.toString(), x.toInt().toFloat(), y.toInt().toFloat(), asciiBarPaint)
    }

    private fun drawTerminalTransport(
        canvas: Canvas,
        w: Float,
        cy: Float,
        row3H: Float,
        dp: Float,
        isPlaying: Boolean,
        cfg: RetroMusicConfig,
        colors: RetroMusicPalette.TerminalColors,
        isCompact: Boolean,
        isLarge: Boolean,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val cxCenter = (w / 2f).toInt().toFloat()
        btnTextPaint.textSize = ((if (isCompact) 9.5f else 11f) * dp).toInt().toFloat()

        val showExtra = cfg.showShuffleRepeat && isLarge && w >= 260f * dp
        val btnH = (row3H * 0.82f).coerceIn(20f * dp, 24f * dp)

        if (showExtra) {
            val btnW = 38f * dp
            val playBtnW = 44f * dp
            val spacing = 6f * dp

            val xShuf = cxCenter - playBtnW / 2f - spacing - btnW - spacing - btnW
            val xPrev = cxCenter - playBtnW / 2f - spacing - btnW
            val xPlay = cxCenter - playBtnW / 2f
            val xNext = cxCenter + playBtnW / 2f + spacing
            val xRep = cxCenter + playBtnW / 2f + spacing + btnW + spacing

            drawAsciiBtn(canvas, xShuf, cy - btnH / 2f, btnW, btnH, "[SHU]", dp, colors, surfaceMode, palette)
            drawAsciiBtn(canvas, xPrev, cy - btnH / 2f, btnW, btnH, "|<<", dp, colors, surfaceMode, palette)
            val playLabel = if (isPlaying) "[ || ]" else "[ > ]"
            drawAsciiBtn(canvas, xPlay, cy - btnH / 2f, playBtnW, btnH, playLabel, dp, colors, surfaceMode, palette, isHighlight = true)
            drawAsciiBtn(canvas, xNext, cy - btnH / 2f, btnW, btnH, ">>|", dp, colors, surfaceMode, palette)
            drawAsciiBtn(canvas, xRep, cy - btnH / 2f, btnW, btnH, "[REP]", dp, colors, surfaceMode, palette)
        } else {
            // Standard Prev, Play/Pause, Next with fixed aspect ratio
            val btnW = (btnH * 1.8f).coerceIn(36f * dp, 48f * dp)
            val playBtnW = btnW * 1.15f
            val spacing = (if (isCompact) 6f else 8f) * dp

            val xPrev = cxCenter - playBtnW / 2f - spacing - btnW
            val xPlay = cxCenter - playBtnW / 2f
            val xNext = cxCenter + playBtnW / 2f + spacing

            drawAsciiBtn(canvas, xPrev, cy - btnH / 2f, btnW, btnH, "|<<", dp, colors, surfaceMode, palette)
            val playLabel = if (isPlaying) "[ || ]" else "[ > ]"
            drawAsciiBtn(canvas, xPlay, cy - btnH / 2f, playBtnW, btnH, playLabel, dp, colors, surfaceMode, palette, isHighlight = true)
            drawAsciiBtn(canvas, xNext, cy - btnH / 2f, btnW, btnH, ">>|", dp, colors, surfaceMode, palette)
        }
    }

    private fun drawAsciiBtn(
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        label: String,
        dp: Float,
        colors: RetroMusicPalette.TerminalColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isHighlight: Boolean = false
    ) {
        rectCache.set(x, y, x + w, y + h)
        val cornerRadius = 2f * dp
        when {
            surfaceMode == RetroMusicConfig.SurfaceMode.NEUMORPHIC && palette != null -> {
                NexusNeumorphicDraw.drawRaisedRoundRect(canvas, rectCache, cornerRadius, palette, dp)
            }
            surfaceMode == RetroMusicConfig.SurfaceMode.FROSTED && palette != null -> {
                btnBgPaint.color = if (palette.isLight) android.graphics.Color.argb(35, 0, 0, 0) else android.graphics.Color.argb(45, 255, 255, 255)
                btnBgPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, btnBgPaint)
                dimBorderPaint.color = if (palette.isLight) android.graphics.Color.argb(40, 0, 0, 0) else android.graphics.Color.argb(60, 255, 255, 255)
                dimBorderPaint.strokeWidth = 1f * dp
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, dimBorderPaint)
            }
            else -> {
                dimBorderPaint.color = if (isHighlight) colors.phosphorGreen else colors.borderGreen
                dimBorderPaint.strokeWidth = 1f * dp
                canvas.drawRect(rectCache, dimBorderPaint)
            }
        }

        val isLightMode = palette != null && palette.isLight && surfaceMode != RetroMusicConfig.SurfaceMode.NEUMORPHIC
        val btnTextColor = if (isHighlight) {
            if (isLightMode) 0xFF2D752D.toInt() else colors.phosphorGreen
        } else {
            if (palette != null && palette.isLight) palette.textPrimary else colors.textOffWhite
        }
        btnTextPaint.color = btnTextColor
        val textY = (y + h / 2f + 3.5f * dp).toInt().toFloat()
        canvas.drawText(label, rectCache.centerX().toInt().toFloat(), textY, btnTextPaint)
    }
}
