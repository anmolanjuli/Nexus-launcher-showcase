package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import kotlin.math.hypot
import kotlin.math.min

/** Shared neutral brackets + span pill for folder/icon resize overlays. */
object ResizeOverlayChrome {

    /** Dashed outline + solid bracket colors derived from theme contrast (dark on light, light on dark). */
    fun chromeStrokeColors(tokens: NexusColorTokens): Pair<Int, Int> {
        val base = tokens.textPrimary
        return ColorUtils.setAlphaComponent(base, 0x80) to ColorUtils.setAlphaComponent(base, 0xCC)
    }

    fun chromeStrokeColors(context: Context): Pair<Int, Int> = chromeStrokeColors(
        try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    )

    fun createDashedPaint(density: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f * density, 10f * density), 0f)
    }

    fun createBracketPaint(density: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun createPillPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6111111")
        style = Paint.Style.FILL
    }

    fun createTextPaint(density: Float) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    fun drawBrackets(canvas: Canvas, rectF: RectF, density: Float, paint: Paint, edgePadding: Float = 0f) {
        val bracketLen = 22f * density
        val l = rectF.left + edgePadding
        val t = rectF.top + edgePadding
        val r = rectF.right - edgePadding
        val b = rectF.bottom - edgePadding
        canvas.drawLine(l, t + bracketLen, l, t, paint)
        canvas.drawLine(l, t, l + bracketLen, t, paint)
        canvas.drawLine(r, t + bracketLen, r, t, paint)
        canvas.drawLine(r, t, r - bracketLen, t, paint)
        canvas.drawLine(l, b - bracketLen, l, b, paint)
        canvas.drawLine(l, b, l + bracketLen, b, paint)
        canvas.drawLine(r, b - bracketLen, r, b, paint)
        canvas.drawLine(r, b, r - bracketLen, b, paint)
    }

    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /**
     * Corner grab zones sit on the item boundary and extend inward along both edges so
     * resize still works when a corner sits against the screen edge.
     */
    fun hitTestCorner(
        x: Float,
        y: Float,
        rect: RectF,
        density: Float,
        outwardSlop: Float = 10f * density,
        inwardReach: Float = 56f * density
    ): Corner? {
        if (rect.isEmpty) return null
        val reach = inwardReach.coerceAtMost(min(rect.width(), rect.height()) * 0.48f)
        val out = outwardSlop

        fun zoneAt(cornerX: Float, cornerY: Float): Boolean {
            val xIn = if (cornerX <= rect.centerX()) {
                x >= cornerX - out && x <= cornerX + reach
            } else {
                x >= cornerX - reach && x <= cornerX + out
            }
            val yIn = if (cornerY <= rect.centerY()) {
                y >= cornerY - out && y <= cornerY + reach
            } else {
                y >= cornerY - reach && y <= cornerY + out
            }
            return xIn && yIn
        }

        val hits = mutableListOf<Pair<Corner, Double>>()
        if (zoneAt(rect.left, rect.top)) {
            hits += Corner.TOP_LEFT to hypot((x - rect.left).toDouble(), (y - rect.top).toDouble())
        }
        if (zoneAt(rect.right, rect.top)) {
            hits += Corner.TOP_RIGHT to hypot((x - rect.right).toDouble(), (y - rect.top).toDouble())
        }
        if (zoneAt(rect.left, rect.bottom)) {
            hits += Corner.BOTTOM_LEFT to hypot((x - rect.left).toDouble(), (y - rect.bottom).toDouble())
        }
        if (zoneAt(rect.right, rect.bottom)) {
            hits += Corner.BOTTOM_RIGHT to hypot((x - rect.right).toDouble(), (y - rect.bottom).toDouble())
        }
        return hits.minByOrNull { it.second }?.first
    }

    fun drawSpanPill(
        canvas: Canvas,
        rectF: RectF,
        spanX: Int,
        spanY: Int,
        density: Float,
        pillPaint: Paint,
        textPaint: TextPaint
    ) {
        val text = "$spanX × $spanY"
        val textWidth = textPaint.measureText(text)
        val paddingX = 12f * density
        val paddingY = 6f * density
        val pillW = textWidth + paddingX * 2
        val pillH = textPaint.textSize + paddingY * 2
        val pillCx = rectF.centerX()
        val pillCy = rectF.bottom + 24f * density + pillH / 2f
        val pillRect = RectF(
            pillCx - pillW / 2f, pillCy - pillH / 2f,
            pillCx + pillW / 2f, pillCy + pillH / 2f
        )
        canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillPaint)
        val baseline = pillCy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, pillCx, baseline, textPaint)
    }
}
