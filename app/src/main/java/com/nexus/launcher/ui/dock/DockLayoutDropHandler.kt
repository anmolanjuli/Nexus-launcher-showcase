package com.nexus.launcher.ui.dock

import android.content.Context
import android.util.Log
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.DockItemMover
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.model.DisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DockLayoutDropHandler {

    private const val TAG = "DockDrop"

    fun attach(
        view: View,
        context: Context,
        items: () -> List<HomeScreenItem>,
        maxDockIcons: () -> Int,
        globalInsertionIndexAt: (Float, Float) -> Int,
        localInsertionIndexAt: (Float, Float) -> Int,
        onItemDropped: (DisplayItem, Int) -> Unit,
        onHoveredSlotChanged: (Int) -> Unit,
        getHoveredSlot: () -> Int,
        invalidate: () -> Unit
    ) {
        view.setOnDragListener { _, event ->
            if (view.width == 0 || view.height == 0) {
                return@setOnDragListener false
            }
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED,
                DragEvent.ACTION_DRAG_LOCATION -> {
                    if (view.width != 0 && view.height != 0) {
                        val dropped = DockDropExtraction.extractItemFromDragEvent(context, event)
                        DockLayoutRenderer.isInternalDrag = false
                        DockLayoutRenderer.draggedItemOriginalColumn = null
                        val isVertical = DockAxis.isVertical(context)
                        DockLayoutRenderer.dragFingerLocalX = DockAxis.main(event.x, event.y, isVertical)
                        dropped?.let { DockLayoutRenderer.draggedItemId = it.id }
                        val newSlot = localInsertionIndexAt(event.x, event.y)
                        if (newSlot != getHoveredSlot()) {
                            onHoveredSlotChanged(newSlot)
                            invalidate()
                        }
                    }
                    true
                }
                DragEvent.ACTION_DROP -> {
                    Log.d(TAG, "ACTION_DROP: x=${event.x} y=${event.y} localState=${event.localState?.javaClass?.simpleName}")
                    val droppedHomeItem = DockDropExtraction.extractItemFromDragEvent(context, event)
                    if (droppedHomeItem == null) {
                        Log.w(TAG, "ACTION_DROP: no extractable item — clip=${event.clipData?.itemCount}")
                        clearHover(getHoveredSlot, onHoveredSlotChanged, invalidate)
                        return@setOnDragListener true
                    }
                    Log.d(TAG, "ACTION_DROP: pkg=${droppedHomeItem.packageName} id=${droppedHomeItem.id}")
                    if (DockDropGuard.shouldReject(context, items(), maxDockIcons(), droppedHomeItem)) {
                        Log.w(TAG, "ACTION_DROP: REJECTED by guard")
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        clearHover(getHoveredSlot, onHoveredSlotChanged, invalidate)
                        return@setOnDragListener true
                    }
                    val targetIndex = (view as? DockLayout)?.let { dock ->
                        dock.insertIndexForLocalCoord(event.x, event.y).let { local ->
                            DockLayoutPageOps.globalInsertionIndexAt(
                                dock.currentPage, dock.maxDockIcons, local
                            )
                        }
                    } ?: localInsertionIndexAt(event.x, event.y)
                    Log.d(TAG, "ACTION_DROP: targetIndex=$targetIndex")
                    val displayItem = DockDropExtraction.toDisplayItem(context, droppedHomeItem)
                    if (displayItem == null) {
                        Log.w(TAG, "ACTION_DROP: toDisplayItem failed for pkg=${droppedHomeItem.packageName}")
                        clearHover(getHoveredSlot, onHoveredSlotChanged, invalidate)
                        return@setOnDragListener true
                    }
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    
                    val hoveredSlot = getHoveredSlot()
                    val itemAtSlot = if (hoveredSlot in 0..4) items().getOrNull(hoveredSlot) else null
                    if (itemAtSlot?.itemType == 1) {
                        CoroutineScope(Dispatchers.Main).launch {
                            val dao = dagger.hilt.EntryPoints.get(context.applicationContext, com.nexus.launcher.di.DaoEntryPoint::class.java).homeScreenDao()
                            com.nexus.launcher.ui.folder.FolderAppendEngine.appendAppToFolder(droppedHomeItem, itemAtSlot.id.toLong(), dao, context)
                        }
                        clearHover(getHoveredSlot, onHoveredSlotChanged, invalidate)
                        DockLayoutRenderer.isInternalDrag = false
                        DockLayoutRenderer.draggedItemOriginalColumn = null
                        DockLayoutRenderer.draggedItemId = null
                        DockHoverCoordinator.reset()
                        return@setOnDragListener true
                    }

                    Log.d("DockDrop2", "onItemDropped called pkg=${displayItem.intent?.component?.packageName} slot=$targetIndex")
                    onItemDropped(displayItem, targetIndex)
                    clearHover(getHoveredSlot, onHoveredSlotChanged, invalidate)
                    DockLayoutRenderer.isInternalDrag = false
                    DockLayoutRenderer.draggedItemOriginalColumn = null
                    DockLayoutRenderer.draggedItemId = null
                    DockHoverCoordinator.reset()
                    true
                }
                DragEvent.ACTION_DRAG_EXITED,
                DragEvent.ACTION_DRAG_ENDED -> {
                    DockLayoutRenderer.clearDragPreview()
                    clearHover(getHoveredSlot, onHoveredSlotChanged, invalidate)
                    true
                }
                DragEvent.ACTION_DRAG_STARTED -> true
                else -> true
            }
        }
    }

    /**
     * Custom finger-drag path (drawer → dock). [canvasX]/[canvasY] are in [LauncherCanvasView] space.
     */
    fun handleFingerDrop(
        dock: DockLayout,
        canvasX: Float,
        canvasY: Float,
        item: DisplayItem,
        items: () -> List<HomeScreenItem>,
        maxDockIcons: () -> Int,
        onItemDropped: (DisplayItem, Int) -> Unit
    ): Boolean {
        val folderId = com.nexus.launcher.ui.folder.FolderDragHandoffHelper.folderIdFromIntent(item.intent)
        val pkg = item.intent?.component?.packageName ?: item.intent?.`package`
        Log.d(TAG, "handleFingerDrop: canvasX=$canvasX canvasY=$canvasY pkg=$pkg folderId=$folderId dockW=${dock.width}")
        if (dock.width == 0) {
            Log.w(TAG, "handleFingerDrop: REJECTED dock width=0")
            return false
        }
        if (!dock.containsCanvasPoint(canvasX, canvasY)) {
            Log.w(TAG, "handleFingerDrop: REJECTED outside dock bounds")
            return false
        }
        if (DockDropGuard.shouldReject(dock.context, items(), maxDockIcons(), item)) {
            Log.w(TAG, "handleFingerDrop: REJECTED by guard")
            dock.forceClearHover()
            return false
        }
        val localX = dock.canvasLocalX(canvasX)
        val localY = canvasY - dock.top
        val localIndex = dock.insertIndexForLocalCoord(localX, localY)
        val targetIndex = DockLayoutPageOps.globalInsertionIndexAt(
            dock.currentPage, dock.maxDockIcons, localIndex
        )
        Log.d(TAG, "handleFingerDrop: localIndex=$localIndex targetIndex=$targetIndex")
        dock.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (folderId != null) {
            Log.d(TAG, "handleFingerDrop: folder drop folderId=$folderId slot=$targetIndex")
            CoroutineScope(Dispatchers.Main).launch {
                val dao = dagger.hilt.EntryPoints.get(dock.context.applicationContext, com.nexus.launcher.di.DaoEntryPoint::class.java).homeScreenDao()
                com.nexus.launcher.ui.folder.FolderDragHandoffHelper.moveFolderToDock(folderId, targetIndex, dao)
            }
        } else {
            Log.d(TAG, "handleFingerDrop: invoking onItemDropped pkg=$pkg slot=$targetIndex")
            onItemDropped(item, targetIndex)
        }
        dock.forceClearHover()
        return true
    }

    /**
     * Canvas engine bridge — homescreen finger-drag uses the same slot math as native [ACTION_DROP].
     * [rawDropX] must be [android.view.MotionEvent.getRawX] (screen space), not canvas-local X.
     */
    fun handleCanvasDrop(
        dockLayout: View,
        rawDropX: Float,
        draggedItem: HomeScreenItem,
        homeScreenDao: HomeScreenDao,
        coroutineScope: CoroutineScope,
        onItemsMerged: (List<HomeScreenItem>) -> Unit
    ): Boolean {
        if (draggedItem.itemType == 1) {
            coroutineScope.launch(Dispatchers.IO) {
                val currentMaxIcons = (dockLayout as? com.nexus.launcher.ui.dock.DockLayout)?.maxDockIcons ?: 5
                val allItems = homeScreenDao.getAllItemsDebug()
                val searchOffset = if (com.nexus.launcher.ui.dock.DockSearchSlot.enabled) 1 else 0
                val occupancy = allItems.count { it.page == com.nexus.launcher.ui.HomeScreenViewModel.DOCK_CONTAINER && it.id != draggedItem.id } + searchOffset

                if (occupancy >= currentMaxIcons) {
                    dockLayout.post {
                        dockLayout.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)
                    }
                    return@launch
                }

                val localX = rawDropX - IntArray(2).also { dockLayout.getLocationOnScreen(it) }[0]
                val targetColumn = kotlinx.coroutines.withContext(Dispatchers.Main.immediate) {
                    (dockLayout as? com.nexus.launcher.ui.dock.DockLayout)?.insertIndexForLocalCoord(localX)
                        ?: DockLayoutRenderer.getInsertIndexForX(localX)
                }

                com.nexus.launcher.ui.folder.FolderDragHandoffHelper.moveFolderToNewGrid(
                    folderId = draggedItem.id.toLong(),
                    newPage = com.nexus.launcher.ui.HomeScreenViewModel.DOCK_CONTAINER,
                    newRow = 0,
                    newColumn = targetColumn,
                    dao = homeScreenDao
                )
                val merged = homeScreenDao.getAllItemsDebug()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onItemsMerged(merged)
                }
            }
            return true
        }

        val currentMaxIcons = (dockLayout as? DockLayout)?.maxDockIcons ?: 5
        coroutineScope.launch(Dispatchers.IO) {
            val location = IntArray(2)
            dockLayout.getLocationOnScreen(location)
            val localX = rawDropX - location[0]
            val allItems = homeScreenDao.getAllItemsDebug()
            val searchOffset = if (DockSearchSlot.enabled) 1 else 0
            val occupancy = allItems.count {
                it.page == HomeScreenViewModel.DOCK_CONTAINER && it.id != draggedItem.id
            } + searchOffset
            Log.d("NexusDock", "--- INCOMING CANVAS DROP ---")
            Log.d("NexusDock", "RawX: $rawDropX, MaxIcons: $currentMaxIcons, Occupancy: $occupancy")
            if (occupancy >= currentMaxIcons) {
                Log.e("NexusDock", "DROP REJECTED: dock at capacity ($occupancy >= $currentMaxIcons)")
                dockLayout.post {
                    dockLayout.performHapticFeedback(HapticFeedbackConstants.REJECT)
                }
                return@launch
            }
            val finalDropSlot = withContext(Dispatchers.Main.immediate) {
                val dock = dockLayout as? DockLayout
                if (dock != null) {
                    dock.insertIndexForLocalCoord(localX)
                } else {
                    DockLayoutRenderer.getInsertIndexForX(localX)
                }
            }
            Log.d("NexusDock", "Final Drop Slot: $finalDropSlot")
            DockItemMover.moveItemToDock(
                homeScreenDao, allItems, draggedItem, finalDropSlot, currentMaxIcons, onItemsMerged
            )
        }
        return true
    }

    private fun clearHover(
        hoveredSlot: () -> Int,
        onHoveredSlotChanged: (Int) -> Unit,
        invalidate: () -> Unit
    ) {
        if (hoveredSlot() != -1) {
            onHoveredSlotChanged(-1)
            invalidate()
        }
    }
}
