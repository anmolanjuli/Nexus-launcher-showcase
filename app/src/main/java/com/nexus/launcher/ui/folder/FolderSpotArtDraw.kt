package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Bold spot art for folder punch-outs — collection, grid, hub, orbit. */
object FolderSpotArtDraw {

    fun drawMenuArt(
        canvas: Canvas,
        bounds: FolderScrimHighlight.Bounds,
        folderId: Int,
        density: Float
    ) {
        val icon = FolderScrimHighlight.iconOnlyFrom(bounds)
        val cx = (icon.left + icon.right) / 2f
        
        val height = icon.bottom - icon.top
        val cyOffset = when (icon.shapeStyle) {
            8 -> height * 0.2f
            9 -> height * 0.225f
            10 -> height * 0.175f
            else -> 0f
        }
        val cy = (icon.top + icon.bottom) / 2f + cyOffset
        
        val half = (icon.right - icon.left) / 2f
        val accent = bounds.accentColor.takeIf { it != 0 } ?: FolderScrimHighlight.SILHOUETTE_FILL_ARGB
        when ((folderId % 4 + 4) % 4) {
            0 -> drawStackedFolders(canvas, cx, cy, half, accent, density)
            1 -> drawAppGrid(canvas, cx, cy, half, accent, density)
            2 -> drawHubBurst(canvas, cx, cy, half, accent, density)
            else -> drawOrbitHub(canvas, cx, cy, half, accent, density)
        }
    }

    private fun drawStackedFolders(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        half: Float,
        accent: Int,
        density: Float
    ) {
        val w = half * 1.05f
        val h = half * 0.82f
        val backPaint = fillPaint(Color.argb(120, 255, 255, 255))
        val frontPaint = fillPaint(accent)
        val stroke = strokePaint(accent, 2f * density)
        val back1 = RectF(cx - w * 0.42f, cy - h * 0.55f, cx + w * 0.42f, cy + h * 0.2f)
        val back2 = RectF(cx - w * 0.46f, cy - h * 0.62f, cx + w * 0.38f, cy + h * 0.12f)
        val r = h * 0.18f
        canvas.drawRoundRect(back2, r, r, backPaint)
        canvas.drawRoundRect(back1, r, r, backPaint)
        val front = RectF(cx - w * 0.5f, cy - h * 0.35f, cx + w * 0.5f, cy + h * 0.45f)
        canvas.drawRoundRect(front, r * 1.1f, r * 1.1f, frontPaint)
        canvas.drawRoundRect(front, r * 1.1f, r * 1.1f, stroke)
    }

    private fun drawAppGrid(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        half: Float,
        accent: Int,
        density: Float
    ) {
        val cell = half * 0.34f
        val gap = 3f * density
        val stroke = strokePaint(accent, 1.5f * density)
        val fill = fillPaint(Color.argb(200, Color.red(accent), Color.green(accent), Color.blue(accent)))
        for (row in 0..1) {
            for (col in 0..1) {
                val left = cx - cell - gap / 2f + col * (cell + gap)
                val top = cy - cell - gap / 2f + row * (cell + gap)
                val rect = RectF(left, top, left + cell, top + cell)
                canvas.drawRoundRect(rect, 4f * density, 4f * density, fill)
                canvas.drawRoundRect(rect, 4f * density, 4f * density, stroke)
            }
        }
    }

    private fun drawHubBurst(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        half: Float,
        accent: Int,
        density: Float
    ) {
        val rayPaint = strokePaint(accent, 2.5f * density)
        val corePaint = fillPaint(accent)
        val inner = half * 0.22f
        canvas.drawCircle(cx, cy, inner, corePaint)
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val x1 = cx + (inner + 2f * density) * kotlin.math.cos(angle).toFloat()
            val y1 = cy + (inner + 2f * density) * kotlin.math.sin(angle).toFloat()
            val x2 = cx + half * 0.72f * kotlin.math.cos(angle).toFloat()
            val y2 = cy + half * 0.72f * kotlin.math.sin(angle).toFloat()
            canvas.drawLine(x1, y1, x2, y2, rayPaint)
        }
    }

    private fun drawOrbitHub(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        half: Float,
        accent: Int,
        density: Float
    ) {
        val ringPaint = strokePaint(accent, 2f * density)
        val dotPaint = fillPaint(accent)
        val orbitR = half * 0.58f
        canvas.drawCircle(cx, cy, orbitR, ringPaint)
        canvas.drawCircle(cx, cy, half * 0.16f, dotPaint)
        for (i in 0 until 4) {
            val angle = Math.toRadians((i * 90 + 45).toDouble())
            val dx = cx + orbitR * kotlin.math.cos(angle).toFloat()
            val dy = cy + orbitR * kotlin.math.sin(angle).toFloat()
            canvas.drawCircle(dx, dy, half * 0.1f, dotPaint)
        }
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = width
    }
}
