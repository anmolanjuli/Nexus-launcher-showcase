package com.nexus.launcher.ui.canvas
import android.view.HapticFeedbackConstants
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.dock.DockCanvasDropHelper
import com.nexus.launcher.ui.dock.DockCoordHelper
import com.nexus.launcher.ui.dock.DockOutboundDropHelper
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutRenderer
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState
import com.nexus.launcher.ui.model.SelectionSource

class DragTouchHandler(private val view: LauncherCanvasView) {

    private val dockHoverHelper = DragDockHoverHelper(view)
    val lastHoveredDockSlot: Int? get() = dockHoverHelper.lastHoveredDockSlot
    private var lastHoveredCell: Pair<Int, Int>? = null
    private var canvasDropDispatched = false

    fun onActionDown() { canvasDropDispatched = false }
    var isHoveringDock: Boolean
        get() = view.dragHandler.isHoveringDock
        set(value) { view.dragHandler.isHoveringDock = value }

    fun updateDockHoverState(canvasLocalX: Float, canvasLocalY: Float) {
        dockHoverHelper.updateDockHoverState(canvasLocalX, canvasLocalY, canvasDropDispatched) { isHoveringDock = it }
    }

    private fun applyMagneticDockHover(rawX: Float, rawY: Float, canvasLocalX: Float, canvasLocalY: Float) {
        dockHoverHelper.applyMagneticDockHover(rawX, rawY, canvasLocalX, canvasLocalY, canvasDropDispatched) { isHoveringDock = it }
    }

    private fun clearMagneticDockHover(dock: DockLayout?, deferDockInvalidate: Boolean = false) {
        dockHoverHelper.clearMagneticDockHover(dock, { isHoveringDock = it }, deferDockInvalidate)
    }

    fun tap(x: Float, y: Float): android.content.Intent? {
        val item = getItemAt(x, y)
        return item?.intent
    }

    fun tapHomeScreen(x: Float, y: Float): android.content.Intent? =
        dragTapHomeScreen(view, x, y)

    fun getItemAt(x: Float, y: Float): DisplayItem? = getDragItemAt(view, x, y)

    fun buildHomeLongPressRunnable(item: com.nexus.launcher.data.HomeScreenItem): Runnable = Runnable {
        if (!view.isDragging) {
            val packageName = item.packageName
            val cacheKey = view.homeScreenRenderer.getCacheKey(item)
            val icon = view.homeScreenRenderer.iconCache[cacheKey]
            val label = view.homeScreenRenderer.labelCache[cacheKey]
                ?: try {
                    if (item.itemType == 2) {
                        item.folderTitle.takeIf { it.isNotEmpty() } ?: packageName
                    } else {
                        val pm = view.context.packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    }
                } catch (e: Exception) {
                    packageName.substringAfterLast('.')
                }
            val intent = if (item.itemType == 2 && !item.folderConfigJson.isNullOrEmpty()) {
                try {
                    val shortcutId = org.json.JSONObject(item.folderConfigJson).optString("shortcutId")
                    android.content.Intent("nexus.shortcut.START").apply {
                        setPackage(packageName)
                        putExtra("packageName", packageName)
                        putExtra("shortcutId", shortcutId)
                    }
                } catch (e: Exception) {
                    view.context.packageManager.getLaunchIntentForPackage(packageName)
                }
            } else {
                view.context.packageManager.getLaunchIntentForPackage(packageName)
            }
            val displayItem = DisplayItem(label, icon, intent, null)
            val itemRect = HomeIconHugRect.forItem(view, item)
            val homeItemId = view.homeScreenItems
                .firstOrNull {
                    val pos = view.fractionDerivedPositions[it.id] ?: Triple(it.page, it.column, it.row)
                    pos.first == view.currentPage && pos.second == view.longPressHomeCol && pos.third == view.longPressHomeRow
                }?.id ?: item.id
            view.homeLongPressConsumed = true
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

            val hitItem = view.homeScreenItems.firstOrNull { it.id == homeItemId }
            if (hitItem != null) {
                val selecting = view.selectionState as? SelectionState.Selecting
                if (selecting != null && selecting.source == SelectionSource.HOME_SCREEN) {
                    view.homeLongPressConsumed = true
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    if (homeItemId in selecting.selectedIds) {
                        if (view.multiDragHandler.tryStart(view, homeItemId)) {
                            view.draggedItem = hitItem
                            view.dragX = view.startTouchX
                            view.dragY = view.startTouchY
                            view.isDragging = true
                            view.isDraggingIcon = true
                            IconDragPageGuard.capture(view)
                            view.setDragOverviewActive(true)
                            view.invalidate()
                        }
                    } else {
                        view.onToggleHomeSelection?.invoke(homeItemId)
                    }
                    return@Runnable
                }
                view.draggedItem = hitItem
                view.dragX = view.startTouchX
                view.dragY = view.startTouchY
                view.isDragging = true
                view.invalidate()
                if (view.onHomeIconLongPressedWithId != null) {
                    view.onHomeIconLongPressedWithId?.invoke(displayItem, itemRect, view, homeItemId)
                } else {
                    view.onHomeIconLongPressed?.invoke(displayItem, itemRect, view)
                }
            }
        }
    }

