package com.nexus.launcher.ui

import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.contextmenu.ContextMenuManager
import com.nexus.launcher.ui.model.DragSource
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState

class CanvasCallbackSetup(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val contextMenuManager: ContextMenuManager,
    private val uiHelpers: LauncherUiHelpers
) {
    fun wire(getCurrentPage: () -> Int, setCurrentPage: (Int) -> Unit) {
        canvasView.onPageChange = setCurrentPage
        canvasView.onStateChanged = { state ->
            if (state == LauncherState.HOME) {
                viewModel.handleSwipeDown()
                if (canvasView.dragHandler.isIconDragActive) {
                    canvasView.onDragCancelled?.invoke()
                }
            } else {
                viewModel.handleSwipeUp()
            }
        }

        canvasView.onItemLongPressed = { item, rect, view ->
            val pkg = item.intent?.component?.packageName
            if (pkg != null && homeScreenViewModel.selectionState.value is SelectionState.Selecting) {
                homeScreenViewModel.toggleSelection(pkg)
            } else {
                contextMenuManager.show(item, rect, view, isUnhideMode = false)
            }
        }

        canvasView.onHomeIconLongPressedWithId = { item, rect, view, id ->
            contextMenuManager.currentItemId = id
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
        canvasView.onEmptyHomeScreenLongPress = {
            uiHelpers.openSettings()
        }

        canvasView.onPageSwipe = { delta ->
            val allItems = homeScreenViewModel.homeScreenItems.value
            val maxPage = (allItems.maxOfOrNull { it.page } ?: 0)
            val newPage = (getCurrentPage() + delta).coerceIn(0, maxPage)
            if (newPage != getCurrentPage()) {
                setCurrentPage(newPage)
                canvasView.setCurrentPage(newPage)
            }
        }

        canvasView.onDragStarted = { item, x, y ->
            homeScreenViewModel.startDrag(item, x, y, DragSource.DRAWER)
            canvasView.closeDrawerInstantly()
            viewModel.handleSwipeDown()
        }
        canvasView.onDragMoved = { x, y ->
            homeScreenViewModel.updateDragPosition(x, y)
        }
        canvasView.onDragDropped = { x, y ->
            val cell = com.nexus.launcher.ui.canvas.CanvasHitTestHelper.getCellAtDrop(canvasView, x, y)
            if (cell != null) {
                homeScreenViewModel.dropItem(
                    page = getCurrentPage(),
                    col = cell.first,
                    row = cell.second
                )
            } else {
                homeScreenViewModel.cancelDrag()
            }
        }
        canvasView.onDragCancelled = {
            homeScreenViewModel.cancelDrag()
        }

        canvasView.onIntentSelected = { intent ->
            val comp = intent.component
            if (comp != null && canvasView.isSearchMode) {
                viewModel.saveRecentApp(comp.packageName)
            }
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("NexusUI", "Failed to start activity", e)
            }
        }
    }
}
