package com.nexus.launcher.ui.canvas
import android.content.Context; import kotlinx.coroutines.launch; import kotlinx.coroutines.cancel
import android.graphics.Canvas; import android.graphics.Color; import android.graphics.Paint
import android.text.TextPaint; import android.text.TextUtils
import android.view.MotionEvent; import android.view.View
import androidx.core.view.ViewCompat; import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.data.HomeScreenItem; import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.model.DisplayItem; import com.nexus.launcher.ui.model.DragSource
import com.nexus.launcher.ui.model.DragState; import com.nexus.launcher.ui.model.GridItem
import com.nexus.launcher.ui.model.LauncherState; import com.nexus.launcher.ui.model.SelectionSource; import com.nexus.launcher.ui.model.SelectionState
class LauncherCanvasView(context: Context, internal val canvasRenderer: CanvasRenderer) : View(context) {
    internal val overlayManager = CanvasOverlayManager(this)
    var showDarkOverlay: Boolean
        get() = overlayManager.showDarkOverlay
        set(value) { overlayManager.showDarkOverlay = value }
    /** Frosted-glass hardware blur + dark tint for menu/overlay states (routed from all openers). */
    fun setBlurState(isBlurred: Boolean) = overlayManager.setBlurState(isBlurred)

    /** Lets FolderBlurCoordinator claim RenderEffect without fighting Gallery blur animation. */
    fun cancelBlurAnimator() = overlayManager.cancelBlurAnimator()

    /** See [CanvasOverlayManager.forceSyncBlurState] — bypasses the cached-flag no-op guard for
     *  resume-time correctness resyncs. */
    fun forceSyncBlurState(isBlurred: Boolean) = overlayManager.forceSyncBlurState(isBlurred)
    internal var mutatingStateSetAt: Long = 0L

