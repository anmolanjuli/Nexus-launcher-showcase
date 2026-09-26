package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem

/** Legacy shapes 8 (app pouch) and 9 (leather wallet) — not in the shape picker. */
object FolderIconLegacyWalletDraw {

    fun draw(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        adjustedBounds: RectF?,
        adjustedRadius: Float,
        config: FolderConfig,
        density: Float,
        folderItem: HomeScreenItem,
        contents: List<HomeScreenItem>,
        drawStyle: Int,
        resolvedIcons: List<Pair<HomeScreenItem, Drawable>>,
        iconCache: Map<String, Drawable>,
        showLabels: Boolean,
        shapeStyle: Int,
        drawPreview: (
            canvas: Canvas, cx: Float, cy: Float, previewRadius: Float,
            drawStyle: Int, resolvedIcons: List<Pair<HomeScreenItem, Drawable>>,
            showLabels: Boolean, applyShapeClip: Boolean
        ) -> Unit,
        drawPlaceholder: (Canvas, Float, Float) -> Unit
    ) {
        require(shapeStyle == 8 || shapeStyle == 9) { "Legacy pocket renderer supports shapes 8 and 9 only" }
        canvas.save()
        val clipRect = adjustedBounds ?: RectF(cx - adjustedRadius, cy - adjustedRadius, cx + adjustedRadius, cy + adjustedRadius)
        canvas.clipPath(FolderIconShapeDraw.getPocketFootprintPath(clipRect, adjustedRadius, shapeStyle))

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FolderIconBallColor.resolve(context, config.copy(backgroundOpacity = 1f))
            style = Paint.Style.FILL
        }
        FolderIconPocketDraw.drawBacking(
            canvas, cx, cy, adjustedRadius, config, bgPaint, density, adjustedBounds, shapeStyle
        )

        if (resolvedIcons.isEmpty()) {
            drawPlaceholder(canvas, cx, cy)
        } else if (drawStyle != 14 && drawStyle != 10) {
            drawPreview(canvas, cx, cy, adjustedRadius, drawStyle, resolvedIcons, showLabels, false)
        }


        if (shapeStyle == 8) {
            FolderIconPocketDraw.drawFrontFlap(context, canvas, cx, cy, adjustedRadius, config, density, adjustedBounds, null)
        } else {
            FolderIconPocketDraw.drawLeatherWalletFlap(context, canvas, cx, cy, adjustedRadius, config, density, adjustedBounds, null)
        }

        if (resolvedIcons.isNotEmpty() && (drawStyle == 14 || drawStyle == 10)) {
            val rect = clipRect
            val flapTopY = if (shapeStyle == 8) {
                rect.top + rect.height() * 0.6f
            } else {
                (cy - adjustedRadius) + (2 * adjustedRadius * 0.45f)
            }
            val flapBottomY = cy + adjustedRadius
            val safeCy = (flapTopY + flapBottomY) / 2f
            val safeRadius = (flapBottomY - flapTopY) / 2f
            drawPreview(canvas, cx, safeCy, safeRadius, drawStyle, resolvedIcons, showLabels, true)
        }

        canvas.restore()
    }
}
