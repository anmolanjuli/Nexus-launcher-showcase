package com.nexus.launcher.ui.widgets.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Procedural weather glyph drawing helper for [NexusWeatherRenderer].
 * Supports crisp strokes, dynamic colors, and glass text-legibility drop shadows.
 */
object NexusWeatherIconDraw {

    fun drawWeatherIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        code: Int,
        color: Int,
        isGlass: Boolean,
        isLight: Boolean,
        dp: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * (size / 12f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            if (isGlass) {
                val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
            }
        }
        val path = Path()

        when (code) {
            0, 1 -> { // Clear / Mostly Clear
                path.addCircle(cx, cy, size * 0.6f, Path.Direction.CW)
                for (i in 0 until 8) {
                    val angle = Math.PI * 2 * i / 8
                    val x1 = cx + kotlin.math.cos(angle).toFloat() * size * 0.8f
                    val y1 = cy + kotlin.math.sin(angle).toFloat() * size * 0.8f
                    val x2 = cx + kotlin.math.cos(angle).toFloat() * size * 1.1f
                    val y2 = cy + kotlin.math.sin(angle).toFloat() * size * 1.1f
                    path.moveTo(x1, y1)
                    path.lineTo(x2, y2)
                }
            }
            2, 3, 45, 48 -> { // Cloudy / Fog
                path.moveTo(cx - size * 0.3f, cy + size * 0.3f)
                path.lineTo(cx + size * 0.5f, cy + size * 0.3f)
                path.addArc(RectF(cx + size * 0.1f, cy - size * 0.5f, cx + size * 0.9f, cy + size * 0.3f), 0f, -180f)
                path.addArc(RectF(cx - size * 0.6f, cy - size * 0.1f, cx + size * 0.2f, cy + size * 0.3f), 180f, 180f)
            }
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> { // Rain
                // Cloud
                path.moveTo(cx - size * 0.3f, cy)
                path.lineTo(cx + size * 0.5f, cy)
                path.addArc(RectF(cx + size * 0.1f, cy - size * 0.8f, cx + size * 0.9f, cy), 0f, -180f)
                path.addArc(RectF(cx - size * 0.6f, cy - size * 0.4f, cx + size * 0.2f, cy), 180f, 180f)
                // Rain drops
                path.moveTo(cx - size * 0.2f, cy + size * 0.4f)
                path.lineTo(cx - size * 0.4f, cy + size * 0.8f)
                path.moveTo(cx + size * 0.2f, cy + size * 0.4f)
                path.lineTo(cx, cy + size * 0.8f)
                path.moveTo(cx + size * 0.6f, cy + size * 0.4f)
                path.lineTo(cx + size * 0.4f, cy + size * 0.8f)
            }
            71, 73, 75, 77 -> { // Snow
                for (i in 0 until 6) {
                    val angle = Math.PI * 2 * i / 6
                    val x2 = cx + kotlin.math.cos(angle).toFloat() * size
                    val y2 = cy + kotlin.math.sin(angle).toFloat() * size
                    path.moveTo(cx, cy)
                    path.lineTo(x2, y2)
                }
            }
            95, 96, 99 -> { // Thunderstorm
                path.moveTo(cx + size * 0.2f, cy - size * 0.8f)
                path.lineTo(cx - size * 0.4f, cy + size * 0.1f)
                path.lineTo(cx + size * 0.1f, cy + size * 0.1f)
                path.lineTo(cx - size * 0.2f, cy + size * 0.8f)
                path.lineTo(cx + size * 0.5f, cy)
                path.lineTo(cx, cy)
                path.close()
            }
            else -> { // Unknown - draw circle
                path.addCircle(cx, cy, size * 0.5f, Path.Direction.CW)
            }
        }
        canvas.drawPath(path, paint)
    }
}
