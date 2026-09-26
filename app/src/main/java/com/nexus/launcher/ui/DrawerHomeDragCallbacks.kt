package com.nexus.launcher.ui

import com.nexus.launcher.ui.canvas.IconDragPageGuard
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.model.DragSource
import com.nexus.launcher.ui.model.DragState

/** Drawer → home finger-drag callbacks for [LauncherCanvasListeners]. */
object DrawerHomeDragCallbacks {

    fun wire(
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        viewModel: MainViewModel,
        homeScreenViewModel: HomeScreenViewModel
    ) {
        var lastDragCell: Pair<Int, Int>? = null

        canvasView.onDragStarted = { item, x, y ->
            val pkg = item.intent?.component?.packageName ?: item.intent?.`package`
            if (pkg != null && item.icon != null) {
                canvasView.homeScreenRenderer.iconCache[pkg] = item.icon
            }
            IconDragPageGuard.capture(canvasView)
            homeScreenViewModel.startDrag(item, x, y, DragSource.DRAWER)
            canvasView.closeDrawerInstantly()
            viewModel.handleSwipeDown()
            lastDragCell = com.nexus.launcher.ui.canvas.CanvasHitTestHelper.getCellAtDrop(canvasView, x, y)
        }

        canvasView.onDragMoved = { x, y ->
            val cell = com.nexus.launcher.ui.canvas.CanvasHitTestHelper.getCellAtDrop(canvasView, x, y)
            if (cell != lastDragCell && cell != null) {
                lastDragCell = cell
                canvasView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            homeScreenViewModel.updateDragPosition(x, y)
            updateMergeHover(activity, canvasView, cell)
        }

        canvasView.onDragDropped = { x, y -> onDropped(activity, canvasView, homeScreenViewModel, x, y) }

        canvasView.onDragCancelled = {
            IconDragPageGuard.restore(canvasView)
            homeScreenViewModel.cancelDrag()
        }

        canvasView.onNativeDrop = { item, page, xF, yF ->
            homeScreenViewModel.addAppToHomeScreen(item, page, xF, yF)
        }
    }

    private fun updateMergeHover(
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        cell: Pair<Int, Int>?
    ) {
        if (cell == null || canvasView.draggedItem != null || canvasView.dragHandler.isHoveringDock) {
            if (canvasView.hoveredMergeTarget != null) {
                canvasView.hoveredMergeTarget = null
                canvasView.invalidate()
            }
            return
        }
        val targetItem = com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
            canvasView.context, canvasView.homeScreenItems, canvasView.currentPage,
            cell.first, cell.second, canvasView.fractionDerivedPositions
        )?.takeIf { it.containerId == -1L && (it.itemType == 0 || it.itemType == 1) }
        if (targetItem != null) {
            if (canvasView.hoveredMergeTarget?.id != targetItem.id) {
                canvasView.hoveredMergeTarget = targetItem
                canvasView.invalidate()
            }
        } else if (canvasView.hoveredMergeTarget != null) {
            canvasView.hoveredMergeTarget = null
            canvasView.invalidate()
        }
    }