    fun buildLongPressRunnable(startX: Float, startY: Float): Runnable = Runnable {
        val xInt = startX.toInt(); val yInt = startY.toInt()
        val gridItem = view.drawerItems.firstOrNull { it.hitRect.contains(xInt, (yInt + view.scrollY - view.drawerTranslationY).toInt()) }

        val item = getItemAt(startX, startY)
        if (item != null && gridItem != null && !view.isDragging) {
            val adjustedRect = android.graphics.Rect(
                gridItem.drawRect.left,
                (gridItem.drawRect.top - view.scrollY + view.drawerTranslationY).toInt(),
                gridItem.drawRect.right,
                (gridItem.drawRect.bottom - view.scrollY + view.drawerTranslationY).toInt()
            )

            if (item.intent?.action == "nexus.folder.OPEN") {
                if (!view.isDragging) {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    view.dragHandler.onLongPressDetected(item, adjustedRect, view)
                }
                return@Runnable
            }

            if (view.selectionState is SelectionState.Selecting) {
                view.onItemLongPressed?.invoke(item, adjustedRect, view)
            } else {
                view.isDragging = true // Prevent tap/scroll
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                view.onItemLongPressed?.invoke(item, adjustedRect, view)
                view.dragHandler.onLongPressDetected(item, adjustedRect, view)
                view.drawerLongPressConsumed = true
            }
        } else if (view.uiState == LauncherState.HOME && !view.isDragging) {
            if (CanvasHitTestHelper.getItemAt(view, startX, startY) == null) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                view.homeLongPressConsumed = true
                view.onEmptyHomeScreenLongPress?.invoke()
            }
        }
    }

    fun snapDrawer(forcedState: LauncherState? = null) =
        DrawerSnapAnimator.snapDrawer(view, forcedState)

    private fun animatePageTransition(view: LauncherCanvasView, targetPage: Int) =
        DrawerSnapAnimator.animatePageTransition(view, targetPage)

    fun handleHomeDragMove(event: android.view.MotionEvent): Boolean {
        if (view.draggedItem == null) return false
        view.dragX = event.x
        view.dragY = event.y
        if (view.multiDragHandler.isActive) {
            view.multiDragHandler.updatePosition(event.x, event.y)
        }

        val distance = Math.hypot((event.x - view.startTouchX).toDouble(), (event.y - view.startTouchY).toDouble())
        if (distance > 15f && !view.isDraggingIcon) {
            view.isDraggingIcon = true
            IconDragPageGuard.capture(view)
            view.onDismissContextMenu?.invoke()
            view.setDragOverviewActive(true)
        }

        val cardTrackActive = SelectionModeTransform.isCardTrackActive(view)
        if (!cardTrackActive) {
            val displayMetrics = view.context.resources.displayMetrics
            val currentTime = System.currentTimeMillis()
            if (IconDragPageGuard.allowsEdgePageSwitch(
                    event.rawY, isHoveringDock, displayMetrics.density, displayMetrics.heightPixels) &&
                currentTime - view.lastPageScrollTime > 800
            ) {
                if (event.x < 30f && view.currentPage > 0) {
                    animatePageTransition(view, view.currentPage - 1)
                    view.lastPageScrollTime = currentTime
                } else if (event.x > view.viewWidth - 30f && view.currentPage < view.totalPages - 1) {
                    animatePageTransition(view, view.currentPage + 1)
                    view.lastPageScrollTime = currentTime
                }
            }
        }
        DockLayout.findFrom(view)?.let { dock ->
            applyMagneticDockHover(event.rawX, event.rawY, event.x, event.y)
        } ?: clearMagneticDockHover(null)

        if (isHoveringDock) {
            lastHoveredCell = null
            view.hoveredMergeTarget = null
            (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
        } else {
            val targetCell = CanvasHitTestHelper.getCellAtDrop(view, event.x, event.y)
            if (targetCell != null) {
                if (lastHoveredCell != targetCell) {
                    lastHoveredCell = targetCell
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                val targetCol = targetCell.first
                val targetRow = targetCell.second
                val draggedItem = view.draggedItem
                if (draggedItem != null) {
                    val targetItem = view.homeScreenItems.find { 
                        val pos = view.fractionDerivedPositions[it.id] ?: Triple(it.page, it.column, it.row)
                        val cCol = pos.second
                        val cRow = pos.third
                        pos.first == view.currentPage && 
                        it.containerId == -1L && 
                        it.id != draggedItem.id &&
                        targetCol >= cCol && targetCol < cCol + it.spanX &&
                        targetRow >= cRow && targetRow < cRow + it.spanY
                    }

                    val overlay = (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout
                    if (targetItem != null && (draggedItem.itemType == 0 || draggedItem.itemType == 2) &&
                        (targetItem.itemType == 0 || targetItem.itemType == 1 || targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX)) {
                        if (view.hoveredMergeTarget?.id != targetItem.id) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        view.hoveredMergeTarget = targetItem
                        if (targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX) {
                            overlay?.setBoxAcceptanceHover(targetItem.id, true)
                        } else {
                            overlay?.setBoxAcceptanceHover(null, false)
                        }
                    } else {
                        view.hoveredMergeTarget = null
                        overlay?.setBoxAcceptanceHover(null, false)
                    }
                } else {
                    view.hoveredMergeTarget = null
                    (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
                }
            } else {
                lastHoveredCell = null
                view.hoveredMergeTarget = null
                (view.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
            }
        }
        
        view.postInvalidateOnAnimation()
        return true
    }

    fun completeHomeDragAt(canvasLocalX: Float, canvasLocalY: Float): Boolean {
        val (rawX, rawY) = DockCoordHelper.canvasLocalToScreen(view, canvasLocalX, canvasLocalY)
        val event = android.view.MotionEvent.obtain(
            System.currentTimeMillis(), System.currentTimeMillis(),
            android.view.MotionEvent.ACTION_UP, canvasLocalX, canvasLocalY, 0)
        val handled = handleHomeDragDrop(event, rawX, rawY)
        event.recycle()
        return handled
    }

    fun completeOutboundDragAt(
        canvasLocalX: Float,
        canvasLocalY: Float,
        internalSlot: Int?,
        wasInternalDrag: Boolean
    ): Boolean {
        val item = view.draggedItem ?: return false
        val dock = DockLayout.findFrom(view) ?: return completeHomeDragAt(canvasLocalX, canvasLocalY)
        val (rawX, rawY) = DockCoordHelper.canvasLocalToScreen(view, canvasLocalX, canvasLocalY)
        if (item.page == HomeScreenViewModel.DOCK_CONTAINER &&
            DockOutboundDropHelper.isOverDock(view, dock, canvasLocalX, canvasLocalY, rawY)) {
            if (wasInternalDrag) {
                DockOutboundDropHelper.commitInternalReorder(
                    view, item, dock, rawX, rawY, internalSlot)
                clearMagneticDockHover(dock, deferDockInvalidate = true)
                clearHomeDragState(view)
                view.invalidate()
                return true
            }
        }
        return completeHomeDragAt(canvasLocalX, canvasLocalY)
    }

    fun handleHomeDragDrop(
        event: android.view.MotionEvent,
        rawX: Float = event.rawX,
        rawY: Float = event.rawY
    ): Boolean = HomeScreenDragDropHelper.handleHomeDragDrop(
        view = view,
        event = event,
        rawX = rawX,
        rawY = rawY,
        canvasDropDispatched = canvasDropDispatched,
        onCanvasDropDispatched = { canvasDropDispatched = true },
        clearMagneticDockHover = ::clearMagneticDockHover,
        setHoveringDock = { isHoveringDock = it }
    )

    private fun clearHomeDragState(view: LauncherCanvasView) {
        HomeScreenDragDropHelper.clearHomeDragState(view, ::clearMagneticDockHover)
    }
}
