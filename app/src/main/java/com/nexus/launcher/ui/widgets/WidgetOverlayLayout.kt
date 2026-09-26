package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout
import android.view.View
import kotlin.math.floor
import kotlin.math.roundToInt
import com.nexus.launcher.ui.canvas.LauncherCanvasView

class WidgetOverlayLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var totalPages = 1
    var viewWidth = 0f
    var onWidgetLongPress: ((com.nexus.launcher.data.HomeScreenItem, android.view.View) -> Unit)? = null
    var onMosaicLongPress: ((com.nexus.launcher.data.HomeScreenItem, com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView) -> Unit)? = null
    var onMosaicPlaceholderTapped: ((com.nexus.launcher.data.HomeScreenItem, Int) -> Unit)? = null
    var onMosaicTileFocusRequested: ((com.nexus.launcher.data.HomeScreenItem, com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView, Int) -> Unit)? = null
    var onMosaicConfigChanged: ((com.nexus.launcher.data.HomeScreenItem, com.nexus.launcher.ui.widgets.mosaic.MosaicConfig) -> Unit)? = null
    var canvasView: LauncherCanvasView? = null
    
    internal val singlePageWidth: Float
        get() = canvasView?.width?.toFloat()
                ?.takeIf { it > 0f } ?: viewWidth
                
    /** Same vertical span as home icon grid (`calculateHomeGrid`: topInset…dockBottomReserve). */
    val gridAreaHeight: Float
        get() = canvasView?.takeIf { it.viewHeight > 0 }?.let {
            (it.viewHeight - it.topInset - it.dockBottomReserve).toFloat().coerceAtLeast(0f)
        } ?: height.toFloat()
    
    internal val positionOverrides = mutableMapOf<Int, Pair<Float, Float>>()

    private var cachedItems: List<com.nexus.launcher.data.HomeScreenItem>? = null
    /** Items for the next [rebindCachedWidgets] — a rotation hands over new-shape positions early. */
    fun replaceCachedItems(items: List<com.nexus.launcher.data.HomeScreenItem>) { if (cachedItems != null) cachedItems = items }
    private var cachedAppWidgetManager: android.appwidget.AppWidgetManager? = null
    private var cachedAppWidgetHost: android.appwidget.AppWidgetHost? = null
    internal var cachedColumns: Int = 5
    internal var cachedRows = 0

    val liveItems = mutableMapOf<Int, com.nexus.launcher.data.HomeScreenItem>()
    val liveMosaics = mutableMapOf<Int, com.nexus.launcher.data.HomeScreenItem>()

    val dragHighlightDrawer = WidgetDragHighlightDrawer(this)
    var draggingWidgetView: View? = null

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
        // scrollX (not translationX) pans widgets — keep translation cleared
        translationX = 0f
    }

    private var cachedFolderContents: Map<Long, List<com.nexus.launcher.data.HomeScreenItem>> = emptyMap()

    fun bindWidgets(
        items: List<com.nexus.launcher.data.HomeScreenItem>,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetHost: android.appwidget.AppWidgetHost,
        columns: Int,
        rows: Int,
        folderContents: Map<Long, List<com.nexus.launcher.data.HomeScreenItem>> = emptyMap()
    ) {
        cachedItems = items
        if (folderContents.isNotEmpty()) {
            cachedFolderContents = folderContents
        }
        cachedAppWidgetManager = appWidgetManager
        cachedAppWidgetHost = appWidgetHost
        cachedColumns = columns
        cachedRows = rows
        AppWidgetOverlayBinder.bindFromOverlay(this, items, appWidgetManager, appWidgetHost, columns, rows)
        bindMosaicsInternal(items, appWidgetManager, appWidgetHost, columns, rows)
        bindShortcutBoxesInternal(items, cachedFolderContents, columns, rows)
        bindAppBoxesInternal(items, cachedFolderContents, columns, rows)
        bindLiveAppBoxesInternal(items, columns, rows)
        canvasView?.takeIf {
            com.nexus.launcher.ui.canvas.SelectionModeTransform.isCardTrackActive(it)
        }?.let(::applySelectionCardPageOffsets)
    }

    private fun bindMosaicsInternal(
        items: List<com.nexus.launcher.data.HomeScreenItem>,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetHost: android.appwidget.AppWidgetHost,
        columns: Int,
        rows: Int
    ) = com.nexus.launcher.ui.widgets.mosaic.LivingMosaicOverlayBinder.bindFromOverlay(
        this, items, appWidgetManager, appWidgetHost, columns, rows
    )

    private fun bindShortcutBoxesInternal(
        items: List<com.nexus.launcher.data.HomeScreenItem>,
        folderContents: Map<Long, List<com.nexus.launcher.data.HomeScreenItem>>,
        columns: Int,
        rows: Int
    ) {
        val onBoxPress: (com.nexus.launcher.data.HomeScreenItem, com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView) -> Unit = { item, view ->
            onWidgetLongPress?.invoke(item, view)
        }
        com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxOverlayBinder.bindBoxes(
            this, items, folderContents, columns, rows, onBoxPress
        )
    }

    private fun bindAppBoxesInternal(
        items: List<com.nexus.launcher.data.HomeScreenItem>,
        folderContents: Map<Long, List<com.nexus.launcher.data.HomeScreenItem>>,
        columns: Int,
        rows: Int
    ) {
        val onBoxPress: (com.nexus.launcher.data.HomeScreenItem, com.nexus.launcher.ui.widgets.appbox.AppBoxView) -> Unit = { item, view ->
            onWidgetLongPress?.invoke(item, view)
        }
        com.nexus.launcher.ui.widgets.appbox.AppBoxOverlayBinder.bindBoxes(
            this, items, folderContents, columns, rows, onBoxPress
        )
    }

    private fun bindLiveAppBoxesInternal(
        items: List<com.nexus.launcher.data.HomeScreenItem>,
        columns: Int,
        rows: Int
    ) {
        com.nexus.launcher.ui.widgets.liveapp.LiveAppOverlayBinder.bindBoxes(
            this, items, columns, rows
        ) { item, view -> onWidgetLongPress?.invoke(item, view) }
    }

    fun setBoxAcceptanceHover(boxId: Int?, active: Boolean) =
        WidgetOverlayBoxHelper.setBoxAcceptanceHover(this, boxId, active)

    fun findBox(id: Int): android.view.View? =
        WidgetOverlayBoxHelper.findBox(this, id)

    fun findMosaic(id: Int): com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView && child.currentItem()?.id == id) {
                return child
            }
        }
        return null
    }

    fun triggerBoxDropPulse(boxId: Int, success: Boolean) {
        setBoxAcceptanceHover(null, false)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView && child.currentItem()?.id == boxId) {
                child.triggerDropPulse(success)
            } else if (child is com.nexus.launcher.ui.widgets.appbox.AppBoxView && child.currentItem()?.id == boxId) {
                child.triggerDropPulse(success)
            }
        }
    }

    fun onThemeChanged() {
        dragHighlightDrawer.onThemeChanged()
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView) {
                child.applyThemeTokens()
            }
        }
    }


    internal var desiredScrollX: Int = 0
    private val dragPositionLock = WidgetDragPositionLock()
    private var lastSyncPage: Int = 0
    private var lastSyncOffset: Float = 0f

    /** Page swipe bridge — scrollX keeps host bounds on-screen (no translationX cull). */
    fun syncScroll(currentPage: Int, dragScrollOffset: Float) {
        lastSyncPage = currentPage
        lastSyncOffset = dragScrollOffset
        val pageW = singlePageWidth
        if (pageW <= 0f) return
        dragPositionLock.capture(this)
        val cv = canvasView
        if (cv != null && com.nexus.launcher.ui.canvas.SelectionModeTransform.isCardTrackActive(cv)) {
            WidgetOverlayCardOffsetHelper.syncSelectionScroll(this, cv, currentPage, dragScrollOffset, pageW)
        } else {
            val exact = currentPage * pageW - dragScrollOffset
            val whole = floor(exact)
            desiredScrollX = whole.toInt()
            translationX = whole - exact
        }
        applyDesiredScroll()
        dragPositionLock.restore(this)
        totalPages = canvasView?.totalPages?.coerceAtLeast(1) ?: totalPages
        if (dragHighlightDrawer.isActive) invalidate()
        // Always redraw glass backdrops so they re-sample wallpaper behind their new position
        invalidateGlassBackdropsForScroll()
    }

    fun resyncScroll() {
        syncScroll(canvasView?.currentPage ?: lastSyncPage, canvasView?.dragScrollOffset ?: lastSyncOffset)
    }

    /**
     * A scrolled ViewGroup repositions an already-rendered child layer without re-running its
     * onDraw — every Glass-mode surface hosted here (widget backdrop, AppBox, ShortcutBox) needs
     * to redraw every scroll frame since it has to re-sample whatever wallpaper is now behind
     * its new screen position. Each of those views caches its own blurred bitmap internally and
     * only recomputes it when position/size/refraction actually changed, so calling invalidate()
     * unconditionally here is cheap.
     */
    private fun invalidateGlassBackdropsForScroll() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is com.nexus.launcher.ui.widgets.WidgetGlassLiveBackdropView ||
                child is com.nexus.launcher.ui.widgets.appbox.AppBoxView ||
                child is com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView ||
                child is com.nexus.launcher.ui.widgets.liveapp.LiveAppView
            ) {
                child.invalidate()
            } else if (child is com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView) {
                child.invalidateBackdropsForScroll()
            }
        }
    }

    fun applySelectionCardPageOffsets(canvas: LauncherCanvasView) =
        WidgetOverlayCardOffsetHelper.applyPageOffsets(this, canvas)

    fun clearSelectionCardPageOffsets() =
        WidgetOverlayCardOffsetHelper.clearPageOffsets(this)

    internal fun childPage(child: View): Int? =
        WidgetOverlayCardOffsetHelper.childPage(this, child)

    private fun applyDesiredScroll() {
        if (scrollX != desiredScrollX) scrollTo(desiredScrollX, 0)
    }

    private val blurCoordinator = WidgetOverlayBlurCoordinator(this)

    /** The sole drawer-presentation entry point; prevents a stale drawer effect on Home. */
    fun setDrawerProgress(progress: Float) = blurCoordinator.setDrawerProgress(progress)

    /** Workspace chrome owns a stronger animated blur independently from drawer progress. */
    fun setBlurState(isBlurred: Boolean) = blurCoordinator.setBlurState(isBlurred)

    /** Called after app/drawer return in case the final animation frame was never rendered. */
    fun restoreHomePresentation() = blurCoordinator.restoreHomePresentation()

    fun applySelectionTransform(@Suppress("UNUSED_PARAMETER") canvasView: LauncherCanvasView, @Suppress("UNUSED_PARAMETER") density: Float) = Unit

    fun clearSelectionTransform() = Unit

    fun rebindCachedWidgets() {
        if (cachedItems != null && cachedAppWidgetManager != null && cachedAppWidgetHost != null) {
            if (viewWidth <= 0f || height <= 0) {
                post { if (viewWidth > 0f && height > 0) rebindCachedWidgets() }
                return
            }
            val liveCols = canvasView?.currentGridCols ?: cachedColumns
            val liveRows = canvasView?.currentGridRows ?: cachedRows
            bindWidgets(cachedItems!!, cachedAppWidgetManager!!, cachedAppWidgetHost!!, liveCols, liveRows)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat()
        if (cachedItems != null && cachedAppWidgetManager != null && cachedAppWidgetHost != null) {
            rebindCachedWidgets()
        } else {
            resyncScroll()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Layout passes can reset scrollX to 0 — reassert desired page offset
        applyDesiredScroll()
    }

    fun startDragHighlight(spanX: Int, spanY: Int, pixelSize: Pair<Int, Int>? = null) {
        dragHighlightDrawer.startDragHighlight(spanX, spanY, pixelSize)
    }

    fun updateDragHighlight(
        hoverPage: Int,
        pageLocalLeft: Float,
        pageLocalTop: Float
    ): WidgetDragHighlight.CellSnap? =
        dragHighlightDrawer.updateDragHighlight(hoverPage, pageLocalLeft, pageLocalTop)

    fun updateDragHighlight(x: Float, y: Float): WidgetDragHighlight.CellSnap? =
        dragHighlightDrawer.updateDragHighlight(x, y)

    fun setDragHighlightSuppressed(suppressed: Boolean) {
        dragHighlightDrawer.setSuppressed(suppressed)
    }

    fun clearDragHighlight() {
        dragHighlightDrawer.clearDragHighlight()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        dragHighlightDrawer.onDraw(canvas)
    }

    fun updateWidgetLayoutParams(
        appWidgetId: Int,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        targetPage: Int = liveItems[appWidgetId]?.page ?: 0
    ) {
        val cols = canvasView?.currentGridCols ?: cachedColumns
        val rows = canvasView?.currentGridRows ?: cachedRows
        WidgetOverlayLayoutParams.update(
            this, appWidgetId, xFraction, yFraction, spanX, spanY,
            liveItems, positionOverrides, viewWidth, cols, rows,
            singlePageWidth, gridAreaHeight, targetPage
        )
    }

    fun updateMosaicLayoutParams(
        itemId: Int,
        xFrac: Float,
        yFrac: Float,
        spanX: Int,
        spanY: Int,
        targetPage: Int = liveMosaics[itemId]?.page ?: 0
    ) {
        val cols = canvasView?.currentGridCols ?: cachedColumns
        val rows = canvasView?.currentGridRows ?: cachedRows
        WidgetOverlayLayoutParams.updateMosaic(
            this, itemId, xFrac, yFrac, spanX, spanY,
            liveMosaics, viewWidth, cols, rows,
            singlePageWidth, gridAreaHeight, targetPage
        )
    }

    fun clearPositionOverride(appWidgetId: Int) {
        positionOverrides.remove(appWidgetId)
    }
}
