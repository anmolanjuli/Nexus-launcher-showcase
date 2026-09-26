package com.nexus.launcher.ui

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

class LauncherObservers(
    private val activity: MainActivity,
    private val canvasView: com.nexus.launcher.ui.canvas.LauncherCanvasView,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val selectionActionBar: android.widget.LinearLayout,
    private val selectionActionBarHelper: SelectionActionBarHelper,
    private val recentContainer: android.widget.LinearLayout,
    private val newContainer: android.widget.LinearLayout,
    private val searchOverlay: android.widget.LinearLayout,
    private val searchBar: android.widget.EditText,
    private val searchFabContainer: android.view.View
) {
    private val settingsObserver = LauncherSettingsObserver(activity, canvasView, searchFabContainer)

    fun register() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var latestDocked = emptyList<com.nexus.launcher.ui.model.DisplayItem>()
                var latestDrawer = emptyList<com.nexus.launcher.ui.model.DisplayItem>()
                
                launch {
                    viewModel.dockedApps.collect { docked ->
                        latestDocked = docked
                        canvasView.updateGrid(latestDocked, latestDrawer)
                    }
                }
                
                launch {
                    viewModel.filteredDrawerItems.collect { drawer ->
                        latestDrawer = drawer
                        canvasView.updateGrid(latestDocked, latestDrawer)
                    }
                }
                launch {
                    kotlinx.coroutines.flow.combine(
                        viewModel.fontSelectionKey,
                        viewModel.customFonts
                    ) { fontKey, customFonts ->
                        fontKey to customFonts
                    }.collect { (fontKey, customFonts) ->
                        val typeface = com.nexus.launcher.typography.DynamicFontProvider(activity, fontKey, customFonts)
                            .getTypeface(com.nexus.launcher.typography.NexusTypeScale.iconLabel.weight)
                        canvasView.updateTypeface(typeface)
                    }
                }
                launch {
                    viewModel.invalidatedPackages.collect { pkg ->
                        canvasView.drawerIconCache.invalidatePackage(pkg)
                        try {
                            EntryPointAccessors.fromApplication(
                                canvasView.context.applicationContext,
                                com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                            ).themedIconFactory().clearGlyphCache(pkg)
                        } catch (_: Exception) {}
                    }
                }
                launch {
                    val windowInsetsController = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    launch {
                        com.nexus.launcher.theme.ThemeObserver.observe(activity, this) { tokens ->
                            val isLight = tokens.bg == com.nexus.launcher.theme.NexusColorTokens.Light.bg ||
                                    androidx.core.graphics.ColorUtils.calculateLuminance(tokens.bg) > 0.5
                            windowInsetsController.isAppearanceLightStatusBars = isLight
                            windowInsetsController.isAppearanceLightNavigationBars = isLight
                            activity.widgetHostLifecycle.widgetOverlayLayout.onThemeChanged()
                        }
                    }

                    viewModel.uiState.collect { state ->
                        canvasView.setUiState(state)
                        if (state == LauncherState.DRAWER) {
                            // Immersive mode already hides the bars, with a behaviour that keeps
                            // Back to one swipe; switching it here would make Back take two.
                            if (viewModel.isGestureNav && !ImmersiveModeController.isActive) {
                                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
                            }
                            
                            if (searchOverlay.visibility != View.VISIBLE) {
                                // The drawer chrome (DrawerChromeController) replaces the FAB and the
                                // standalone overflow button. The binder controls visibility;
                                // do NOT re-show them here.
                                searchFabContainer.visibility = View.GONE
                            }
                        } else {
                            // Immersive mode keeps the bars hidden on the home screen too.
                            if (viewModel.isGestureNav && !ImmersiveModeController.isActive) {
                                windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
                            }
                            searchOverlay.visibility = View.GONE
                            searchBar.setText("")
                            searchBar.clearFocus()
                            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
                        }
                        if (state == LauncherState.HOME) {
                            if (android.os.Build.VERSION.SDK_INT >= 31) {
                                canvasView.setRenderEffect(null)
                            }
                            activity.contextMenuManager.dismiss()

                        }
                    }
                }
                launch {
                    viewModel.recentApps.collect { recents ->
                        recentContainer.removeAllViews()
                        if (!viewModel.isUsageStatsPermissionGranted()) {
                            val density = recentContainer.resources
                                .displayMetrics.density
                            val promptView = android.widget.TextView(
                                activity).apply {
                                text = activity.getString(com.nexus.launcher.R.string.search_enable_recent_apps)
                                setTextColor(android.graphics.Color
                                    .parseColor("#997EB8D4"))
                                textSize = 12f
                                setPadding(
                                    (16*density).toInt(),
                                    (8*density).toInt(),
                                    (16*density).toInt(),
                                    (8*density).toInt())
                                setOnClickListener {
                                    val intent = android.content.Intent(
                                        android.provider.Settings
                                        .ACTION_USAGE_ACCESS_SETTINGS)
                                    activity.startActivity(intent)
                                }
                            }
                            recentContainer.addView(promptView)
                            return@collect
                        }
                        recents.forEach { recent ->
                            val itemLayout = activity.uiHelpers.createIconLayout(recent)
                            recentContainer.addView(itemLayout)
                        }
                    }
                }
                launch {
                    viewModel.newApps.collect { newApps ->
                        newContainer.removeAllViews()
                        newApps.forEach { newApp ->
                            val itemLayout = activity.uiHelpers.createIconLayout(newApp)
                            newContainer.addView(itemLayout)
                        }
                    }
                }
                launch {
                    homeScreenViewModel.homeScreenItems.collect { items ->
                        canvasView.updateHomeScreenItems(items)
                    }
                }
                launch {
                    homeScreenViewModel.folderContents.collect { contents ->
                        canvasView.drawerFolderTileCache.clear()
                        canvasView.homeScreenRenderer.applyFolderContents(contents)
                        canvasView.invalidate()
                    }
                }
                launch {
                    homeScreenViewModel.dragState.collect { state ->
                        canvasView.setDragState(state)
                    }
                }
                launch {
                    com.nexus.launcher.service.NexusNotificationService.badgeCounts.collect { counts ->
                        canvasView.badgeCounts = counts
                        canvasView.invalidate()
                    }
                }
                launch {
                    viewModel.apps.collect { apps ->
                        canvasView.homeScreenRenderer.iconCache.clear()
                        canvasView.homeScreenRenderer.bitmapCache.clear()
                        canvasView.drawerIconCache.clear()
                        // Folder previews cache resolved drawables and rendered bitmaps under
                        // keys that do not mention the icon source, so they survive an icon pack
                        // change unless dropped explicitly.
                        canvasView.homeScreenRenderer.folderIconRenderer.evictIconCaches()
                        com.nexus.launcher.ui.dock.DockLayoutRenderer.evictFolderIconCaches()
                        // Clear dockLayout cache as well
                        // Type check, not a simpleName string: R8 renames DockLayout to `ys` in
                        // release, so comparing the name never matched and the dock's icon cache
                        // was never cleared. DockIconLoader only fills gaps
                        // (`if (iconCache.containsKey(...)) continue`), so the dock then kept the
                        // previous icon pack's icons for the life of the process.
                        fun findDockLayout(root: android.view.ViewGroup): com.nexus.launcher.ui.dock.DockLayout? {
                            for (i in 0 until root.childCount) {
                                val child = root.getChildAt(i)
                                if (child is com.nexus.launcher.ui.dock.DockLayout) return child
                                if (child is android.view.ViewGroup) {
                                    findDockLayout(child)?.let { return it }
                                }
                            }
                            return null
                        }
                        val decorView = activity.window.decorView as android.view.ViewGroup
                        val dockLayout = findDockLayout(decorView)

                        // Schedule IO cache refetch for home screen apps and folders
                        canvasView.homeScreenRenderer.refreshAllCaches(canvasView.homeScreenItems)
                        // Re-resolve in place instead of clearing first. The dock draws straight
                        // from this cache and skips any icon it cannot find, so an empty window
                        // is a frame of missing icons - and a permanent one if the reload is
                        // interrupted.
                        dockLayout?.reloadIconsInternal()
                        
                        canvasView.invalidate()
                        dockLayout?.invalidate()
                        activity.widgetHostLifecycle.widgetOverlayLayout.rebindCachedWidgets()
                    }
                }
                
                launch {
                    viewModel.nexusSettings.collect { settings ->
                        settingsObserver.applySettings(settings)
                    }
                }
                launch {
                    viewModel.renamedApps.collect { renamed ->
                        canvasView.applyRenamedLabels(renamed)
                    }
                }
                launch {
                    homeScreenViewModel.selectionState.collect { state ->
                        canvasView.setSelectionState(state)
                        com.nexus.launcher.ui.island.IslandController.notifyChrome()

                        if (state is SelectionState.Selecting) {
                            if (state.source == SelectionSource.HOME_SCREEN) {
                                com.nexus.launcher.ui.canvas.SelectionModeChromeSync.onSelectionChanged(
                                    canvasView, activity
                                )
                            }

                            val mainContainer = activity.findViewById<android.widget.FrameLayout>(
                                com.nexus.launcher.R.id.main_container
                            )
                            if (state.source == SelectionSource.HOME_SCREEN) {
                                if (mainContainer.findViewWithTag<View>("selection_backdrop") == null) {
                                    val backdrop = com.nexus.launcher.ui.canvas.SelectionModeBackdropView(
                                        activity, canvasView.canvasRenderer
                                    ).apply {
                                        tag = "selection_backdrop"
                                        layoutParams = android.widget.FrameLayout.LayoutParams(
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                    mainContainer.addView(backdrop, 0)
                                }
                            } else {
                                mainContainer.findViewWithTag<View>("selection_backdrop")?.let {
                                    mainContainer.removeView(it)
                                }
                            }
                            mainContainer.findViewWithTag<View>("selection_scrim")?.let {
                                mainContainer.removeView(it)
                            }
                                
                            val count = if (state.source == SelectionSource.HOME_SCREEN) state.selectedIds.size else state.selectedPackages.size
                            val appCount = if (state.source == SelectionSource.HOME_SCREEN) {
                                homeScreenViewModel.homeScreenItems.value.count { it.id in state.selectedIds && it.itemType == 0 }
                            } else {
                                state.selectedPackages.size
                            }
                            
                            val folderCount = if (state.source == SelectionSource.HOME_SCREEN) {
                                homeScreenViewModel.homeScreenItems.value.count {
                                    it.id in state.selectedIds && it.itemType == 1
                                }
                            } else {
                                0
                            }
                            
                            if (selectionActionBarHelper.isShowing()) {
                                selectionActionBarHelper.updatePillForSelection(count, appCount, folderCount, state)
                            } else {
                                selectionActionBarHelper.buildSelectionActionBar(state)
                                selectionActionBarHelper.updatePillForSelection(count, appCount, folderCount, state)
                            }
                        } else {
                            com.nexus.launcher.ui.canvas.SelectionModeChromeSync.onSelectionChanged(
                                canvasView, activity
                            )
                            val mainContainer = activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.main_container)
                            mainContainer.findViewWithTag<View>("selection_backdrop")?.let {
                                mainContainer.removeView(it)
                            }
                            mainContainer.findViewWithTag<View>("selection_scrim")?.let {
                                mainContainer.removeView(it)
                            }
                                
                            selectionActionBarHelper.hideSelectionActionBar()
                        }
                    }
                }
            }
        }
    }
}
