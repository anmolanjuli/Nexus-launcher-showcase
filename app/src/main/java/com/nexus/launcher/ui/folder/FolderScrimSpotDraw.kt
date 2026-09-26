package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** Frosted plate + spot art in scrim punch-outs. */
object FolderScrimSpotDraw {

    fun drawOpenCount(
        canvas: Canvas,
        bounds: FolderScrimHighlight.Bounds,
        appCount: Int,
        folderId: Int,
        density: Float
    ) {
        if (appCount <= 0) return
        drawPlate(canvas, bounds, density)
        drawAccentRing(canvas, bounds, density)

        val icon = FolderScrimHighlight.iconOnlyFrom(bounds)
        val cx = (icon.left + icon.right) / 2f
        val height = icon.bottom - icon.top
        val cyOffset = getCyOffset(icon.shapeStyle, height)
        val cy = (icon.top + icon.bottom) / 2f + cyOffset
        val half = (icon.right - icon.left) / 2f
        val accent = bounds.accentColor.takeIf { it != 0 } ?: FolderScrimHighlight.SILHOUETTE_FILL_ARGB
        val label = FolderCountEasterEgg.format(appCount, folderId)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = half * 0.52f
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, cx, textY, textPaint)
    }

    fun drawMenuGlyph(
        canvas: Canvas,
        bounds: FolderScrimHighlight.Bounds,
        folderId: Int,
        density: Float
    ) {
        drawPlate(canvas, bounds, density)
        drawAccentRing(canvas, bounds, density)
        FolderSpotArtDraw.drawMenuArt(canvas, bounds, folderId, density)
    }

    private fun drawAccentRing(
        canvas: Canvas,
        bounds: FolderScrimHighlight.Bounds,
        density: Float
    ) {
        val stroke = bounds.accentColor.takeIf { it != 0 }
            ?: FolderScrimHighlight.resolvedSilhouetteStrokeColor()
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = stroke
            strokeWidth = 2.5f * density
            alpha = 230
        }
        FolderScrimHighlight.drawAccentRing(canvas, bounds, 2.5f * density, ringPaint)
    }

    private fun drawPlate(
        canvas: Canvas,
        bounds: FolderScrimHighlight.Bounds,
        density: Float
    ) {
        val icon = FolderScrimHighlight.iconOnlyFrom(bounds)
        val cx = (icon.left + icon.right) / 2f
        val cy = (icon.top + icon.bottom) / 2f
        val half = (icon.right - icon.left) / 2f
        FolderSpotPlateDraw.draw(canvas, icon.shapeStyle, cx, cy, half, density)
    }

    private fun getCyOffset(shapeStyle: Int, height: Float): Float {
        return when (shapeStyle) {
            8 -> height * 0.2f // Flap starts at 40%, usable center is 70% (+20% offset)
            9 -> height * 0.225f // Flap starts at 45% (55% up from bottom), usable center is 72.5% (+22.5% offset)
            10 -> height * 0.175f // Flap starts at 35%, usable center is 67.5% (+17.5% offset)
            else -> 0f
        }
    }
}
