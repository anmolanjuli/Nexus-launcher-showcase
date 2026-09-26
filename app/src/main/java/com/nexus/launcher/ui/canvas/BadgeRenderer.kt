package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.nexus.launcher.theme.ColorBlindMode

class BadgeRenderer(private val density: Float) {

    private var customBadgeColor: Int? = null
    private var colorBlindMode: ColorBlindMode = ColorBlindMode.NONE

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE5484D.toInt()
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val tempPillRect = RectF()
    private val tempRect = Rect()

    fun setCustomBadgeColor(hex: String?) {
        customBadgeColor = if (!hex.isNullOrBlank()) {
            try {
                Color.parseColor(hex)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun setColorBlindMode(mode: ColorBlindMode) {
        colorBlindMode = mode
    }

    fun drawBadge(
        canvas: Canvas,
        iconRect: Rect,
        count: Int,
        style: Int,
        alpha: Int = 255
    ) {
        if (style == 0 || count == 0) return

        val baseColor = customBadgeColor ?: when (colorBlindMode) {
            ColorBlindMode.RED_GREEN -> 0xFFFF8C00.toInt()
            ColorBlindMode.BLUE_YELLOW -> 0xFFFFC107.toInt()
            ColorBlindMode.NONE -> 0xFFE5484D.toInt()
        }

        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        bgPaint.color = Color.argb(alpha, r, g, b)
        textPaint.alpha = alpha

        val dotRadius = 5f * density
        // Badge sits at top-right corner of icon
        val cx = iconRect.right.toFloat() - dotRadius * 0.5f
        val cy = iconRect.top.toFloat() + dotRadius * 0.5f

        if (style == 1) {
            canvas.drawCircle(cx, cy, dotRadius, bgPaint)
        } else {
            // Count style — pill shape
            val countStr = if (count > 99) "99+" else count.toString()
            val textWidth = textPaint.measureText(countStr)
            val pillW = (textWidth + 6f * density).coerceAtLeast(dotRadius * 2)
            val pillH = dotRadius * 2
            tempPillRect.set(
                cx - pillW / 2f, cy - pillH / 2f,
                cx + pillW / 2f, cy + pillH / 2f
            )
            canvas.drawRoundRect(tempPillRect, pillH / 2f, pillH / 2f, bgPaint)
            canvas.drawText(
                countStr,
                cx,
                cy + textPaint.textSize * 0.35f,
                textPaint
            )
        }
    }

    // Overload for RectF (drawer grid items)
    fun drawBadge(
        canvas: Canvas,
        iconRect: RectF,
        count: Int,
        style: Int,
        alpha: Int = 255
    ) {
        tempRect.set(
            iconRect.left.toInt(),
            iconRect.top.toInt(),
            iconRect.right.toInt(),
            iconRect.bottom.toInt()
        )
        drawBadge(canvas, tempRect, count, style, alpha)
    }
}
