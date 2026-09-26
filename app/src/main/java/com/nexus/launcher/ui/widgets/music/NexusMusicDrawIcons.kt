package com.nexus.launcher.ui.widgets.music

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

import kotlin.math.roundToInt

/**
 * Static drawing helpers for music artwork, progress bar, and playback control glyphs.
 */
internal object NexusMusicDrawIcons {

    private val artBgWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val artBgFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isFilterBitmap = true
        isDither = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    private val artDstRect = Rect()
    private val artBoundsRect = RectF()

    fun drawArtwork(
        canvas: Canvas,
        art: Bitmap?,
        left: Float,
        top: Float,
        size: Float,
        corner: Float,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val l = left.roundToInt()
        val t = top.roundToInt()
        val s = size.roundToInt()
        artBoundsRect.set(l.toFloat(), t.toFloat(), (l + s).toFloat(), (t + s).toFloat())
        val rCorner = corner.roundToInt().toFloat()

        if (art != null && !art.isRecycled) {
            val src = centerCropRect(art.width, art.height, s, s)
            artDstRect.set(l, t, l + s, t + s)
            val save = canvas.saveLayer(artBoundsRect, null)
            canvas.drawRoundRect(artBoundsRect, rCorner, rCorner, artBgWhitePaint)
            canvas.drawBitmap(art, src, artDstRect, artBitmapPaint)
            canvas.restoreToCount(save)
        } else {
            artBgFallbackPaint.color = palette?.debossedBg ?: Color.parseColor("#33FFFFFF")
            canvas.drawRoundRect(artBoundsRect, rCorner, rCorner, artBgFallbackPaint)
        }
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawProgressBar(
        canvas: Canvas,
        px: Float,
        py: Float,
        pw: Float,
        progress: Float,
        dp: Float,
        palette: NexusNeumorphicDraw.SoftPalette?
    ) {
        val x = px.roundToInt().toFloat()
        val y = py.roundToInt().toFloat()
        val w = pw.roundToInt().toFloat()

        trackPaint.color = palette?.debossedBg ?: Color.parseColor("#33FFFFFF")
        trackPaint.strokeWidth = (4f * dp).roundToInt().toFloat()
        fillPaint.color = palette?.textPrimary ?: Color.WHITE
        fillPaint.strokeWidth = (4f * dp).roundToInt().toFloat()

        canvas.drawLine(x, y, x + w, y, trackPaint)
        val activeW = (w * progress).coerceIn(0f, w).roundToInt().toFloat()
        canvas.drawLine(x, y, x + activeW, y, fillPaint)

        thumbPaint.color = palette?.textPrimary ?: Color.WHITE
        canvas.drawCircle(x + activeW, y, (5f * dp).roundToInt().toFloat(), thumbPaint)
    }

    fun centerCropRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcRatio = srcW.toFloat() / srcH
        val dstRatio = dstW.toFloat() / dstH
        return if (srcRatio > dstRatio) {
            val offset = ((srcRatio - dstRatio) * srcH / 2).toInt()
            Rect(offset, 0, srcW - offset, srcH)
        } else {
            val offset = ((dstRatio - srcRatio) * srcW / 2).toInt()
            Rect(0, offset, srcW, srcH - offset)
        }
    }

    private val pathCache1 = Path()
    private val pathCache2 = Path()

