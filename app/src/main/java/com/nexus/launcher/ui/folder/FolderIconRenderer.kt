package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem

class FolderIconRenderer(private val context: Context) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#55FFFFFF")
        style = Paint.Style.FILL
    }
    private val configCache = android.util.LruCache<Int, Pair<String, com.nexus.launcher.data.FolderConfig>>(50)
    private val iconPreviewCache = android.util.LruCache<String, android.graphics.Bitmap>(50)
    private val folderResolvedIconsCache = android.util.LruCache<String, List<Pair<HomeScreenItem, Drawable>>>(50)

    private fun getConfig(item: HomeScreenItem): com.nexus.launcher.data.FolderConfig {
        val json = item.folderConfigJson.ifBlank { "{}" }
        val cached = configCache.get(item.id)
        if (cached != null && cached.first == json) return cached.second
        val config = FolderConfigCodec.parse(json)
        configCache.put(item.id, json to config)
        return config
    }

    fun evictConfig(itemId: Int) {
        configCache.remove(itemId)
        folderResolvedIconsCache.evictAll()
    }

    /**
     * Drops everything derived from the icon source — resolved [Drawable]s and the pre-rendered
     * preview bitmaps built from them. Must be called whenever the icon pack, icon shape or
     * theming changes.
     *
     * Both caches are keyed on identity only — folder id, slot count, and the package names in
     * the preview — so switching icon pack does not change any key and the old pack's drawables
     * stay valid forever. The tell was that only *rearranging* a folder fixed it: the key is
     * order-sensitive, so a move produced an unseen key and forced a fresh resolve, while moving
     * an app back to where it started hit the pre-existing stale entry again.
     *
     * Drawer folder previews never showed this because `DrawerFolderTileCache` constructs a new
     * [FolderIconRenderer] per draw, so its caches are always empty; the home screen keeps one
     * instance for the life of the process.
     */
    fun evictIconCaches() {
        folderResolvedIconsCache.evictAll()
        iconPreviewCache.evictAll()
    }

    fun drawFolder(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderItem: HomeScreenItem,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        density: Float,
        folderRadiusOverride: Float? = null,
        folderBoundsOverride: android.graphics.RectF? = null,
        showLabels: Boolean = true,
        /** When true, fill the icon rect like an app icon (no optical shrink). Used for drawer tiles. */
        matchAppIconSize: Boolean = false,
        /**
         * Set when [canvas] belongs to a standalone preview widget (e.g. the folder edit
         * sheet's mini icon preview) rather than the real home-screen canvas — lets the Glass
         * mode wallpaper blur sample live wallpaper from behind the preview's own screen
         * position instead of computing a meaningless position from the home canvas.
         */
        previewHostView: android.view.View? = null
    ) {
        val folderRadius = folderRadiusOverride ?: (28 * density)
        val config = getConfig(folderItem)
        val shapeStyle = FolderShapeStyle.normalize(config.shapeStyle)
        // Keep a safe optical inset so shadows and shape strokes are never clipped at bitmap boundary.
        val opticalInset = if (matchAppIconSize) {
            0f
        } else when {
            shapeStyle == 6 -> folderRadius * 0.08f
            shapeStyle == FolderShapeStyle.FILE_FOLDER -> folderRadius * 0.10f
            else -> folderRadius * 0.10f
        }
        val adjustedBounds = folderBoundsOverride?.let {
            android.graphics.RectF(it.left + opticalInset, it.top + opticalInset, it.right - opticalInset, it.bottom - opticalInset)
        }
        val adjustedRadius = (folderRadius - opticalInset).coerceAtLeast(1f)

        val drawStyle = FolderPreviewOptions.drawStyle(config.previewStyle, folderItem.spanX, folderItem.spanY)
        val iconsOnly = false
        
        val takeCount = when (drawStyle) {
            14 -> 8
            15 -> 6 // Bento: one large tile plus the five-cell L around it
            12 -> 6
            5, 4, 11 -> 3
            13 -> 4
            2 -> 1
            3 -> 4
            else -> FolderIconGridPreviewDraw.gridSlotCount(
                contents.size, folderItem.spanX, folderItem.spanY
            )
        }
        var contentsHash = 1
        val checkCount = minOf(contents.size, takeCount)
        for (i in 0 until checkCount) {
            contentsHash = 31 * contentsHash + contents[i].packageName.hashCode()
        }
        val iconsKey = "${folderItem.id}_${takeCount}_$contentsHash"
        val resolvedIcons = folderResolvedIconsCache.get(iconsKey) ?: run {
            val list = contents.take(takeCount).mapNotNull { item ->
                resolveIcon(item, iconCache)?.let { item to it }
            }
            folderResolvedIconsCache.put(iconsKey, list)
            list
        }

        val sx = if (config.flipHorizontal) -1f else 1f
        val sy = if (config.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            canvas.save()
            canvas.scale(sx, sy, cx, cy)
        }

        if (shapeStyle == FolderShapeStyle.FILE_FOLDER) {
            drawFileTabFolder(canvas, cx, cy, adjustedBounds, adjustedRadius, config, density, folderItem, contents, drawStyle, resolvedIcons, iconCache, showLabels)
        } else if (shapeStyle == FolderShapeStyle.SOFT_CAPSULE) {
            drawSoftCapsuleFolder(canvas, cx, cy, adjustedBounds, adjustedRadius, config, density, folderItem, contents, drawStyle, resolvedIcons, iconCache, showLabels)
        } else if (shapeStyle == 8 || shapeStyle == 9) {
            FolderIconLegacyWalletDraw.draw(
                context, canvas, cx, cy, adjustedBounds, adjustedRadius, config, density,
                folderItem, contents, drawStyle, resolvedIcons, iconCache, showLabels,
                shapeStyle = shapeStyle,
                drawPreview = { c, pcx, pcy, pr, ds, icons, labels, clip ->
                    if (ds == 14) {
                        FolderIconSpecialPreviewDraw.drawSummaryCard(context, c, pcx, pcy, folderItem, icons, pr, density, contents.size, labels)
                    } else {
                        FolderIconPreviewDraw.drawPreview(
                            context, c, pcx, pcy, config.previewStyle, icons,
                            pr, density, config.customIconPackage, iconCache,
                            shapeStyle, folderItem, contents.size, labels, applyShapeClip = clip
                        )
                    }
                },
                drawPlaceholder = { c, pcx, pcy -> drawPlaceholderGrid(c, pcx, pcy, density, config) }
            )
        } else { // Classic Layering (Bottom to Top)
            if (!iconsOnly && shapeStyle != 6) {
                val plateRect = adjustedBounds ?: android.graphics.RectF(
                    cx - adjustedRadius, cy - adjustedRadius, cx + adjustedRadius, cy + adjustedRadius
                )
                if (!FolderIconPlateDraw.drawIconPlate(
                        context, canvas, plateRect, adjustedRadius, shapeStyle, config, density, folderItem.id.toLong(),
                        previewHostView
                    )
                ) {
                    FolderIcon3DDraw.drawShadow(canvas, cx, cy, adjustedRadius, config, density, adjustedBounds)
                    FolderIcon3DDraw.drawCardFillAndBorder(context, canvas, cx, cy, adjustedRadius, config, density, adjustedBounds)
                    FolderIconGlassDraw.drawGlassShell(context, canvas, cx, cy, adjustedRadius, config, density, adjustedBounds)
                    FolderIcon3DDraw.drawTopGlint(canvas, cx, cy, adjustedRadius, config, density, adjustedBounds)
                }
            }
            if (contents.isEmpty()) {
                if (iconsOnly) drawPlaceholderGrid(canvas, cx, cy, density, config)
            } else {
                if (resolvedIcons.isEmpty()) {
                    drawPlaceholderGrid(canvas, cx, cy, density, config)
                } else {
                    if (shapeStyle == FolderShapeStyle.BALL && drawStyle == 14) {
                        FolderIconSpecialPreviewDraw.drawOrbitalSummary(
                            context, canvas, cx, cy, folderItem, resolvedIcons, adjustedRadius, density, contents.size, showLabels
                        )
                    } else if (shapeStyle == FolderShapeStyle.BALL) {
                        val cacheKey = "${folderItem.id}_${folderItem.folderTitle}_${config.previewStyle}_${adjustedRadius.toInt()}_${resolvedIcons.joinToString { it.first.packageName }}"
                        var cachedBitmap = iconPreviewCache.get(cacheKey)
                        
                        if (cachedBitmap == null) {
                            val renderScale = 2f // Render at 2x resolution to maintain quality during warp stretch
                            val size = (adjustedRadius * 2 * renderScale).toInt().coerceAtLeast(1)
                            cachedBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                            val offscreenCanvas = Canvas(cachedBitmap)
                            offscreenCanvas.scale(renderScale, renderScale)
                            
                            val iconScale = 0.8f // 20% smaller icons to compensate for spherical stretch
                            FolderIconPreviewDraw.drawPreview(
                                context, offscreenCanvas, adjustedRadius, adjustedRadius, config.previewStyle, resolvedIcons,
                                adjustedRadius * iconScale, density, config.customIconPackage, iconCache,
                                6, folderItem, contents.size, showLabels
                            )
                            iconPreviewCache.put(cacheKey, cachedBitmap)
                        }
                        
                        FolderIconSpecialPreviewDraw.drawWarpedPreview(canvas, cx, cy, adjustedRadius, cachedBitmap)
                    } else {
                        val plateRect = adjustedBounds ?: android.graphics.RectF(
                            cx - adjustedRadius, cy - adjustedRadius, cx + adjustedRadius, cy + adjustedRadius
                        )
                        val previewLayout = FolderIconPreviewLayout.resolve(
                            cx, cy, adjustedRadius, plateRect, shapeStyle, config, density
                        )
                        FolderIconPreviewDraw.drawPreview(
                            context, canvas, cx, cy, config.previewStyle, resolvedIcons,
                            adjustedRadius, density, config.customIconPackage, iconCache,
                            shapeStyle, folderItem, contents.size, showLabels,
                            previewLayout = previewLayout
                        )
                    }
                }
            }
        }
        
        if (sx != 1f || sy != 1f) {
            canvas.restore()
        }
        
        if (config.showAccentRing) {
            drawPermanentAccentRing(canvas, cx, cy, adjustedRadius, density)
        }
    }

    private fun drawFileTabFolder(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        adjustedBounds: android.graphics.RectF?,
        adjustedRadius: Float,
        config: com.nexus.launcher.data.FolderConfig,
        density: Float,
        folderItem: HomeScreenItem,
        contents: List<HomeScreenItem>,
        drawStyle: Int,
        resolvedIcons: List<Pair<HomeScreenItem, Drawable>>,
        iconCache: Map<String, Drawable>,
        showLabels: Boolean
    ) {
        val rect = adjustedBounds ?: android.graphics.RectF(cx - adjustedRadius, cy - adjustedRadius, cx + adjustedRadius, cy + adjustedRadius)
        val previewRadius = FolderIconShapeDraw.fileFolderPreviewRadius(rect, adjustedRadius)
        FolderIconFileFolderDraw.draw(context, canvas, rect, adjustedRadius, config, density, folderItem.id.toLong()) {
            drawShapePreviews(canvas, cx, cy, previewRadius, config, folderItem, contents, drawStyle, resolvedIcons, iconCache, FolderShapeStyle.FILE_FOLDER, showLabels, density, clipToShape = false)
        }
    }

    private fun drawSoftCapsuleFolder(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        adjustedBounds: android.graphics.RectF?,
        adjustedRadius: Float,
        config: com.nexus.launcher.data.FolderConfig,
        density: Float,
        folderItem: HomeScreenItem,
        contents: List<HomeScreenItem>,
        drawStyle: Int,
        resolvedIcons: List<Pair<HomeScreenItem, Drawable>>,
        iconCache: Map<String, Drawable>,
        showLabels: Boolean
    ) {
        val rect = adjustedBounds ?: android.graphics.RectF(cx - adjustedRadius, cy - adjustedRadius, cx + adjustedRadius, cy + adjustedRadius)
        val plateRect = FolderIconPlateDraw.cardBounds(rect, density, config)
        val previewRadius = FolderIconShapeDraw.softCapsulePreviewRadius(plateRect, adjustedRadius)
        FolderIconSoftCapsuleDraw.draw(context, canvas, rect, adjustedRadius, config, density, folderItem.id.toLong()) {
            val well = FolderIconShapeDraw.softCapsuleWellBounds(plateRect)
            drawShapePreviews(canvas, well.centerX(), well.centerY(), previewRadius, config, folderItem, contents, drawStyle, resolvedIcons, iconCache, FolderShapeStyle.SOFT_CAPSULE, showLabels, density, clipToShape = false)
        }
    }

    private fun drawShapePreviews(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        previewRadius: Float,
        config: com.nexus.launcher.data.FolderConfig,
        folderItem: HomeScreenItem,
        contents: List<HomeScreenItem>,
        drawStyle: Int,
        resolvedIcons: List<Pair<HomeScreenItem, Drawable>>,
        iconCache: Map<String, Drawable>,
        shapeStyle: Int,
        showLabels: Boolean,
        density: Float,
        clipToShape: Boolean
    ) {
        if (resolvedIcons.isEmpty()) {
            drawPlaceholderGrid(canvas, cx, cy, density, config)
        } else if (drawStyle == 14) {
            FolderIconSpecialPreviewDraw.drawSummaryCard(
                context, canvas, cx, cy, folderItem, resolvedIcons, previewRadius, density, contents.size, showLabels
            )
        } else {
            FolderIconPreviewDraw.drawPreview(
                context, canvas, cx, cy, config.previewStyle, resolvedIcons,
                previewRadius, density, config.customIconPackage, iconCache,
                shapeStyle, folderItem, contents.size, showLabels,
                applyShapeClip = clipToShape
            )
        }
    }

    private fun drawPermanentAccentRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        density: Float
    ) {
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = FolderScrimHighlight.resolvedSilhouetteStrokeColor()
            strokeWidth = 2.5f * density
            alpha = 178
        }
        canvas.drawCircle(cx, cy, folderRadius + 4f * density, ringPaint)
    }

    private fun resolveBackgroundColor(config: com.nexus.launcher.data.FolderConfig): Int {
        if (!config.solidBackgroundColor.isNullOrBlank()) {
            return try {
                android.graphics.Color.parseColor(config.solidBackgroundColor)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#2C2C2C")
            }
        }
        if (!config.backgroundColor.isNullOrBlank()) {
            return try {
                android.graphics.Color.parseColor(config.backgroundColor)
            } catch (_: Exception) {
                android.graphics.Color.parseColor("#2C2C2C")
            }
        }
        return FolderIconSurfaceColor.plate(context, config).fill
    }

    private fun resolveIcon(item: HomeScreenItem, iconCache: Map<String, Drawable>?): Drawable? {
        // Prefer a ConstantState clone — never mutate the shared cache entry (blank tiles).
        iconCache?.get(item.packageName)?.constantState?.newDrawable()?.mutate()?.let { return it }
        return try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context, com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
            ).iconResolver().getIcon(
                item.packageName,
                item.launchIntent?.component?.className
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun drawPlaceholderGrid(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        density: Float,
        config: com.nexus.launcher.data.FolderConfig
    ) {
        placeholderPaint.color = FolderIconPlateDraw.placeholderDotColor(context, config)
        val dotRadius = 4f * density
        val offset = 10f * density
        listOf(
            cx - offset to cy - offset,
            cx + offset to cy - offset,
            cx - offset to cy + offset,
            cx + offset to cy + offset
        ).forEach { (px, py) -> canvas.drawCircle(px, py, dotRadius, placeholderPaint) }
    }
}