    var isMutatingState: Boolean = false
        set(value) {
            field = value
            if (value) mutatingStateSetAt = System.currentTimeMillis()
            // This is written from 27 places, including LauncherTouchHandler's ACTION_MOVE — so
            // once per frame for the whole of a horizontal page drag. Capturing
            // `Thread.currentThread().stackTrace` walks and allocates the entire frame array
            // every time, on the exact gesture the trace was added to study.
            if (com.nexus.launcher.util.NexusDiag.ENABLED) {
                android.util.Log.d(
                    "MutatingState",
                    "set to $value from " + Thread.currentThread().stackTrace[3].methodName
                )
            }
        }
    var suppressDraw = false
    var isManagePagesOpen = false
    internal val renderScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val settingsApplier = CanvasSettingsApplier(this)
    private val stateAnimator = CanvasStateAnimator(this)
    internal var accentColor = android.graphics.Color.parseColor(com.nexus.launcher.data.prefs.NexusDefaults.ACCENT_COLOR)
    internal var viewWidth = 0; internal var viewHeight = 0
    internal val homeLabelColor: Int get() = CanvasDisplayHelper.resolveHomeLabelColor(context, canvasRenderer.wallpaperLuminance, canvasRenderer.backgroundLayerMode, canvasRenderer.wallpaperType)
    internal val homeLabelBold get() = true
    internal val isLandscape get() = LayoutProfile.isPhoneLandscape(context)
    internal val isRtl: Boolean get() = com.nexus.launcher.rtl.LayoutDirectionHelper.isRtl(context)
    internal fun getDisplayRotation(): Int = CanvasDisplayHelper.getDisplayRotation(this)
    internal var dockStripWidth: Int = 0
    internal val dockOnRight get() = !isLandscape || getDisplayRotation() == android.view.Surface.ROTATION_90
    internal val gridAreaLeft get() = HomeGridArea.left(this)
    internal val gridAreaWidth get() = HomeGridArea.width(this)
    internal var dockBottomReserve: Int = 0
    internal var dockVisualTopY: Float = 0f
    internal var effectiveHomeColumns: Int = 6
    internal var effectiveHomeRows: Int = 10
    /** Canonical grid dims for the CURRENT orientation — DrawEngineLayout builds homeGridCells from these (landscape swaps cols/rows). */
    internal val currentGridCols: Int get() = if (isLandscape) LandscapeGridSpec.columns(this) else gridRenderer.homeColumns
    internal val currentGridRows: Int get() = if (isLandscape) LandscapeGridSpec.rows(this) else gridRenderer.homeRows
    internal var fractionDerivedPositions: Map<Int, Triple<Int, Int, Int>> = emptyMap()
    internal var draftResizeItem: com.nexus.launcher.data.HomeScreenItem? = null
    internal var drawerItems: List<GridItem> = emptyList()
    internal var dockItems: List<GridItem> = emptyList()
    internal var rawDrawerApps: List<DisplayItem> = emptyList()
    internal var rawDockApps: List<DisplayItem> = emptyList()
    internal val drawEngine = LauncherDrawEngine(this)
    internal val gridRenderer = GridRenderer(columnCount = 5, rowCount = 12)
    internal val railRenderer = DrawerRailRenderer(context.resources.displayMetrics.density)
    internal val dragRenderer = DragRenderer(context.resources.displayMetrics.density)
    internal val homeScreenRenderer = HomeScreenRenderer(context, context.resources.displayMetrics.density)
    internal val selectionRenderer = SelectionRenderer(context.resources.displayMetrics.density)
    internal val multiDragHandler = HomeScreenMultiDragHandler()
    internal val badgeRenderer = BadgeRenderer(context.resources.displayMetrics.density)
    val dragHandler = DragHandler().apply { hostCanvasView = this@LauncherCanvasView }
    internal var selectionState: SelectionState = SelectionState.Idle
    internal var badgeCounts: Map<String, Int> = emptyMap()
    internal var badgeStyleApp: Int = 1
    internal var badgeStyleFolder: Int = 1
    internal var badgeStyle: Int get() = badgeStyleApp; set(value) { badgeStyleApp = value }
    var onDrawerChromeSettings: ((com.nexus.launcher.data.prefs.NexusSettingsData) -> Unit)? = null
    var onDrawerRailVisibility: ((Boolean) -> Unit)? = null; var onDrawerAnimationProgress: ((progress: Float) -> Unit)? = null
    internal val drawerChrome = DrawerChromeReserve()
    var onCanvasSizeChanged: ((Int, Int) -> Unit)? = null; var onEnterSelectionMode: ((String, SelectionSource) -> Unit)? = null
    var onToggleSelection: ((String) -> Unit)? = null; var onToggleHomeSelection: ((Int) -> Unit)? = null; var onClearSelection: (() -> Unit)? = null
    var onDismissContextMenu: (() -> Unit)? = null; var onHomeItemDropped: ((Int, Int, Float, Float, Int, Int) -> Unit)? = null; var onNativeDrop: ((DisplayItem, Int, Float, Float) -> Unit)? = null
    var onPageSwipe: ((delta: Int) -> Unit)? = null; var onPageScroll: ((currentPage: Int, dragScrollOffset: Float) -> Unit)? = null
    var onPageChange: ((Int) -> Unit)? = null; var onFeedSwipeBegan: (() -> Unit)? = null
    var onSwipeRightPageZero: ((rawOffset: Float, width: Float) -> Unit)? = null
    var onReleaseSwipeRightPageZero: ((rawOffset: Float, velocityX: Float, width: Float) -> Unit)? = null
    var showFeed: Boolean = true
    var pageTransitionAlpha = 1f
    var transitionStyle: String = "slide" // routed in PageTransitionDispatcher; set by CanvasSettingsApplier
    internal val pageFoldHandler = PageFoldGestureHandler(this)
    // Live horizontal offset of the home pages while a finger drag / snap is in flight.
    internal var dragScrollOffset: Float = 0f
    internal var pageAnimator: android.animation.ValueAnimator? = null

    /** Paging and the drawer's close run here rather than on an animator; see [PageSettleDriver]. */
    internal val pageSettle = PageSettleDriver()
    internal val drawerSettle = PageSettleDriver()
    private val canvasPageAnimator = CanvasPageAnimator(this)
    fun animatePageMutation(onEnd: () -> Unit) = canvasPageAnimator.animatePageMutation(onEnd)
    internal var currentPage: Int = 0
        private set
    var fastScrollLetter: String? = null
    var draggedItem: com.nexus.launcher.data.HomeScreenItem? = null
    var hoveredMergeTarget: com.nexus.launcher.data.HomeScreenItem? = null
    internal val mergeBounceSpring = com.nexus.launcher.ui.dock.DockSpringPhysics.Channel(0f)
    internal var cacheRefreshJob: kotlinx.coroutines.Job? = null
    var dragX: Float = 0f; var dragY: Float = 0f; var lastPageScrollTime: Long = 0L; var isDraggingIcon: Boolean = false
    var isHomeDragOverviewActive: Boolean = false
    var isNativeDragActive: Boolean = false

