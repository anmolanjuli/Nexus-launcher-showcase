package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderConfigCodec
import com.nexus.launcher.ui.folder.FolderIconRenderer
import com.nexus.launcher.ui.folder.FolderIconShapeDraw
import com.nexus.launcher.ui.folder.FolderShapeStyle

/**
 * Handles rendering of home screen folder items (itemType == 1), including open morph animations,
 * folder glow effects, drag-merge target highlights, and badges with zero heap allocations.
 */
internal object HomeScreenFolderItemDraw {

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val tempFolderRectF = RectF()
    private val tempBadgeRect = Rect()

    private var cachedGlowRadius = -1f
    private var cachedGlowFilter: BlurMaskFilter? = null

    fun drawFolderItem(
        context: Context,
        canvas: Canvas,
        item: HomeScreenItem,
        cell: RectF,
        iconRect: Rect,
        alpha: Int,
        alphaPaint: Paint,
        contentsMap: Map<Long, List<HomeScreenItem>>,
        iconCache: Map<String, Drawable>,
        folderIconRenderer: FolderIconRenderer,
        density: Float,
        showLabels: Boolean,
        badgeCounts: Map<String, Int>,
        badgeStyleFolder: Int,
        badgeRenderer: BadgeRenderer?,
        hiddenFolderItemId: Int?,
        folderMorphProgress: Float,
        folderGlowId: Long?,
        folderGlowColor: Int,
        folderGlowAlpha: Float,
        hoveredMergeTargetId: Int?,
        mergeBounceScale: Float
    ) {
        if (item.id == hiddenFolderItemId && folderMorphProgress >= 1f) return
        val contents = contentsMap[item.resolveFolderContentsId()] ?: emptyList()
        val morphingOpen = item.id == hiddenFolderItemId && folderMorphProgress in 0f..1f

        val iconLeft = iconRect.left
        val iconTop = iconRect.top
        val iconRight = iconRect.right
        val iconBottom = iconRect.bottom

        if (morphingOpen) {
            val fadeAlpha = ((1f - folderMorphProgress) * alpha).toInt().coerceIn(0, 255)
            alphaPaint.alpha = fadeAlpha
            canvas.save()
            var cx = cell.centerX()
            val cy = iconTop + iconRect.height() / 2f
            val scale = 1f + folderMorphProgress * 0.08f

            if (scale > 1f) {
                val displayWidth = context.resources.displayMetrics.widthPixels.toFloat()
                val maxCx = (iconRect.left * scale) / (scale - 1f)
                val minCx = (iconRect.right * scale - displayWidth) / (scale - 1f)
                cx = cx.coerceIn(minCx, maxCx)
            }

            canvas.scale(scale, scale, cx, cy)
        }

        if (item.id.toLong() == folderGlowId && folderGlowAlpha > 0f) {
            val blurRadius = 20f * density
            if (cachedGlowFilter == null || cachedGlowRadius != blurRadius) {
                cachedGlowRadius = blurRadius
                cachedGlowFilter = BlurMaskFilter(blurRadius.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            glowPaint.color = folderGlowColor
            glowPaint.alpha = (folderGlowAlpha * 180).toInt()
            glowPaint.maskFilter = cachedGlowFilter

            val cx = cell.centerX()
            val cy = iconTop + iconRect.height() / 2f

            val folderConfig = FolderConfigCodec.parse(item.folderConfigJson)
            val shapeStyle = FolderShapeStyle.normalize(folderConfig.shapeStyle)
            val sx = if (folderConfig.flipHorizontal) -1f else 1f
            val sy = if (folderConfig.flipVertical) -1f else 1f

            canvas.save()
            canvas.scale(1.25f * sx, 1.25f * sy, cx, cy)
            tempFolderRectF.set(iconLeft.toFloat(), iconTop.toFloat(), iconRight.toFloat(), iconBottom.toFloat())
            val shapePath = FolderIconShapeDraw.getShapePath(
                tempFolderRectF,
                iconRect.width() / 2f,
                shapeStyle
            )
            canvas.drawPath(shapePath, glowPaint)
            canvas.restore()
        }

        val isMergeTarget = hoveredMergeTargetId != null && item.id == hoveredMergeTargetId
        if (isMergeTarget) {
            val cx = cell.centerX()
            val cy = iconTop + iconRect.height() / 2f
            highlightPaint.color = Color.WHITE
            highlightPaint.alpha = (255 * ((mergeBounceScale - 1.0f) / 0.15f).coerceIn(0f, 1f)).toInt()
            highlightPaint.strokeWidth = 3f * density

            val folderConfig = FolderConfigCodec.parse(item.folderConfigJson)
            val shapeStyle = FolderShapeStyle.normalize(folderConfig.shapeStyle)
            val flipHorizontal = folderConfig.flipHorizontal
            val flipVertical = folderConfig.flipVertical
            val sx = if (flipHorizontal) -1f else 1f
            val sy = if (flipVertical) -1f else 1f

            if (sx != 1f || sy != 1f) {
                canvas.save()
                canvas.scale(sx, sy, cx, cy)
            }
            tempFolderRectF.set(iconLeft.toFloat(), iconTop.toFloat(), iconRight.toFloat(), iconBottom.toFloat())
            val shapePath = FolderIconShapeDraw.getShapePath(
                tempFolderRectF,
                iconRect.width() / 2f,
                shapeStyle
            )
            canvas.drawPath(shapePath, highlightPaint)
            if (sx != 1f || sy != 1f) {
                canvas.restore()
            }

            canvas.save()
            canvas.scale(mergeBounceScale, mergeBounceScale, cx, cy)
        }

        tempFolderRectF.set(iconRect)
        folderIconRenderer.drawFolder(
            canvas, cell.centerX(), iconTop + iconRect.height() / 2f,
            item, contents, iconCache, density,
            minOf(iconRect.width(), iconRect.height()) / 2f,
            tempFolderRectF, showLabels, matchAppIconSize = true
        )
        if (isMergeTarget) canvas.restore()

        val folderBadgeCount = contents.sumOf { badgeCounts[it.packageName] ?: 0 }
        if (folderBadgeCount > 0) {
            tempBadgeRect.set(iconLeft, iconTop, iconRight, iconBottom)
            badgeRenderer?.drawBadge(
                canvas, tempBadgeRect, folderBadgeCount, badgeStyleFolder, alphaPaint.alpha
            )
        }
        if (morphingOpen) canvas.restore()
    }
}
