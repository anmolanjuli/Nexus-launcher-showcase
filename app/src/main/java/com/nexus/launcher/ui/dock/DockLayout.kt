package com.nexus.launcher.ui.dock

import android.content.Context; import android.graphics.Canvas; import android.graphics.drawable.Drawable
import android.view.View; import android.view.GestureDetector; import android.view.MotionEvent; import android.view.ViewConfiguration
import android.widget.FrameLayout; import androidx.lifecycle.findViewTreeLifecycleOwner
import com.nexus.launcher.data.HomeScreenItem; import com.nexus.launcher.ui.dock.settings.DockSettingsRepository; import com.nexus.launcher.ui.dock.settings.DockSettingsEntryPoint; import com.nexus.launcher.ui.model.DisplayItem
import dagger.hilt.android.EntryPointAccessors; import kotlinx.coroutines.CoroutineScope; import kotlinx.coroutines.Dispatchers; import kotlinx.coroutines.Job; import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive; import kotlinx.coroutines.cancel; import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Independent dock layer: draws dock items, handles touch, drops, and paging. */
class DockLayout(context: Context) : FrameLayout(context) {

    var maxDockIcons: Int = DockSlotLayout.DEFAULT_MAX_DOCK_ICONS

    var dockHeightDp: Int = DockSettingsRepository.DEFAULT_DOCK_HEIGHT_DP
        internal set

    var freezeTranslation = false

    internal val density = resources.displayMetrics.density
    private val bottomPaddingPx = 16f * density
    private val dockBackground = DockLayoutBackground(this)
    private val dockBlur = DockBlurCoordinator(this)
    internal val dockBlurInternal get() = dockBlur

    fun setBlurState(isBlurred: Boolean) = dockBlur.setBlurState(isBlurred)
    val isBlurActive: Boolean get() = dockBlur.isBlurActive

    private fun effectiveBottomPaddingPx(): Float =
        bottomPaddingPx + DockLabelRenderer.labelExtraHeightPx(density)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val flingThreshold = 800f