    fun setDragOverviewActive(active: Boolean) {
        if (isHomeDragOverviewActive == active) return
        isHomeDragOverviewActive = active
        (context as? com.nexus.launcher.ui.MainActivity)?.let { activity ->
            SelectionModeChromeSync.apply(this, activity)
        }
        invalidate()
    }
    fun setCurrentPage(page: Int) {
        if (currentPage == page) return
        currentPage = page

        // Ensure UI stays in sync without redundant DB calls
        onPageChange?.invoke(currentPage)
        onPageScroll?.invoke(currentPage, dragScrollOffset)
        invalidate()
    }
    fun commitPageSwipe(newPage: Int) {
        val pageChanged = currentPage != newPage
        currentPage = newPage
        dragScrollOffset = 0f
        isMutatingState = false
        if (pageChanged) onPageChange?.invoke(currentPage)
        onPageScroll?.invoke(currentPage, 0f)
        invalidate()
    }
    fun animateScrollToPage(targetPage: Int) = canvasPageAnimator.animateScrollToPage(targetPage)
    internal var totalPages: Int = 1
    fun updateTotalPages(settingPageCount: Int = 1) =
        CanvasLayoutCalculator.updateTotalPages(this, settingPageCount)
    fun setSelectionState(state: SelectionState) {
        selectionState = state
        dragHandler.onTouchCancel()
        draggedItem = null
        isDraggingIcon = false
        multiDragHandler.cancel()
        invalidate()
    }
    internal var homeScreenItems: List<HomeScreenItem> = emptyList()
    /** O(1) folder id → row for drawer/home folder draws (includes page=-2 drawer groups). */
    internal var folderById: Map<Long, HomeScreenItem> = emptyMap()
    internal val drawerIconCache = DrawerIconCache()
    internal val drawerFolderTileCache = DrawerFolderTileCache()
    /** Bumped when home items change so drawer-open blur node re-records. */
    internal var homeDrawGeneration: Int = 0
    internal var homeGridCells: List<android.graphics.RectF> = emptyList()
    internal var topInset = 0
    internal var uiState = LauncherState.HOME
        set(value) {
            if (field != value) { field = value; onDrawerAnimationProgress?.invoke(DrawerProgress.of(this)) }
        }
    var onIntentSelected: ((android.content.Intent) -> Unit)? = null
    var onStateChanged: ((LauncherState) -> Unit)? = null
    // Both of these announce drawer progress when they change; see DrawerProgress for why.
    internal var drawerTranslationY = -1f
        set(value) {
            if (field != value) { field = value; onDrawerAnimationProgress?.invoke(DrawerProgress.of(this)) }
        }
    internal var scrollY = 0f
    
    val folderStateManager = CanvasFolderStateManager(this)
    internal var folderOverlayFrozen: Boolean get() = folderStateManager.folderOverlayFrozen; set(v) { folderStateManager.folderOverlayFrozen = v }
    internal var frozenScrollY: Float get() = folderStateManager.frozenScrollY; set(v) { folderStateManager.frozenScrollY = v }
    internal var openFolderItemId: Int? get() = folderStateManager.openFolderItemId; set(v) { folderStateManager.openFolderItemId = v }
    internal var folderMorphProgress: Float get() = folderStateManager.folderMorphProgress; set(v) { folderStateManager.folderMorphProgress = v }
    internal var isFolderClosing: Boolean get() = folderStateManager.isFolderClosing; set(v) { folderStateManager.isFolderClosing = v }

    fun setFolderOpenState(itemId: Int?, morphProgress: Float) {
        folderStateManager.setFolderOpenState(itemId, morphProgress)
    }

