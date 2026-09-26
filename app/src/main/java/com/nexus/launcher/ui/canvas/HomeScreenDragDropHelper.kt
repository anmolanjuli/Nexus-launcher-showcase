package com.nexus.launcher.ui.canvas

import android.view.MotionEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import com.nexus.launcher.ui.folder.FolderAppendEngine
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home-screen icon/folder drop commit — extracted from [DragTouchHandler] for file-size limits. */
internal object HomeScreenDragDropHelper {

    fun handleHomeDragDrop(
        view: LauncherCanvasView,
        event: MotionEvent,
        rawX: Float,
        rawY: Float,
        canvasDropDispatched: Boolean,
        onCanvasDropDispatched: () -> Unit,
        clearMagneticDockHover: (DockLayout?, Boolean) -> Unit,
        setHoveringDock: (Boolean) -> Unit
    ): Boolean {
        val item = view.draggedItem
        val displayItem = view.dragHandler.dragItem
        if (item == null && displayItem == null) return false
        if (canvasDropDispatched) return true

        if (view.multiDragHandler.isActive) {
            val dropper = view.onHomeItemDropped
            val committed = dropper != null && HomeScreenMultiDragDropCommit.commit(
                view, view.multiDragHandler, event.x, event.y, dropper
            )
            if (committed) {
                clearHomeDragState(view, clearMagneticDockHover)
                IconDragPageGuard.clear()
                view.recalculateLayout()
            } else {
                clearHomeDragState(view, clearMagneticDockHover)
                IconDragPageGuard.restore(view)
            }
            view.invalidate()
            return true
        }

        // Resolve against the cards as displayed at release, before cancelling/rebasing motion.
        val dropTargetPage = if (SelectionModeTransform.isCardTrackActive(view)) {
            SelectionModeCardTrack.pageAtScreenPoint(view, event.x, event.y) ?: view.currentPage
        } else view.currentPage
        val targetCell = CanvasHitTestHelper.getCellAtDrop(view, event.x, event.y, dropTargetPage)
        view.cancelPageMotion()
        view.dragScrollOffset = 0f
        view.isMutatingState = false

        val distance = Math.hypot(
            (event.x - view.startTouchX).toDouble(),
            (event.y - view.startTouchY).toDouble()
        )
        if (distance < 15f) {
            view.draggedItem = null
            view.isDraggingIcon = false
            view.multiDragHandler.cancel()
            view.setDragOverviewActive(false)
            IconDragPageGuard.restore(view)
            clearMagneticDockHover(DockLayout.findFrom(view), false)
            view.invalidate()
            return true
        }

        // Commit to dock only when the finger is actually over the dock view.
        // A Y-only "gravity well" previously stole bottom-row home rearranges into the
        // dock (and DockItemMover then deleted the home copies).
        val dock = DockLayout.findFrom(view)
        if (dock != null) {
            val (parentX, parentY) = com.nexus.launcher.ui.dock.DockCoordHelper
                .canvasLocalToParent(view, event.x, event.y)
            if (dock.containsCanvasPoint(parentX, parentY)) {
                val safeMax = dock.maxDockIcons.coerceAtLeast(1)
                val searchOffset = if (com.nexus.launcher.ui.dock.DockSearchSlot.enabled && dock.currentPage == 0) 1 else 0
                val dockOccupancy = view.homeScreenItems.count {
                    it.page == HomeScreenViewModel.DOCK_CONTAINER && it.id != (item?.id ?: -1)
                } + searchOffset
                val atCapacity = dockOccupancy >= safeMax && !dock.containsDockItem(item?.id ?: -1)
                if (!atCapacity) {
                    val droppedItem = item ?: com.nexus.launcher.data.HomeScreenItem(
                        packageName = displayItem?.intent?.component?.packageName ?: "",
                        page = -1, row = 0, column = 0, itemType = 0
                    )
                    val slot = dock.prepareCanvasDropSlot(rawX, rawY, droppedItem)
                    if (slot in 0 until safeMax) {
                        onCanvasDropDispatched()
                        view.findViewTreeViewModelStoreOwner()?.let { owner ->
                            ViewModelProvider(owner)[HomeScreenViewModel::class.java]
                                .moveItemToDock(droppedItem, slot)
                        }
                        clearMagneticDockHover(dock, true)
                        clearHomeDragState(view, clearMagneticDockHover)
                        IconDragPageGuard.restore(view)
                        view.invalidate()
                        return true
                    }
                }
                // Over dock but rejected (full / bad slot): fall through to home drop.
            }
        }

        if (view.hoveredMergeTarget != null) {
            val target = view.hoveredMergeTarget!!
            val shortcutId = displayItem?.intent?.getStringExtra("shortcutId")
            val isShortcut = shortcutId != null || item?.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT
            val droppedItem = item ?: com.nexus.launcher.data.HomeScreenItem(
                packageName = displayItem?.intent?.component?.packageName ?: displayItem?.intent?.`package` ?: "",
                page = -1, row = 0, column = 0,
                itemType = if (isShortcut) com.nexus.launcher.data.HomeItemTypes.SHORTCUT else 0,
                folderConfigJson = if (shortcutId != null) "{\"shortcutId\":\"$shortcutId\"}" else "{}"
            )
            // TEMPORARY — HomeGridDropDiag; remove with the diag object.
            HomeGridDropDiag.logDrop(
                source = "handleHomeDragDrop.merge",
                draggedId = droppedItem.id,
                beforeSpanX = droppedItem.spanX,
                beforeSpanY = droppedItem.spanY,
                beforeCell = "${droppedItem.column},${droppedItem.row}",
                dropCell = "${target.column},${target.row}",
                overlap = true,
                occupant = target,
                outcome = when (target.itemType) {
                    1 -> "APPEND_TO_FOLDER"
                    com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX -> "APPEND_TO_SHORTCUT_BOX"
                    com.nexus.launcher.data.HomeItemTypes.APP_BOX -> "APPEND_TO_APP_BOX"
                    else -> "MERGE_INTO_FOLDER"
                }
            )
            if (target.itemType == 1) {
                val targetFolderId = target.resolveFolderContentsId()
                view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                    val dao = EntryPointAccessors.fromApplication(
                        view.context.applicationContext,
                        DaoEntryPoint::class.java
                    ).homeScreenDao()
                    val success = FolderAppendEngine.appendAppToFolder(droppedItem, targetFolderId, dao)
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        view.findViewTreeViewModelStoreOwner()?.let { owner ->
                            ViewModelProvider(owner)[HomeScreenViewModel::class.java]
                                .triggerFolderGlow(targetFolderId, success)
                        }
                    }
                }
            } else if (target.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX) {
                HomeScreenBoxDropHelper.handleDropOnShortcutBox(view, target, droppedItem, event.x, event.y)
            } else if (target.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX) {
                HomeScreenBoxDropHelper.handleDropOnAppBox(view, target, droppedItem, event.x, event.y)
            } else {
                view.findViewTreeViewModelStoreOwner()?.let { owner ->
                    ViewModelProvider(owner)[HomeScreenViewModel::class.java]
                        .mergeAppsAtHoverTarget(droppedItem, target)
                }
            }
            clearHomeDragState(view, clearMagneticDockHover)
            view.hoveredMergeTarget = null
            return true
        }

        if (item == null) return false

        if (targetCell != null) {
            val spanX = item.spanX
            val spanY = item.spanY
            val rawCol = targetCell.first - (spanX - 1) / 2f
            val rawRow = targetCell.second - (spanY - 1) / 2f
            val clampedCol = Math.round(rawCol).coerceIn(0, (view.currentGridCols - spanX).coerceAtLeast(0))
            val clampedRow = Math.round(rawRow).coerceIn(0, (view.currentGridRows - spanY).coerceAtLeast(0))
            // TEMPORARY — HomeGridDropDiag; remove with the diag object.
            view.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                val dao = EntryPointAccessors.fromApplication(
                    view.context.applicationContext,
                    DaoEntryPoint::class.java
                ).homeScreenDao()
                val dbItem = dao.getItemById(item.id)
                HomeGridDropDiag.logDb(
                    source = "handleHomeDragDrop.dbBefore",
                    id = item.id,
                    after = dbItem,
                    extra = "memorySpan=${spanX}x$spanY memoryCell=${item.column},${item.row} " +
                        "dropCell=$clampedCol,$clampedRow"
                )
            }
            
            val overlapItem = com.nexus.launcher.ui.canvas.GridOccupancyHelper.findItemAtCell(
                view.context, view.homeScreenItems, dropTargetPage, clampedCol, clampedRow, view.fractionDerivedPositions, listOf(item.id), spanX, spanY
            )
            val overlap = overlapItem != null
            // TEMPORARY — HomeGridDropDiag; remove with the diag object.
            HomeGridDropDiag.logIntersecting(
                source = "handleHomeDragDrop",
                page = view.currentPage,
                col = clampedCol,
                row = clampedRow,
                spanX = spanX,
                spanY = spanY,
                items = view.homeScreenItems,
                excludeIds = listOf(item.id)
            )
            
            if (overlapItem != null) {
                if (overlapItem.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX && (item.itemType == 0 || item.itemType == 2)) {
                    HomeScreenBoxDropHelper.handleDropOnShortcutBox(view, overlapItem, item, event.x, event.y)
                    clearHomeDragState(view, clearMagneticDockHover)
                    return true
                } else if (overlapItem.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX && (item.itemType == 0 || item.itemType == 2)) {
                    HomeScreenBoxDropHelper.handleDropOnAppBox(view, overlapItem, item, event.x, event.y)
                    clearHomeDragState(view, clearMagneticDockHover)
                    return true
                }

                HomeGridDropDiag.logDrop(
                    source = "handleHomeDragDrop",
                    draggedId = item.id,
                    beforeSpanX = spanX,
                    beforeSpanY = spanY,
                    beforeCell = "${item.column},${item.row}",
                    dropCell = "$clampedCol,$clampedRow",
                    overlap = true,
                    occupant = overlapItem,
                    outcome = "BLOCKED_RESTORE",
                    extra = "liveCols=${view.currentGridCols}"
                )
                val hasWidget = isWidgetOrContainer(item) || isWidgetOrContainer(overlapItem)
                if (hasWidget) {
                    com.nexus.launcher.ui.HomeScreenDropHandler.showWidgetRejection(view.context, view)
                }
                val targetPage = dropTargetPage
                view.draggedItem = null
                view.isDraggingIcon = false
                view.dragScrollOffset = 0f
                view.isMutatingState = false
                setHoveringDock(false)
                IconDragPageGuard.restore(view)
                clearMagneticDockHover(DockLayout.findFrom(view), false)
                view.setCurrentPage(targetPage)
                view.setDragOverviewActive(false)
                view.invalidate()
                return true
            }

            val (newFractionX, newFractionY) = com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(
                clampedCol, clampedRow, view.currentGridCols, view.currentGridRows, view.context, spanX, spanY
            )
            view.setCurrentPage(dropTargetPage)
            view.onPageSwipe?.invoke(0)
            view.homeScreenItems = view.homeScreenItems.map {
                if (it.id == item.id) {
                    it.copy(
                        page = dropTargetPage,
                        column = clampedCol,
                        row = clampedRow,
                        xFraction = newFractionX,
                        yFraction = newFractionY
                    )
                } else {
                    it
                }
            }
            if (item.page == HomeScreenViewModel.DOCK_CONTAINER) {
                val dockLayout = DockLayout.findFrom(view)
                val max = dockLayout?.maxDockIcons?.coerceAtLeast(1) ?: 5
                DockLayoutRenderer.commitItems(
                    DockLayoutRenderer.reindexExcluding(view.homeScreenItems, item.id, max)
                )
                dockLayout?.invalidate()
            }
            view.draggedItem = null
            view.isDraggingIcon = false
            view.recalculateLayout()
            val prefs = view.context.getSharedPreferences("nexus_prefs", android.content.Context.MODE_PRIVATE)
            val pageCountKey = "home_page_count"
            val currentExplicit = prefs.getInt(pageCountKey, 1)
            if (dropTargetPage + 1 > currentExplicit) {
                prefs.edit().putInt(pageCountKey, dropTargetPage + 1).apply()
            }
            HomeGridDropDiag.logDrop(
                source = "handleHomeDragDrop",
                draggedId = item.id,
                beforeSpanX = spanX,
                beforeSpanY = spanY,
                beforeCell = "${item.column},${item.row}",
                dropCell = "$clampedCol,$clampedRow",
                overlap = false,
                occupant = null,
                outcome = "PERSIST_POSITION",
                extra = "liveCols=${view.currentGridCols} xf=$newFractionX yf=$newFractionY"
            )
            view.onHomeItemDropped?.invoke(item.id, dropTargetPage, newFractionX, newFractionY, clampedCol, clampedRow)
        } else {
            HomeGridDropDiag.logDrop(
                source = "handleHomeDragDrop",
                draggedId = item.id,
                beforeSpanX = item.spanX,
                beforeSpanY = item.spanY,
                beforeCell = "${item.column},${item.row}",
                dropCell = "null",
                overlap = false,
                occupant = null,
                outcome = "NO_CELL_CLEAR_NO_RESTORE"
            )
        }

        val targetPage = dropTargetPage
        view.draggedItem = null
        view.isDraggingIcon = false
        view.dragScrollOffset = 0f
        view.isMutatingState = false
        setHoveringDock(false)
        IconDragPageGuard.clear()
        clearMagneticDockHover(DockLayout.findFrom(view), false)
        view.setCurrentPage(targetPage)
        view.setDragOverviewActive(false)
        view.invalidate()
        return true
    }

    fun clearHomeDragState(
        view: LauncherCanvasView,
        clearMagneticDockHover: (DockLayout?, Boolean) -> Unit
    ) {
        view.cancelPageMotion()
        view.draggedItem = null
        view.isDraggingIcon = false
        view.multiDragHandler.cancel()
        view.dragScrollOffset = 0f
        view.isMutatingState = false
        view.hoveredMergeTarget = null
        view.setDragOverviewActive(false)
        clearMagneticDockHover(DockLayout.findFrom(view), false)
        (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
    }

    private fun isWidgetOrContainer(item: HomeScreenItem?): Boolean {
        if (item == null) return false
        return item.itemType == 3 || item.itemType == com.nexus.launcher.data.HomeItemTypes.MOSAIC || item.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX || item.itemType == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX
    }
}
