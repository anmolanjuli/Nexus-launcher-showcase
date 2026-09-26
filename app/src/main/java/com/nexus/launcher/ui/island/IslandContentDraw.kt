package com.nexus.launcher.ui.island

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import kotlin.math.sin

object IslandContentDraw {

    fun drawDormant(canvas: Canvas, pill: RectF, accentPaint: Paint) {
        canvas.drawCircle(pill.centerX(), pill.centerY(), pill.height() * 0.18f, accentPaint)
    }

    fun drawCompact(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        glyphPaint: Paint,
        titlePaint: TextPaint,
        density: Float,
        contentAlpha: Float,
        zones: IslandCompactZones.Zones = IslandCompactZones.Zones(),
    ) {
        val pad = 10f * density
        val glyph = (pill.height() * 0.48f).coerceIn(11f * density, 16f * density)
        val cx = pill.left + pad + glyph / 2f
        val cy = pill.centerY()
        glyphPaint.color = tokens.accent
        glyphPaint.alpha = (contentAlpha * 255f).toInt()
        IslandGlyphs.draw(canvas, payload.kind, cx, cy, glyph, glyphPaint)

        titlePaint.color = android.graphics.Color.WHITE
        titlePaint.alpha = (contentAlpha * 255f).toInt()
        titlePaint.textSize = (pill.height() * 0.40f).coerceIn(10f * density, 13f * density)
        val textBaseline = cy - (titlePaint.ascent() + titlePaint.descent()) / 2f

        if (zones.split) {
            drawAroundCutout(canvas, payload, zones, glyph, textBaseline, cy, glyphPaint, titlePaint, density)
            return
        }

        if (payload.kind == IslandKind.CALL) {
            IslandCallDraw.drawCompact(canvas, pill, payload, tokens, glyphPaint, titlePaint, density, contentAlpha)
            return
        }

        var rightEdge = pill.right - pad
        when (payload.kind) {
            IslandKind.MUSIC -> {
                if (payload.playing) {
                    drawWave(canvas, pill.right - pad - 10f * density, cy, density, tokens.accent, contentAlpha)
                    rightEdge = pill.right - pad - 24f * density
                }
            }
            IslandKind.CHARGING, IslandKind.BATTERY_LOW -> {
                val label = payload.subtitle.takeIf { it.isNotEmpty() } ?: payload.title
                val tw = titlePaint.measureText(label)
                if (tw + pad * 2f <= pill.width() / 2f) {
                    canvas.drawText(label, pill.right - pad - tw, textBaseline, titlePaint)
                    rightEdge = pill.right - pad - tw - 6f * density
                }
            }
            IslandKind.TIMER, IslandKind.STOPWATCH -> {
                val time = payload.subtitle.takeIf { it.isNotEmpty() } ?: payload.title
                val tw = titlePaint.measureText(time)
                if (tw + pad * 2f <= pill.width() / 2f) {
                    canvas.drawText(time, pill.right - pad - tw, textBaseline, titlePaint)
                    rightEdge = pill.right - pad - tw - 6f * density
                }
            }
            else -> Unit
        }

        // If user configured a wide capsule (>= 170dp), render title text between left and right indicators
        if (pill.width() >= 170f * density) {
            val textLeft = cx + glyph / 2f + 8f * density
            val avail = (rightEdge - textLeft).coerceAtLeast(0f)
            if (avail > 24f * density) {
                val text = TextUtils.ellipsize(payload.title, titlePaint, avail, TextUtils.TruncateAt.END)
                canvas.drawText(text.toString(), textLeft, textBaseline, titlePaint)
            }
        }
    }

    /**
     * The camera is inside the capsule: the glyph goes to its left, one short line to its
     * right, and a side too narrow for either is simply left empty.
     */
    private fun drawAroundCutout(
        canvas: Canvas,
        payload: IslandPayload,
        zones: IslandCompactZones.Zones,
        glyph: Float,
        baseline: Float,
        cy: Float,
        glyphPaint: Paint,
        titlePaint: TextPaint,
        density: Float,
    ) {
        if (zones.leftWidth >= glyph) {
            IslandGlyphs.draw(canvas, payload.kind, zones.leftEnd - glyph / 2f, cy, glyph, glyphPaint)
        }
        val line = payload.subtitle.takeIf { it.isNotEmpty() } ?: payload.title
        if (zones.rightWidth < 18f * density || line.isEmpty()) return
        val text = TextUtils.ellipsize(line, titlePaint, zones.rightWidth, TextUtils.TruncateAt.END)
        canvas.drawText(text.toString(), zones.rightStart, baseline, titlePaint)
    }