    fun drawPlayIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        pathCache1.reset()
        val size = (radius * 0.90f).roundToInt().toFloat()
        val rx = cx.roundToInt().toFloat()
        val ry = cy.roundToInt().toFloat()
        pathCache1.moveTo(rx - size * 0.45f, ry - size * 0.65f)
        pathCache1.lineTo(rx + size * 0.65f, ry)
        pathCache1.lineTo(rx - size * 0.45f, ry + size * 0.65f)
        pathCache1.close()
        canvas.drawPath(pathCache1, paint)
    }

    fun drawPauseIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val size = (radius * 0.70f).roundToInt().toFloat()
        val width = (size * 0.35f).roundToInt().toFloat()
        val rx = cx.roundToInt().toFloat()
        val ry = cy.roundToInt().toFloat()
        canvas.drawRect(rx - size * 0.6f, ry - size, rx - size * 0.6f + width, ry + size, paint)
        canvas.drawRect(rx + size * 0.25f, ry - size, rx + size * 0.25f + width, ry + size, paint)
    }

    fun drawNextIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val size = (radius * 0.60f).roundToInt().toFloat()
        val rx = cx.roundToInt().toFloat()
        val ry = cy.roundToInt().toFloat()
        pathCache1.reset()
        pathCache1.moveTo(rx - size, ry - size)
        pathCache1.lineTo(rx, ry)
        pathCache1.lineTo(rx - size, ry + size)
        pathCache1.close()
        canvas.drawPath(pathCache1, paint)
        pathCache2.reset()
        pathCache2.moveTo(rx, ry - size)
        pathCache2.lineTo(rx + size, ry)
        pathCache2.lineTo(rx, ry + size)
        pathCache2.close()
        canvas.drawPath(pathCache2, paint)
    }

    fun drawPrevIcon(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val size = (radius * 0.60f).roundToInt().toFloat()
        val rx = cx.roundToInt().toFloat()
        val ry = cy.roundToInt().toFloat()
        pathCache1.reset()
        pathCache1.moveTo(rx + size, ry - size)
        pathCache1.lineTo(rx, ry)
        pathCache1.lineTo(rx + size, ry + size)
        pathCache1.close()
        canvas.drawPath(pathCache1, paint)
        pathCache2.reset()
        pathCache2.moveTo(rx, ry - size)
        pathCache2.lineTo(rx - size, ry)
        pathCache2.lineTo(rx, ry + size)
        pathCache2.close()
        canvas.drawPath(pathCache2, paint)
    }

    private val sharedBtnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sharedBtnRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sharedBtnIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawRoundControl(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        dp: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean
    ) {
        val rcx = cx.roundToInt().toFloat()
        val rcy = cy.roundToInt().toFloat()
        val r = radius.roundToInt().toFloat()

        if (isNeumorphic) {
            NexusNeumorphicDraw.drawNeumorphicRoundButton(canvas, rcx, rcy, r, palette, dp)
        } else {
            // Frosted glass circular button with subtle rim definition
            sharedBtnBgPaint.color = if (palette.isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
            canvas.drawCircle(rcx, rcy, r, sharedBtnBgPaint)
            sharedBtnRimPaint.color = if (palette.isLight) Color.argb(35, 0, 0, 0) else Color.argb(55, 255, 255, 255)
            sharedBtnRimPaint.strokeWidth = (1f * dp).roundToInt().toFloat().coerceAtLeast(1f)
            canvas.drawCircle(rcx, rcy, r, sharedBtnRimPaint)
        }
    }

    fun drawHorizontalTriad(
        canvas: Canvas,
        cxPrev: Float,
        cxCenter: Float,
        cxNext: Float,
        ctrlY: Float,
        sideR: Float,
        centerR: Float,
        isPlaying: Boolean,
        dp: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean
    ) {
        val rxPrev = cxPrev.roundToInt().toFloat()
        val rxCenter = cxCenter.roundToInt().toFloat()
        val rxNext = cxNext.roundToInt().toFloat()
        val ry = ctrlY.roundToInt().toFloat()
        val rSide = sideR.roundToInt().toFloat()
        val rCenter = centerR.roundToInt().toFloat()

        drawRoundControl(canvas, rxPrev, ry, rSide, dp, palette, isNeumorphic)
        drawRoundControl(canvas, rxCenter, ry, rCenter, dp, palette, isNeumorphic)
        drawRoundControl(canvas, rxNext, ry, rSide, dp, palette, isNeumorphic)

        sharedBtnIconPaint.color = palette.textPrimary
        drawPrevIcon(canvas, rxPrev, ry, rSide * 0.58f, sharedBtnIconPaint)
        if (isPlaying) drawPauseIcon(canvas, rxCenter, ry, rCenter * 0.60f, sharedBtnIconPaint)
        else drawPlayIcon(canvas, rxCenter, ry, rCenter * 0.60f, sharedBtnIconPaint)
        drawNextIcon(canvas, rxNext, ry, rSide * 0.58f, sharedBtnIconPaint)
    }

    fun drawVerticalTriad(
        canvas: Canvas,
        cx: Float,
        cyPrev: Float,
        cyPlay: Float,
        cyNext: Float,
        sideR: Float,
        centerR: Float,
        isPlaying: Boolean,
        dp: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean
    ) {
        val rx = cx.roundToInt().toFloat()
        val ryPrev = cyPrev.roundToInt().toFloat()
        val ryPlay = cyPlay.roundToInt().toFloat()
        val ryNext = cyNext.roundToInt().toFloat()
        val rSide = sideR.roundToInt().toFloat()
        val rCenter = centerR.roundToInt().toFloat()

        drawRoundControl(canvas, rx, ryPrev, rSide, dp, palette, isNeumorphic)
        drawRoundControl(canvas, rx, ryPlay, rCenter, dp, palette, isNeumorphic)
        drawRoundControl(canvas, rx, ryNext, rSide, dp, palette, isNeumorphic)

        sharedBtnIconPaint.color = palette.textPrimary
        drawPrevIcon(canvas, rx, ryPrev, rSide * 0.58f, sharedBtnIconPaint)
        if (isPlaying) drawPauseIcon(canvas, rx, ryPlay, rCenter * 0.60f, sharedBtnIconPaint)
        else drawPlayIcon(canvas, rx, ryPlay, rCenter * 0.60f, sharedBtnIconPaint)
        drawNextIcon(canvas, rx, ryNext, rSide * 0.58f, sharedBtnIconPaint)
    }
}
