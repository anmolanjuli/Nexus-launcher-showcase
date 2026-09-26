package com.nexus.launcher.ui.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.folder.FolderContentsResolver
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object DockLayoutRenderer {

    private const val TAG = "DockDrop"

    const val DRAG_SHADOW_ALPHA = 64

    @Volatile var hoveredSlotIndex: Int? = null
    @Volatile var draggedItemId: Int? = null
    @Volatile var draggedItemOriginalColumn: Int? = null
    @Volatile var isInternalDrag: Boolean = false
    @Volatile var dragFingerLocalX: Float? = null

    var requestInvalidate: (() -> Unit)? = null

    @Volatile private var iconFallbackContext: Context? = null
    @Volatile private var lastDensity: Float = 3f
    @Volatile private var folderIconRenderer: com.nexus.launcher.ui.folder.FolderIconRenderer? = null

    /** Drops icon-derived caches on the dock's folder renderer — see
     *  [com.nexus.launcher.ui.folder.FolderIconRenderer.evictIconCaches]. */
    fun evictFolderIconCaches() {
        folderIconRenderer?.evictIconCaches()
    }

    @Volatile private var dropCommitBatch = false
    @Volatile private var springLoopActive = false

    fun isSpringLoopActive(): Boolean = springLoopActive

    fun setSpringLoopActive(active: Boolean) {
        springLoopActive = active
    }

    @Volatile private var committedDockItems: List<HomeScreenItem>? = null
    @Volatile private var commitFresh = false

    var onCommitItems: ((List<HomeScreenItem>) -> Unit)? = null

    private val animX = mutableMapOf<Int, DockSpringPhysics.Channel>()
    private val animBgLeft = DockSpringPhysics.Channel(0f)
    private val animBgWidth = DockSpringPhysics.Channel(0f)
    private var lastLayout: DockRenderHelper.PaintLayout? = null

    private fun layoutParams(): DockLayoutAnimParams {
        val ctx = iconFallbackContext
        val density = if (ctx != null) ctx.resources.displayMetrics.density else lastDensity
        return DockLayoutAnimParams(dragFingerLocalX, density)
    }

    fun ensureIconContext(appContext: Context) {
        iconFallbackContext = appContext.applicationContext
        lastDensity = appContext.resources.displayMetrics.density
        if (folderIconRenderer == null) {
            folderIconRenderer = com.nexus.launcher.ui.folder.FolderIconRenderer(appContext.applicationContext)
        }
    }

    fun beginDropCommitBatch() {
        dropCommitBatch = true
    }

    fun endDropCommitBatch() {
        dropCommitBatch = false
    }

    fun isDropCommitBatchActive(): Boolean = dropCommitBatch

    /** Ground-truth dock list after DB commit; used for draw until bind syncs. */
    fun dockItemsForDraw(fallback: List<HomeScreenItem>): List<HomeScreenItem> =
        committedDockItems ?: fallback

    fun shouldSkipBindRedraw(): Boolean = commitFresh

    fun acknowledgeBindSync() {
        commitFresh = false
        committedDockItems = null
    }

    /**
     * Replaces the renderer's committed dock list on the main thread and triggers
     * exactly one redraw via [requestInvalidate].
     */
    fun commitItems(list: List<HomeScreenItem>) {
        committedDockItems = list.toList()
        if (com.nexus.launcher.util.NexusDiag.ENABLED) {
            val ordered = committedDockItems!!.joinToString(",") { "${it.packageName}@${it.column}" }
            Log.d(TAG, "final dock state (count=${committedDockItems!!.size}): $ordered")
        }
        commitFresh = true
        clearDragPreview()
        endDropCommitBatch()
        onCommitItems?.invoke(committedDockItems!!)
        requestInvalidate?.invoke()
    }

    fun clearDragPreview() {
        hoveredSlotIndex = null
        draggedItemId = null
        draggedItemOriginalColumn = null
        isInternalDrag = false
        dragFingerLocalX = null
        committedDockItems = null
        commitFresh = false
        DockHoverCoordinator.reset()
    }

    /** Optimistic in-memory dock list after an icon leaves the dock. */
    fun reindexExcluding(
        items: List<HomeScreenItem>,
        excludeId: Int,
        maxDockIcons: Int
    ): List<HomeScreenItem> {
        val span = 1f / maxDockIcons.coerceAtLeast(1)
        return items
            .filter { it.page == HomeScreenViewModel.DOCK_CONTAINER && it.id != excludeId }
            .sortedBy { it.column }
            .mapIndexed { i, d ->
                d.copy(column = i, xFraction = (i * span) + span / 2f, yFraction = 0.5f)
            }
    }

    fun snapAnimToFinal(axisSize: Int, maxDockIcons: Int, items: List<HomeScreenItem>) {
        val safeMax = maxDockIcons.coerceAtLeast(1)
        val layout = lastLayout
        val density = lastDensity
        val iconSize = layout?.iconSizePx ?: (DockSlotLayout.userIconSizeDp * density)
        val visible = items.filter { it.column < safeMax }.sortedBy { it.column }
        val visualSlotCount = visible.size.coerceAtLeast(1)
        val slotWidth = layout?.slotWidth
            ?: DockSlotLayout.cappedSlotWidth(axisSize, visualSlotCount, iconSize, density)
        val startOffset = DockSlotLayout.centerStartOffset(axisSize, visualSlotCount, slotWidth)
        val keepIds = mutableSetOf<Int>()
        for (item in visible) {
            val col = item.column.coerceIn(0, safeMax - 1)
            val target = startOffset + col * slotWidth
            val channel = animX.getOrPut(item.id) { DockSpringPhysics.Channel(target) }
            DockSpringPhysics.snap(channel, target)
            keepIds.add(item.id)
        }
        animX.keys.retainAll(keepIds)
        
        DockSpringPhysics.snap(animBgLeft, startOffset)
        DockSpringPhysics.snap(animBgWidth, visualSlotCount * slotWidth)
    }

    fun syncLayout(
        axisSize: Int,
        height: Int,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        iconSizePx: Float,
        bottomPaddingPx: Float,
        outboundDraggedId: Int? = null,
        isVertical: Boolean = false
    ) {
        val params = layoutParams()
        val excludeId = DockInternalDragRenderer.excludeItemId(
            isInternalDrag, draggedItemId, outboundDraggedId
        )
        lastLayout = DockRenderHelper.buildLayout(
            axisSize, height, items, maxDockIcons, iconSizePx, bottomPaddingPx,
            excludeId, hoveredSlotIndex,
            isInternalDrag, draggedItemOriginalColumn, animX,
            params.fingerLocalX, params.density, params.motionIntensity,
            advanceSprings = false,
            isVertical = isVertical
        )
        DockSpringPhysics.snap(animBgLeft, lastLayout!!.bgLeft)
        DockSpringPhysics.snap(animBgWidth, lastLayout!!.bgWidth)
    }

    fun getIconBoundsAt(index: Int): RectF? {
        val layout = lastLayout ?: return null
        return DockRenderHelper.iconBoundsAt(layout, index)
    }

    fun getIconIndexForTouch(x: Float, y: Float): Int {
        val layout = lastLayout ?: return -1
        return DockRenderHelper.iconIndexForTouch(layout, x, y)
    }

    fun resolveInsertIndexForX(x: Float): Int {
        val layout = lastLayout ?: return 0
        return DockRenderHelper.insertIndexForX(
            layout,
            x,
            animX,
            isInternalDrag,
            draggedItemOriginalColumn,
            allowAppendGap = !isInternalDrag && layout.originalCount < layout.maxDockIcons
        )
    }

    fun getInsertIndexForX(x: Float): Int = resolveInsertIndexForX(x)

    fun drawIcons(
        canvas: Canvas,
        axisSize: Int,
        height: Int,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        currentPage: Int,
        iconSizePx: Float,
        bottomPaddingPx: Float,
        iconCache: ConcurrentHashMap<String, Drawable>,
        outboundDraggedId: Int? = null,
        folderContents: Map<Long, List<HomeScreenItem>> = emptyMap(),
        hiddenFolderItemId: Int? = null,
        badgeCounts: Map<String, Int> = emptyMap(),
        badgeStyleApp: Int = 1,
        badgeStyleFolder: Int = 1,
        badgeRenderer: com.nexus.launcher.ui.canvas.BadgeRenderer? = null,
        isVertical: Boolean = false
    ) {
        val params = layoutParams()
        val excludeId = DockInternalDragRenderer.excludeItemId(
            isInternalDrag, draggedItemId, outboundDraggedId
        )
        if (items.isEmpty() && !DockHighlightRenderer.shouldDraw()) return
        val layout = DockRenderHelper.buildLayout(
            axisSize, height, items, maxDockIcons, iconSizePx, bottomPaddingPx,
            excludeId, hoveredSlotIndex,
            isInternalDrag, draggedItemOriginalColumn, animX,
            params.fingerLocalX, params.density, params.motionIntensity,
            isVertical = isVertical
        )
        lastLayout = layout
        animBgLeft.position = layout.bgLeft
        animBgWidth.position = layout.bgWidth
        
        DockHighlightRenderer.draw(canvas, layout, params.density)
        if (items.isEmpty()) return
        val isNeumorphic = DockBackgroundRenderer.shouldDrawNeumorphicOnCanvas()
        val tokens = iconFallbackContext?.let {
            try { com.nexus.launcher.theme.ThemeObserver.currentTokens(it) } catch (_: Exception) { com.nexus.launcher.theme.NexusColorTokens.Dark }
        } ?: com.nexus.launcher.theme.NexusColorTokens.Dark
        for (item in layout.sortedVisible) {
            if (!DockInternalDragRenderer.shouldDrawItem(item, excludeId)) continue
            val mainPos = layout.itemLeft[item.id] ?: continue
            val scale = layout.itemScale[item.id] ?: 1f
            val targetCol = DockRenderHelper.displacedColumn(
                item.column, hoveredSlotIndex, isInternalDrag,
                draggedItemOriginalColumn, layout.maxDockIcons
            )
            val crossPos = DockSlotLayout.iconTop(
                layout.baseTop, targetCol, layout.viewHeight
            )
            val itemX = DockAxis.x(mainPos, crossPos, isVertical)
            val itemY = DockAxis.y(mainPos, crossPos, isVertical)
            if (mainPos + layout.iconSizePx * scale < 0f || mainPos > axisSize) continue

            val cx = itemX + layout.iconSizePx / 2f
            val cy = itemY + layout.iconSizePx / 2f

            if (isNeumorphic) {
                DockInternalDragRenderer.drawNeumorphicTile(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    size = layout.iconSizePx * scale,
                    tokens = tokens,
                    density = params.density
                )
            }

            if (DockSearchSlot.isSearchItem(item)) {
                DockInternalDragRenderer.drawSearchIcon(
                    canvas, iconFallbackContext, itemX, itemY, layout.iconSizePx, scale, params.density
                )
                continue
            }

            if (item.itemType == 1) { // FOLDER
                if (item.id == hiddenFolderItemId) continue
                val contents = FolderContentsResolver.fromMap(folderContents, item)
                val scaledIconSize = iconSizePx * scale
                folderIconRenderer?.drawFolder(
                    canvas = canvas,
                    cx = cx,
                    cy = cy,
                    folderItem = item,
                    contents = contents,
                    iconCache = iconCache,
                    density = params.density,
                    folderRadiusOverride = scaledIconSize / 2f
                )
                
                val folderCount = contents.sumOf { badgeCounts[it.packageName] ?: 0 }
                if (folderCount > 0) {
                    val fallbackBadge = badgeRenderer ?: com.nexus.launcher.ui.canvas.BadgeRenderer(params.density)
                    fallbackBadge.drawBadge(
                        canvas = canvas,
                        iconRect = android.graphics.Rect(itemX.toInt(), itemY.toInt(), (itemX + scaledIconSize).toInt(), (itemY + scaledIconSize).toInt()),
                        count = folderCount,
                        style = badgeStyleFolder,
                        alpha = 255
                    )
                }
                
                continue
            }

            var drawable = iconCache[item.packageName]
            if (drawable == null) continue
            DockIconPainter.drawIcon(canvas, drawable, itemX, itemY, layout.iconSizePx, scale)
            
            val count = badgeCounts[item.packageName] ?: 0
            if (count > 0) {
                val fallbackBadge = badgeRenderer ?: com.nexus.launcher.ui.canvas.BadgeRenderer(params.density)
                val scaledSize = layout.iconSizePx * scale
                fallbackBadge.drawBadge(
                    canvas = canvas,
                    iconRect = android.graphics.Rect(itemX.toInt(), itemY.toInt(), (itemX + scaledSize).toInt(), (itemY + scaledSize).toInt()),
                    count = count,
                    style = badgeStyleApp,
                    alpha = 255
                )
            }
        }
    }

    fun drawLabels(
        canvas: Canvas,
        axisSize: Int,
        height: Int,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        iconSizePx: Float,
        bottomPaddingPx: Float,
        dockBackgroundColor: Int,
        labelResolver: (HomeScreenItem) -> String?
    ) {
        if (!DockLabelRenderer.showLabels) return
        val layout = lastLayout ?: return
        DockLabelRenderer.drawLabels(
            canvas, layout, items, layoutParams().density, dockBackgroundColor, labelResolver
        )
    }

    fun drawPageDots(
        canvas: Canvas,
        width: Int,
        pageCount: Int,
        scrollOffsetX: Float,
        top: Float,
        density: Float,
        dotPaint: Paint
    ) = Unit

    fun currentAnimatedBgLeft(): Float = animBgLeft.position
    fun currentAnimatedBgWidth(): Float = animBgWidth.position
}
