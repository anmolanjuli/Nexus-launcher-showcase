package com.nexus.launcher.ui
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.ui.canvas.CanvasRenderer
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.dock.DockContextMenuLauncher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    val viewModel: MainViewModel by viewModels()
    private val homeScreenViewModel: HomeScreenViewModel by viewModels()

    private var currentPage = 0
    private lateinit var overflowController: OverflowMenuController
    private lateinit var drawerSearchController: DrawerSearchController
    private lateinit var drawerChromeController: DrawerChromeController

    /** The drawer's floating bars, for anything blurring the launcher behind a surface. */
    fun drawerChromeViews(): List<android.view.View> =
        if (::drawerChromeController.isInitialized) drawerChromeController.chromeViews else emptyList()
    private lateinit var homeKeyController: HomeKeyController
    private lateinit var dockLifecycle: com.nexus.launcher.ui.dock.DockLifecycle
    @Inject
    lateinit var canvasRenderer: CanvasRenderer
    @Inject lateinit var dockSettingsRepository: com.nexus.launcher.ui.dock.settings.DockSettingsRepository
    @Inject lateinit var preferenceManager: com.nexus.launcher.data.prefs.PreferenceManager
    @Inject lateinit var feedRepository: com.nexus.launcher.feed.NexusFeedRepository
    @Inject lateinit var feedDao: com.nexus.launcher.data.FeedDao
    lateinit var feedPanelController: com.nexus.launcher.feed.FeedPanelController
    lateinit var canvasView: LauncherCanvasView
    private val layoutShape = LayoutShapeCoordinator(this) { canvasView }
    private lateinit var selectionActionBar: android.widget.LinearLayout
    private lateinit var selectionActionBarHelper: SelectionActionBarHelper

    private lateinit var searchBar: EditText
    private lateinit var newScroll: android.widget.HorizontalScrollView
    private lateinit var newTitle: android.widget.TextView
    private lateinit var recentScroll: android.widget.HorizontalScrollView
    private lateinit var recentTitle: android.widget.TextView
    lateinit var uiHelpers: LauncherUiHelpers
    private lateinit var observers: LauncherObservers
    lateinit var contextMenuManager: com.nexus.launcher.ui.contextmenu.ContextMenuManager
    lateinit var homeEditController: HomeEditController
    lateinit var widgetHostLifecycle: com.nexus.launcher.ui.widgets.WidgetHostLifecycle

    private val wallpaperReceiver = MainActivityWallpaperReceiver(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before anything reads the launch intent — see HomeRoute.
        HomeRoute.unwrap(intent)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        setContentView(R.layout.activity_main)
        canvasView = LauncherCanvasView(this, canvasRenderer)
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setMainCanvas(canvasView)
        uiHelpers = LauncherUiHelpers(this, canvasView, viewModel)
        val mainContainer = findViewById<FrameLayout>(R.id.main_container)
        val workspaceContainer = findViewById<FrameLayout>(R.id.workspace_container) ?: mainContainer
        mainContainer.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        workspaceContainer.addView(canvasView, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        selectionActionBar = MainActivitySelectionBar.create(this, mainContainer)
        selectionActionBarHelper = SelectionActionBarHelper(
            this, mainContainer, selectionActionBar, viewModel, homeScreenViewModel
        ) { currentPage }

        contextMenuManager = com.nexus.launcher.ui.contextmenu.ContextMenuManager(this, mainContainer, viewModel, homeScreenViewModel)
        dockLifecycle = com.nexus.launcher.ui.dock.DockLifecycle(this, mainContainer, canvasView, homeScreenViewModel, contextMenuManager)
        dockLifecycle.setup()
        dockLifecycle.dockLayout.also { dock ->
            dock.onItemLongClicked = { DockContextMenuLauncher.show(this, contextMenuManager, dock, it) }
            dock.onDockSettingsRequested = { com.nexus.launcher.ui.dock.settings.DockSettingsBottomSheet(dockSettingsRepository, dock).show(supportFragmentManager, "DockSettings") }
        }
        overflowController = OverflowMenuController(this, mainContainer, canvasView, uiHelpers)
        contextMenuManager.apply {
            onEnterSelectionMode = { pkg, source -> homeScreenViewModel.enterSelectionMode(pkg, source) }
            onEnterHomeSelectionMode = { id, pkg -> homeScreenViewModel.enterHomeSelectionMode(id, pkg) }
            onMenuDismissed = {
                if (!canvasView.dragHandler.isIconDragActive) canvasView.dragHandler.onTouchCancel()
                canvasView.isDragging = false
            }
        }
        viewModel.isGestureNav = android.provider.Settings.Secure.getInt(
            contentResolver, "navigation_mode", 0
        ) == 2
        val searchFab = findViewById<android.widget.ImageView>(R.id.search_fab)
        val searchOverlay = findViewById<android.widget.LinearLayout>(R.id.search_overlay)
        val searchCloseBtn = findViewById<android.widget.ImageView>(R.id.search_close_btn)
        val recentContainer = findViewById<android.widget.LinearLayout>(R.id.recent_container)
        val newContainer = findViewById<android.widget.LinearLayout>(R.id.new_container)
        searchBar = findViewById(R.id.search_bar)
        recentScroll = findViewById(R.id.recent_scroll)
        recentTitle = findViewById(R.id.recent_title)
        newScroll = findViewById(R.id.new_scroll)
        newTitle = findViewById(R.id.new_title)
        drawerSearchController = DrawerSearchController(this, canvasView, viewModel, uiHelpers)
        drawerSearchController.setup(
            searchFab, searchOverlay, searchCloseBtn, searchBar,
            newScroll, newTitle, recentScroll, recentTitle
        )
        val overflowBtn = findViewById<android.view.View>(R.id.drawer_overflow_btn)
        val overflowIcon = findViewById<android.widget.ImageView>(R.id.drawer_overflow_icon)
        val fabContainer = findViewById<android.view.View>(R.id.search_fab_container)

        drawerChromeController = DrawerChromeController(this, canvasView, viewModel)
        drawerChromeController.attach(findViewById(R.id.main_container), overflowBtn) { icon ->
            // One trigger for the whole drawer, wherever the current arrangement parks it — the
            // click listener is bound once, here, and survives every chrome rebuild.
            overflowController.setupClickListener(icon)
        }
        // Rebuilds the chrome whenever the persisted preferences change; the applier fires once on
        // the first settings emission, so the arrangement is settled before the first draw.
        canvasView.onDrawerChromeSettings = { s -> drawerChromeController.applySettings(s) }
        fabContainer.visibility = android.view.View.GONE
        overflowBtn.visibility = android.view.View.GONE

        MainActivityDrawerChromeBinder.bind(
            this, canvasView, overflowBtn, fabContainer,
            isHomeEditMode = { if (this::homeEditController.isInitialized) homeEditController.isHomeEditMode else false },
            chromeViews = { drawerChromeController.chromeViews },
            railOverflowVisible = { drawerChromeController.railOverflowIsHome }
        )
        
        overflowController.positionButton(overflowBtn)
        MainActivityFlowObservers.bind(
            lifecycleOwner = this,
            viewModel = viewModel,
            homeScreenViewModel = homeScreenViewModel,
            dockSettingsRepository = dockSettingsRepository,
            overflowController = overflowController,
            overflowBtn = overflowBtn,
            canvasView = canvasView,
            dockLifecycle = dockLifecycle,
            contextMenuManager = contextMenuManager,
            getCurrentPage = { currentPage }
        )
        overflowController.setupClickListener(overflowIcon)
        observers = LauncherObservers(
            this, canvasView, viewModel, homeScreenViewModel,
            selectionActionBar, selectionActionBarHelper,
            recentContainer, newContainer,
            searchOverlay, searchBar,
            findViewById<android.view.View>(R.id.search_fab_container)
        )
        observers.register()
        val canvasListeners = LauncherCanvasListeners(
            this, canvasView, viewModel, homeScreenViewModel, uiHelpers, contextMenuManager,
            { currentPage }, { newPage -> currentPage = newPage; canvasView.setCurrentPage(newPage) }
        )
        canvasListeners.register()
        dockLifecycle.wireDrawerDrop()
        dockLifecycle.wireOutboundDrag(
            com.nexus.launcher.ui.canvas.DragTouchHandler(canvasView), contextMenuManager)
        homeEditController = HomeEditController(this, canvasView, viewModel, homeScreenViewModel, uiHelpers) { currentPage }
        widgetHostLifecycle = com.nexus.launcher.ui.widgets.WidgetHostLifecycle(this, canvasView, homeScreenViewModel)
        feedPanelController = com.nexus.launcher.feed.FeedPanelController(this, feedRepository, feedDao)
        canvasView.onFeedSwipeBegan = { feedPanelController.onCanvasFeedSwipeBegan() }
        canvasView.onSwipeRightPageZero = { offset, width ->
            feedPanelController.onCanvasSwipeRight(offset, width)
        }
        canvasView.onReleaseSwipeRightPageZero = { offset, vx, width ->
            feedPanelController.onCanvasSwipeReleased(offset, vx, width)
        }
        MainActivityBackPressRouter.install(
            activity = this,
            widgetHostLifecycle = widgetHostLifecycle,
            canvasView = canvasView,
            homeScreenViewModel = homeScreenViewModel,
            homeEditController = homeEditController,
            overflowController = overflowController,
            contextMenuManager = contextMenuManager,
            viewModel = viewModel,
            uiHelpers = uiHelpers,
            searchOverlay = searchOverlay,
            searchBar = searchBar,
            newScroll = newScroll,
            newTitle = newTitle,
            recentScroll = recentScroll,
            recentTitle = recentTitle
        )
        homeKeyController = HomeKeyController(
            this, viewModel, homeScreenViewModel, uiHelpers,
            contextMenuManager, overflowController, homeEditController,
            searchBar, newScroll, newTitle, recentScroll, recentTitle
        )
        homeKeyController.register()
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            feedRepository.seedDefaultSources(feedDao)
        }

        val filter = android.content.IntentFilter(android.content.Intent.ACTION_WALLPAPER_CHANGED)
        registerReceiver(wallpaperReceiver, filter)
    }
    override fun onNewIntent(intent: android.content.Intent) {
        // First, ahead of the Home-button check below: a routed widget or Settings intent arrives
        // as MAIN + HOME, and would otherwise close the drawer and jump to the default page before
        // its real action ran.
        HomeRoute.unwrap(intent)
        android.util.Log.d("SearchDebug", "onNewIntent action=${intent.action} flags=0x${Integer.toHexString(intent.flags)} component=${intent.component}")
        super.onNewIntent(intent)
        setIntent(intent)
        // Home button pressed while launcher is already active
        val isHomeIntent = intent.action == android.content.Intent.ACTION_MAIN && 
                           intent.hasCategory(android.content.Intent.CATEGORY_HOME)
        if (isHomeIntent) {
            com.nexus.launcher.search.ui.NexusSearchOverlay.dismiss(this)
            contextMenuManager.dismiss()
            overflowController.dismiss()
            homeEditController.dismissHomeEditMode()
            homeKeyController.navigateHome()
        }
        // Deep link from Nexus Settings > Appearance's Frosted Glass section — the actual
        // capture mechanism needs THIS activity's own window (it hides canvas/widget-overlay/
        // dock and captures the composited result), so Settings can only hand off to here
        // rather than performing the capture itself.
        if (intent.action == ACTION_OPEN_WALLPAPER_CAPTURE) {
            homeEditController.showWallpaperSheet(initialTab = "system")
        }
    }

    companion object {
        const val ACTION_OPEN_WALLPAPER_CAPTURE = "com.nexus.launcher.ACTION_OPEN_WALLPAPER_CAPTURE"
    }
    var preserveDrawerOnResume: Boolean = false

    override fun onResume() {
        super.onResume()
        consumeCalendarPermissionIntent(intent)
        consumeLocationPermissionIntent(intent)
        consumeProgressWidgetIntent(intent)
        consumeNotesWidgetIntent(intent)
        refreshCalendarWidgetsIfPermissionGained()
        refreshWeatherWidgetsIfPermissionGained()
        refreshMusicWidgetsIfAccessGained()
        MainActivityStyleSync.syncStyle(this, viewModel.nexusSettings.value)

        // Snapshot the settled, unblurred home screen rather than the frosted frame the exit
        // transition leaves behind. Rate-limited inside HomeSnapshotStore, and skipped outright
        // unless home is idle and unblurred.
        canvasView.postDelayed({ runCatching { captureHomeSnapshot(force = true) } }, 900L)

        val isSearchIntent = intent?.action == "com.nexus.launcher.ACTION_OPEN_SEARCH"
        if (!isSearchIntent) {
            homeEditController.dismissHomeEditMode()
            if (preserveDrawerOnResume) {
                preserveDrawerOnResume = false
            } else {
                homeKeyController.settleLauncherChrome()
            }
        }
        // After the settle, not before: settleLauncherChrome() starts with setBlurState(false),
        // so coming back from the photo picker animated away the frost this had just restored
        // under the still-open wallpaper sheet. This is the correctness resync — it goes last.
        homeEditController.reassertCanvasBlurOnResume()

        if (isSearchIntent) {
            intent.action = null // consume it
            window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            com.nexus.launcher.search.ui.NexusSearchOverlay.show(this, com.nexus.launcher.search.SearchContext.HOME_SCREEN)
        }
        // Onboarding permission sheet disabled while in development
        // com.nexus.launcher.ui.onboarding.OnboardingPilotController.checkAndShow(this)
        // com.nexus.launcher.ui.onboarding.OnboardingPilotController.onResume()
        viewModel._usagePermissionGranted.value =
            viewModel.isUsageStatsPermissionGranted()
        if (viewModel.isUsageStatsPermissionGranted()) {
            viewModel.refreshRecentApps()
        }
        if (preferenceManager.getPendingPrefsRefresh()) {
            preferenceManager.setPendingPrefsRefresh(false)
            viewModel.refreshPerAppPreferences()
        }
        if (preferenceManager.getPendingWidgetRebind()) {
            preferenceManager.setPendingWidgetRebind(false)
            // Do NOT call refreshPerAppPreferences() here — it re-emits apps and clears
            // icon/bitmap caches, making the drawer laggy for seconds after Nova restore.
            lifecycleScope.launch {
                val pending = homeScreenViewModel.getAllItemsSnapshot().filter { it.appWidgetId == -1 && (it.itemType == 3 || it.itemType == 4) }
                if (pending.isNotEmpty()) {
                    MainActivityWidgetRestorer.restoreWidgets(
                        this@MainActivity,
                        homeScreenViewModel,
                        widgetHostLifecycle.appWidgetController,
                        pending
                    ) {
                        widgetHostLifecycle.widgetOverlayLayout.rebindCachedWidgets()
                        widgetHostLifecycle.widgetOverlayLayout.invalidate()
                        canvasView.invalidate()
                    }
                }
            }
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig); layoutShape.onConfigurationChanged()
        val overflowBtn = findViewById<android.view.View>(R.id.drawer_overflow_btn)
        overflowController.onConfigurationChanged(overflowBtn)
        canvasView.post {
            canvasView.viewWidth = canvasView.width
            canvasView.viewHeight = canvasView.height
            canvasView.recalculateLayout()
            canvasView.invalidate()
            overflowController.onConfigurationChanged(overflowBtn)
            widgetHostLifecycle.widgetOverlayLayout.resyncScroll()
            widgetHostLifecycle.widgetOverlayLayout.rebindCachedWidgets()
            feedPanelController.onConfigurationChanged()
        }
        ViewCompat.requestApplyInsets(canvasView)
        // Clear cached folder overlay on any configuration change (e.g. rotation, uiMode).
        // Prevents the singleton from holding a stale View reference if the layout changes.
        // Skip if a folder is currently open — its live overlay must not be torn down here.
        if (!com.nexus.launcher.ui.folder.FolderOverlayController.isShowing()) {
            com.nexus.launcher.ui.folder.FolderOverlayController.invalidateCache(this)
        }
    }
    /**
     * Fires while the window is still on screen — PixelCopy needs a live surface, so this is the
     * capture point rather than onPause, which can run after the surface is already gone.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        captureHomeSnapshot()
    }

    override fun onPause() {
        super.onPause()
        // Second chance for the paths onUserLeaveHint does not cover (screen off, an activity
        // started by something other than the user). A dead surface just fails the copy.
        captureHomeSnapshot()
    }

    /**
     * Backups carry a picture of the real home screen, and only this Activity can take one — the
     * exporter runs in SettingsActivity, where the live launcher is unreachable. Skipped whenever
     * anything is layered over home, so a backup cover never bakes in the edit-mode radial menu
     * or an open drawer.
     */
    private fun captureHomeSnapshot(force: Boolean = false) {
        val isHomeIdle = viewModel.uiState.value == com.nexus.launcher.ui.model.LauncherState.HOME &&
            !(this::homeEditController.isInitialized && homeEditController.isHomeEditMode) &&
            !com.nexus.launcher.ui.folder.FolderOverlayController.isShowing() &&
            // The workspace blurs itself on the way out so Settings has a frosted backdrop to
            // sample. PixelCopy reads the real frame, blur included — capturing then would store
            // a frosted smear as the backup's cover.
            !canvasView.canvasRenderer.isWorkspaceBlurred &&
            canvasView.canvasRenderer.dynamicBlur <= 0f
        runCatching {
            com.nexus.launcher.ui.backup.HomeSnapshotStore.capture(this, isHomeIdle, force)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wallpaperReceiver)
        homeKeyController.unregister()
        com.nexus.launcher.ui.folder.FolderOverlayController.invalidateCache(this)
    }
    override fun onStart() { 
        super.onStart(); widgetHostLifecycle.onStart(); layoutShape.onStart()
    }
    override fun onStop() { 
        super.onStop(); widgetHostLifecycle.onStop(); layoutShape.onStop()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetHostLifecycle.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            canvasRenderer.invalidateSystemWallpaperCache()
            canvasView.invalidate()
        }
        onCalendarPermissionResult(requestCode, grantResults)
        onLocationPermissionResult(requestCode, grantResults)
    }
}

