package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Tier 4 (Expanded, 4x2+) renderer for the 1980s CRT Terminal style.
 * Fills 100% of the widget canvas with an authentic hacker/VT100 console.
 * Static CRT screen and metadata lines are cached as a bitmap per widget.
 */
object RetroTerminalExpandedDraw {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dimBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }
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
    private val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val logPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
    }

    private val artRect = RectF()
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
        colors: RetroMusicPalette.TerminalColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?,
        appWidgetId: Int = 0
    ) {
        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        val padding = 10f * dp
        val stroke = 1.5f * dp
        val headerY = padding + 12f * dp

        val primaryGreen = if (isCustomSurface && palette != null) palette.textPrimary else colors.phosphorGreen
        val offWhite = if (isCustomSurface && palette != null) palette.textPrimary else colors.textOffWhite
        val dimText = if (isCustomSurface && palette != null) palette.textSecondary else colors.phosphorDim

        val showArt = cfg.showAlbumArt
        val artSize = minOf(h * 0.40f, 96f * dp).coerceAtLeast(48f * dp)
        val artLeft = padding + 4f * dp
        val artTop = headerY + 8f * dp
        val contentLeft = if (showArt) artLeft + artSize + 10f * dp else padding + 6f * dp
        val contentW = (w - padding - 6f * dp - contentLeft).coerceAtLeast(10f)

        // 1. Static CRT Console Backdrop (Cached per widget and track)
        val artHash = art?.hashCode() ?: 0
        val cacheKey = "terminal_${title}_${artist}_${showArt}_${artHash}_${surfaceMode}_${palette?.isLight}_${w.toInt()}_${h.toInt()}"
        val staticBmp = RetroMusicStaticCache.getOrRender(appWidgetId, w.toInt(), h.toInt(), cacheKey) { staticCanvas ->
            drawStaticTerminal(
                staticCanvas, w, h, dp, title, artist, art, colors,
                surfaceMode, isCustomSurface, padding, stroke, primaryGreen,
                offWhite, dimText, headerY, showArt, artSize, artLeft, artTop,
                contentLeft, contentW
            )
        }
        canvas.drawBitmap(staticBmp, 0f, 0f, null)

        // 2. Dynamic ASCII Progress Bar
        val barY = (artTop + artSize + 14f * dp).coerceAtMost(h - 56f * dp)
        drawAsciiProgressBar(canvas, padding + 4f * dp, barY, w - padding * 2f - 8f * dp, dp, progress, posStr, durStr, primaryGreen, dimText)

        // 3. Dynamic Transport Key Blocks
        val ctrlY = h - 24f * dp
        drawTerminalTransportExpanded(canvas, w, ctrlY, dp, isPlaying, colors, primaryGreen, offWhite, surfaceMode, palette)

        // 4. System Log Line with Blinking Cursor
        if (h >= 170f * dp) {
            val logY = barY + 16f * dp
            logPaint.color = dimText
            logPaint.textSize = 8f * dp
            val logStr = "[SYS_OK] dsp: active | buffer: 100% | mem: 64k"
            canvas.drawText(logStr, padding + 4f * dp, logY, logPaint)

            val blink = (System.currentTimeMillis() / 500L) % 2 == 0L
            if (blink && isPlaying) {
                cursorPaint.color = primaryGreen
                val cursorX = padding + 4f * dp + logPaint.measureText(logStr) + 4f * dp
                canvas.drawRect(cursorX, logY - 7f * dp, cursorX + 5f * dp, logY + 1f * dp, cursorPaint)
            }
        }
    }

    private fun drawStaticTerminal(
        canvas: Canvas, w: Float, h: Float, dp: Float,
        title: String, artist: String, art: Bitmap?,
        colors: RetroMusicPalette.TerminalColors, surfaceMode: RetroMusicConfig.SurfaceMode,
        isCustomSurface: Boolean, padding: Float, stroke: Float,
        primaryGreen: Int, offWhite: Int, dimText: Int, headerY: Float,
        showArt: Boolean, artSize: Float, artLeft: Float, artTop: Float,
        contentLeft: Float, contentW: Float
    ) {
        if (surfaceMode == RetroMusicConfig.SurfaceMode.DEFAULT) {
            bgPaint.color = colors.bg
            canvas.drawRect(0f, 0f, w, h, bgPaint)

            borderPaint.color = colors.borderGreen
            borderPaint.strokeWidth = stroke
            canvas.drawRect(stroke / 2f, stroke / 2f, w - stroke / 2f, h - stroke / 2f, borderPaint)

            dimBorderPaint.color = colors.phosphorDim
            dimBorderPaint.strokeWidth = 0.75f * dp
            canvas.drawRect(3f * dp, 3f * dp, w - 3f * dp, h - 3f * dp, dimBorderPaint)
        }

        headerPaint.color = primaryGreen
        headerPaint.textSize = (8.5f * dp).toInt().toFloat()
        canvas.drawText("NEXUS AUDIO SUBSYSTEM v2.4", (padding + 4f * dp).toInt().toFloat(), headerY.toInt().toFloat(), headerPaint)

        if (showArt) {
            artRect.set(artLeft, artTop, artLeft + artSize, artTop + artSize)
            if (art != null) {
                canvas.drawBitmap(art, null, artRect, artBitmapPaint)
            } else {
                bgPaint.color = 0xFF0D180D.toInt()
                canvas.drawRect(artRect, bgPaint)
                btnTextPaint.color = primaryGreen
                btnTextPaint.textSize = (14f * dp).toInt().toFloat()
                canvas.drawText("[AUDIO]", artRect.centerX().toInt().toFloat(), (artRect.centerY() + 5f * dp).toInt().toFloat(), btnTextPaint)
            }
            artBorderPaint.color = primaryGreen
            artBorderPaint.strokeWidth = 1f * dp
            canvas.drawRect(artRect, artBorderPaint)
        }

        promptPaint.color = primaryGreen
        promptPaint.textSize = (10.5f * dp).toInt().toFloat()
        val promptText = "> play \"$title\" by \"$artist\""
        val promptEllipsized = TextUtils.ellipsize(promptText, promptPaint, contentW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(promptEllipsized, contentLeft.toInt().toFloat(), (artTop + 14f * dp).toInt().toFloat(), promptPaint)

        titlePaint.color = offWhite
        titlePaint.textSize = (13.5f * dp).toInt().toFloat()
        val titleEllipsized = TextUtils.ellipsize(title, titlePaint, contentW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(titleEllipsized, contentLeft.toInt().toFloat(), (artTop + 32f * dp).toInt().toFloat(), titlePaint)

        artistPaint.color = dimText
        artistPaint.textSize = (10f * dp).toInt().toFloat()
        val artistEllipsized = TextUtils.ellipsize(artist, artistPaint, contentW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(artistEllipsized, contentLeft.toInt().toFloat(), (artTop + 48f * dp).toInt().toFloat(), artistPaint)

        infoPaint.color = primaryGreen
        infoPaint.textSize = (8.5f * dp).toInt().toFloat()
        val infoStr = "> fmt: FLAC | rate: 320kbps | freq: 44.1kHz"
        canvas.drawText(infoStr, contentLeft.toInt().toFloat(), (artTop + 64f * dp).toInt().toFloat(), infoPaint)
    }

    private fun drawAsciiProgressBar(
        canvas: Canvas, left: Float, y: Float, availW: Float, dp: Float,
        progress: Float, posStr: String, durStr: String, primaryGreen: Int, dimText: Int
    ) {
        asciiBarPaint.textSize = (9.5f * dp).toInt().toFloat()
        val charW = asciiBarPaint.measureText("=")
        val timeLabel = " $posStr / $durStr "
        val timeLabelW = asciiBarPaint.measureText(timeLabel)
        val barCharsAvail = ((availW - timeLabelW - 4f * dp) / charW).toInt().coerceAtLeast(10)
        val filledChars = (progress * barCharsAvail).toInt().coerceIn(0, barCharsAvail)

        asciiBarPaint.color = primaryGreen
        canvas.drawText("[", left.toInt().toFloat(), y.toInt().toFloat(), asciiBarPaint)

        val barStr = buildString {
            for (i in 0 until barCharsAvail) {
                append(if (i < filledChars) "=" else "-")
            }
        }
        canvas.drawText(barStr, (left + charW).toInt().toFloat(), y.toInt().toFloat(), asciiBarPaint)
        canvas.drawText("]", (left + charW + barCharsAvail * charW).toInt().toFloat(), y.toInt().toFloat(), asciiBarPaint)

        asciiBarPaint.color = dimText
        canvas.drawText(timeLabel, (left + charW + barCharsAvail * charW + 4f * dp).toInt().toFloat(), y.toInt().toFloat(), asciiBarPaint)
    }

    private fun drawTerminalTransportExpanded(
        canvas: Canvas, w: Float, cy: Float, dp: Float, isPlaying: Boolean,
        colors: RetroMusicPalette.TerminalColors, primaryGreen: Int, offWhite: Int,
        surfaceMode: RetroMusicConfig.SurfaceMode, palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val cxCenter = (w / 2f).toInt().toFloat()
        val offset = (72f * dp).toInt().toFloat()
        val btnW = (58f * dp).toInt().toFloat()
        val playBtnW = (66f * dp).toInt().toFloat()
        val btnH = (22f * dp).toInt().toFloat()
        val cyR = cy.toInt().toFloat()

        drawTerminalKey(canvas, cxCenter - offset, cyR, btnW, btnH, dp, "[ << ]", primaryGreen, colors, surfaceMode, palette)
        val playLabel = if (isPlaying) "[ || ]" else "[ > ]"
        drawTerminalKey(canvas, cxCenter, cyR, playBtnW, btnH, dp, playLabel, offWhite, colors, surfaceMode, palette)
        drawTerminalKey(canvas, cxCenter + offset, cyR, btnW, btnH, dp, "[ >> ]", primaryGreen, colors, surfaceMode, palette)
    }

    private fun drawTerminalKey(
        canvas: Canvas, cx: Float, cy: Float, bw: Float, bh: Float, dp: Float,
        label: String, textColor: Int, colors: RetroMusicPalette.TerminalColors,
        surfaceMode: RetroMusicConfig.SurfaceMode, palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        rectCache.set(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
        val cornerRadius = 2f * dp
        when {
            surfaceMode == RetroMusicConfig.SurfaceMode.NEUMORPHIC && palette != null -> {
                NexusNeumorphicDraw.drawRaisedRoundRect(canvas, rectCache, cornerRadius, palette, dp)
            }
            surfaceMode == RetroMusicConfig.SurfaceMode.FROSTED && palette != null -> {
                btnBgPaint.color = if (palette.isLight) android.graphics.Color.argb(35, 0, 0, 0) else android.graphics.Color.argb(45, 255, 255, 255)
                btnBgPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, btnBgPaint)
                borderPaint.color = if (palette.isLight) android.graphics.Color.argb(40, 0, 0, 0) else android.graphics.Color.argb(60, 255, 255, 255)
                borderPaint.strokeWidth = 1f * dp
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, borderPaint)
            }
            else -> {
                btnBgPaint.color = 0xFF0D180D.toInt()
                canvas.drawRect(rectCache, btnBgPaint)
                borderPaint.color = colors.borderGreen
                borderPaint.strokeWidth = 1f * dp
                canvas.drawRect(rectCache, borderPaint)
            }
        }

        btnTextPaint.color = textColor
        btnTextPaint.textSize = (9.5f * dp).toInt().toFloat()
        canvas.drawText(label, cx.toInt().toFloat(), (cy + 3.5f * dp).toInt().toFloat(), btnTextPaint)
    }
}
