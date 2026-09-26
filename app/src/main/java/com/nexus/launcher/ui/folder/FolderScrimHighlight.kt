package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.data.FolderConfig

/** Shape-aware dim punch-out for folder scrim / context menu. */
object FolderScrimHighlight {

    /** Neutral silhouette stroke — folders do not use theme accent on chrome. */
    const val SILHOUETTE_STROKE_ARGB: Int = 0xCCFFFFFF.toInt()

    const val SILHOUETTE_FILL_ARGB: Int = 0xE6FFFFFF.toInt()

    fun resolvedSilhouetteStrokeColor(): Int = SILHOUETTE_STROKE_ARGB

    fun resolvedAccentColor(): Int = resolvedSilhouetteStrokeColor()

    data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val shapeStyle: Int,
        val showAccentRing: Boolean = false,
        val accentColor: Int = 0,
        val flipHorizontal: Boolean = false,
        val flipVertical: Boolean = false,
        val isGeneric: Boolean = false
    )

    fun boundsForConfig(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        config: FolderConfig
    ): Bounds = Bounds(
        left,
        top,
        right,
        bottom,
        shapeStyle(config),
        config.showAccentRing,
        resolvedSilhouetteStrokeColor(),
        config.flipHorizontal,
        config.flipVertical
    )

    /** Icon-only square bounds centered on the icon — excludes label text below. */
    fun iconOnlyFrom(bounds: Bounds): Bounds {
        val iconSize = bounds.right - bounds.left
        val half = iconSize / 2f
        val cx = bounds.left + half
        val cy = bounds.top + half
        return Bounds(cx - half, cy - half, cx + half, cy + half, bounds.shapeStyle, bounds.showAccentRing, bounds.accentColor, bounds.flipHorizontal, bounds.flipVertical)
    }

    fun iconOnlyFrom(centerX: Float, centerY: Float, iconSizePx: Float, shapeStyle: Int): Bounds {
        val half = iconSizePx / 2f
        return Bounds(
            centerX - half,
            centerY - half,
            centerX + half,
            centerY + half,
            shapeStyle
        )
    }

    fun punchOut(canvas: Canvas, bounds: Bounds, padPx: Float, clearPaint: Paint, density: Float) {
        val icon = iconOnlyFrom(bounds)
        val rect = RectF(
            icon.left - padPx,
            icon.top - padPx,
            icon.right + padPx,
            icon.bottom + padPx
        )
        drawShapeClear(canvas, rect, icon, clearPaint)
        val strokeColor = bounds.accentColor.takeIf { it != 0 } ?: resolvedSilhouetteStrokeColor()
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = strokeColor
            strokeWidth = 3f * density
            alpha = 204
        }
        drawAccentRing(canvas, bounds, 3f * density, ringPaint)
    }

    fun clipPath(bounds: Bounds, padPx: Float): Path {
        val icon = iconOnlyFrom(bounds)
        val rect = RectF(
            icon.left - padPx,
            icon.top - padPx,
            icon.right + padPx,
            icon.bottom + padPx
        )
        if (bounds.isGeneric) {
            val path = Path()
            // Inflate the rect by 15% to safely swallow sharp Teardrop corners and Hexagon extrusions,
            // without growing as massively as the bounding circle.
            val rx = rect.width() / 2f
            val ry = rect.height() / 2f
            val safeRect = RectF(rect).apply { inset(-rx * 0.15f, -ry * 0.15f) }
            val r = Math.max(safeRect.width(), safeRect.height()) / 2f
            path.addRoundRect(safeRect, r * 0.4f, r * 0.4f, Path.Direction.CW)
            return path
        }
        val r = rect.width() / 2f
        val path = FolderIconShapeDraw.getShapePath(rect, r, icon.shapeStyle)
        
        val sx = if (icon.flipHorizontal) -1f else 1f
        val sy = if (icon.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            val matrix = android.graphics.Matrix()
            matrix.setScale(sx, sy, cx, cy)
            path.transform(matrix)
        }
        
        return path
    }

    fun drawAccentRing(
        canvas: Canvas,
        bounds: Bounds,
        strokePx: Float,
        ringPaint: Paint
    ) {
        val icon = iconOnlyFrom(bounds)
        val cx = (icon.left + icon.right) / 2f
        val cy = (icon.top + icon.bottom) / 2f
        val r = (icon.right - icon.left) / 2f + strokePx / 2f
        
        val rect = RectF(cx - r, cy - r, cx + r, cy + r)
        
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = strokePx
        
        if (bounds.isGeneric) {
            val rx = rect.width() / 2f
            val ry = rect.height() / 2f
            val safeRect = RectF(rect).apply { inset(-rx * 0.15f, -ry * 0.15f) }
            val safeR = Math.max(safeRect.width(), safeRect.height()) / 2f
            canvas.drawRoundRect(safeRect, safeR * 0.4f, safeR * 0.4f, ringPaint)
            return
        }
        
        val sx = if (icon.flipHorizontal) -1f else 1f
        val sy = if (icon.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            canvas.save()
            canvas.scale(sx, sy, cx, cy)
        }
        
        val path = FolderIconShapeDraw.getShapePath(rect, r, icon.shapeStyle)
        canvas.drawPath(path, ringPaint)
        
        if (sx != 1f || sy != 1f) {
            canvas.restore()
        }
    }

    fun shapeStyle(config: FolderConfig): Int = FolderShapeStyle.scrimShape(config)

    private fun drawShapeClear(canvas: Canvas, rect: RectF, bounds: Bounds, clearPaint: Paint) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        val sx = if (bounds.flipHorizontal) -1f else 1f
        val sy = if (bounds.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            canvas.save()
            canvas.scale(sx, sy, cx, cy)
        }
        
        val path = FolderIconShapeDraw.getShapePath(rect, r, bounds.shapeStyle)
        canvas.drawPath(path, clearPaint)
        
        if (sx != 1f || sy != 1f) {
            canvas.restore()
        }
    }

}