    internal fun effectiveScrollY(): Float = folderStateManager.effectiveScrollY(scrollY)
    internal var maxScrollY = 0f
    var onItemLongPressed: ((DisplayItem, android.graphics.Rect, View) -> Unit)? = null
    var onHomeIconLongPressed: ((DisplayItem, android.graphics.Rect, View) -> Unit)? = null
    var onHomeIconLongPressedWithId: ((DisplayItem, android.graphics.Rect, View, Int) -> Unit)? = null
    var onEmptyHomeScreenLongPress: (() -> Unit)? = null
    internal var longPressRunnable: Runnable? = null
    internal var homeLongPressRunnable: Runnable? = null
    internal var folderLongPressRunnable: Runnable? = null
    internal var folderContextMenuPending = false
    internal var folderContextMenuItem: HomeScreenItem? = null
    
    internal val folderGlowManager = FolderGlowManager(this)
    internal val homeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    internal var homeLongPressConsumed = false
    internal var drawerLongPressConsumed = false
    internal var longPressHomeCol = 0
    internal var longPressHomeRow = 0
    var isSearchQueryEmpty: Boolean = true
    var showSearchBackground: Boolean = false
    internal var currentThemeTokens = com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
    internal val searchBgPaint = Paint().apply { color = (currentThemeTokens.bg and 0x00FFFFFF) or (0x99 shl 24) }
    internal val overlayBgPaint = Paint().apply { color = (currentThemeTokens.bg and 0x00FFFFFF) or (0xCC shl 24) }
    // Dark scrim that fades in behind the drawer as it opens over the home screen
    internal val overlayPaint = Paint().apply { color = currentThemeTokens.bg; style = Paint.Style.FILL }
    var isSearchMode = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    scrollY = 0f
                    drawerTranslationY = 0f
                }
                recalculateLayout()
            }
        }
    var currentOverlayTotalHeight: Int = 0
        set(value) {
            if (field != value) {
                field = value
                if (isSearchMode) recalculateLayout()
            }
        }
    internal var bottomBarHeight = 0
    internal var drawerGridTop = 0
    internal var drawerGridBottom = 0
    internal var railTopY: Int = 0
    internal var railBottomY: Int = 0
    internal var activeLetter: Char? = null
    internal var drawerIconSizeMultiplier: Float = 0.65f
    internal var homeIconSizeMultiplier: Float = 0.65f
    internal var homePaddingLeftRightDp: Float = 0f

    /** What the user set; [homePaddingTopBottomDp] is this, bounded by [HomeGridPadding]. */
    internal var homePaddingTopBottomSetting: Float = 0f

    /** The vertical grid padding actually used — see [HomeGridPadding]. */
    internal val homePaddingTopBottomDp: Float
        get() = HomeGridPadding.effective(homePaddingTopBottomSetting, topInset, resources.displayMetrics.density)
    internal var homeGapHorizontalDp: Float = 0f
    internal var homeGapVerticalDp: Float = 0f
    internal var showDrawerLabels: Boolean = true
    internal var drawerTwoLineLabels: Boolean = false
    internal var drawerTransition: String = com.nexus.launcher.data.prefs.NexusDefaults.DRAWER_TRANSITION
    fun setBottomBarHeight(height: Int) {
        if (bottomBarHeight != height) {
            bottomBarHeight = height
            recalculateLayout()
        }
    }
    internal var lastY = 0f
    internal var isDragging = false
    internal var layoutDirty = false
    internal var animator: android.animation.ValueAnimator? = null
    fun resetScrollY() {
        scrollY = 0f
        invalidate()
    }
    // Drag callbacks delegated to DragHandler
    var onDragStarted: ((DisplayItem, Float, Float) -> Unit)? get() = dragHandler.onDragStarted; set(v) { dragHandler.onDragStarted = v }
    var onDragMoved: ((Float, Float) -> Unit)? get() = dragHandler.onDragMoved; set(v) { dragHandler.onDragMoved = v }
    var onDragDropped: ((Float, Float) -> Unit)? get() = dragHandler.onDragDropped; set(v) { dragHandler.onDragDropped = v }
    var onDragCancelled: (() -> Unit)? get() = dragHandler.onDragCancelled; set(v) { dragHandler.onDragCancelled = v }
    /** Collapses the drawer to HOME in a single frame — no animation. */
    fun closeDrawerInstantly() = DrawerSnapAnimator.closeDrawerInstantly(this)
    fun updateHomeScreenItems(items: List<HomeScreenItem>) =
        CanvasHomeItemsApplier.apply(this, items)
    fun setDragState(state: DragState) {
        dragHandler.setDragState(state)
        if (state is DragState.Idle && layoutDirty) {
            recalculateLayout()
        }
        invalidate()
    }

    /** Apply persisted NexusSettings — propagates grid dimensions and visual prefs to renderers. */
    fun applySettings(settings: NexusSettingsData) = settingsApplier.applySettings(settings)
    fun applyRenamedLabels(renamedApps: Map<String, String>) =
        settingsApplier.applyRenamedLabels(renamedApps)
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawerPrewarm.cancel()
        renderScope.cancel()
    }
    private val drawerPrewarm = DrawerPrewarmScheduler(this)
    internal val scroller by lazy {
        // No interpolator: OverScroller has its own physics-based deceleration curve.
        // Passing a DecelerateInterpolator on top doubles the deceleration, making the
        // tail of a fling feel sluggish. Halved friction keeps the long-momentum feel.
        android.widget.OverScroller(context).apply {
            setFriction(android.view.ViewConfiguration.getScrollFriction() * 0.5f)
        }
    }
    internal var velocityTracker: android.view.VelocityTracker? = null
    internal val touchSlop by lazy { android.view.ViewConfiguration.get(context).scaledTouchSlop }
    internal var startTouchX = 0f
    internal var startTouchY = 0f
    private fun recalculateTopInset() = CanvasLayoutCalculator.recalculateTopInset(this)
    init {
        canvasRenderer.invalidateCallback = {
            // Fires when a wallpaper finishes loading or its colours change — the glass samples
            // have to follow, not just the canvas (see GlassBackdropRefresh).
            post { overlayManager.refreshBlurBackgroundIfNeeded(); com.nexus.launcher.ui.folder.GlassBackdropRefresh.refresh(this); invalidate() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            recalculateTopInset()
            insets
        }
        post { drawerTranslationY = viewHeight.toFloat() }
        railRenderer.onActiveLetterChanged = { letter ->
            activeLetter = letter
            invalidate()
        }
        setOnDragListener(LauncherDragListener(this))
        settingsApplier.initThemeObservers(this, renderScope)
    }
    internal val textPaint = TextPaint().apply {
        color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER; isAntiAlias = true
    }
    fun updateTypeface(typeface: android.graphics.Typeface) {
        textPaint.typeface = typeface
        homeScreenRenderer.updateTypeface(typeface)
        com.nexus.launcher.ui.dock.DockLabelRenderer.updateTypeface(typeface)
        invalidate()
    }
    fun setUiState(state: LauncherState) = stateAnimator.setUiState(state)
    fun updateGrid(docked: List<DisplayItem>, drawer: List<DisplayItem>) {
        this.rawDockApps = docked
        this.rawDrawerApps = drawer
        recalculateLayout()
        drawerPrewarm.onGridUpdated()
    }
    internal fun triggerDrawerPrewarm() = drawerPrewarm.triggerNow()
    internal fun cancelDrawerPrewarmInFlight() = drawerPrewarm.cancelInFlight()

    internal fun recalculateLayout() = CanvasLayoutCalculator.recalculateLayout(this)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w; viewHeight = h
        if (uiState == LauncherState.HOME || drawerTranslationY < 0f) {
            drawerTranslationY = if (uiState == LauncherState.HOME) h.toFloat() else 0f
        }
        recalculateLayout()
        invalidate() // Ensure redraw if settings arrived before view had dimensions
        onCanvasSizeChanged?.invoke(w, h)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawEngine.onDraw(canvas)
        overlayManager.drawOverlayIfNeeded(canvas, viewWidth, viewHeight)
    }
    private fun drawItem(canvas: Canvas, item: GridItem, baseAlpha: Int) = drawEngine.drawItem(canvas, item, baseAlpha)
    private val touchHandler = LauncherTouchHandler(this)
    override fun onTouchEvent(event: MotionEvent): Boolean = touchHandler.onTouchEvent(event)
}
