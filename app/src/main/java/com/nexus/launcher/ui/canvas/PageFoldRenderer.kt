package com.nexus.launcher.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.nexus.launcher.ui.NexusDesignSystem

/**
 * Pure Gravity Fold drawing. No touch logic. All Paint/RectF allocated once.
 */
class PageFoldRenderer {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dimPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
    }
    private val creaseRect = RectF()
    private var lastCreaseKey = ""

    fun drawUnderlay(canvas: Canvas, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
    }

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        hingeX: Float,
        rotationAngle: Float,
        foldLeftPiece: Boolean,
        pageWidth: Int,
        pageHeight: Int,
        density: Float,
        accentColor: Int
    ) {
        if (bitmap.isRecycled) return
        val w = pageWidth.toFloat()
        val h = pageHeight.toFloat()
        val hinge = hingeX.coerceIn(1f, (pageWidth - 1).toFloat())
        val progress = (kotlin.math.abs(rotationAngle) / PageFoldGestureHandler.MAX_ANGLE)
            .coerceIn(0f, 1f)
        val signedAngle = if (foldLeftPiece) -rotationAngle else rotationAngle
        val pivotY = h / 2f
        val left = 0f
        val right = w
        val foldLeft = if (foldLeftPiece) left else hinge
        val foldRight = if (foldLeftPiece) hinge else right
        val stayLeft = if (foldLeftPiece) hinge else left
        val stayRight = if (foldLeftPiece) right else hinge

        canvas.save()
        canvas.clipRect(stayLeft, 0f, stayRight, h)
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        canvas.restore()
        drawCreaseShadow(canvas, hinge, foldLeftPiece, density, h, progress)

        canvas.save()
        PageFoldMatrixUtil.applyYRotation(canvas, hinge, pivotY, signedAngle, density)
        canvas.clipRect(foldLeft, 0f, foldRight, h)
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)
        val towardEdge = (kotlin.math.abs(rotationAngle) / 90f).coerceIn(0f, 1f)
        dimPaint.alpha = (towardEdge * 160f).toInt().coerceIn(0, 160)
        canvas.drawRect(foldLeft, 0f, foldRight, h, dimPaint)
        canvas.restore()

        rimPaint.color = accentColor
        rimPaint.strokeWidth = 1.5f * density
        rimPaint.alpha = (80f + progress * 100f).toInt().coerceIn(0, 180)
        canvas.drawLine(hinge, 0f, hinge, h, rimPaint)
    }

    private fun drawCreaseShadow(
        canvas: Canvas,
        hingeX: Float,
        foldLeftPiece: Boolean,
        density: Float,
        height: Float,
        progress: Float
    ) {
        val widthPx = 6f * density
        ensureCreaseShader(hingeX, foldLeftPiece, density, height)
        creasePaint.alpha = (90f + progress * 80f).toInt().coerceIn(90, 170)
        if (foldLeftPiece) {
            creaseRect.set(hingeX, 0f, hingeX + widthPx, height)
        } else {
            creaseRect.set(hingeX - widthPx, 0f, hingeX, height)
        }
        canvas.drawRect(creaseRect, creasePaint)
    }

    private fun ensureCreaseShader(
        hingeX: Float,
        foldLeftPiece: Boolean,
        density: Float,
        height: Float
    ) {
        val widthPx = 6f * density
        val key = "$hingeX|$foldLeftPiece|$height|$widthPx"
        if (key == lastCreaseKey) return
        lastCreaseKey = key
        val x0 = hingeX
        val x1 = if (foldLeftPiece) hingeX + widthPx else hingeX - widthPx
        creasePaint.shader = LinearGradient(
            x0, 0f, x1, 0f,
            Color.BLACK, Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }
}