    fun drawExpanded(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        glyphPaint: Paint,
        titlePaint: TextPaint,
        subPaint: TextPaint,
        fillPaint: Paint,
        density: Float,
        contentAlpha: Float,
        playRect: RectF,
        prevRect: RectF,
        nextRect: RectF,
        actionRect: RectF,
        artRect: RectF,
        context: android.content.Context,
    ) {
        val pad = 10f * density
        val alpha = (contentAlpha * 255f).toInt()
        titlePaint.alpha = alpha
        subPaint.alpha = alpha
        glyphPaint.alpha = alpha
        titlePaint.color = android.graphics.Color.WHITE
        subPaint.color = android.graphics.Color.WHITE
        subPaint.alpha = (contentAlpha * 180f).toInt()
        glyphPaint.color = tokens.accent

        when (payload.kind) {
            IslandKind.CALL -> IslandCallDraw.drawExpanded(
                canvas, pill, payload, tokens, titlePaint, subPaint, density, pad, prevRect, nextRect, context,
            )
            IslandKind.MUSIC -> drawMusic(
                canvas, pill, payload, tokens, titlePaint, subPaint, fillPaint, glyphPaint,
                density, pad, playRect, prevRect, nextRect, artRect,
            )
            IslandKind.TIMER, IslandKind.STOPWATCH -> drawTimer(
                canvas, pill, payload, tokens, glyphPaint, titlePaint, fillPaint, subPaint,
                density, pad, actionRect, context,
            )
            IslandKind.CALENDAR -> drawCalendar(
                canvas, pill, payload, tokens, glyphPaint, titlePaint, fillPaint, subPaint,
                density, pad, actionRect, context,
            )
            else -> drawCompact(
                canvas, pill, payload, tokens, glyphPaint, titlePaint, density, contentAlpha,
            )
        }
    }

    private fun drawMusic(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        titlePaint: TextPaint,
        subPaint: TextPaint,
        fillPaint: Paint,
        iconPaint: Paint,
        density: Float,
        pad: Float,
        playRect: RectF,
        prevRect: RectF,
        nextRect: RectF,
        artRect: RectF,
    ) {
        val btn = 40f * density
        val gap = 18f * density
        val rowTop = pill.bottom - pad - btn
        val total = btn * 3f + gap * 2f
        var x = pill.centerX() - total / 2f
        prevRect.set(x, rowTop, x + btn, rowTop + btn)
        x += btn + gap
        playRect.set(x, rowTop, x + btn, rowTop + btn)
        x += btn + gap
        nextRect.set(x, rowTop, x + btn, rowTop + btn)

        val art = (rowTop - pill.top - pad * 2f).coerceIn(36f * density, 48f * density)
        artRect.set(pill.left + pad, pill.top + pad, pill.left + pad + art, pill.top + pad + art)
        val bmp = payload.art
        if (bmp != null && !bmp.isRecycled) {
            fillPaint.alpha = titlePaint.alpha
            canvas.drawBitmap(bmp, null, artRect, fillPaint)
        } else {
            fillPaint.color = tokens.accentMuted
            fillPaint.alpha = titlePaint.alpha
            canvas.drawRoundRect(artRect, 8f * density, 8f * density, fillPaint)
        }
        val textLeft = artRect.right + 8f * density
        val textRight = pill.right - pad
        val avail = (textRight - textLeft).coerceAtLeast(0f)
        val title = TextUtils.ellipsize(payload.title, titlePaint, avail, TextUtils.TruncateAt.END)
        val titleY = artRect.top + art * 0.38f - (titlePaint.ascent() + titlePaint.descent()) / 2f
        canvas.drawText(title.toString(), textLeft, titleY, titlePaint)
        if (payload.subtitle.isNotEmpty()) {
            val sub = TextUtils.ellipsize(payload.subtitle, subPaint, avail, TextUtils.TruncateAt.END)
            canvas.drawText(sub.toString(), textLeft, artRect.top + art * 0.78f, subPaint)
        }
        drawTransportButton(
            canvas, prevRect, "prev",
            bgColor = tokens.surfaceRaised,
            iconColor = tokens.textPrimary,
            strokeColor = tokens.divider,
            density = density,
            alpha = titlePaint.alpha,
        )
        // The card is black whatever the theme, so the accent fill read as a stray coloured
        // blob; play is the larger, brighter button instead.
        drawTransportButton(
            canvas, playRect, if (payload.playing) "pause" else "play",
            bgColor = tokens.surfaceRaised,
            iconColor = tokens.textPrimary,
            strokeColor = tokens.divider,
            density = density,
            alpha = titlePaint.alpha,
        )
        drawTransportButton(
            canvas, nextRect, "next",
            bgColor = tokens.surfaceRaised,
            iconColor = tokens.textPrimary,
            strokeColor = tokens.divider,
            density = density,
            alpha = titlePaint.alpha,
        )
    }

    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val btnStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val btnIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawTransportButton(
        canvas: Canvas,
        rect: RectF,
        symbol: String,
        bgColor: Int,
        iconColor: Int,
        strokeColor: Int?,
        density: Float,
        alpha: Int,
    ) {
        btnBgPaint.color = bgColor
        btnBgPaint.alpha = alpha
        canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, btnBgPaint)

