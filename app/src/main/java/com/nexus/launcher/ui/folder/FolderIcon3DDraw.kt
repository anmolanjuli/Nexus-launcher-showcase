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

/**
 * Shared 3D depth treatment for folder icons.
 */
object FolderIcon3DDraw {

    fun drawShadow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        config: FolderConfig,
        density: Float,
        bounds: RectF? = null
    ) {
        if (config.shapeStyle == 6) return
        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return
        
        val left = bounds?.left ?: (cx - folderRadius)
        val top = bounds?.top ?: (cy - folderRadius)
        val right = bounds?.right ?: (cx + folderRadius)
        val bottom = bounds?.bottom ?: (cy + folderRadius)
        val rect = bounds ?: RectF(left, top, right, bottom)
        val shapeType = config.shapeStyle.coerceIn(0, 7)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val shadowStrength = 0.40f * op
            color = Color.argb((255 * shadowStrength).toInt().coerceIn(0, 255), 0, 0, 0)
            maskFilter = android.graphics.BlurMaskFilter(8f * density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        val shadowRect = RectF(left, top + 2f * density, right, bottom + 2f * density)
        val shadowPath = FolderIconShapeDraw.getShapePath(shadowRect, folderRadius, shapeType)
        canvas.drawPath(shadowPath, shadowPaint)
    }

    fun drawCardFillAndBorder(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        config: FolderConfig,
        density: Float,
        bounds: RectF? = null
    ) {
        if (config.shapeStyle == 6) return
        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return
        
        val left = bounds?.left ?: (cx - folderRadius)
        val top = bounds?.top ?: (cy - folderRadius)
        val right = bounds?.right ?: (cx + folderRadius)
        val bottom = bounds?.bottom ?: (cy + folderRadius)
        val rect = bounds ?: RectF(left, top, right, bottom)
        val shapeType = config.shapeStyle.coerceIn(0, 7)

        val shapePath = FolderIconShapeDraw.getShapePath(rect, folderRadius, shapeType)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            val base = FolderIconBallColor.resolve(context, config)
            val light = FolderIconBallColor.shade(base, Color.WHITE, 0.32f)
            val dark = FolderIconBallColor.shade(base, Color.BLACK, 0.30f)
            shader = RadialGradient(
                cx - rect.width() * 0.22f,
                cy - rect.height() * 0.26f,
                rect.width() * 0.88f,
                intArrayOf(light, base, dark),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(shapePath, fillPaint)

        val borderAlpha = (255 * 0.14f * op).toInt().coerceIn(0, 255)
        if (borderAlpha > 0 && shapeType != 7) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f * density
                color = Color.argb(borderAlpha, 255, 255, 255)
            }
            if (shapeType == 4) {
                borderPaint.pathEffect = android.graphics.CornerPathEffect(folderRadius * 0.25f)
            }
            canvas.drawPath(shapePath, borderPaint)
        }
    }

    fun drawTopGlint(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        config: FolderConfig,
        density: Float,
        bounds: RectF? = null
    ) {
        if (config.shapeStyle == 6 || config.shapeStyle == 7) return
        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return
        
        val left = bounds?.left ?: (cx - folderRadius)
        val top = bounds?.top ?: (cy - folderRadius)
        val right = bounds?.right ?: (cx + folderRadius)
        val bottom = bounds?.bottom ?: (cy + folderRadius)
        val rect = bounds ?: RectF(left, top, right, bottom)
        val shapeType = config.shapeStyle.coerceIn(0, 7)

        val shapePath = FolderIconShapeDraw.getShapePath(rect, folderRadius, shapeType)

        canvas.save()
        canvas.clipPath(shapePath)

        val glintHeight = rect.height() * 0.35f
        val glintAlpha = (255 * 0.14f * op).toInt().coerceIn(0, 255)
        val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (shapeType == 0) {
                shader = RadialGradient(
                    cx, top,
                    rect.width() * 0.7f,
                    Color.argb(glintAlpha, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            } else {
                shader = LinearGradient(
                    left, top, left, top + glintHeight,
                    Color.argb(glintAlpha, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
            }
        }
        
        val glintRect = RectF(
            left - rect.width() * 0.2f, 
            top, 
            right + rect.width() * 0.2f, 
            top + glintHeight * 2f
        )
        canvas.drawOval(glintRect, glintPaint)
        canvas.restore()
    }
}
