package com.nexus.launcher.ui.widgets.music

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import kotlin.math.roundToInt

/**
 * Handles transport buttons (Prev, Play/Pause, Next) for the Winamp retro music widget across
 * standard, expanded, and compact layouts.
 */
object RetroWinampTransportDraw {

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelLightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bevelDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rectCache = RectF()
    private val pathCache = Path()

    fun drawWinampTransport(
        canvas: Canvas,
        w: Float,
        cy: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        val cxCenter = (w / 2f).roundToInt().toFloat()
        val offset = (68f * dp).roundToInt().toFloat()
        val cxPrev = cxCenter - offset
        val cxNext = cxCenter + offset

        val btnW = (50f * dp).roundToInt().toFloat()
        val playBtnW = (56f * dp).roundToInt().toFloat()
        val btnH = (26f * dp).roundToInt().toFloat()
        val cyRounded = cy.roundToInt().toFloat()

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && palette != null) palette.textPrimary else colors.btnIcon

        // Prev
        drawButton(canvas, (cxPrev - btnW / 2f).roundToInt().toFloat(), (cyRounded - btnH / 2f).roundToInt().toFloat(), btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxPrev, cyRounded, 6.5f * dp, false)

        // Play / Pause
        drawButton(canvas, (cxCenter - playBtnW / 2f).roundToInt().toFloat(), (cyRounded - btnH / 2f).roundToInt().toFloat(), playBtnW, btnH, dp, colors, surfaceMode, palette)
        if (isPlaying) drawPauseIcon(canvas, cxCenter, cyRounded, 7f * dp) else drawPlayIcon(canvas, cxCenter, cyRounded, 7f * dp)

