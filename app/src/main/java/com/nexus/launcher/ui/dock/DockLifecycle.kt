package com.nexus.launcher.ui.dock

import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.IconDragPageGuard
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.contextmenu.ContextMenuManager
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.ui.dock.settings.DockSettingsDialog
import com.nexus.launcher.ui.dock.settings.DockSettingsEntryPoint
import com.nexus.launcher.ui.model.LauncherState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

class DockLifecycle(
    private val activity: MainActivity,
    private val mainContainer: FrameLayout,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val contextMenuManager: ContextMenuManager
) {
    val dockLayout: DockLayout = DockLayout(activity)

    fun setup() {
        val dp = activity.resources.displayMetrics.density
        val targetContainer = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.workspace_container) ?: mainContainer
        targetContainer.addView(
            dockLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (100 * dp).toInt(),
                android.view.Gravity.BOTTOM
            )
        )

        val screenWidth = dockLayout.resources.displayMetrics.widthPixels
        dockLayout.minimumHeight = (80 * dp).toInt()
        dockLayout.minimumWidth = screenWidth

        dockLayout.setupDropTarget { droppedItem, slotIndex ->
            homeScreenViewModel.moveItemToDock(droppedItem, slotIndex)
        }
        dockLayout.onItemClicked = { item ->
            if (!DockSearchSlot.isSearchItem(item)) {
                dockLayout.launchIntentFor(item)?.let { activity.startActivity(it) }
            }
        }
        dockLayout.onSearchSlotClicked = {
            DockSearchLauncher.onSearchSlotTapped(activity)
        }
    }

    /**
     * Must run after [com.nexus.launcher.ui.LauncherCanvasListeners.register] so the dock
     * interceptor is not overwritten by the grid-only drop handler.
     */
    fun wireDrawerDrop() {
        val gridDropHandler = canvasView.onDragDropped
        canvasView.onDragDropped = { x, y ->
            val dragged = canvasView.dragHandler.dragItem
            if (dragged != null && dockLayout.width > 0 &&
                dockLayout.containsCanvasPoint(x, y)) {
                dockLayout.dispatchDrop(x, dragged, y)
                IconDragPageGuard.restore(canvasView)
                canvasView.onDragCancelled?.invoke()
            } else {
                gridDropHandler?.invoke(x, y)
            }
        }
    }

    fun wireOutboundDrag(
        dragTouchHandler: com.nexus.launcher.ui.canvas.DragTouchHandler,
        contextMenuManager: ContextMenuManager
    ) {
        wireDrawerVisibilityEnforcer()
        wireContextMenuAndSettings()
        dockLayout.onInitiateDrag = { item, anchor ->
            contextMenuManager.dismiss()
            val maxIcons = dockLayout.maxDockIcons.coerceAtLeast(1)
            val localColumn = DockSlotLayout.localColumnFor(item, maxIcons)
            val metrics = DockSlotLayout.metrics(anchor.width, dockLayout.pageItemCount(), maxIcons)
            val parentX = anchor.left + DockSlotLayout.centerX(localColumn, metrics)
            val parentY = anchor.top + anchor.height / 2f
            val (cx, cy) = DockCoordHelper.parentToCanvasLocal(canvasView, parentX, parentY)
            canvasView.draggedItem = item
            canvasView.dragX = cx
            canvasView.dragY = cy
            canvasView.startTouchX = cx
            canvasView.startTouchY = cy
            canvasView.isDragging = true
            canvasView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        dockLayout.onOutboundDragMove = { x, y ->
            canvasView.dragX = x
            canvasView.dragY = y
            if (!canvasView.isDraggingIcon) {
                canvasView.isDraggingIcon = true
                contextMenuManager.dismiss()
            }
            dragTouchHandler.updateDockHoverState(x, y)
            canvasView.invalidate()
        }
        dockLayout.onOutboundDragEnd = outboundDragEnd@{ x, y, internalSlot, wasInternal ->
            val dragged = dockLayout.outbound.currentDraggedItem()
            if (wasInternal && dragged != null && DockSearchSlot.isSearchItem(dragged)) {
                val slot = internalSlot ?: 0
                val repository = EntryPointAccessors.fromApplication(
                    activity.applicationContext,
                    DockSettingsEntryPoint::class.java
                ).dockSettingsRepository()
                activity.lifecycleScope.launch {
                    repository.updateSearchSlotIndex(slot)
                }
                DockSearchSlot.slotIndex = slot
                canvasView.draggedItem = null
                canvasView.isDragging = false
                canvasView.isDraggingIcon = false
                dockLayout.invalidate()
                canvasView.invalidate()
                return@outboundDragEnd
            }
            if (dragged != null && DockSearchSlot.isSearchItem(dragged)) {
                canvasView.draggedItem = null
                canvasView.isDragging = false
                canvasView.isDraggingIcon = false
                dockLayout.invalidate()
                canvasView.invalidate()
                return@outboundDragEnd
            }
            canvasView.dragX = x
            canvasView.dragY = y
            dragTouchHandler.completeOutboundDragAt(x, y, internalSlot, wasInternal)
            canvasView.isDragging = false
            canvasView.invalidate()
        }
    }

    /** Brute-force dock visible whenever the drawer settles home or a slide animation is cancelled. */
    private fun wireDrawerVisibilityEnforcer() {
        enforceDockVisibility()

        val previousStateChanged = canvasView.onStateChanged
        canvasView.onStateChanged = { state ->
            previousStateChanged?.invoke(state)
            if (state == LauncherState.HOME) {
                enforceDockVisibility()
            } else {
                dockLayout.alpha = 0f
                dockLayout.visibility = View.GONE
            }
        }

        val previousProgress = canvasView.onDrawerAnimationProgress
        canvasView.onDrawerAnimationProgress = { progress ->
            previousProgress?.invoke(progress)
            if (progress <= 0f) {
                assertDockProperties()
            } else if (progress >= 1f) {
                dockLayout.alpha = 0f
                dockLayout.visibility = View.GONE
            }
        }
    }

    private fun enforceDockVisibility() {
        assertDockProperties()
        dockLayout.invalidate()
        dockLayout.requestLayout()
    }

    /**
     * Property-only half of [enforceDockVisibility], for the per-frame caller.
     *
     * `onDrawerAnimationProgress` fires from inside LauncherDrawEngine.onDraw, and progress is
     * 0f on every home frame — so the full enforcer was scheduling a dock measure+layout pass on
     * every frame of every page swipe. None of these writes can invalidate layout (alpha,
     * visibility, translation and scale are all draw-time transforms), and every setter below
     * already early-outs when the value is unchanged and self-invalidates when it is not.
     * The brute-force invalidate/requestLayout stays on the edge-triggered callers, which is
     * where it was actually needed.
     */
    private fun assertDockProperties() {
        dockLayout.alpha = 1f
        dockLayout.visibility = View.VISIBLE
        if (!dockLayout.freezeTranslation) {
            dockLayout.translationY = 0f
            dockLayout.scaleX = 1f
            dockLayout.scaleY = 1f
        }
    }

    /** Runs after MainActivity onCreate wiring so dock menu + settings stay on [DockLifecycle]. */
    private fun wireContextMenuAndSettings() {
        dockLayout.onItemLongClicked = { item ->
            DockContextMenuLauncher.show(activity, contextMenuManager, dockLayout, item)
        }
        val repository = EntryPointAccessors.fromApplication(
            activity.applicationContext,
            DockSettingsEntryPoint::class.java
        ).dockSettingsRepository()
        dockLayout.onDockSettingsRequested = {
            DockSettingsDialog(repository, dockLayout).show(activity.supportFragmentManager, "DockSettings")
        }
    }
}
