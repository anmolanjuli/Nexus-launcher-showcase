package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.nexus.launcher.data.FolderConfig

/** Glass depth shell drawn on home/dock folder icons. */
object FolderIconGlassDraw {

    fun drawGlassShell(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        config: FolderConfig,
        density: Float,
        bounds: RectF? = null
    ) {
        if (config.shapeStyle == 6) return
        val shape = config.shapeStyle.coerceIn(0, 7)
        val left = bounds?.left ?: (cx - radius)
        val top = bounds?.top ?: (cy - radius)
        val right = bounds?.right ?: (cx + radius)
        val bottom = bounds?.bottom ?: (cy + radius)
        val rect = bounds ?: RectF(left, top, right, bottom)

        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return
        val depthAlpha = (90 * op).toInt()
        val sheenAlpha = (70 * op).toInt()
        val rimAlpha = (120 * op).toInt()

        val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (shape == 7) {
                val base = FolderIconBallColor.resolve(context, config)
                val rim = FolderIconBallColor.shade(base, Color.BLACK, 0.45f)
                shader = RadialGradient(
                    cx, cy,
                    radius,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                        androidx.core.graphics.ColorUtils.setAlphaComponent(rim, (55 * op).toInt()),
                        androidx.core.graphics.ColorUtils.setAlphaComponent(rim, (110 * op).toInt())
                    ),
                    floatArrayOf(0f, 0.72f, 0.90f, 1f),
                    Shader.TileMode.CLAMP
                )
            } else if (shape == 0) {
                shader = RadialGradient(
                    cx - radius * 0.3f, cy - radius * 0.3f,
                    radius * 1.5f,
                    Color.argb(0, 0, 0, 0),
                    Color.argb(depthAlpha, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            } else {
                shader = LinearGradient(
                    left, top, left, bottom,
                    Color.argb(0, 0, 0, 0),
                    Color.argb(depthAlpha, 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
            }
        }
        FolderIconShapeDraw.drawShape(canvas, cx, cy, radius, rect, shape, depthPaint)

        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (shape == 7) {
                color = Color.TRANSPARENT
            } else if (shape == 0) {
                shader = RadialGradient(
                    cx - radius * 0.3f, cy - radius * 0.3f,
                    radius * 1.2f,
                    Color.argb(sheenAlpha, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            } else {
                shader = LinearGradient(
                    left, top, right, top,
                    Color.argb(sheenAlpha, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
        }
        FolderIconShapeDraw.drawShape(canvas, cx, cy, radius, rect, shape, sheenPaint)

        if (shape != 7 && rimAlpha > 0) {
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.argb(rimAlpha, 255, 255, 255)
                strokeWidth = 1f * density
            }
            FolderIconShapeDraw.drawShape(canvas, cx, cy, radius, rect, shape, rimPaint)
        }
    }
}