        // Next
        drawButton(canvas, (cxNext - btnW / 2f).roundToInt().toFloat(), (cyRounded - btnH / 2f).roundToInt().toFloat(), btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxNext, cyRounded, 6.5f * dp, true)
    }

    fun drawWinampTransportExpanded(
        canvas: Canvas,
        w: Float,
        cy: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val cxCenter = (w / 2f).roundToInt().toFloat()
        val offset = (76f * dp).roundToInt().toFloat()
        val cxPrev = cxCenter - offset
        val cxNext = cxCenter + offset

        val btnW = (58f * dp).roundToInt().toFloat()
        val playBtnW = (66f * dp).roundToInt().toFloat()
        val btnH = (28f * dp).roundToInt().toFloat()
        val cyRounded = cy.roundToInt().toFloat()

        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && palette != null) palette.textPrimary else colors.btnIcon

        // Prev
        drawButton(canvas, (cxPrev - btnW / 2f).roundToInt().toFloat(), (cyRounded - btnH / 2f).roundToInt().toFloat(), btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxPrev, cyRounded, 7.5f * dp, false)

        // Play / Pause
        drawButton(canvas, (cxCenter - playBtnW / 2f).roundToInt().toFloat(), (cyRounded - btnH / 2f).roundToInt().toFloat(), playBtnW, btnH, dp, colors, surfaceMode, palette)
        if (isPlaying) drawPauseIcon(canvas, cxCenter, cyRounded, 8f * dp) else drawPlayIcon(canvas, cxCenter, cyRounded, 8f * dp)

        // Next
        drawButton(canvas, (cxNext - btnW / 2f).roundToInt().toFloat(), (cyRounded - btnH / 2f).roundToInt().toFloat(), btnW, btnH, dp, colors, surfaceMode, palette)
        drawSkipIcon(canvas, cxNext, cyRounded, 7.5f * dp, true)
    }

    fun drawTinyPlayPause(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        dp: Float,
        isPlaying: Boolean,
        colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        val x = (cx - size / 2f).roundToInt().toFloat()
        val y = (cy - size / 2f).roundToInt().toFloat()
        val s = size.roundToInt().toFloat()
        drawButton(canvas, x, y, s, s, dp, colors, surfaceMode, palette)
        val isCustomSurface = surfaceMode != RetroMusicConfig.SurfaceMode.DEFAULT && palette != null
        iconPaint.color = if (isCustomSurface && palette != null) palette.textPrimary else colors.btnIcon
        val iconR = s * 0.28f
        if (isPlaying) drawPauseIcon(canvas, cx, cy, iconR) else drawPlayIcon(canvas, cx, cy, iconR)
    }

    fun drawButton(
        canvas: Canvas, x: Float, y: Float, w: Float, h: Float,
        dp: Float, colors: RetroMusicPalette.WinampColors,
        surfaceMode: RetroMusicConfig.SurfaceMode = RetroMusicConfig.SurfaceMode.DEFAULT,
        palette: NexusNeumorphicDraw.SoftPalette? = null
    ) {
        rectCache.set(x, y, x + w, y + h)
        val cornerRadius = 4f * dp
        when {
            surfaceMode == RetroMusicConfig.SurfaceMode.NEUMORPHIC && palette != null -> {
                NexusNeumorphicDraw.drawRaisedRoundRect(canvas, rectCache, cornerRadius, palette, dp)
            }
            surfaceMode == RetroMusicConfig.SurfaceMode.FROSTED && palette != null -> {
                btnBgPaint.color = if (palette.isLight) Color.argb(35, 0, 0, 0) else Color.argb(45, 255, 255, 255)
                btnBgPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, btnBgPaint)
                bevelLightPaint.color = if (palette.isLight) Color.argb(40, 0, 0, 0) else Color.argb(60, 255, 255, 255)
                bevelLightPaint.strokeWidth = 1f * dp
                bevelLightPaint.style = Paint.Style.STROKE
                canvas.drawRoundRect(rectCache, cornerRadius, cornerRadius, bevelLightPaint)
                bevelLightPaint.style = Paint.Style.FILL
            }
            else -> {
                btnBgPaint.color = colors.btnBg
                btnBgPaint.style = Paint.Style.FILL
                canvas.drawRect(rectCache, btnBgPaint)
                drawBevel(canvas, x, y, x + w, y + h, dp, colors.bevelLight, colors.bevelDark)
            }
        }
    }

    private fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        pathCache.reset()
        pathCache.moveTo(cx - r * 0.55f, cy - r)
        pathCache.lineTo(cx + r, cy)
        pathCache.lineTo(cx - r * 0.55f, cy + r)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }

    private fun drawPauseIcon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val barW = r * 0.55f
        canvas.drawRect(cx - r * 0.85f, cy - r, cx - r * 0.85f + barW, cy + r, iconPaint)
        canvas.drawRect(cx + r * 0.85f - barW, cy - r, cx + r * 0.85f, cy + r, iconPaint)
    }

    private fun drawSkipIcon(canvas: Canvas, cx: Float, cy: Float, r: Float, isNext: Boolean) {
        val barW = r * 0.35f
        val dir = if (isNext) 1f else -1f
        canvas.drawRect(
            cx + (if (isNext) r - barW else -r),
            cy - r * 0.9f,
            cx + (if (isNext) r else -r + barW),
            cy + r * 0.9f,
            iconPaint
        )
        pathCache.reset()
        pathCache.moveTo(cx - dir * r * 0.8f, cy - r * 0.9f)
        pathCache.lineTo(cx + dir * (r - barW), cy)
        pathCache.lineTo(cx - dir * r * 0.8f, cy + r * 0.9f)
        pathCache.close()
        canvas.drawPath(pathCache, iconPaint)
    }

    private fun drawBevel(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        dp: Float, light: Int, shadow: Int
    ) {
        val stroke = 1f * dp
        bevelLightPaint.color = light
        bevelDarkPaint.color = shadow
        canvas.drawRect(l, t, r, t + stroke, bevelLightPaint)
        canvas.drawRect(l, t, l + stroke, b, bevelLightPaint)
        canvas.drawRect(l, b - stroke, r, b, bevelDarkPaint)
        canvas.drawRect(r - stroke, t, r, b, bevelDarkPaint)
    }
}
