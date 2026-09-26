package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.data.FolderConfig

/**
 * File-tab folder: complete rounded body + small top-left tab (mock-aligned).
 * Previews use the full interior like squircle; a light bottom sheet adds depth only.
 */
object FolderIconFileFolderDraw {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val clipPath = Path()

    fun draw(
        context: Context,
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        config: FolderConfig,
        density: Float,
        folderId: Long = -1L,
        drawPreview: () -> Unit
    ) {
        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return

        val plate = FolderIconSurfaceColor.plate(context, config)
        val outer = FolderIconShapeDraw.getShapePath(rect, radius, FolderShapeStyle.FILE_FOLDER)

        FolderIconPlateDraw.drawIconPlate(
            context, canvas, rect, radius, FolderShapeStyle.FILE_FOLDER, config, density, folderId
        )

        clipPath.reset()
        clipPath.set(outer)
        canvas.save()
        canvas.clipPath(clipPath)
        drawPreview()
        canvas.restore()

        drawTabSeam(canvas, rect, density, plate, op)
        drawBottomSheet(context, canvas, rect, radius, config, density, plate, op)
    }

    private fun drawTabSeam(canvas: Canvas, rect: RectF, density: Float, plate: FolderIconSurfaceColor.Plate, op: Float) {
        val bodyTop = rect.top + rect.height() * FolderIconShapeDraw.fileFolderTabHeightRatio()
        val tabRight = rect.left + rect.width() * 0.36f
        strokePaint.shader = null
        strokePaint.strokeWidth = 1f * density
        strokePaint.color = ColorUtils.setAlphaComponent(plate.stroke, (op * 0.55f * 255f).toInt().coerceIn(0, 255))
        canvas.drawLine(tabRight, bodyTop, rect.right - rect.width() * 0.06f, bodyTop, strokePaint)
    }

    private fun drawBottomSheet(
        context: Context,
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        config: FolderConfig,
        density: Float,
        plate: FolderIconSurfaceColor.Plate,
        op: Float
    ) {
        val pocket = Path()
        FolderIconShapeDraw.appendFileFolderFrontPocket(pocket, rect, radius)
        val flapTop = rect.top + rect.height() * FolderIconShapeDraw.fileFolderFlapTopRatio()

        var glassTop = ColorUtils.setAlphaComponent(plate.raised, (op * 0.34f * 255f).toInt().coerceIn(0, 255))
        var glassBottom = ColorUtils.setAlphaComponent(plate.fill, (op * 0.42f * 255f).toInt().coerceIn(0, 255))
        if (config.isExpressive) {
            val tint = FolderIconSurfaceColor.plate(context, config).fill
            glassTop = ColorUtils.blendARGB(glassTop, tint, 0.12f)
            glassBottom = ColorUtils.blendARGB(glassBottom, tint, 0.08f)
        }

        fillPaint.shader = LinearGradient(
            rect.left, flapTop, rect.left, rect.bottom,
            glassTop, glassBottom,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(pocket, fillPaint)
        fillPaint.shader = null

        strokePaint.shader = null
        strokePaint.strokeWidth = 1f * density
        strokePaint.color = ColorUtils.setAlphaComponent(plate.stroke, (op * 0.45f * 255f).toInt().coerceIn(0, 255))
        canvas.drawPath(pocket, strokePaint)

        val crease = ColorUtils.setAlphaComponent(Color.BLACK, (op * 0.10f * 255f).toInt().coerceIn(0, 255))
        strokePaint.color = crease
        val inset = rect.width() * 0.07f
        canvas.drawLine(rect.left + inset, flapTop, rect.right - inset, flapTop, strokePaint)
    }
}
