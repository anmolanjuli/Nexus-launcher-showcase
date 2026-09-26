package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem

/** Preview icon layouts inside folder badges. */
object FolderIconPreviewDraw {

    fun drawPreview(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        previewStyle: Int,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float,
        density: Float,
        customPackage: String?,
        iconCache: Map<String, Drawable>,
        shapeType: Int,
        folderItem: HomeScreenItem,
        contentsCount: Int,
        showLabels: Boolean = true,
        applyShapeClip: Boolean = true,
        previewLayout: FolderIconPreviewLayout.Metrics? = null
    ) {
        val style = FolderPreviewOptions.drawStyle(previewStyle, folderItem.spanX, folderItem.spanY)
        val drawCx = previewLayout?.cx ?: cx
        val drawCy = previewLayout?.cy ?: cy
        val drawRadius = previewLayout?.contentRadius ?: folderRadius
        val radialRadius = previewLayout?.radialRadius ?: folderRadius

        canvas.save()
        // Shape 6 (None) must not circular-clip — that chops app-icon corners on a square grid.
        // Pocket callers pass applyShapeClip=false (already clipped to footprint).
        if (applyShapeClip && shapeType != 6) {
            if (previewLayout != null) {
                canvas.clipPath(
                    FolderIconShapeDraw.getShapePath(
                        previewLayout.clipBounds,
                        previewLayout.clipRadius,
                        previewLayout.clipShapeType
                    )
                )
            } else {
                val bounds = android.graphics.RectF(
                    cx - folderRadius, cy - folderRadius, cx + folderRadius, cy + folderRadius
                )
                canvas.clipPath(FolderIconShapeDraw.getShapePath(bounds, folderRadius, shapeType))
            }
        }

        if (style == 10 && !customPackage.isNullOrBlank()) {
            resolveIcon(context, customPackage, iconCache)?.let { d ->
                drawDominantIcon(canvas, drawCx, drawCy, d, drawRadius, density)
            }
            canvas.restore()
            return
        }
        if (icons.isEmpty()) {
            canvas.restore()
            return
        }
        when (style) {
            14 -> FolderIconSpecialPreviewDraw.drawSummaryCard(
                context, canvas, drawCx, drawCy, folderItem, icons, drawRadius, density, contentsCount, showLabels
            )
            // Fan and Hero + Orbit arrange radially, so they are budgeted against the largest
            // circle the shape affords rather than the grid's inscribed square. On a circular
            // plate that is 1.41x more room; on a hexagon 1.37x.
            13 -> drawHeroOrbit(canvas, drawCx, drawCy, icons, radialRadius, density)
            5 -> drawFanOfThree(canvas, drawCx, drawCy, icons, radialRadius, density)
            // Bento tiles a rectangle, so it takes the inscribed-square budget like Grid does.
            15 -> drawBento(canvas, drawCx, drawCy, icons, drawRadius, density)
            else -> FolderIconGridPreviewDraw.drawAdaptiveGrid(
                canvas, drawCx, drawCy, icons, drawRadius, density,
                folderItem.spanX, folderItem.spanY
            )
        }
        canvas.restore()
    }

