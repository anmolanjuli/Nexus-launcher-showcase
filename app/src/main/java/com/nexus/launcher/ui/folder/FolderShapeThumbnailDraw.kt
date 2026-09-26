package com.nexus.launcher.ui.folder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.theme.NexusColorTokens

/** Bitmap previews for folder-shape carousel tiles. */
object FolderShapeThumbnailDraw {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clipPath = Path()

    fun render(
        shapeStyle: Int,
        sizePx: Int,
        selected: Boolean,
        tokens: NexusColorTokens,
        density: Float
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val inset = 1f * density
        val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)
        val r = rect.width() / 2f
        val primary = tokens.textPrimary
        val cr = Color.red(primary)
        val cg = Color.green(primary)
        val cb = Color.blue(primary)

        fillPaint.shader = null
        fillPaint.color = Color.argb(if (selected) 0x30 else 0x18, cr, cg, cb)
        strokePaint.color = Color.argb(if (selected) 0xCC else 0x66, cr, cg, cb)
        strokePaint.strokeWidth = if (selected) 1.4f * density else 1f * density
        dotPaint.color = Color.argb(if (selected) 0x65 else 0x40, cr, cg, cb)

        when (shapeStyle) {
            6 -> drawNonePreview(canvas, rect, r)
            7 -> drawBallPreview(canvas, rect, r)
            8, 9 -> drawPocketTile(canvas, rect, r, shapeStyle)
            FolderShapeStyle.FILE_FOLDER -> drawFileTabTile(canvas, rect, r, density)
            FolderShapeStyle.SOFT_CAPSULE -> drawSoftCapsuleTile(canvas, rect, r, density)
            else -> drawClassicPlateTile(canvas, rect, r, shapeStyle)
        }
        return bmp
    }

    private fun drawClassicPlateTile(canvas: Canvas, rect: RectF, r: Float, shapeStyle: Int) {
        val path = FolderIconShapeDraw.getShapePath(rect, r, shapeStyle)
        canvas.drawPath(path, fillPaint)
        clipPath.reset()
        clipPath.set(path)
        canvas.save()
        canvas.clipPath(clipPath)
        drawMiniGrid(canvas, rect)
        canvas.restore()
        canvas.drawPath(path, strokePaint)
    }

    private fun drawMiniGrid(canvas: Canvas, rect: RectF) {
        val gapX = rect.width() * 0.22f
        val gapY = rect.height() * 0.22f
        val dotR = rect.width() * 0.055f
        val cx = rect.centerX()
        val cy = rect.centerY()
        listOf(
            cx - gapX to cy - gapY,
            cx + gapX to cy - gapY,
            cx - gapX to cy + gapY,
            cx + gapX to cy + gapY
        ).forEach { (px, py) -> canvas.drawCircle(px, py, dotR, dotPaint) }
    }

    private fun drawNonePreview(canvas: Canvas, rect: RectF, r: Float) {
        canvas.drawCircle(rect.centerX(), rect.centerY(), r * 0.82f, strokePaint)
        canvas.drawLine(
            rect.left + r * 0.32f, rect.bottom - r * 0.32f,
            rect.right - r * 0.32f, rect.top + r * 0.32f,
            strokePaint
        )
    }

    private fun drawBallPreview(canvas: Canvas, rect: RectF, r: Float) {
        canvas.drawCircle(rect.centerX(), rect.centerY(), r * 0.9f, fillPaint)
        canvas.drawCircle(rect.centerX(), rect.centerY(), r * 0.9f, strokePaint)
    }

    private fun drawFileTabTile(canvas: Canvas, rect: RectF, r: Float, density: Float) {
        val path = FolderIconShapeDraw.getShapePath(rect, r, FolderShapeStyle.FILE_FOLDER)
        canvas.drawPath(path, fillPaint)
        clipPath.reset()
        clipPath.set(path)
        canvas.save()
        canvas.clipPath(clipPath)
        drawMiniGrid(canvas, rect)
        canvas.restore()
        canvas.drawPath(path, strokePaint)
        val bodyTop = rect.top + rect.height() * FolderIconShapeDraw.fileFolderTabHeightRatio()
        val tabRight = rect.left + rect.width() * 0.36f
        canvas.drawLine(tabRight, bodyTop, rect.right - r * 0.2f, bodyTop, strokePaint)
        val flapY = rect.top + rect.height() * FolderIconShapeDraw.fileFolderFlapTopRatio()
        canvas.drawLine(rect.left + r * 0.2f, flapY, rect.right - r * 0.2f, flapY, strokePaint)
    }

    private fun drawSoftCapsuleTile(canvas: Canvas, rect: RectF, r: Float, density: Float) {
        val outer = FolderIconShapeDraw.getShapePath(rect, r, FolderShapeStyle.SOFT_CAPSULE)
        canvas.drawPath(outer, fillPaint)
        canvas.drawPath(outer, strokePaint)
        val margin = 5f * density
        val plate = RectF(rect.left + margin, rect.top + margin, rect.right - margin, rect.bottom - margin)
        val well = FolderIconShapeDraw.softCapsuleWellBounds(plate)
        val wellR = FolderIconShapeDraw.softCapsuleWellCornerRadius(plate, r)
        canvas.drawRoundRect(well, wellR, wellR, strokePaint)
        clipPath.reset()
        clipPath.addRoundRect(well, wellR, wellR, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        drawMiniGrid(canvas, well)
        canvas.restore()
    }

    private fun drawPocketTile(canvas: Canvas, rect: RectF, r: Float, shapeStyle: Int) {
        val path = FolderIconShapeDraw.getShapePath(rect, r, shapeStyle)
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
        val flapY = when (shapeStyle) {
            9 -> rect.bottom - rect.height() * 0.55f
            else -> rect.top + rect.height() * 0.4f
        }
        canvas.drawLine(rect.left + r * 0.25f, flapY, rect.right - r * 0.25f, flapY, strokePaint)
    }
}
