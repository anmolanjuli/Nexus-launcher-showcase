package com.nexus.launcher.ui

import android.widget.FrameLayout
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class HomeEditController(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val uiHelpers: LauncherUiHelpers,
    private val pageProvider: () -> Int
) {
    val isHomeEditMode: Boolean
        get() = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)?.findViewWithTag<android.view.View>("edit_mode_sheet") != null

    /** See [ResumeBlurResync] — call last in onResume(). */
    fun reassertCanvasBlurOnResume() = ResumeBlurResync.reassert(activity)

    fun getDefaultPage(): Int =
        activity.getSharedPreferences("nexus_prefs",
            android.content.Context.MODE_PRIVATE)
            .getInt("default_page", 0)

    fun saveDefaultPage(page: Int) {
        activity.getSharedPreferences("nexus_prefs",
            android.content.Context.MODE_PRIVATE)
            .edit().putInt("default_page", page).apply()
    }

    internal var managePagesView: ManagePagesView? = null

    private fun savePageCount(count: Int) {
        activity.getSharedPreferences("nexus_prefs",
            android.content.Context.MODE_PRIVATE)
            .edit()
            .putInt("home_page_count", count)
            .apply()
        canvasView.totalPages = count.coerceAtLeast(1)
    }

    private fun setBlurState(isBlurred: Boolean) = HomeEditBlurCoordinator.set(activity, isBlurred)

    fun showHomeEditMode(touchX: Float, touchY: Float) {
        if (isHomeEditMode) return

        val rootLayout = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        val density = activity.resources.displayMetrics.density

        val sheet = HomeEditSheet(
            activity, density, canvasView.currentThemeTokens, touchX, touchY,
            onSettings = { uiHelpers.openSettings() },
            onManagePages = { showManagePages() },
            onAddPage = { savePageCount(canvasView.totalPages + 1) },
            onAddWidget = { activity.widgetHostLifecycle.appWidgetController.launchWidgetPicker() },
            onLockLayout = {},
            onAddFolder = {
                val targetCell = com.nexus.launcher.ui.canvas.CanvasHitTestHelper.getCellAtDrop(canvasView, touchX, touchY)
                if (targetCell != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val dao = dagger.hilt.android.EntryPointAccessors.fromApplication(
                            activity.applicationContext,
                            com.nexus.launcher.di.DaoEntryPoint::class.java
                        ).homeScreenDao()
                        com.nexus.launcher.ui.folder.FolderMergeEngine.createEmptyFolder(
                            page = canvasView.currentPage,
                            dao = dao,
                            targetCol = targetCell.first,
                            targetRow = targetCell.second,
                            context = activity.applicationContext,
                            visualPositions = canvasView.fractionDerivedPositions
                        )
                    }
                }
            },
            onAddIcon = { launchIconPicker() },
            onAddShortcut = { launchShortcutPicker() },
            onWallpaper = { showWallpaperSheet() },
            onDismiss = {
                // Handoff overlays own blur — don't clear after closeSheet()'s 200ms fade
                val root = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
                val handoffOpen = canvasView.isManagePagesOpen ||
                    root.findViewWithTag<android.view.View>("manage_pages_view") != null ||
                    root.findViewWithTag<android.view.View>("wallpaper_sheet") != null ||
                    root.findViewWithTag<android.view.View>("widget_picker_overlay") != null
                if (!handoffOpen) setBlurState(false)
            },
            branches = HomeEditBranches.build(
                onSettingsSection = { section -> uiHelpers.openSettings(section) },
                onDockSettings = {
                    com.nexus.launcher.ui.dock.DockLayoutFinder.findInActivity(activity)?.onDockSettingsRequested?.invoke()
                },
                onAddPage = {
                    val newPage = canvasView.totalPages
                    savePageCount(newPage + 1)
                    canvasView.setCurrentPage(newPage)
                },
            ),
        )
        sheet.tag = "edit_mode_sheet"
        
        val root = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        root.addView(sheet, params)

        setBlurState(true)
        sheet.alpha = 0f
        sheet.animate().alpha(1f).setDuration(200).start()
    }

    fun dismissHomeEditMode() {
        val rootLayout = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        rootLayout.findViewWithTag<android.view.View>("edit_mode_sheet")?.let { sheet ->
            setBlurState(false)
            sheet.animate()
                .alpha(0f).setDuration(200)
                .withEndAction { rootLayout.removeView(sheet) }.start()
        }
        rootLayout.findViewWithTag<android.view.View>("edit_mode_scrim")?.let { scrim ->
            rootLayout.removeView(scrim)
        }
    }

    fun dismissManagePages() {
        val mainContainer = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        setBlurState(false)

        mainContainer.findViewWithTag<android.view.View>("cover_scrim")
            ?.let { mainContainer.removeView(it) }
        managePagesView?.let { mainContainer.removeView(it) }
        managePagesView = null

        pageThumbnails?.values?.forEach { it.recycle() }
        pageThumbnails = null

        mainContainer.findViewWithTag<android.view.View>("manage_pages_bg")?.let {
            mainContainer.removeView(it)
        }

        canvasView.isManagePagesOpen = false
        canvasView.invalidate()

        dismissHomeEditMode()
    }

    /**
     * Kept for [com.nexus.launcher.ui.MainActivityBackPressRouter]: pages are added directly now
     * that there is only one page type, so there is never a picker to dismiss.
     */
    fun dismissPageAddPickerIfShowing(): Boolean = false

    fun dismissWallpaperSheet(): Boolean {
        val rootLayout = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        val sheet = rootLayout.findViewWithTag<android.view.View>("wallpaper_sheet") as? WallpaperSheet
            ?: return false
        sheet.dismiss()
        return true
    }

    fun showWallpaperSheet(initialTab: String? = null) {
        val rootLayout = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        // Already showing (e.g. the Frosted Glass capture deep link firing while the sheet is
        // already open) — nothing to do.
        if (rootLayout.findViewWithTag<android.view.View>("wallpaper_sheet") != null) return
        var dockView: android.view.View? = null
        for (i in 0 until rootLayout.childCount) {
            val child = rootLayout.getChildAt(i)
            if (child is com.nexus.launcher.ui.dock.DockLayout) {
                dockView = child
                break
            }
        }
        val wallpaperSheet = WallpaperSheet(
            activity,
            viewModel,
            activity,
            activity.resources.displayMetrics.density,
            canvasView.canvasRenderer,
            dockView,
            initialTab
        ) {
            // Was canvasView.setBlurState(false) ONLY — but this sheet can be reached from Home
            // Edit Mode tapping "Wallpaper" (HomeEditSheet.closeSheet() deliberately leaves
            // HomeEditBlurCoordinator's workspace_container blur ON as a handoff, expecting
            // WHOEVER opens next to own clearing it) as well as directly (the Frosted Glass
            // capture deep link). Only ever clearing canvasView's own blur left
            // HomeEditBlurCoordinator's blur stuck on whenever entered via that handoff — the
            // whole workspace (widgets/dock included, not just canvas) stayed blurred until an
            // unrelated event (back press) forced homeEditController.dismissHomeEditMode() to
            // finally clear it. Clear both, unconditionally, so this sheet always leaves the
            // workspace in the state it actually found/left it in, regardless of entry path.
            canvasView.setBlurState(false)
            setBlurState(false)
        }
        wallpaperSheet.tag = "wallpaper_sheet"
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Frosted Glass only, like every other workspace blur (see FolderBlurCoordinator).
        if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) canvasView.setBlurState(true)
        // Idempotent if already on from a Home Edit Mode handoff — but also correctly turns on
        // the FULL workspace blur (not just canvasView's own) when this sheet is opened directly
        // (e.g. the Frosted Glass capture deep link), keeping the two entry paths consistent.
        setBlurState(true)
        rootLayout.addView(wallpaperSheet, params)
        wallpaperSheet.alpha = 0f
        wallpaperSheet.animate().alpha(1f).setDuration(200).start()
    }

    private fun generatePageThumbnails(): Map<Int, android.graphics.Bitmap> {
        val density = activity.resources.displayMetrics.density
        val thumbW = (ManagePagesThumbSpec.WIDTH_DP * density).toInt()
        val ratio = ManagePagesThumbSpec.heightOverWidth(activity)
        val thumbH = (thumbW * ratio).toInt().coerceAtLeast(1)
        return com.nexus.launcher.ui.canvas.PageThumbnailRenderer.renderAll(
            canvasView, thumbW, thumbH
        )
    }

    private var pageThumbnails: Map<Int, android.graphics.Bitmap>? = null

    fun showManagePages() {
        val mainContainer = activity.findViewById<FrameLayout>(
            com.nexus.launcher.R.id.main_container)

        // Tear down any existing overlay first (also clears prior thumbnails)
        dismissManagePages()

        // No dim cover — frosted workspace blur is the sole backdrop (same as Home Edit Mode).
        // A solid ~45% scrim on top of blur made the screen read muddy/too dark.

        canvasView.isManagePagesOpen = true
        setBlurState(true)
        canvasView.invalidate()

        val density = activity.resources.displayMetrics.density
        val thumbnails = generatePageThumbnails()
        pageThumbnails = thumbnails

        val pageItemCounts = (0 until canvasView.totalPages).associate { page ->
            page to canvasView.homeScreenItems.count { it.page == page }
        }

        val clampedDefault = getDefaultPage()
            .coerceIn(0, (canvasView.totalPages - 1).coerceAtLeast(0))
        if (clampedDefault != getDefaultPage()) saveDefaultPage(clampedDefault)

        val view = ManagePagesView(
            activity, density, canvasView.accentColor,
            topInset = canvasView.topInset,
            totalPages = canvasView.totalPages,
            currentPage = pageProvider(),
            defaultPage = clampedDefault,
            pageThumbnails = thumbnails,
            pageItemCounts = pageItemCounts,
            onDeletePage = { page ->
                val newCount = (canvasView.totalPages - 1).coerceAtLeast(1)
                homeScreenViewModel.deletePage(page, newCount) {
                    savePageCount(newCount)
                    saveDefaultPage(
                        PageReorderHelper.defaultPageAfterDelete(getDefaultPage(), page, newCount)
                    )
                    if (pageProvider() >= newCount) {
                        canvasView.setCurrentPage(newCount - 1)
                    }
                }
            },
            onSetDefault = { page ->
                saveDefaultPage(page)
            },
            onAddPage = {
                val newIndex = canvasView.totalPages
                savePageCount(newIndex + 1)
                showManagePages()
            },
            onReorderPages = { from, to -> reorderManagedPages(from, to) },
            onDismiss = {
                setBlurState(false)
                canvasView.invalidate()
                dismissManagePages()
            }
        )
        view.tag = "manage_pages_view"
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT)
        view.alpha = 0f
        mainContainer.addView(view)
        view.animate().alpha(1f).setDuration(200).start()
        managePagesView = view
    }

    private fun reorderManagedPages(from: Int, to: Int) {
        val total = canvasView.totalPages
        if (from == to || from !in 0 until total || to !in 0 until total) return
        val oldToNew = PageReorderHelper.oldToNew(from, to, total)

        val def = getDefaultPage()
        if (def in oldToNew.indices) saveDefaultPage(oldToNew[def])

        val cur = pageProvider()
        if (cur in oldToNew.indices) {
            canvasView.setCurrentPage(oldToNew[cur])
        }

        // Optimistic in-memory remap so thumbs rebuild correctly while DB writes
        canvasView.homeScreenItems =
            PageReorderHelper.remapInMemory(canvasView.homeScreenItems, oldToNew)
        canvasView.fractionDerivedPositions = canvasView.fractionDerivedPositions.mapValues { (_, pos) ->
            if (pos.first in oldToNew.indices) Triple(oldToNew[pos.first], pos.second, pos.third)
            else pos
        }

        homeScreenViewModel.reorderPages(oldToNew) {
            showManagePages()
        }
    }

    fun launchShortcutPicker() {
        dismissHomeEditMode()
        com.nexus.launcher.ui.picker.ShortcutPickerOverlay.show(activity) { shortcutInfo ->
            activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    activity.applicationContext,
                    com.nexus.launcher.di.DaoEntryPoint::class.java
                )
                val dao = entryPoint.homeScreenDao()
                val page = pageProvider()
                val currentItems = homeScreenViewModel.homeScreenItems.value

                val shortcutJson = "{\"shortcutId\":\"${shortcutInfo.id}\"}"
                val item = com.nexus.launcher.data.HomeScreenItem(
                    packageName = shortcutInfo.`package`,
                    page = -1, column = 0, row = 0, itemType = 2, containerId = -1L, folderTitle = shortcutInfo.shortLabel?.toString() ?: "",
                    folderConfigJson = shortcutJson
                )

                com.nexus.launcher.model.GridPlacementEngine.injectShortcut(
                    item, page,
                    canvasView.currentGridCols, canvasView.currentGridRows,
                    dao, currentItems, canvasView.fractionDerivedPositions, activity.applicationContext
                )
            }
        }
    }

    fun launchIconPicker() {
        dismissHomeEditMode()
        HomeEditPickerHelper.launchIconPicker(activity, pageProvider, homeScreenViewModel, canvasView)
    }
    fun showFolderResizeMode(folderItem: com.nexus.launcher.data.HomeScreenItem) {
        dismissHomeEditMode()
        val launcher = com.nexus.launcher.ui.folder.FolderResizeModeLauncher(
            activity, canvasView, homeScreenViewModel
        )
        launcher.showFolderResizeMode(folderItem)
    }
    fun showFolderFlipMode(folderItem: com.nexus.launcher.data.HomeScreenItem) {
        dismissHomeEditMode()
        val launcher = com.nexus.launcher.ui.folder.FolderFlipModeLauncher(
            activity, canvasView, homeScreenViewModel
        )
        launcher.showFolderFlipMode(folderItem)
    }
}