    private fun resolveIcon(
        context: Context,
        packageName: String,
        iconCache: Map<String, Drawable>
    ): Drawable? {
        if (packageName.startsWith("/")) {
            val src = android.graphics.BitmapFactory.decodeFile(packageName)
            if (src != null) {
                val dm = context.resources.displayMetrics
                val iconSize = (96 * dm.density).toInt()
                val out = android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(out)
                val scale = maxOf(iconSize / src.width.toFloat(), iconSize / src.height.toFloat())
                val cropW = (iconSize / scale).toInt().coerceIn(1, src.width)
                val cropH = (iconSize / scale).toInt().coerceIn(1, src.height)
                val srcRect = android.graphics.Rect(
                    (src.width - cropW) / 2, (src.height - cropH) / 2,
                    (src.width + cropW) / 2, (src.height + cropH) / 2
                )
                canvas.drawBitmap(src, srcRect, android.graphics.RectF(0f, 0f, iconSize.toFloat(), iconSize.toFloat()), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                src.recycle()
                return android.graphics.drawable.BitmapDrawable(context.resources, out)
            }
        } else if (packageName.contains("::")) {
            val parts = packageName.split("::")
            if (parts.size == 2) {
                try {
                    val packContext = context.createPackageContext(parts[0], 0)
                    val resId = packContext.resources.getIdentifier(parts[1], "drawable", parts[0])
                    if (resId != 0) {
                        androidx.core.content.res.ResourcesCompat.getDrawable(packContext.resources, resId, null)?.let { return it }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FolderIconPreviewDraw", "Failed to load pack icon $packageName", e)
                }
            }
        }
        iconCache[packageName]?.constantState?.newDrawable()?.mutate()?.let { return it }
        return try {
            context.packageManager.getApplicationIcon(packageName).mutate()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Column/row of each small tile in the 3x3 unit grid, in reading order around the large
     * tile. Held as fields so the layout costs no allocation on the draw path.
     */
    private val bentoCols = intArrayOf(2, 2, 0, 1, 2)
    private val bentoRows = intArrayOf(0, 1, 2, 2, 2)

    /**
     * Bento: one 2x2 tile in the top-left of a 3x3 unit grid, with the remaining five 1x1 cells
     * wrapping it in an L.
     *
     * Unlike Fan and Hero + Orbit — which cluster toward the centre and leave the corners empty
     * by construction, however they are tuned — this tiles the fitted square exactly: every unit
     * of the budget carries an icon, edge to edge and with no gap between tiles. The 2:1 size
     * ratio is what separates it from Grid: Grid gives every app equal weight, Bento states which
     * one leads.
     *
     * Fewer than six icons simply leaves later cells of the L empty, the same way Grid leaves
     * trailing cells empty.
     */
    private fun drawBento(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float
    ) {
        if (icons.isEmpty()) return
        val unit = (folderRadius * 2f / 3f).coerceAtLeast(6f * density)
        val left = cx - unit * 1.5f
        val top = cy - unit * 1.5f
        drawBentoTile(canvas, icons[0].second, left, top, unit * 2f)
        val small = minOf(icons.size - 1, bentoCols.size)
        for (i in 0 until small) {
            drawBentoTile(
                canvas,
                icons[i + 1].second,
                left + bentoCols[i] * unit,
                top + bentoRows[i] * unit,
                unit
            )
        }
    }

    private fun drawBentoTile(canvas: Canvas, drawable: Drawable, x: Float, y: Float, size: Float) {
        val right = (x + size).toInt()
        val bottom = (y + size).toInt()
        val previousAlpha = drawable.alpha
        drawable.alpha = 255
        drawable.setBounds(x.toInt(), y.toInt(), right, bottom)
        drawable.draw(canvas)
        drawable.alpha = previousAlpha
    }

    private fun drawHeroOrbit(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float
    ) {
        // Satellites sit at -60/60/180 degrees. The hero is an axis-aligned square, so toward
        // +/-60 degrees it reaches (hero / 2) / cos(30) -- 1.155x its half-side, not 1x. At the
        // old 1.28R hero and 0.68R orbit that reach was 0.739R against a satellite inner edge of
        // 0.440R, burying each satellite 0.3R deep behind the hero: three slivers poking out from
        // under one big icon. These values clear the hero by 0.03R while keeping the outermost
        // satellite corner at 0.94R, inside the radius the shape actually affords.
        val hero = (folderRadius * 0.92f).coerceAtLeast(16f * density)
        val sat = (folderRadius * 0.38f).coerceAtLeast(10f * density)
        val orbit = folderRadius * 0.756f
        val satellites = icons.drop(1).take(3)
        satellites.forEachIndexed { index, (_, drawable) ->
            val angle = -Math.PI / 2 + (2 * Math.PI * index / 3.0) + Math.PI / 6
            val dx = orbit * Math.cos(angle).toFloat()
            val dy = orbit * Math.sin(angle).toFloat()
            val left = (cx + dx - sat / 2).toInt()
            val top = (cy + dy - sat / 2).toInt()
            val previousAlpha = drawable.alpha
            drawable.alpha = 210
            drawable.setBounds(left, top, left + sat.toInt(), top + sat.toInt())
            drawable.draw(canvas)
            drawable.alpha = previousAlpha
        }
        val main = icons.first().second
        main.setBounds(
            (cx - hero / 2).toInt(), (cy - hero / 2).toInt(),
            (cx + hero / 2).toInt(), (cy + hero / 2).toInt()
        )
        main.draw(canvas)
    }

    private fun drawFanOfThree(
        canvas: Canvas, cx: Float, cy: Float,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float
    ) {
        // A square of side s rotated by theta reaches (s/2)(cos+sin) from its own centre, so the
        // outer cards reached 0.28R + 1.22R * 0.606 = 1.019R -- just past the radius the shape
        // affords, which is why the outermost card corners came back clipped. Trimmed to 0.941R.
        // Side cards are also drawn slightly smaller than the centre one so the fan reads as
        // receding rather than as three cards of equal weight.
        val size = (folderRadius * 1.20f).coerceAtLeast(14f * density)
        val sideSize = size * 0.93f
        val count = icons.size.coerceAtMost(3)
        val angles = floatArrayOf(-14f, 0f, 14f)
        val offsetsX = floatArrayOf(-folderRadius * 0.263f, 0f, folderRadius * 0.263f)
        val offsetsY = floatArrayOf(folderRadius * 0.06f, -folderRadius * 0.04f, folderRadius * 0.06f)
        // Centre card last so it sits on top of both siblings.
        for (i in intArrayOf(0, 2, 1)) {
            if (i >= count) continue
            val drawable = icons[i].second
            val cardSize = if (i == 1) size else sideSize
            canvas.save()
            canvas.translate(cx + offsetsX[i], cy + offsetsY[i])
            canvas.rotate(angles[i])
            drawable.setBounds(
                (-cardSize / 2).toInt(), (-cardSize / 2).toInt(),
                (cardSize / 2).toInt(), (cardSize / 2).toInt()
            )
            val previousAlpha = drawable.alpha
            drawable.alpha = if (i == 1 || count == 1) 255 else 220
            drawable.draw(canvas)
            drawable.alpha = previousAlpha
            canvas.restore()
        }
    }

    private fun drawDominantIcon(
        canvas: Canvas, cx: Float, cy: Float,
        drawable: Drawable, folderRadius: Float, density: Float
    ) {
        val big = (folderRadius * 1.82f).coerceAtLeast(16f * density)
        drawable.setBounds(
            (cx - big / 2).toInt(), (cy - big / 2).toInt(),
            (cx + big / 2).toInt(), (cy + big / 2).toInt()
        )
        drawable.draw(canvas)
    }

}
