package com.nexus.launcher.ui

import android.graphics.Color
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.contextmenu.ContextMenuManager
import com.nexus.launcher.ui.dock.DockColumnHealer
import com.nexus.launcher.ui.dock.DockLifecycle
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MainActivityFlowObservers {
    fun bind(
        lifecycleOwner: LifecycleOwner,
        viewModel: MainViewModel,
        homeScreenViewModel: HomeScreenViewModel,
        dockSettingsRepository: DockSettingsRepository,
        overflowController: OverflowMenuController,
        overflowBtn: View,
        canvasView: LauncherCanvasView,
        dockLifecycle: DockLifecycle,
        contextMenuManager: ContextMenuManager,
        getCurrentPage: () -> Int
    ) {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        overflowController.onStateChanged(state, overflowBtn)
                    }
                }
                launch {
                    homeScreenViewModel.homeScreenItems.collect {
                        canvasView.setCurrentPage(getCurrentPage())
                    }
                }
                launch {
                    homeScreenViewModel.dockItems.collect { items ->
                        // A hole in the dock's columns drew the pill a slot short with the last
                        // icon past its edge (DockColumnHealer). Draw it closed up now, and
                        // renumber the stored rows; their next emission has no hole.
                        if (DockColumnHealer.hasGaps(items)) {
                            val max = HomeScreenViewModel.maxDockIcons
                            dockLifecycle.dockLayout.bindItems(DockColumnHealer.contiguous(items, max))
                            launch(Dispatchers.IO) {
                                val dao = EntryPointAccessors.fromApplication(
                                    canvasView.context.applicationContext,
                                    com.nexus.launcher.di.DaoEntryPoint::class.java,
                                ).homeScreenDao()
                                DockItemMover.compactDockColumns(dao, max)
                            }
                        } else {
                            dockLifecycle.dockLayout.bindItems(items)
                        }
                    }
                }
                launch {
                    homeScreenViewModel.folderContents.collect { contents ->
                        dockLifecycle.dockLayout.bindFolderContents(contents)
                    }
                }
                launch {
                    dockSettingsRepository.maxIcons.collect { v -> 
                        com.nexus.launcher.ui.HomeScreenViewModel.maxDockIcons = v
                        dockLifecycle.dockLayout.maxDockIcons = v
                        dockLifecycle.dockLayout.invalidate()
                        dockLifecycle.dockLayout.requestLayout() 
                    }
                }
                launch {
                    viewModel.nexusSettings.collect { settings ->
                        contextMenuManager.setAccentColor(
                            try { Color.parseColor(settings.accentColor) }
                            catch (e: Exception) { Color.parseColor(com.nexus.launcher.data.prefs.NexusDefaults.ACCENT_COLOR) }
                        )
                        dockLifecycle.dockLayout.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
