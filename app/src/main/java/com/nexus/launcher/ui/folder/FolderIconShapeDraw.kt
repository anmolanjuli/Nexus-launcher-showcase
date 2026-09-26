package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.data.FolderConfig
import kotlin.math.min

object FolderIconShapeDraw {

    fun drawBackground(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        config: FolderConfig,
        bgPaint: Paint,
        density: Float,
        bounds: RectF? = null
    ) {
        val left = bounds?.left ?: (cx - folderRadius)
        val top = bounds?.top ?: (cy - folderRadius)
        val rect = bounds ?: RectF(left, top, cx + folderRadius, cy + folderRadius)
        
        // Draw Ambient Glow
        val originalAlpha = bgPaint.alpha
        val glowPaint = Paint(bgPaint).apply {
            val blurRadius = folderRadius * 0.4f
            if (blurRadius > 0f) {
                maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            alpha = (originalAlpha * 0.6f).toInt().coerceIn(0, 255)
        }
        // Offset shadow slightly down
        val glowCy = cy + folderRadius * 0.1f
        val glowRect = RectF(left, top + folderRadius * 0.1f, rect.right, rect.bottom + folderRadius * 0.1f)
        
        val shapeType = config.shapeStyle.coerceIn(0, 7)
        drawShape(canvas, cx, glowCy, folderRadius, glowRect, shapeType, glowPaint)
        drawShape(canvas, cx, cy, folderRadius, rect, shapeType, bgPaint)
    }

    fun drawShape(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        rect: RectF,
        shapeType: Int,
        paint: Paint
    ) {
        when (shapeType) {
            0 -> canvas.drawOval(rect, paint)
            1 -> canvas.drawRoundRect(rect, r * 0.4f, r * 0.4f, paint)
            2 -> canvas.drawRect(rect, paint)
            3 -> {
                val path = android.graphics.Path()
                val radii = floatArrayOf(
                    0f, 0f, 
                    r, r, 
                    r, r, 
                    r, r
                )
                path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
                canvas.drawPath(path, paint)
            }
            4 -> {
                val path = android.graphics.Path()
                val originalPathEffect = paint.pathEffect
                if (originalPathEffect is android.graphics.DashPathEffect) {
                    paint.pathEffect = android.graphics.ComposePathEffect(originalPathEffect, android.graphics.CornerPathEffect(r * 0.25f))
                } else {
                    paint.pathEffect = android.graphics.CornerPathEffect(r * 0.25f)
                }
                
                val rx = rect.width() / 2f
                val ry = rect.height() / 2f
                val cxCenter = rect.centerX()
                val cyCenter = rect.centerY()
                
                for (i in 0 until 6) {
                    val angle = Math.PI / 3 * i - Math.PI / 2
                    val px = cxCenter + (rx * 1.05f) * Math.cos(angle).toFloat()
                    val py = cyCenter + (ry * 1.05f) * Math.sin(angle).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, paint)
                paint.pathEffect = originalPathEffect
            }
            5 -> {
                val path = android.graphics.Path()
                val radii = floatArrayOf(
                    r * 0.7f, r * 0.7f, 
                    r * 0.3f, r * 0.3f, 
                    r * 0.6f, r * 0.6f, 
                    r * 0.4f, r * 0.4f
                )
                path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
                canvas.drawPath(path, paint)
            }
            6 -> { /* None: Do nothing */ }
            11 -> {
                val pillR = Math.min(rect.width(), rect.height()) / 2f
                canvas.drawRoundRect(rect, pillR, pillR, paint)
            }
            else -> canvas.drawCircle(cx, cy, r, paint)
        }
    }

    fun getShapePath(
        rect: RectF,
        r: Float,
        shapeType: Int
    ): android.graphics.Path {
        val path = android.graphics.Path()
        when (shapeType) {
            0 -> path.addOval(rect, android.graphics.Path.Direction.CW)
            1 -> path.addRoundRect(rect, r * 0.4f, r * 0.4f, android.graphics.Path.Direction.CW)
            2 -> path.addRect(rect, android.graphics.Path.Direction.CW)
            3 -> {
                val radii = floatArrayOf(
                    0f, 0f, 
                    r, r, 
                    r, r, 
                    r, r
                )
                path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
            }
            4 -> {
                val rx = rect.width() / 2f
                val ry = rect.height() / 2f
                val cxCenter = rect.centerX()
                val cyCenter = rect.centerY()
                
                for (i in 0 until 6) {
                    val angle = Math.PI / 3 * i - Math.PI / 2
                    val px = cxCenter + (rx * 1.05f) * Math.cos(angle).toFloat()
                    val py = cyCenter + (ry * 1.05f) * Math.sin(angle).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            5 -> {
                val radii = floatArrayOf(
                    r * 0.7f, r * 0.7f, 
                    r * 0.3f, r * 0.3f, 
                    r * 0.6f, r * 0.6f, 
                    r * 0.4f, r * 0.4f
                )
                path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
            }
            6 -> path.addCircle(rect.centerX(), rect.centerY(), r, android.graphics.Path.Direction.CW)
            11 -> {
                val pillR = Math.min(rect.width(), rect.height()) / 2f
                path.addRoundRect(rect, pillR, pillR, android.graphics.Path.Direction.CW)
            }
            // 3D Ball — same circular silhouette as Round; material/sphere shading is
            // applied by FolderIconRenderer / tile art, not by path geometry.
            7 -> path.addCircle(rect.centerX(), rect.centerY(), r, android.graphics.Path.Direction.CW)
            8 -> addAppPouchFlapSilhouette(path, rect, r)
            9 -> addLeatherWalletFlapSilhouette(path, rect, r)
            10 -> addFileFolderSilhouette(path, rect, r)
            13 -> path.addRoundRect(rect, r * 0.4f, r * 0.4f, android.graphics.Path.Direction.CW)
            else -> path.addCircle(rect.centerX(), rect.centerY(), r, android.graphics.Path.Direction.CW)
        }
        return path
    }

    /**
     * Outer footprint for pocket backing + content clip (round plate).
     * Distinct from [getShapePath] flap silhouettes used by highlight / morph punch-out.
     */
    fun getPocketFootprintPath(rect: RectF, r: Float, shapeStyle: Int): android.graphics.Path {
        val path = android.graphics.Path()
        val corner = when (shapeStyle) {
            9 -> r * 0.35f
            else -> r * 0.4f
        }
        path.addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW)
        return path
    }

    /** Morph reveal: starting corner radius so the card expands from the folder’s silhouette feel. */
    fun morphStartCornerRadius(shapeStyle: Int, iconSize: Float): Float = when (shapeStyle) {
        0, 7 -> iconSize / 2f
        1 -> iconSize * 0.20f
        2 -> iconSize * 0.04f
        3 -> iconSize * 0.28f
        4 -> iconSize * 0.12f
        5 -> iconSize * 0.22f
        6 -> iconSize * 0.18f
        8 -> iconSize * 0.20f
        9 -> iconSize * 0.175f
        10 -> iconSize * 0.14f
        13 -> iconSize * 0.20f
        else -> iconSize * 0.20f
    }

    /** Height of top-left tab rail above the flat body top. Synced with [addFileFolderSilhouette]. */
    fun fileFolderTabHeightRatio(): Float = 0.10f

    /** Bottom front-sheet overlay — icons render underneath across the full folder. */
    fun fileFolderFlapTopRatio(): Float = 0.68f

    fun fileFolderPreviewRadius(rect: RectF, @Suppress("UNUSED_PARAMETER") r: Float): Float =
        min(rect.width(), rect.height()) * 0.48f

    /** Inset well for Soft Capsule (shape 13) — tight rim like the mock. */
    fun softCapsuleWellBounds(plateRect: RectF): RectF {
        val insetX = plateRect.width() * 0.055f
        val insetTop = plateRect.height() * 0.065f
        val insetBottom = plateRect.height() * 0.055f
        return RectF(
            plateRect.left + insetX,
            plateRect.top + insetTop,
            plateRect.right - insetX,
            plateRect.bottom - insetBottom
        )
    }

    fun softCapsuleWellCornerRadius(plateRect: RectF, r: Float): Float {
        val well = softCapsuleWellBounds(plateRect)
        return min(well.width(), well.height()) * 0.26f
    }

    fun softCapsulePreviewRadius(plateRect: RectF, @Suppress("UNUSED_PARAMETER") r: Float): Float {
        val well = softCapsuleWellBounds(plateRect)
        return min(well.width(), well.height()) * 0.46f
    }

    fun appendFileFolderFrontPocket(path: Path, rect: RectF, r: Float) {
        val insetX = rect.width() * 0.055f
        val insetBottom = rect.height() * 0.045f
        val topEdge = rect.top + rect.height() * fileFolderFlapTopRatio()
        val corner = r * 0.28f
        path.reset()
        path.addRoundRect(
            RectF(
                rect.left + insetX,
                topEdge,
                rect.right - insetX,
                rect.bottom - insetBottom
            ),
            corner,
            corner,
            android.graphics.Path.Direction.CW
        )
    }

    /** @deprecated Use [appendFileFolderFrontPocket] — kept for pocket draw call sites during migration. */
    fun appendFileFolderFrontFlap(path: Path, rect: RectF, r: Float) {
        appendFileFolderFrontPocket(path, rect, r)
    }

    private fun addAppPouchFlapSilhouette(path: android.graphics.Path, rect: RectF, r: Float) {
        val corner = r * 0.4f
        path.moveTo(rect.left + corner, rect.bottom)
        path.lineTo(rect.left, rect.bottom - corner)
        path.lineTo(rect.left, rect.top + rect.height() * 0.4f)
        val tabStartX = rect.left
        val tabStartY = rect.top + rect.height() * 0.4f
        val dipX = rect.left + rect.width() * 0.35f
        val dipY = rect.top + rect.height() * 0.45f
        val tabEndX = rect.left + rect.width() * 0.5f
        val tabEndY = rect.top + rect.height() * 0.6f
        path.cubicTo(
            tabStartX + rect.width() * 0.1f, tabStartY,
            dipX - rect.width() * 0.1f, dipY,
            dipX, dipY
        )
        path.cubicTo(
            dipX + rect.width() * 0.1f, dipY,
            tabEndX - rect.width() * 0.1f, tabEndY,
            tabEndX, tabEndY
        )
        path.lineTo(rect.right - corner, tabEndY)
        path.quadTo(rect.right, tabEndY, rect.right, tabEndY + corner)
        path.lineTo(rect.right, rect.bottom - corner)
        path.quadTo(rect.right, rect.bottom, rect.right - corner, rect.bottom)
        path.lineTo(rect.left + corner, rect.bottom)
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - corner)
        path.close()
    }

    private fun addLeatherWalletFlapSilhouette(path: android.graphics.Path, rect: RectF, r: Float) {
        val corner = r * 0.35f
        val topEdge = rect.bottom - (rect.height() * 0.55f)
        path.moveTo(rect.left, rect.bottom - corner)
        path.lineTo(rect.left, topEdge + corner)
        path.quadTo(rect.left, topEdge, rect.left + corner, topEdge)
        val midX = rect.centerX()
        val dipY = topEdge + (r * 0.15f)
        path.quadTo(midX, dipY, rect.right - corner, topEdge)
        path.quadTo(rect.right, topEdge, rect.right, topEdge + corner)
        path.lineTo(rect.right, rect.bottom - corner)
        path.quadTo(rect.right, rect.bottom, rect.right - corner, rect.bottom)
        path.lineTo(rect.left + corner, rect.bottom)
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - corner)
        path.close()
    }

    private fun addFileFolderSilhouette(path: android.graphics.Path, rect: RectF, r: Float) {
        val w = rect.width()
        val h = rect.height()
        val corner = r * 0.26f
        val tabCorner = corner * 0.82f
        val bodyTop = rect.top + h * fileFolderTabHeightRatio()
        val tabTop = rect.top + h * 0.025f
        val tabRight = rect.left + w * 0.36f

        path.moveTo(rect.left + corner, rect.bottom)
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - corner)
        path.lineTo(rect.left, tabTop + tabCorner)
        path.quadTo(rect.left, tabTop, rect.left + tabCorner, tabTop)
        path.lineTo(tabRight - tabCorner, tabTop)
        path.quadTo(tabRight, tabTop, tabRight, tabTop + tabCorner)
        path.lineTo(tabRight, bodyTop)
        path.lineTo(rect.right - corner, bodyTop)
        path.quadTo(rect.right, bodyTop, rect.right, bodyTop + corner)
        path.lineTo(rect.right, rect.bottom - corner)
        path.quadTo(rect.right, rect.bottom, rect.right - corner, rect.bottom)
        path.close()
    }
}