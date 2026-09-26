package com.nexus.launcher.ui.canvas
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.text.*
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderContentsResolver
import com.nexus.launcher.ui.folder.FolderShapeStyle
import com.nexus.launcher.data.prefs.NexusDefaults
import androidx.core.graphics.drawable.toBitmap
class HomeScreenRenderer(
    private val context: Context,
    private val density: Float
) {
    private val iconCaches = HomeScreenIconCache(context)
    val iconCache: java.util.concurrent.ConcurrentHashMap<String, Drawable> get() = iconCaches.iconCache
    val labelCache: java.util.concurrent.ConcurrentHashMap<String, String> get() = iconCaches.labelCache
    internal val folderIconRenderer = com.nexus.launcher.ui.folder.FolderIconRenderer(context)
    /** Whether to draw labels under icons — driven by homeShowLabels setting. */
    var showLabels: Boolean = true
    /** Wrap leftover label text onto a second line — driven by homeTwoLineLabels. */
    var twoLineLabels: Boolean = false
    /** Icon size as a fraction of cell width — driven by homeIconSizeMultiplier setting. */
    var userIconSizeMultiplier: Float = NexusDefaults.HOME_ICON_SIZE_MULTIPLIER
    var gridRows: Int = 8
    /** Gap properties for rendering — driven by gap settings. */
    var gapHorizontalPx: Float = 0f
    var gapVerticalPx: Float = 0f
    @Volatile
    private var folderContentsSnapshot: Map<Long, List<HomeScreenItem>> = emptyMap<Long, List<HomeScreenItem>>()
    var badgeStyleFolder: Int = 1

    /**
     * Pre-sized bitmap cache — populated off-thread via prebuildBitmapCache().
     * Keyed identically to iconCache. Cleared when icon size or icons change.
     */
    internal val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()
    private var lastCachedIconSize: Int = -1

    /** Pre-allocated paint reused for every icon draw call — avoids per-icon heap allocation. */
    private val alphaPaint = android.graphics.Paint()
    private val tempDstRect = android.graphics.Rect()
    private val tempDstRectF = android.graphics.RectF()
    private val previousIconBounds = android.graphics.Rect()

    /** Sync folder child items for preview rendering (from ViewModel flow). */
    fun applyFolderContents(map: Map<Long, List<HomeScreenItem>>) {
        folderContentsSnapshot = map
        iconCaches.precacheFolderChildIcons(map)
    }
    fun folderContentsFor(folderId: Long): List<HomeScreenItem> =
        folderContentsSnapshot[folderId] ?: emptyList<HomeScreenItem>()
    fun folderContentsForItem(item: HomeScreenItem): List<HomeScreenItem> =
        FolderContentsResolver.fromMap(folderContentsSnapshot, item)
    private val labelPaint = TextPaint().apply {
        color = android.graphics.Color.WHITE
        textSize = HomeGridAvailableSpace.LABEL_TEXT_SIZE_PX
        textAlign = Paint.Align.CENTER
        isAntiAlias = true; isFakeBoldText = true
        setShadowLayer(3f * density, 0f, 1f * density, android.graphics.Color.argb(180, 0, 0, 0))
    }
    fun updateTypeface(typeface: android.graphics.Typeface) {
        labelPaint.typeface = typeface
    }
    fun overrideLabel(packageName: String, label: String) = iconCaches.overrideLabel(packageName, label)
    fun clearLabelOverride(packageName: String) = iconCaches.clearLabelOverride(packageName)
    fun getCacheKey(item: HomeScreenItem): String = iconCaches.getCacheKey(item)
    /** Pre-populate caches for all items in the list. Safe to call off the main thread. */
    fun updateCache(items: List<HomeScreenItem>) =
        iconCaches.updateCache(items, folderContentsSnapshot)
    fun precacheFolderChildIcons(map: Map<Long, List<HomeScreenItem>>) =
        iconCaches.precacheFolderChildIcons(map)
    fun refreshAllCaches(items: List<HomeScreenItem>) =
        iconCaches.refreshAllCaches(items, folderContentsSnapshot)

    /**
     * Pre-renders icons from [iconCache] into [bitmapCache] at [iconSizePx].
     * Safe to call off the main thread. Clears and rebuilds if size changed.
     */
    fun prebuildBitmapCache(iconSizePx: Int) {
        if (iconSizePx <= 0) return
        if (iconSizePx != lastCachedIconSize) {
            bitmapCache.clear()
            lastCachedIconSize = iconSizePx
        }
        for ((key, drawable) in iconCache) {
            if (!bitmapCache.containsKey(key)) {
                bitmapCache[key] = drawable.toBitmap(iconSizePx, iconSizePx)
            }
        }
    }

    fun draw(
        canvas: Canvas,
        items: List<HomeScreenItem>,
        gridCells: List<RectF>,
        gridCols: Int,
        gridRows: Int,
        alpha: Int = 255,
        currentPage: Int = 0,
        fractionPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap<Int, Triple<Int, Int, Int>>(),
        labelColor: Int = android.graphics.Color.WHITE,
        labelBold: Boolean = false,
        badgeCounts: Map<String, Int> = emptyMap<String, Int>(),
        badgeStyle: Int = 1,
        badgeRenderer: BadgeRenderer? = null,
        draggedItemId: Int? = null,
        draggedDisplayItem: com.nexus.launcher.ui.model.DisplayItem? = null,
        isDraggingIcon: Boolean = false,
        folderContents: Map<Long, List<HomeScreenItem>> = emptyMap<Long, List<HomeScreenItem>>(),
        hoveredMergeTargetId: Int? = null,
        hiddenFolderItemId: Int? = null,
        folderMorphProgress: Float = 0f,
        folderGlowId: Long? = null,
        folderGlowColor: Int = android.graphics.Color.GREEN,
        folderGlowAlpha: Float = 0f,
        mergeBounceScale: Float = 1.0f,
        draftResizeItemId: Int? = null,
        ghostedItemIds: Set<Int> = emptySet()
    ) {
        val displayWidthPx = context.resources.displayMetrics.widthPixels
        val displayHeightPx = context.resources.displayMetrics.heightPixels
        val contentsMap = folderContents.ifEmpty { folderContentsSnapshot }
        for (item in items) {
            if (item.itemType != 0 && item.itemType != 1 && item.itemType != 2) continue
            if (item.appWidgetId != -1) continue // Widgets are drawn by WidgetOverlayLayout
            val pos = if (item.id == draftResizeItemId) {
                Triple(item.page, item.column, item.row)
            } else {
                fractionPositions[item.id] ?: Triple(item.page, item.column, item.row)
            }
            if (pos.first != currentPage) continue // filter by VISUAL (spillover) page, not DB page
            val effCol = pos.second
            val effRow = pos.third
            // No raw bounds-cull: the reflow engine guarantees safe in-bounds indices; the
            // index-range check below is the only net (== gridCells.getOrNull(cellIndex) ?: continue).
            val cellIndex = effRow * gridCols + effCol
            if (cellIndex !in gridCells.indices) continue
            // Only copy the RectF when span expansion is needed; otherwise use the list element directly.
            val cell: android.graphics.RectF
            if (item.spanX > 1 || item.spanY > 1) {
                val baseCellRf = gridCells[cellIndex]
                val endCol = (effCol + item.spanX - 1).coerceAtMost(gridCols - 1)
                val endRow = (effRow + item.spanY - 1).coerceAtMost(gridRows - 1)
                val endCellIndex = endRow * gridCols + endCol
                cell = if (endCellIndex in gridCells.indices) {
                    val endCell = gridCells[endCellIndex]
                    android.graphics.RectF(baseCellRf.left, baseCellRf.top, endCell.right, endCell.bottom)
                } else {
                    android.graphics.RectF(baseCellRf)
                }
            } else {
                cell = gridCells[cellIndex]
            }
            val icon = if (item.itemType == 0 || item.itemType == 2) {
                iconCache[getCacheKey(item)] ?: continue
            } else null
            val labelText = if (showLabels) {
                if (item.itemType == 1) {
                    item.folderTitle.takeIf { it.isNotBlank() && it != "Folder" } ?: context.getString(com.nexus.launcher.R.string.folder_default_name)
                } else {
                    labelCache[getCacheKey(item)] ?: item.packageName.substringAfterLast('.')
                }
            } else null
            val layoutResult = IconLayoutMetrics.compute(
                cell = cell,
                gridRows = gridRows,
                gapHorizontalPx = gapHorizontalPx,
                gapVerticalPx = gapVerticalPx,
                spanX = item.spanX.coerceAtLeast(1),
                spanY = item.spanY.coerceAtLeast(1),
                showLabels = showLabels,
                userIconSizeMultiplier = userIconSizeMultiplier,
                density = density,
                displayWidth = displayWidthPx,
                displayHeight = displayHeightPx,
                labelText = labelText,
                labelPaint = labelPaint,
                caller = HomeGridOverlapDiag.CALLER_HOME,
                twoLineLabels = twoLineLabels
            )
            val iconRect = layoutResult.iconRect
            val iconSize = iconRect.width()
            val iconLeft = iconRect.left
            val iconTop = iconRect.top
            val iconRight = iconRect.right
            val iconBottom = iconRect.bottom
            val isBeingDragged = item.id == draggedItemId && isDraggingIcon
            val isGhosted = item.id in ghostedItemIds
            if (isGhosted) continue
            alphaPaint.alpha = if (isBeingDragged) 64 else 255
            if (item.itemType == 1) {
                HomeScreenFolderItemDraw.drawFolderItem(
                    context = context,
                    canvas = canvas,
                    item = item,
                    cell = cell,
                    iconRect = iconRect,
                    alpha = alpha,
                    alphaPaint = alphaPaint,
                    contentsMap = contentsMap,
                    iconCache = iconCache,
                    folderIconRenderer = folderIconRenderer,
                    density = density,
                    showLabels = showLabels,
                    badgeCounts = badgeCounts,
                    badgeStyleFolder = badgeStyle,
                    badgeRenderer = badgeRenderer,
                    hiddenFolderItemId = hiddenFolderItemId,
                    folderMorphProgress = folderMorphProgress,
                    folderGlowId = folderGlowId,
                    folderGlowColor = folderGlowColor,
                    folderGlowAlpha = folderGlowAlpha,
                    hoveredMergeTargetId = hoveredMergeTargetId,
                    mergeBounceScale = mergeBounceScale
                )
            } else if (icon != null) {
                var drewComposite = false
                var targetIconAlpha = alphaPaint.alpha
                val isMergeTarget = hoveredMergeTargetId != null && item.id == hoveredMergeTargetId
                if (isMergeTarget) {
                    val cx = cell.centerX()
                    val cy = iconTop + iconRect.height() / 2f
                    val mergeProgress = ((mergeBounceScale - 1.0f) / 0.15f).coerceIn(0f, 1f)
                    val highlightPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#7EB8D4")
                        this.alpha = (255 * mergeProgress).toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f * density
                        isAntiAlias = true
                    }
                    val shapeStyle = try {
                        if (item.folderConfigJson.isNotEmpty()) org.json.JSONObject(item.folderConfigJson).optInt("shapeStyle", 1) else 1
                    } catch (_: Exception) {
                        1
                    }
                    tempDstRectF.set(iconLeft.toFloat(), iconTop.toFloat(), iconRight.toFloat(), iconBottom.toFloat())
                    val shapePath = com.nexus.launcher.ui.folder.FolderIconShapeDraw.getShapePath(
                        tempDstRectF, iconRect.width() / 2f, shapeStyle
                    )
                    canvas.drawPath(shapePath, highlightPaint)
                    canvas.save()
                    canvas.scale(mergeBounceScale, mergeBounceScale, cx, cy)
                    val draggedItem = items.find { it.id == draggedItemId } ?: draggedDisplayItem?.let {
                        com.nexus.launcher.data.HomeScreenItem(
                            packageName = it.intent?.component?.packageName ?: it.intent?.`package` ?: "",
                            page = -1, row = 0, column = 0, itemType = 0
                        )
                    }
                    if (draggedItem != null && (draggedItem.itemType == 0 || draggedItem.itemType == 2) && icon != null) {
                        val draggedIcon = iconCache[draggedItem.packageName]
                        if (draggedIcon != null) {
                            val compositeAlpha = (mergeProgress * 255).toInt()
                            if (compositeAlpha > 0) {
                                canvas.saveLayerAlpha(cell.left, cell.top, cell.right, cell.bottom, compositeAlpha)
                                canvas.clipPath(shapePath)
                                com.nexus.launcher.ui.folder.FolderIconPreviewDraw.drawPreview(
                                    context, canvas, cx, cy, 0,
                                    listOf(Pair(item, icon), Pair(draggedItem, draggedIcon)),
                                    iconRect.width() / 2f, density, null, iconCache, shapeStyle, item, 2
                                )
                                canvas.restore()
                            }
                            targetIconAlpha = ((1f - mergeProgress) * 255).toInt().coerceIn(0, 255)
                            drewComposite = true
                        }
                    }
                }
                val prevAlpha = alphaPaint.alpha
                alphaPaint.alpha = if (isMergeTarget && drewComposite) targetIconAlpha else prevAlpha
                val key = getCacheKey(item)
                val iconBitmap = bitmapCache[key]
                tempDstRect.set(iconLeft, iconTop, iconRight, iconBottom)
                if (iconBitmap != null && !iconBitmap.isRecycled) {
                    canvas.drawBitmap(iconBitmap, null, tempDstRect, alphaPaint)
                } else {
                    val prevIconAlpha = icon.alpha
                    previousIconBounds.set(icon.bounds)
                    icon.alpha = alphaPaint.alpha
                    icon.bounds = tempDstRect
                    icon.draw(canvas)
                    icon.alpha = prevIconAlpha
                    icon.bounds = previousIconBounds
                }
                alphaPaint.alpha = prevAlpha
                if (isMergeTarget) canvas.restore()
                val count = badgeCounts[item.packageName] ?: 0
                if (count > 0) {
                    tempDstRect.set(iconLeft, iconTop, iconRight, iconBottom)
                    badgeRenderer?.drawBadge(canvas, tempDstRect, count, badgeStyle, alphaPaint.alpha)
                }
            }
            if (showLabels) {
                val skipLabel = item.itemType == 1 &&
                    item.id == hiddenFolderItemId &&
                    folderMorphProgress > 0f
                if (skipLabel) continue
                labelPaint.color = labelColor
                labelPaint.isFakeBoldText = labelBold
                labelPaint.alpha = alphaPaint.alpha
                val isLight = androidx.core.graphics.ColorUtils.calculateLuminance(labelColor) > 0.5
                if (isLight) {
                    labelPaint.setShadowLayer(4f * density, 0f, 1f * density, android.graphics.Color.argb(160, 0, 0, 0))
                } else {
                    labelPaint.clearShadowLayer()
                }
                IconLabelText.drawCentered(
                    canvas,
                    labelPaint,
                    cell.centerX(),
                    layoutResult.labelY,
                    layoutResult.ellipsizedLabel,
                    layoutResult.labelLine2,
                    layoutResult.labelLine2Y - layoutResult.labelY
                )
                labelPaint.clearShadowLayer()
            }
        }
    }
    /**
     * Returns the exact Rect used to draw the icon for [item] in [cell].
     * Mirrors the calculation in draw() exactly — same rowShift, adjustedTop,
     * iconSizeRaw, maxSafeHeight, and iconTop logic.
     */
    fun getIconRect(
        item: com.nexus.launcher.data.HomeScreenItem,
        cell: android.graphics.RectF
    ): android.graphics.Rect {
        if (item.itemType != 0 && item.itemType != 1 && item.itemType != 2) return android.graphics.Rect()
        
        val dm = context.resources.displayMetrics
        val result = IconLayoutMetrics.compute(
            cell = cell,
            gridRows = gridRows,
            gapHorizontalPx = gapHorizontalPx,
            gapVerticalPx = gapVerticalPx,
            spanX = item.spanX.coerceAtLeast(1),
            spanY = item.spanY.coerceAtLeast(1),
            showLabels = showLabels,
            userIconSizeMultiplier = userIconSizeMultiplier,
            density = density,
            displayWidth = dm.widthPixels,
            displayHeight = dm.heightPixels,
            labelText = null,
            labelPaint = null,
            caller = HomeGridOverlapDiag.CALLER_HOME,
            twoLineLabels = twoLineLabels
        )
        return result.iconRect
    }
    fun drawSingleItemCentered(canvas: Canvas, item: com.nexus.launcher.data.HomeScreenItem) {
        if (item.itemType != 0 && item.itemType != 2) return
        val icon = iconCache[getCacheKey(item)] ?: return
        val iconSize = (56f * density).toInt()
        val half = iconSize / 2
        icon.alpha = 255
        icon.setBounds(-half, -half, half, half)
        icon.draw(canvas)
    }
}
