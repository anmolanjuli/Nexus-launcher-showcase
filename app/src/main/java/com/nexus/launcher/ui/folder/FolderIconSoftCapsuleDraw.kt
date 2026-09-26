package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/**
 * Soft Capsule — thick squircle shell with debossed inner well (mock-aligned).
 * Outer chrome uses the same plate path + neumorphic margin as widgets.
 */
object FolderIconSoftCapsuleDraw {

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

        val plateRect = FolderIconPlateDraw.cardBounds(rect, density, config)
        val plateR = FolderIconPlateDraw.cardRadius(radius, density, config)
        FolderIconPlateDraw.drawIconPlate(
            context, canvas, rect, radius, FolderShapeStyle.SOFT_CAPSULE, config, density, folderId
        )

        val well = FolderIconShapeDraw.softCapsuleWellBounds(plateRect)
        val wellR = FolderIconShapeDraw.softCapsuleWellCornerRadius(plateRect, plateR)
        if (FolderIconPlateDraw.isNeumorphic(config)) {
            canvas.save()
            val layerPaint = android.graphics.Paint().apply {
                alpha = (op * 255f).toInt().coerceIn(0, 255)
            }
            val layer = canvas.saveLayer(rect.left, rect.top, rect.right, rect.bottom, layerPaint)
            val palette = NexusNeumorphicDraw.resolvePalette(context, config.themeMode)
            NexusNeumorphicDraw.drawDebossedWell(canvas, well, wellR, palette, density)
            canvas.restoreToCount(layer)
            canvas.restore()
        }

        clipPath.reset()
        clipPath.addRoundRect(well, wellR, wellR, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        drawPreview()
        canvas.restore()
    }
}
