package com.nexus.launcher.ui

import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlinx.coroutines.launch

/**
 * Owns the drawer overflow menu (Hidden Apps / Create Folder / Drawer Settings / Column Style /
 * Normal First Letter), delegating its chrome to [DrawerOverflowMenuDialog] — same anchored-card
 * pattern as the Feed page's header menu (dim+blur, visible trigger-icon copy, tap-to-open/
 * tap-again-to-close, back-button dismiss via [isShowing]/[dismiss]).
 */
class OverflowMenuController(
    private val activity: MainActivity,
    @Suppress("UNUSED_PARAMETER") mainContainer: android.widget.FrameLayout,
    private val canvasView: LauncherCanvasView,
    private val uiHelpers: LauncherUiHelpers
) {
    private var dialog: DrawerOverflowMenuDialog? = null

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun dismiss() {
        dialog?.dismiss()
    }

    fun onStateChanged(state: com.nexus.launcher.ui.model.LauncherState, overflowBtn: android.view.View) {
        val dockOffset = if (canvasView.isLandscape)
            canvasView.dockStripWidth.toFloat() else 0f
        overflowBtn.translationX = -dockOffset

        if (state == com.nexus.launcher.ui.model.LauncherState.HOME) {
            dismiss()
        }
    }

    fun onConfigurationChanged(overflowBtn: android.view.View?) {
        dismiss()
        val dockOffset = if (canvasView.isLandscape)
            canvasView.dockStripWidth.toFloat() else 0f
        overflowBtn?.translationX = -dockOffset
        canvasView.post { canvasView.railTopY = railTopFor() }
    }

    /**
     * In portrait the A-Z rail starts below the overflow button at mid-screen. In phone
     * landscape half the height is too little for 26 letters, so the rail uses the full drawer
     * grid height instead (0 = fall back to drawerGridTop).
     */
    private fun railTopFor(): Int = if (canvasView.isLandscape) 0 else canvasView.viewHeight / 2

    fun positionButton(overflowBtn: android.view.View) {
        overflowBtn.post {
            val lp = overflowBtn.layoutParams as android.widget.FrameLayout.LayoutParams
            lp.topMargin = (activity.resources.displayMetrics.heightPixels / 2) -
                (40 * activity.resources.displayMetrics.density).toInt()
            overflowBtn.layoutParams = lp

            canvasView.railTopY = railTopFor()
        }

        overflowBtn.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            canvasView.railTopY = railTopFor()
        }
    }

    fun setupClickListener(overflowIcon: android.widget.ImageView) {
        // Accent-free by design (item 7) — neutral token tint, kept live across theme changes.
        overflowIcon.imageTintList = android.content.res.ColorStateList.valueOf(canvasView.currentThemeTokens.textPrimary)
        com.nexus.launcher.theme.ThemeObserver.observe(activity, activity.lifecycleScope) { tokens ->
            overflowIcon.imageTintList = android.content.res.ColorStateList.valueOf(tokens.textPrimary)
        }

        overflowIcon.setOnClickListener { anchor ->
            com.nexus.launcher.ui.canvas.DrawerHaptics.onOverflowTap(anchor)
            if (isShowing()) {
                dismiss()
                return@setOnClickListener
            }

            val effectiveMode = canvasView.gridRenderer.layoutMode
            val currentCols = if (effectiveMode.endsWith("_2")) 2 else 1
            val settings = activity.viewModel.nexusSettings.value

            val newDialog = DrawerOverflowMenuDialog(
                activity = activity,
                anchorView = anchor,
                effectiveLayoutMode = effectiveMode,
                currentListColumns = currentCols,
                currentGridColumns = canvasView.gridRenderer.columnCount,
                currentCategoryLayout = settings.drawerCategoryLayout,
                onColumnsSelected = { cols -> switchDrawerListColumns(cols) },
                onGridColumnsSelected = { cols -> switchDrawerColumns(cols) },
                onDrawerModeChanged = { mode -> switchDrawerMode(mode) },
                onCategoryLayoutChanged = { style -> switchDrawerCategoryLayout(style) },
                onHiddenApps = {
                    activity.supportFragmentManager.beginTransaction()
                        .replace(
                            android.R.id.content,
                            com.nexus.launcher.ui.settings.HiddenAppsFragment.newInstance()
                        )
                        .addToBackStack(null).commit()
                },
                onCreateFolder = { createDrawerFolder() },
                onDrawerSettings = { uiHelpers.openSettings("drawer") },
                positionRowLabel = positionRowLabel(),
                positionRowValue = positionRowValue(),
                onPositionSelected = { position ->
                    dismiss()
                    // Retarget the write to whichever bar the row is currently labelled for,
                    // so the control always moves the thing the user is looking at.
                    if (activity.viewModel.nexusSettings.value.drawerShowSearchPill) {
                        activity.viewModel.updateDrawerSearchBarPosition(position)
                    } else {
                        activity.viewModel.updateDrawerCategoryPosition(position)
                    }
                },
                onDismissed = { dialog = null }
            )
            dialog = newDialog
            newDialog.show()
        }
    }

    /**
     * The quick position toggle follows whichever bar is on screen: the search pill when it is
     * enabled, otherwise the category bar. Null hides the row — with neither bar showing, this
     * menu is reached from the rail button and there is nothing to reposition.
     */
    private fun positionRowLabel(): String? {
        val s = activity.viewModel.nexusSettings.value
        return when {
            s.drawerShowSearchPill -> activity.getString(com.nexus.launcher.R.string.drawer_overflow_search_position_label)
            s.drawerShowCategoryBar -> activity.getString(com.nexus.launcher.R.string.drawer_overflow_category_position_label)
            else -> null
        }
    }

    private fun positionRowValue(): String {
        val s = activity.viewModel.nexusSettings.value
        return if (s.drawerShowSearchPill) s.drawerSearchBarPosition else s.drawerCategoryPosition
    }

    private fun createDrawerFolder() {
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val drawerFolder = com.nexus.launcher.data.HomeScreenItem(
                packageName = "folder_${System.currentTimeMillis()}",
                page = -2,
                row = 0,
                column = 0,
                itemType = 1,
                containerId = -1L,
                folderTitle = activity.getString(R.string.folder_new_title),
                folderConfigJson = com.nexus.launcher.ui.folder.FolderConfigCodec.toJson(
                    com.nexus.launcher.data.FolderConfig()
                )
            )
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                activity.applicationContext,
                com.nexus.launcher.di.DaoEntryPoint::class.java
            )
            val folderId = entryPoint.homeScreenDao().insertItemAndGetId(drawerFolder)
            com.nexus.launcher.ui.drawercategories.CategoriesFolderTag.tagCreated(
                activity.applicationContext,
                folderId,
            )
        }
    }

    /**
     * Writes the shared List column count directly, bypassing the Settings draft/Apply flow —
     * see WORKING_FEATURES.md "Known Bugs / Current State" for the two-write-paths fragility this
     * pattern carries if App Drawer Settings ever moves to the staged Apply model.
     */
    private fun switchDrawerListColumns(cols: Int) {
        activity.viewModel.updateDrawerListColumns(cols)
        canvasView.gridRenderer.layoutMode = "list_$cols"
        canvasView.recalculateLayout()
        canvasView.invalidate()
    }

    private fun switchDrawerMode(mode: String) {
        activity.viewModel.updateDrawerGridOrList(mode)
        val cols = if (canvasView.gridRenderer.layoutMode.endsWith("_2")) 2 else 1
        canvasView.gridRenderer.layoutMode = when (mode) {
            "grid" -> "grid"
            "categories" -> "categories"
            else -> "list_$cols"
        }
        canvasView.recalculateLayout()
        canvasView.invalidate()
    }

    private fun switchDrawerCategoryLayout(style: String) {
        activity.viewModel.updateDrawerCategoryLayout(style)
        canvasView.recalculateLayout()
        canvasView.invalidate()
    }

    private fun switchDrawerColumns(cols: Int) {
        activity.viewModel.updateDrawerColumns(cols)
        canvasView.gridRenderer.setColumns(cols)
        canvasView.recalculateLayout()
        canvasView.invalidate()
    }
}
