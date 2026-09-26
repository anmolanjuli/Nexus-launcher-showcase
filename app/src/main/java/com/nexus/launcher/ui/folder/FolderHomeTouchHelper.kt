package com.nexus.launcher.ui.folder

import android.view.ViewConfiguration
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.FolderOpenAnchor
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.GlobalScope

/** Home-screen folder tap, long-press menu, and drag handoff. */
internal object FolderHomeTouchHelper {

    private val menuSlopDp = 8f
    private var pendingItem: HomeScreenItem? = null
    private var longPressFired = false
    private var menuShown = false

    fun isArmingLongPress(): Boolean = pendingItem != null && !longPressFired

    fun armFolderLongPress(view: LauncherCanvasView, homeItem: HomeScreenItem) {
        cancelFolderLongPress(view)
        resetGesture()
        pendingItem = homeItem
        view.folderContextMenuItem = homeItem
        view.folderLongPressRunnable = Runnable {
            val selecting = view.selectionState as? SelectionState.Selecting
            if (selecting != null && selecting.source == SelectionSource.HOME_SCREEN) {
                val item = pendingItem ?: return@Runnable
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                longPressFired = true
                if (item.id in selecting.selectedIds) {
                    if (view.multiDragHandler.tryStart(view, item.id)) {
                        view.draggedItem = item
                        view.dragX = view.startTouchX
                        view.dragY = view.startTouchY
                        view.isDragging = true
                        view.isDraggingIcon = true
                        com.nexus.launcher.ui.canvas.IconDragPageGuard.capture(view)
                        view.invalidate()
                    }
                } else {
                    view.onToggleHomeSelection?.invoke(item.id)
                }
                return@Runnable
            }
            if (view.selectionState is SelectionState.Selecting) return@Runnable
            val item = pendingItem ?: return@Runnable
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            longPressFired = true
            menuShown = true
            showContextMenu(view, item)
        }
        view.homeHandler.postDelayed(
            view.folderLongPressRunnable!!,
            ViewConfiguration.getLongPressTimeout().toLong()
        )
    }

    fun cancelFolderLongPress(view: LauncherCanvasView) {
        view.folderLongPressRunnable?.let { view.homeHandler.removeCallbacks(it) }
        view.folderLongPressRunnable = null
    }

    fun onFolderMove(view: LauncherCanvasView, totalDx: Float, totalDy: Float) {
        if (!longPressFired) return
        val slop = menuSlopDp * view.resources.displayMetrics.density
        if (kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble()) <= slop) return
        if (menuShown) {
            FolderContextMenuLauncher.dismiss()
            menuShown = false
        }
        val item = pendingItem ?: return
        if (view.draggedItem == null) {
            startFolderDrag(view, item)
        }
    }

    fun onFolderUp(view: LauncherCanvasView): Boolean {
        cancelFolderLongPress(view)
        if (view.draggedItem != null) return false
        val consume = longPressFired || menuShown || FolderContextMenuLauncher.isShowing()
        if (consume) {
            view.homeLongPressConsumed = true
        }
        resetGesture()
        view.folderContextMenuItem = null
        view.folderContextMenuPending = false
        return consume
    }

    fun onDragDropEnded(view: LauncherCanvasView) {
        resetGesture()
        view.folderContextMenuItem = null
        view.folderContextMenuPending = false
    }

    fun shouldBlockFolderTap(): Boolean =
        longPressFired || menuShown || FolderContextMenuLauncher.isShowing()

    fun openFolderOnTap(view: LauncherCanvasView, tappedItem: HomeScreenItem): Boolean {
        val rootFrame = (view.context as? android.app.Activity)
            ?.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return false
        val owner = view.findViewTreeViewModelStoreOwner() ?: return false
        val vm = ViewModelProvider(owner)[HomeScreenViewModel::class.java]
        val nestedApps = FolderContentsResolver.fromMap(vm.folderContents.value, tappedItem)
        val anchor = FolderOpenAnchor.homeFolderAnchorOnScreen(view, tappedItem)
        val iconX = anchor?.screenX ?: 0f
        val iconY = anchor?.screenY ?: 0f
        val canonicalIconSize = anchor?.canonicalIconSize ?: (view.resources.displayMetrics.density * 48f)
        FolderWindowManager.showFolder(
            context = view.context,
            rootView = rootFrame,
            folderItem = tappedItem,
            iconX = iconX,
            iconY = iconY,
            contents = nestedApps,
            iconCache = view.homeScreenRenderer.iconCache,
            iconSize = canonicalIconSize,
            highlightBounds = anchor?.highlightBounds
        )
        return true
    }

    private fun resetGesture() {
        pendingItem = null
        longPressFired = false
        menuShown = false
    }

    private fun startFolderDrag(view: LauncherCanvasView, homeItem: HomeScreenItem) {
        view.draggedItem = homeItem
        view.dragX = view.startTouchX
        view.dragY = view.startTouchY
        view.isDragging = true
        view.isDraggingIcon = true
        view.setDragOverviewActive(true)
        view.invalidate()
    }

    private fun showContextMenu(view: LauncherCanvasView, item: HomeScreenItem) {
        val anchor = FolderOpenAnchor.homeFolderAnchorOnScreen(view, item)
        val dao = EntryPointAccessors.fromApplication(
            view.context.applicationContext, DaoEntryPoint::class.java
        ).homeScreenDao()
        val owner = view.findViewTreeViewModelStoreOwner()
        val vm = owner?.let { ViewModelProvider(it)[HomeScreenViewModel::class.java] }
        val scope = view.findViewTreeLifecycleOwner()?.lifecycleScope ?: GlobalScope
        val iconSize = anchor?.canonicalIconSize ?: (view.resources.displayMetrics.density * 48f)
        FolderContextMenuLauncher.show(
            context = view.context,
            folderItem = item,
            dao = dao,
            coroutineScope = scope,
            iconX = anchor?.screenX ?: 0f,
            iconY = anchor?.screenY ?: 0f,
            iconSize = iconSize,
            highlightBounds = anchor?.highlightBounds,
            onSelect = { vm?.enterHomeSelectionMode(item.id, item.packageName) }
        )
    }
}