    private fun onDropped(
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        homeScreenViewModel: HomeScreenViewModel,
        x: Float,
        y: Float
    ) {
        val dock = com.nexus.launcher.ui.dock.DockLayout.findFrom(canvasView)
        val displayMetrics = canvasView.context.resources.displayMetrics
        val gravityWellY = displayMetrics.heightPixels - (130 * displayMetrics.density)
        val location = IntArray(2)
        canvasView.getLocationOnScreen(location)
        val rawY = y + location[1]
        val state = homeScreenViewModel.dragState.value as? DragState.Dragging
        val displayItem = state?.item
        val mergeTarget = canvasView.hoveredMergeTarget
        canvasView.hoveredMergeTarget = null
        canvasView.invalidate()

        when {
            dock != null && rawY >= gravityWellY && displayItem != null -> {
                dropOnDock(homeScreenViewModel, dock, displayItem, x, y)
            }
            mergeTarget != null && displayItem != null -> {
                bumpPageCount(canvasView)
                val (xF, yF) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                    mergeTarget.column, mergeTarget.row,
                    canvasView.currentGridCols, canvasView.currentGridRows,
                    canvasView.context, 1, 1
                )
                homeScreenViewModel.dropItemAtFraction(
                    canvasView.currentPage, xF, yF, mergeTarget.column, mergeTarget.row
                )
                IconDragPageGuard.clear()
            }
            else -> dropOnGrid(canvasView, homeScreenViewModel, x, y)
        }
    }

    private fun dropOnDock(
        vm: HomeScreenViewModel,
        dock: com.nexus.launcher.ui.dock.DockLayout,
        displayItem: com.nexus.launcher.ui.model.DisplayItem,
        x: Float,
        y: Float
    ) {
        val droppedItem = com.nexus.launcher.data.HomeScreenItem(
            packageName = displayItem.intent?.component?.packageName ?: "",
            page = -1, row = 0, column = 0, itemType = 0
        )
        val safeMax = dock.maxDockIcons.coerceAtLeast(1)
        val searchOffset = if (com.nexus.launcher.ui.dock.DockSearchSlot.enabled && dock.currentPage == 0) 1 else 0
        val occupancy = vm.dockItems.value.size + searchOffset
        if (occupancy < safeMax) {
            val slot = dock.prepareCanvasDropSlot(x, y, droppedItem)
            if (slot in 0 until safeMax) vm.bridgeCanvasHomeToDockDrop(droppedItem, dock, x)
            else vm.cancelDrag()
        } else {
            vm.cancelDrag()
        }
        IconDragPageGuard.clear()
    }

    private fun dropOnGrid(
        canvasView: LauncherCanvasView,
        vm: HomeScreenViewModel,
        x: Float,
        y: Float
    ) {
        val cell = com.nexus.launcher.ui.canvas.CanvasHitTestHelper.getCellAtDrop(canvasView, x, y)
        if (cell == null) {
            vm.cancelDrag()
            IconDragPageGuard.restore(canvasView)
            return
        }
        val draggedItem = canvasView.draggedItem
        val spanX = draggedItem?.spanX ?: 1
        val spanY = draggedItem?.spanY ?: 1
        val clampedCol = Math.round(cell.first - (spanX - 1) / 2f)
            .coerceIn(0, canvasView.currentGridCols - spanX)
        val clampedRow = Math.round(cell.second - (spanY - 1) / 2f)
            .coerceIn(0, canvasView.currentGridRows - spanY)
        val (xF, yF) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
            clampedCol, clampedRow, canvasView.currentGridCols, canvasView.currentGridRows,
            canvasView.context, spanX, spanY
        )
        bumpPageCount(canvasView)
        // TEMPORARY — HomeGridPlacementDiag; remove with the diag object.
        com.nexus.launcher.ui.canvas.HomeGridPlacementDiag.logPlace(
            source = "dropOnGrid",
            searchCols = canvasView.currentGridCols,
            searchRows = canvasView.currentGridRows,
            liveCols = canvasView.gridRenderer.homeColumns,
            liveRows = canvasView.gridRenderer.homeRows,
            page = canvasView.currentPage,
            assignedCol = clampedCol,
            assignedRow = clampedRow
        )
        vm.dropItemAtFraction(canvasView.currentPage, xF, yF, clampedCol, clampedRow)
        IconDragPageGuard.clear()
    }

    private fun bumpPageCount(canvasView: LauncherCanvasView) {
        val prefs = canvasView.context.getSharedPreferences("nexus_prefs", 0)
        val pageCountKey = "home_page_count"
        if (canvasView.currentPage + 1 > prefs.getInt(pageCountKey, 1)) {
            prefs.edit().putInt(pageCountKey, canvasView.currentPage + 1).apply()
        }
    }
}
