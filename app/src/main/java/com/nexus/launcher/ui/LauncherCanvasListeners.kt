package com.nexus.launcher.ui

import android.util.Log
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import kotlinx.coroutines.launch

class LauncherCanvasListeners(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val uiHelpers: LauncherUiHelpers,
    private val contextMenuManager: com.nexus.launcher.ui.contextmenu.ContextMenuManager,
    private val pageProvider: () -> Int,
    private val onPageChanged: (Int) -> Unit
) {
    fun register() {
        // Chain onto whatever was already registered instead of overwriting it — this is the one
        // onStateChanged writer in the codebase that didn't, and since MainActivity.onCreate()
        // wires this class up AFTER MainActivityDrawerChromeBinder, the direct overwrite was
        // silently deleting that binder's FAB/overflow-visibility enforcer every single time.
        val previousStateChanged = canvasView.onStateChanged
        canvasView.onStateChanged = { state ->
            previousStateChanged?.invoke(state)
            if (state == LauncherState.HOME) {
                viewModel.handleSwipeDown()
            } else {
                viewModel.handleSwipeUp()
            }
        }
        
        canvasView.onIntentSelected = { intent ->
            if (intent.action == "nexus.folder.OPEN") {
                val blocked = com.nexus.launcher.ui.folder.FolderContextMenuLauncher.isShowing() ||
                    com.nexus.launcher.ui.folder.FolderDrawerTouchHelper.shouldBlockFolderTap()
                if (!blocked) {
                    com.nexus.launcher.ui.folder.FolderDrawerTouchHelper.cancel(canvasView)
                    val folderId = intent.getLongExtra("folderId", -1L)
                    if (folderId != -1L) {
                        val anchor = if (canvasView.uiState == LauncherState.HOME) {
                            val folderItem = canvasView.homeScreenItems.find { it.id.toLong() == folderId }
                            if (folderItem != null) {
                                com.nexus.launcher.ui.canvas.FolderOpenAnchor.homeFolderAnchorOnScreen(canvasView, folderItem)
                            } else null
                        } else {
                            com.nexus.launcher.ui.canvas.FolderOpenAnchor.drawerFolderAnchorOnScreen(canvasView, folderId)
                        }
                        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                            activity.applicationContext,
                            com.nexus.launcher.di.DaoEntryPoint::class.java
                        )
                        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val dao = entryPoint.homeScreenDao()
                            val folderItem = dao.getItemById(folderId.toInt()) ?: return@launch
                            val contents = dao.getItemsInFolderSync(folderItem.resolveFolderContentsId())
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val rootFrame = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                                if (rootFrame != null) {
                                    val iconX = anchor?.screenX ?: (canvasView.width / 2f)
                                    val iconY = anchor?.screenY ?: (canvasView.height / 2f)
                                    val iconSize = anchor?.canonicalIconSize
                                        ?: (48f * canvasView.resources.displayMetrics.density)
                                    com.nexus.launcher.ui.folder.FolderWindowManager.showFolder(
                                        context = activity,
                                        rootView = rootFrame,
                                        folderItem = folderItem,
                                        iconX = iconX,
                                        iconY = iconY,
                                        contents = contents,
                                        iconCache = canvasView.homeScreenRenderer.iconCache,
                                        iconSize = iconSize,
                                        highlightBounds = anchor?.highlightBounds
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val comp = intent.component
                if (comp != null && canvasView.isSearchMode) {
                    viewModel.saveRecentApp(comp.packageName)
                }
                if (intent.action == "nexus.shortcut.START") {
                    try {
                        val packageName = intent.getStringExtra("packageName")
                        val shortcutId = intent.getStringExtra("shortcutId")
                        if (packageName == activity.packageName || com.nexus.launcher.ui.widgets.shortcutbox.NexusBuiltinShortcuts.isBuiltin(shortcutId)) {
                            com.nexus.launcher.ui.widgets.shortcutbox.NexusBuiltinShortcuts.execute(activity, shortcutId)
                        } else if (packageName != null && shortcutId != null) {
                            val launcherApps = activity.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                            launcherApps.startShortcut(packageName, shortcutId, null, null, android.os.Process.myUserHandle())
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NexusUI", "Failed to start shortcut", e)
                    }
                } else {
                    try {
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        android.util.Log.e("NexusUI", "Failed to start activity", e)
                    }
                }
                if (canvasView.isSearchMode) {
                    uiHelpers.closeSearchMode(
                        activity.findViewById(R.id.search_overlay),
                        activity.findViewById(R.id.search_bar),
                        activity.findViewById(R.id.new_scroll),
                        activity.findViewById(R.id.new_title),
                        activity.findViewById(R.id.recent_scroll),
                        activity.findViewById(R.id.recent_title)
                    )
                }
            }
        }

        canvasView.onItemLongPressed = { item, rect, view ->
            val pkg = item.intent?.component?.packageName
            if (pkg != null && homeScreenViewModel.selectionState.value is SelectionState.Selecting) {
                homeScreenViewModel.toggleSelection(pkg)
            } else {
                val isHidden = (item.categoryName == "Hidden")
                contextMenuManager.show(item, rect, view, isUnhideMode = isHidden)
            }
        }
        canvasView.onHomeIconLongPressedWithId = { item, rect, view, id ->
            contextMenuManager.currentItemId = id
            contextMenuManager.currentPage = canvasView.currentPage
            contextMenuManager.show(item, rect, view, isUnhideMode = false, isHomeScreen = true)
        }

        canvasView.onEnterSelectionMode = { pkg, source ->
            homeScreenViewModel.enterSelectionMode(pkg, source)
        }
        canvasView.onToggleSelection = { pkg ->
            homeScreenViewModel.toggleSelection(pkg)
        }
        canvasView.onToggleHomeSelection = { id ->
            homeScreenViewModel.toggleHomeSelection(id)
        }
        canvasView.onClearSelection = {
            homeScreenViewModel.clearSelection()
        }

        canvasView.onDismissContextMenu = {
            contextMenuManager.dismiss()
        }

        canvasView.onHomeItemDropped = { id, page, xF, yF, col, row ->
            homeScreenViewModel.updateItemPosition(id, page, xF, yF, col, row)
        }

        canvasView.onEmptyHomeScreenLongPress = {
            activity.homeEditController.showHomeEditMode(
                canvasView.startTouchX, canvasView.startTouchY
            )
        }

        // Pure page-index commit. The 3D-cube settle animation is driven 1:1 by the
        // finger in LauncherTouchHandler, which calls this only once the page is decided.
        canvasView.onPageSwipe = { delta ->
            val maxPage = canvasView.totalPages - 1
            val current = pageProvider()
            val newPage = (current + delta).coerceIn(0, maxPage)
            if (newPage != current) {
                onPageChanged(newPage)
            }
        }
        canvasView.onPageChange = onPageChanged

        DrawerHomeDragCallbacks.wire(activity, canvasView, viewModel, homeScreenViewModel)
        
        canvasView.post {
            val pm = activity.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val installedPkgs = pm.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .toSet()
            
            activity.lifecycleScope.launch {
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    activity.applicationContext,
                    com.nexus.launcher.di.DaoEntryPoint::class.java
                )
                com.nexus.launcher.sync.AppSyncEngine.removeGhostIcons(entryPoint.homeScreenDao(), installedPkgs)
            }
            
            activity.lifecycleScope.launch {
                homeScreenViewModel.folderGlowEvent.collect { event ->
                    val folderId = event.first
                    val success = event.second
                    if (!success) {
                        canvasView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        val rootFrame = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                        if (rootFrame != null) {
                            val dp = activity.resources.displayMetrics.density
                            val dialogView = android.widget.TextView(activity).apply {
                                text = activity.getString(com.nexus.launcher.R.string.toast_app_already_exists)
                                setTextColor(android.graphics.Color.WHITE)
                                textSize = 14f
                                gravity = android.view.Gravity.CENTER
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                val pad = (12 * dp).toInt()
                                setPadding(pad * 2, pad, pad * 2, pad)
                                background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(android.graphics.Color.parseColor("#E61A1A24"))
                                    cornerRadius = 24 * dp
                                    setStroke((1 * dp).toInt(), android.graphics.Color.parseColor("#4DFFFFFF"))
                                }
                                elevation = 16 * dp
                                alpha = 0f
                            }
                            val lp = android.widget.FrameLayout.LayoutParams(
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP
                                val item = canvasView.homeScreenItems.firstOrNull { it.id.toLong() == folderId }
                                if (item != null) {
                                    val anchor = com.nexus.launcher.ui.canvas.FolderOpenAnchor.homeFolderAnchorOnScreen(canvasView, item)
                                    topMargin = if (anchor != null) (anchor.screenY - 80 * dp).toInt().coerceAtLeast((40 * dp).toInt()) else (100 * dp).toInt()
                                } else {
                                    topMargin = (100 * dp).toInt()
                                }
                            }
                            rootFrame.addView(dialogView, lp)
                            dialogView.animate().alpha(1f).translationY(-20f).setDuration(250).withEndAction {
                                dialogView.postDelayed({
                                    dialogView.animate().alpha(0f).translationY(0f).setDuration(250).withEndAction {
                                        rootFrame.removeView(dialogView)
                                    }.start()
                                }, 1500)
                            }.start()
                        }
                    } else {
                        canvasView.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    }
                    canvasView.folderGlowManager.triggerFolderGlow(folderId, success)
                }
            }
        }
    }
}