    internal var items: List<HomeScreenItem> = emptyList()
    internal var folderContentsSnapshot: Map<Long, List<HomeScreenItem>> = emptyMap()
    internal var hiddenOpenFolderId: Int? = null
        private set
    internal val launchIntents = mutableMapOf<Int, android.content.Intent>()
    internal val iconCache = ConcurrentHashMap<String, Drawable>()
    /**
     * Cancelled in [onDetachedFromWindow] and rebuilt in [onAttachedToWindow].
     *
     * It used to be a `val`, so once the dock had detached even once the scope stayed cancelled
     * for the life of the view and every later icon load launched into it silently did nothing.
     * That was invisible while the icon cache was never cleared - the stale icons simply stayed
     * on screen - and became "dock icons vanish a few seconds after launch" the moment clearing
     * started working.
     */
    internal var ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var settingsCollectJob: Job? = null
    internal var currentThemeTokens = com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
    internal val backgroundPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = currentThemeTokens.surface; style = android.graphics.Paint.Style.FILL }

    private var iconSizePx = 0f
    internal var pageCount = 0
    internal var scrollOffsetX = 0f
    private var snapAnimator: android.animation.ValueAnimator? = null
    private var onItemDropped: ((DisplayItem, Int) -> Unit)? = null

    var onItemClicked: ((HomeScreenItem) -> Unit)? = null
    var onItemLongClicked: ((HomeScreenItem) -> Unit)? = null
    var onInitiateDrag: ((HomeScreenItem, View) -> Unit)? = null
    var onOutboundDragMove: ((Float, Float) -> Unit)? = null
    var onOutboundDragEnd: ((Float, Float, Int?, Boolean) -> Unit)? = null
    var onDockSettingsRequested: (() -> Unit)? = null
    var onSearchSlotClicked: (() -> Unit)? = null

    private val outboundDrag = DockOutboundDrag(this).apply {
        onDragMove = { x, y -> this@DockLayout.onOutboundDragMove?.invoke(x, y) }
        onDragEnd = { x, y, slot, internal ->
            this@DockLayout.onOutboundDragEnd?.invoke(x, y, slot, internal)
        }
    }

    var hoveredSlot: Int = -1
        private set

    @Volatile var skipNextFullRebind: Boolean = false

    val currentPage: Int get() = DockLayoutPager.currentPageIndex(dockAxisSize(), scrollOffsetX, pageCount)

    private val isPhoneLandscape: Boolean
        get() = com.nexus.launcher.ui.canvas.LayoutProfile.isPhoneLandscape(resources.configuration)

    private val dockGesture = DockLayoutGesture(
        this, touchSlop, flingThreshold, outboundDrag, ::dockSettingsRepository
    )
    private val gestureDetector = GestureDetector(context, dockGesture)

    init {
        setWillNotDraw(false)
        dockBackground.install()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        DockLayoutLifecycleHelper.onAttachedToWindow(this)
    }

    fun resetToPageZero() {
        cancelSnapAnimator()
        scrollOffsetX = 0f
        outboundDrag.cancel()
        forceClearHover()
        DockLayoutRenderer.clearDragPreview()
        recompute()
        invalidate()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        resetToPageZero()
        DockLayoutSettingsBinder.updateOrientationBounds(this, dockHeightDp, density)
        recompute()
    }

    fun updateOrientationBounds() {
        resetToPageZero()
        DockLayoutSettingsBinder.updateOrientationBounds(this, dockHeightDp, density)
        recompute()
    }

    fun isOutboundDragActive(): Boolean = outboundDrag.active

    fun containsCanvasPoint(canvasX: Float, canvasY: Float): Boolean {
        if (width == 0 || height == 0 || visibility != VISIBLE || alpha <= 0.05f) return false
        val localX = canvasX - left
        val localY = canvasY - top
        if (localX < 0 || localX > width || localY < 0 || localY > height) return false
        val mainLen = DockLayoutRenderer.currentAnimatedBgWidth()
        if (mainLen <= 0f) return true
        val mainStart = DockLayoutRenderer.currentAnimatedBgLeft()
        val localMain = DockAxis.main(localX, localY, isPhoneLandscape)
        return localMain >= mainStart && localMain <= mainStart + mainLen
    }

    fun canvasLocalX(canvasX: Float): Float = canvasX - left

    private fun dockAxisSize(): Int = DockAxis.mainSize(width, height, isPhoneLandscape)
    private fun dockCrossSize(): Int = DockAxis.crossSize(width, height, isPhoneLandscape)

    private fun pageItems(): List<HomeScreenItem> =
        DockSearchSlot.mergeForLayout(items, maxDockIcons, currentPage)

    private fun displayItemCount(): Int =
        items.size + if (DockSearchSlot.enabled && currentPage == 0) 1 else 0

    fun pageItemCount(): Int = DockLayoutPageOps.pageItemCount(items, currentPage, maxDockIcons)

    fun cachedIconFor(packageName: String): Drawable? = iconCache[packageName]

    fun iconHitRectFor(item: HomeScreenItem): android.graphics.Rect =
        DockLayoutHitTest.iconHitRect(this, item)

    private fun dropAxisCoord(localX: Float, localY: Float): Float =
        DockAxis.main(localX, localY, isPhoneLandscape)

    internal fun dropAxisCoordInternal(localX: Float, localY: Float): Float =
        dropAxisCoord(localX, localY)

    fun prepareCanvasDropSlot(rawX: Float, rawY: Float, item: HomeScreenItem): Int =
        DockLayoutDropCommit.prepareCanvasDrop(this, rawX, rawY, item)

    fun clearHoverStateOnly() {
        hoveredSlot = -1
    }

    fun containsDockItem(id: Int): Boolean = items.any { it.id == id }

    fun containsDockPackage(packageName: String): Boolean {
        if (packageName == DockSearchSlot.PACKAGE) return DockSearchSlot.enabled
        return items.any { it.packageName == packageName }
    }

    fun insertIndexForLocalCoord(localX: Float, localY: Float = 0f): Int =
        DockCanvasDropHelper.insertIndexForLocalCoord(this, localX, localY)

    fun setHiddenOpenFolderId(folderItemId: Int?) {
        hiddenOpenFolderId = folderItemId
        invalidate()
    }

    internal fun syncRendererLayoutInternal() = syncRendererLayout()
    internal val dockBackgroundInternal get() = dockBackground
    internal val pageCountInternal get() = pageCount
    internal val iconSizePxInternal get() = iconSizePx
    internal val bottomPaddingPxInternal get() = bottomPaddingPx
    internal val scrollOffsetXInternal get() = scrollOffsetX
    internal val iconCacheInternal get() = iconCache
    internal val outboundDraggedItemId get() = outbound.draggedItemId
    internal val dockAxisSizeInternal get() = dockAxisSize()
    internal val dockCrossSizeInternal get() = dockCrossSize()
    internal fun effectiveBottomPaddingPxInternal() = effectiveBottomPaddingPx()
    internal fun recomputeLayout() = recompute()
    internal fun labelForItemInternal(item: HomeScreenItem) = labelForItem(item)
    internal fun itemAtTouch(e: MotionEvent) = getItemAt(e)
    internal fun slotMetricsForDraw(incomingDrop: Boolean = false) = DockSlotLayout.metrics(dockAxisSize(), pageItems().size, maxDockIcons, incomingDrop)
    internal fun scrollByDelta(distanceMain: Float) {
        scrollOffsetX = (scrollOffsetX + distanceMain).coerceIn(0f, DockLayoutPager.maxScrollX(dockAxisSize(), pageCount))
        invalidate()
    }
    internal fun animateToPageIndex(page: Int) = animateToPage(page)

    private fun syncRendererLayout() {
        if (width == 0 || height == 0) return
        recompute()
        DockLayoutRenderer.syncLayout(
            dockAxisSize(), dockCrossSize(), pageItems(), maxDockIcons,
            iconSizePx, effectiveBottomPaddingPx(), outboundDrag.draggedItemId,
            isVertical = isPhoneLandscape
        )
    }

    internal fun localInsertionIndexAt(localX: Float, localY: Float): Int { syncRendererLayout(); return DockLayoutRenderer.resolveInsertIndexForX(dropAxisCoord(localX, localY)) }
    internal fun globalInsertionIndexAt(localX: Float, localY: Float) = DockLayoutPageOps.globalInsertionIndexAt(currentPage, maxDockIcons, localInsertionIndexAt(localX, localY))
    internal fun getItemsSnapshot() = items
    internal fun setHoveredSlot(slot: Int) { hoveredSlot = slot }
    internal fun setDropCallback(callback: (DisplayItem, Int) -> Unit) { onItemDropped = callback }
    internal fun invokeDropCallback(item: DisplayItem, index: Int) { onItemDropped?.invoke(item, index) }

    internal fun applyItems(newItems: List<HomeScreenItem>, refreshAllIntents: Boolean) =
        DockLayoutItemOps.applyItems(this, newItems, refreshAllIntents)

    internal fun refreshCacheInternal() = DockLayoutItemOps.refreshCache(this)

    internal fun reloadIconsInternal() = DockLayoutItemOps.reloadIcons(this)

    internal fun hasCachedIcon(packageName: String): Boolean = iconCache.containsKey(packageName)
    internal fun loadMissingIconsAsync(onLoaded: () -> Unit) = DockLayoutItemOps.loadMissingIconsAsync(this, onLoaded)
    internal fun loadMissingIconsSync(newItems: List<HomeScreenItem>) = DockLayoutItemOps.loadMissingIconsSync(this, newItems)
    internal fun snapRendererAfterDrop() = DockLayoutItemOps.snapRendererAfterDrop(this)

    private fun getItemAt(e: MotionEvent): HomeScreenItem? = DockLayoutItemResolver.getItemAt(this, e)
    private fun labelForItem(item: HomeScreenItem): String? = DockLayoutItemResolver.labelForItem(this, item)

    private fun dockSettingsRepository() =
        DockLayoutLifecycleHelper.dockSettingsRepository(this)

    fun launchIntentFor(item: HomeScreenItem): android.content.Intent? = launchIntents[item.id]

    fun bindItems(newItems: List<HomeScreenItem>) {
        val skipRedraw = DockLayoutRenderer.shouldSkipBindRedraw()
        if (skipNextFullRebind || consumeIncrementalBindRequest()) {
            skipNextFullRebind = false
            DockLayoutBindHelper.bindIncremental(this, newItems, skipRedraw)
            return
        }
        DockLayoutBindHelper.bindFull(this, newItems, skipRedraw)
    }

    fun bindFolderContents(map: Map<Long, List<HomeScreenItem>>) {
        folderContentsSnapshot = map
        DockLayoutBindHelper.precacheFolderChildIcons(this, ioScope, context, map, iconCache)
        invalidate()
    }

    internal val folderContentsSnapshotInternal: Map<Long, List<HomeScreenItem>>
        get() = folderContentsSnapshot

    private fun recompute() {
        if (width == 0 || height == 0) return
        val axis = dockAxisSize()
        val cross = dockCrossSize()
        val pageCount_items = pageItems().size
        val metrics = DockSlotLayout.metrics(axis, pageCount_items, maxDockIcons, density = density)
        iconSizePx = DockSlotLayout.dynamicIconSizePx(
            metrics.slotWidth, density, cross.toFloat(),
            showLabels = if (isPhoneLandscape) false else DockLabelRenderer.showLabels
        )
        pageCount = DockLayoutPageOps.pageCountFor(displayItemCount(), maxDockIcons)
        scrollOffsetX = scrollOffsetX.coerceIn(0f, DockLayoutPager.maxScrollX(axis, pageCount))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetToPageZero()
        syncRendererLayout()
        dockBackground.onSizeChanged()
        if (w != oldw || h != oldh) DockHomeGridSync.notifyCanvasFromDock(this)
    }

    internal fun applyCornerRadiusOutline() = dockBackground.applyCornerRadiusOutline()

    internal fun snapToNearestPageInternal() {
        animateToPage(DockLayoutPager.currentPageIndex(dockAxisSize(), scrollOffsetX, pageCount))
    }

    internal fun cancelSnapAnimator() {
        snapAnimator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        dockBackground.draw(canvas, density)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        DockLayoutForeground.draw(this, canvas)
        // The icon pass sets the pill bounds after the background pass has clipped to the old
        // ones (e.g. right after rotation); one more frame brings the clip up to date.
        if (dockBackground.isClipStale()) postInvalidateOnAnimation()
    }
    internal fun pageItemsInternal(): List<HomeScreenItem> = pageItems()

    internal val outbound get() = outboundDrag
    internal val dockGestureRef get() = dockGesture
    internal val touchSlopPx get() = touchSlop
    internal val gesture get() = gestureDetector

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return DockLayoutTouch.onTouchEvent(this, event)
    }

    fun setupDropTarget(onItemDropped: (DisplayItem, Int) -> Unit) =
        DockLayoutDropApi.setupDropTarget(this, onItemDropped)

    fun updateHoveredSlot(localX: Float, localY: Float = 0f) {
        DockHoverCoordinator.updateHover(this, localX, localY)
    }

    fun forceClearHover() {
        if (hoveredSlot != -1 || DockLayoutRenderer.hoveredSlotIndex != null) {
            hoveredSlot = -1
            DockLayoutRenderer.clearDragPreview()
            if (!DockLayoutRenderer.isDropCommitBatchActive()) invalidate()
        }
    }

    fun dispatchDrop(canvasX: Float, item: DisplayItem, canvasY: Float) {
        DockLayoutDropApi.dispatchDrop(this, canvasX, item, canvasY)
    }

    fun tryDropFromCanvas(canvasX: Float, canvasY: Float, item: DisplayItem): Boolean =
        DockLayoutDropApi.tryDropFromCanvas(this, canvasX, canvasY, item)

    private fun animateToPage(page: Int) {
        DockLayoutPager.animateToPage(
            dockAxisSize(), pageCount, page, scrollOffsetX, snapAnimator,
            onOffset = { scrollOffsetX = it; invalidate() },
            onAnimator = { snapAnimator = it }
        )
    }

    companion object {
        @Volatile private var incrementalBindRequested = false
        fun requestIncrementalBind() { incrementalBindRequested = true }

        fun consumeIncrementalBindRequest(): Boolean {
            val requested = incrementalBindRequested
            incrementalBindRequested = false
            return requested
        }

        fun findFrom(anchor: View): DockLayout? = DockLayoutFinder.findFrom(anchor)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        outboundDrag.cancel()
        DockLayoutRenderer.clearDragPreview()
        DockLayoutLifecycleHelper.onDetachedFromWindow(this)
    }
}
