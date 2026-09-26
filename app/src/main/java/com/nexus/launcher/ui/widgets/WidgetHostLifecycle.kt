package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class WidgetHostLifecycle(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel
) {
    val appWidgetManager: AppWidgetManager by lazy { AppWidgetManager.getInstance(activity) }
    val appWidgetHost: AppWidgetHost by lazy {
        object : NexusWidgetHost(activity, NexusWidgetHost.HOST_ID) {
            override fun onProvidersChanged() {
                super.onProvidersChanged()
                WidgetProviderCatalog.invalidateCache()
            }
        }
    }
    val widgetOverlayLayout: WidgetOverlayLayout by lazy { WidgetOverlayLayout(activity) }
    val widgetViewModel: WidgetViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(activity)[WidgetViewModel::class.java]
    }
    private var observationJob: Job? = null
    private val mainContainer by lazy {
        activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.main_container)
    }

    val appWidgetController: AppWidgetController by lazy {
        AppWidgetController(
            activity = activity,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager,
            canvasView = canvasView,
            homeScreenViewModel = homeScreenViewModel,
            widgetProviderCatalog = WidgetProviderCatalog(activity)
        )
    }

    private val editModeManager by lazy {
        WidgetEditModeManager(
            activity = activity,
            canvasView = canvasView,
            widgetOverlayLayout = widgetOverlayLayout,
            widgetViewModel = widgetViewModel,
            appWidgetController = appWidgetController,
            appWidgetManager = appWidgetManager,
            appWidgetHost = appWidgetHost
        )
    }

    private val mosaicSettingsSheet by lazy {
        com.nexus.launcher.ui.widgets.mosaic.LivingMosaicSettingsSheet(
            activity, homeScreenViewModel, appWidgetController, appWidgetHost
        )
    }

    private val mosaicEditSession by lazy {
        com.nexus.launcher.ui.widgets.mosaic.LivingMosaicEditSession(
            activity = activity,
            canvasView = canvasView,
            widgetOverlayLayout = widgetOverlayLayout,
            homeScreenViewModel = homeScreenViewModel,
            widgetViewModel = widgetViewModel,
            appWidgetController = appWidgetController,
            appWidgetHost = appWidgetHost,
            appWidgetManager = appWidgetManager,
            mosaicSettingsSheet = mosaicSettingsSheet
        )
    }

    private val mosaicFocusController by lazy {
        com.nexus.launcher.ui.widgets.mosaic.MosaicFocusController(
            activity, mainContainer
        )
    }

    fun hasActiveOverlays(): Boolean =
        editModeManager.hasActiveOverlays() ||
            mosaicEditSession.hasActive() ||
            mosaicSettingsSheet.isShowing() ||
            mosaicFocusController.isShowing()

    fun dismissOverlaysFromActivity() {
        mosaicFocusController.close()
        mosaicEditSession.dismiss()
        mosaicSettingsSheet.requestDismiss()
        editModeManager.dismissOverlaysFromActivity()
    }

    init {
        widgetOverlayLayout.canvasView = canvasView
        com.nexus.launcher.ui.widgets.music.RetroMusicAnimationCoordinator.bindOverlay(widgetOverlayLayout)
        val previousProgress = canvasView.onDrawerAnimationProgress
        canvasView.onDrawerAnimationProgress = { progress ->
            previousProgress?.invoke(progress)
            widgetOverlayLayout.setDrawerProgress(progress)
        }
        canvasView.onPageScroll = { page, offset ->
            widgetOverlayLayout.syncScroll(page, offset)
            com.nexus.launcher.ui.canvas.SelectionModeChromeSync.onPageScroll(canvasView, activity)
        }
        canvasView.onCanvasSizeChanged = { _, _ ->
            widgetOverlayLayout.rebindCachedWidgets()
        }
        val targetContainer = activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.workspace_container) ?: mainContainer
        targetContainer.addView(
            widgetOverlayLayout, 1,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        editModeManager.hasActiveOverlays()
        // Avoid re-entrant dismiss: mosaic.dismiss must not call editModeManager
        var clearingMosaic = false
        editModeManager.onDismissAll = {
            if (!clearingMosaic) {
                clearingMosaic = true
                mosaicEditSession.dismiss()
                clearingMosaic = false
            }
        }
        widgetOverlayLayout.onMosaicLongPress = { item, view ->
            editModeManager.dismissOverlaysFromActivity()
            mosaicEditSession.open(item, view)
        }
        widgetOverlayLayout.onMosaicPlaceholderTapped = { item, slot ->
            appWidgetController.launchWidgetPickerForMosaic(item, slot)
        }
        widgetOverlayLayout.onMosaicTileFocusRequested = { item, view, index ->
            mosaicFocusController.open(item, view, index)
        }
        widgetOverlayLayout.onMosaicConfigChanged = { item, config ->
            homeScreenViewModel.updateMosaicConfig(item, config)
        }
    }

    fun onStart() {
        // Defer RemoteViews updates on all existing host views BEFORE startListening().
        // startListening() causes AppWidgetService to replay the last RemoteViews for
        // every widget immediately, re-inflating their layouts while the window-return
        // animation is still running — this is the primary cause of visible flicker.
        // beginDefer() intercepts updateAppWidget() calls so they are buffered instead
        // of applied. commitDeferred() applies them 380ms later, after the animation.
        walkForHostViews { it.beginDefer() }
        appWidgetHost.startListening()
        Log.d("WidgetHostLifecycle", "startListening called")

        observationJob = activity.lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                homeScreenViewModel.homeScreenItems,
                homeScreenViewModel.folderContents
            ) { items, folderMap -> items to folderMap }.collect { (items, folderMap) ->
                widgetOverlayLayout.bindWidgets(
                    items = items,
                    appWidgetManager = appWidgetManager,
                    appWidgetHost = appWidgetHost,
                    columns = canvasView.currentGridCols,
                    rows = canvasView.currentGridRows,
                    folderContents = folderMap
                )
                widgetOverlayLayout.syncScroll(canvasView.currentPage, 0f)
            }
        }
        widgetOverlayLayout.resyncScroll()

        // Phase 1 (immediate, non-flashing): restore overlay state and poke Nexus
        // widget options so presentation is correct as soon as the window appears.
        widgetOverlayLayout.post { WidgetHostSurfaceRefresh.applyNonFlashing(widgetOverlayLayout) }

        // Phase 2 (after animation): commit buffered RemoteViews and reconnect
        // collection adapters. 380ms covers the typical window-return animation.
        widgetOverlayLayout.postDelayed({ walkForHostViews { it.commitDeferred() } }, ANIMATION_SETTLE_MS)

        WidgetRealTimeUpdateDispatcher.start(activity)
        if (com.nexus.launcher.ui.settings.NotificationPermissionStatus.hasNotificationAccess(activity)) {
            com.nexus.launcher.ui.widgets.music.NexusMusicManager.startListening(activity)
        }
    }

    fun onStop() {
        com.nexus.launcher.ui.widgets.music.RetroMusicAnimationCoordinator.pause()
        WidgetRealTimeUpdateDispatcher.stop(activity)
        com.nexus.launcher.ui.widgets.music.NexusMusicManager.stopListening()
        appWidgetHost.stopListening()
        Log.d("WidgetHostLifecycle", "stopListening called")
        observationJob?.cancel()
        observationJob = null
    }

    fun onHomeResumed() {
        mosaicFocusController.restoreAfterActivityReturn()
        widgetOverlayLayout.restoreHomePresentation()
        widgetOverlayLayout.resyncScroll()
        WidgetRealTimeUpdateDispatcher.dispatchImmediateTick(activity)
        com.nexus.launcher.ui.widgets.music.RetroMusicAnimationCoordinator.resume(activity)
        if (com.nexus.launcher.ui.widgets.liveapp.LiveAppRepository.hasActiveWidgets()) {
            com.nexus.launcher.ui.widgets.liveapp.LiveAppRepository.refresh(activity)
        }
        // rebindCachedWidgets() and WidgetHostSurfaceRefresh.schedule() removed:
        // onStart() already rebound after startListening. Re-binding again on
        // every settleLauncherChrome() (plain app return) is redundant work and
        // was the main amplifier of visible widget refresh on every app switch.
        // The schedule() debounce (600ms) would suppress the pulse anyway, but
        // not scheduling at all avoids even the non-flashing applyNonFlashing pass.
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        appWidgetController.handleActivityResult(requestCode, resultCode, data)
    }

    /**
     * Walks widgetOverlayLayout to find all NexusWidgetView instances and
     * calls [action] on each. Used to arm/commit the deferred-update mechanism
     * around startListening() in onStart().
     */
    private fun walkForHostViews(action: (NexusWidgetView) -> Unit) {
        walkGroup(widgetOverlayLayout, action)
    }

    private fun walkGroup(group: ViewGroup, action: (NexusWidgetView) -> Unit) {
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is NexusWidgetView -> action(child)
                is ViewGroup -> walkGroup(child, action)
            }
        }
    }

    companion object {
        /** Approx. window-return animation duration — used for deferred update commit. */
        const val ANIMATION_SETTLE_MS = 380L
    }
}
