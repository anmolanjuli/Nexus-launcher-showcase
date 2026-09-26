package com.nexus.launcher.ui.widgets.music

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Handles chrome piano key transport buttons and chassis screws
 * for the 1990s Cassette Tape Deck retro music widget across standard, expanded, and tiny layouts.
 */
object RetroCassetteTransportDraw {

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screwSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rectCache = RectF()
    private val pathCache = Path()

    fun drawCassetteTransport(
        canvas: Canvas,
        w: Float,
        cy: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.CassetteColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        val cxCenter = (w / 2f).toInt().toFloat()
        val offset = (68f * dp).toInt().toFloat()
        val cxPrev = cxCenter - offset
        val cxNext = cxCenter + offset
        val cyR = cy.toInt().toFloat()

        val btnW = (50f * dp).toInt().toFloat()
        val playBtnW = (56f * dp).toInt().toFloat()
        val btnH = (26f * dp).toInt().toFloat()

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && !palette!!.isLight) palette.textPrimary else colors.textPrimary

        // Prev
        drawChromeKey(canvas, cxPrev - btnW / 2f, cyR - btnH / 2f, btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxPrev, cyR, 6.5f * dp, false)

        // Play / Pause
        drawChromeKey(canvas, cxCenter - playBtnW / 2f, cyR - btnH / 2f, playBtnW, btnH, dp, colors, surfaceMode, palette)
        if (isPlaying) drawPauseIcon(canvas, cxCenter, cyR, 7f * dp) else drawPlayIcon(canvas, cxCenter, cyR, 7f * dp)

        // Next
        drawChromeKey(canvas, cxNext - btnW / 2f, cyR - btnH / 2f, btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxNext, cyR, 6.5f * dp, true)
    }

    fun drawCassetteTransportExpanded(
        canvas: Canvas,
        cxCenter: Float,
        cy: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.CassetteColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val cxCenterR = cxCenter.toInt().toFloat()
        val cyR = cy.toInt().toFloat()
        val offset = (68f * dp).toInt().toFloat()
        val btnW = (54f * dp).toInt().toFloat()
        val playBtnW = (62f * dp).toInt().toFloat()
        val btnH = (26f * dp).toInt().toFloat()

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && palette != null) palette.textPrimary else colors.textPrimary

        drawChromeKey(canvas, cxCenterR - offset - btnW / 2f, cyR - btnH / 2f, btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxCenterR - offset, cyR, 7f * dp, false)

        drawChromeKey(canvas, cxCenterR - playBtnW / 2f, cyR - btnH / 2f, playBtnW, btnH, dp, colors, surfaceMode, palette)
        if (isPlaying) drawPauseIcon(canvas, cxCenterR, cyR, 7.5f * dp) else drawPlayIcon(canvas, cxCenterR, cyR, 7.5f * dp)

        drawChromeKey(canvas, cxCenterR + offset - btnW / 2f, cyR - btnH / 2f, btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxCenterR + offset, cyR, 7f * dp, true)
    }

    fun drawTinyPlayPause(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.CassetteColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        val s = (radius * 2f).toInt().toFloat()
        val x = (cx - s / 2f).toInt().toFloat()
        val y = (cy - s / 2f).toInt().toFloat()
        drawChromeKey(canvas, x, y, s, s, dp, colors, surfaceMode, palette)
        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && palette != null) palette.textPrimary else colors.textPrimary
        val iconR = s * 0.28f
        if (isPlaying) drawPauseIcon(canvas, cx, cy, iconR) else drawPlayIcon(canvas, cx, cy, iconR)
    }

    fun drawScrew(canvas: Canvas, cx: Float, cy: Float, dp: Float) {
        val r = 3.5f * dp
        screwPaint.color = 0xFF4A443A.toInt()
        canvas.drawCircle(cx, cy, r, screwPaint)
        screwSlotPaint.color = 0xFF1C1A18.toInt()
        screwSlotPaint.strokeWidth = 1f * dp
        canvas.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, screwSlotPaint)
    }

    fun drawChromeKey(
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        dp: Float,
        colors: RetroMusicPalette.CassetteColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        rectCache.set(x, y, x + w, y + h)
        val cornerRadius = 3f * dp
        when {
            surfaceMode == RetroMusicConfig.SurfaceMode.NEUMORPHIC && palette != null -> {
                NexusNeumorphicDraw.drawRaisedRoundRect(canvas, rectCache, cornerRadius, palette, dp)
            }
            surfaceMode == RetroMusicConfig.SurfaceMode.FROSTED && palette != null -> {
                btnBgPaint.color = if (palette.isLight) Color.argb(35, 0, 0, 0) else Color.argb(45, 255, 255, 255)
                btnBgPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, btnBgPaint)
                strokePaint.color = if (palette.isLight) Color.argb(40, 0, 0, 0) else Color.argb(60, 255, 255, 255)
                strokePaint.strokeWidth = 1f * dp
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, strokePaint)
            }
            else -> {
                btnBgPaint.color = colors.chromeBtn
                btnBgPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, btnBgPaint)
                strokePaint.color = colors.labelBorder
                strokePaint.strokeWidth = 1f * dp
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, strokePaint)
            }
        }
    }

    private fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        pathCache.reset()
        pathCache.moveTo(cx - r * 0.55f, cy - r * 0.75f)
        pathCache.lineTo(cx + r * 0.75f, cy)
        pathCache.lineTo(cx - r * 0.55f, cy + r * 0.75f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val barW = r * 0.32f
        val gap = r * 0.25f
        val h = r * 0.75f
        canvas.drawRect(cx - gap - barW, cy - h, cx - gap, cy + h, iconPaint)
        canvas.drawRect(cx + gap, cy - h, cx + gap + barW, cy + h, iconPaint)
    }

    private fun drawSkipIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, isNext: Boolean) {
        val barW = r * 0.35f
        val dir = if (isNext) 1f else -1f
        canvas.drawRect(cx + (if (isNext) r - barW else -r), cy - r * 0.9f, cx + (if (isNext) r else -r + barW), cy + r * 0.9f, iconPaint)
        pathCache.reset()
        pathCache.moveTo(cx - dir * r * 0.8f, cy - r * 0.9f)
        pathCache.lineTo(cx + dir * (r - barW), cy)
        pathCache.lineTo(cx - dir * r * 0.8f, cy + r * 0.9f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }
}