        if (strokeColor != null) {
            btnStrokePaint.color = strokeColor
            btnStrokePaint.alpha = alpha
            btnStrokePaint.strokeWidth = 1f * density
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, btnStrokePaint)
        }

        btnIconPaint.color = iconColor
        btnIconPaint.alpha = alpha
        IslandGlyphs.drawTransport(canvas, symbol, rect.centerX(), rect.centerY(), rect.width() * 0.46f, btnIconPaint)
    }

    private fun drawTimer(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        glyphPaint: Paint,
        titlePaint: TextPaint,
        fillPaint: Paint,
        subPaint: TextPaint,
        density: Float,
        pad: Float,
        actionRect: RectF,
        context: android.content.Context,
    ) {
        IslandGlyphs.draw(canvas, payload.kind, pill.left + pad + 10f * density, pill.centerY(), 18f * density, glyphPaint)
        val chipW = 72f * density
        actionRect.set(pill.right - pad - chipW, pill.centerY() - 14f * density, pill.right - pad, pill.centerY() + 14f * density)
        fillPaint.color = tokens.accentMuted
        canvas.drawRoundRect(actionRect, actionRect.height() / 2f, actionRect.height() / 2f, fillPaint)
        subPaint.color = tokens.accent
        subPaint.textAlign = Paint.Align.CENTER
        val label = if (payload.kind == IslandKind.TIMER) {
            context.getString(R.string.island_cancel)
        } else {
            context.getString(R.string.island_pause)
        }
        canvas.drawText(label, actionRect.centerX(), actionRect.centerY() - (subPaint.ascent() + subPaint.descent()) / 2f, subPaint)
        subPaint.textAlign = Paint.Align.LEFT
        titlePaint.textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 18f, context.resources.displayMetrics,
        )
        canvas.drawText(payload.title, pill.left + pad + 28f * density, pill.centerY() - (titlePaint.ascent() + titlePaint.descent()) / 2f, titlePaint)
    }

    private fun drawCalendar(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        glyphPaint: Paint,
        titlePaint: TextPaint,
        fillPaint: Paint,
        subPaint: TextPaint,
        density: Float,
        pad: Float,
        actionRect: RectF,
        context: android.content.Context,
    ) {
        IslandGlyphs.draw(canvas, payload.kind, pill.left + pad + 10f * density, pill.centerY(), 18f * density, glyphPaint)
        val chipW = 64f * density
        actionRect.set(pill.right - pad - chipW, pill.centerY() - 14f * density, pill.right - pad, pill.centerY() + 14f * density)
        fillPaint.color = tokens.accentMuted
        canvas.drawRoundRect(actionRect, actionRect.height() / 2f, actionRect.height() / 2f, fillPaint)
        subPaint.color = tokens.accent
        subPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            context.getString(R.string.island_join),
            actionRect.centerX(),
            actionRect.centerY() - (subPaint.ascent() + subPaint.descent()) / 2f,
            subPaint,
        )
        subPaint.textAlign = Paint.Align.LEFT
        val avail = actionRect.left - pill.left - pad - 28f * density
        val title = TextUtils.ellipsize(payload.title, titlePaint, avail, TextUtils.TruncateAt.END)
        canvas.drawText(title.toString(), pill.left + pad + 28f * density, pill.centerY() - (titlePaint.ascent() + titlePaint.descent()) / 2f, titlePaint)
    }

    private fun drawWave(canvas: Canvas, right: Float, cy: Float, density: Float, color: Int, alpha: Float) {
        val paint = wavePaint
        paint.color = color
        paint.alpha = (alpha * 255f).toInt()
        val t = SystemClock.uptimeMillis() / 140f
        for (i in 0 until 5) {
            val h = (4f + 7f * kotlin.math.abs(sin(t + i * 0.7f))) * density
            val x = right - i * 3.4f * density
            canvas.drawRoundRect(x, cy - h / 2f, x + 2f * density, cy + h / 2f, density, density, paint)
        }
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
}
