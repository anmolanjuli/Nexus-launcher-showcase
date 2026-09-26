package com.nexus.launcher.ui.canvas

import android.graphics.Rect
import android.view.View
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.DragState

/**
 * Manages icon drag state: the transition from long-press → drag vs context-menu,
 * active drag position tracking, and ViewModel sync via [setDragState].
 */
class DragHandler {

    // Callbacks wired by LauncherCanvasView, called by MainActivity
    var onDragStarted: ((DisplayItem, Float, Float) -> Unit)? = null
    var onDragMoved: ((Float, Float) -> Unit)? = null
    var onDragDropped: ((Float, Float) -> Unit)? = null
    var onDragCancelled: (() -> Unit)? = null

    // Long-press detected but finger hasn't moved enough to commit drag yet
    var pendingItem: DisplayItem? = null
        private set
    var pendingRect: Rect? = null
        private set
    var pendingView: View? = null
        private set

    // Active drag state
    var isIconDragActive = false
        private set
    var isHoveringDock = false
    var dragItem: DisplayItem? = null
        private set
    var dragX = 0f
        private set
    var dragY = 0f
        private set

    private var hostView: View? = null

    /** Called from the long-press runnable. Stores item, waits for movement. */
    fun onLongPressDetected(item: DisplayItem, rect: Rect, view: View) {
        hostView = view
        pendingItem = item
        pendingRect = rect
        pendingView = view
    }

    /**
     * Returns true to consume the event (icon drag active or pending).
     * Starts icon drag once finger moves past [touchSlop].
     */
    fun onTouchMove(
        x: Float, y: Float,
        startX: Float, startY: Float,
        touchSlop: Int
    ): Boolean {
        if (isIconDragActive) {
            dragX = x
            dragY = y
            onDragMoved?.invoke(x, y)
            hostView?.let { hv ->
                val dock = com.nexus.launcher.ui.dock.DockLayout.findFrom(hv)
                isHoveringDock = dock?.containsCanvasPoint(x, y) == true
            }
            return true
        }
        pendingItem ?: return false

        val dx = kotlin.math.abs(x - startX)
        val dy = y - startY // positive = down
        if (dy > touchSlop * 6 && dy > dx * 2) {
            // Downward swipe — cancel drag
            clearPending()
            return false
        }

        val moved = kotlin.math.hypot(
            (x - startX).toDouble(),
            (y - startY).toDouble()
        )

        val isHorizontallyDominant = dx > kotlin.math.abs(y - startY)
        val threshold = if (isHorizontallyDominant) touchSlop * 2 else touchSlop * 4

        if (moved > threshold) {
            isIconDragActive = true
            dragItem = pendingItem
            dragX = x
            dragY = y
            clearPending()
            onDragStarted?.invoke(dragItem!!, x, y)
        }
        // Consume regardless — prevents drawer scroll while deciding
        return true
    }

    /**
     * Returns true if an icon drag was active and drop was fired.
     * Call [consumePending] after this to check if context menu should show.
     */
    fun onTouchUp(x: Float, y: Float): Boolean {
        if (isIconDragActive) {
            isIconDragActive = false
            isHoveringDock = false
            onDragDropped?.invoke(x, y)
            dragItem = null
            hostView = null
            return true
        }
        return false
    }

    /** Returns pending item triple for context-menu display, then clears state. */
    fun consumePending(): Triple<DisplayItem, Rect, View>? {
        val item = pendingItem ?: return null
        val rect = pendingRect ?: return null
        val view = pendingView ?: return null
        clearPending()
        return Triple(item, rect, view)
    }

    /** Cancels both pending and active drag. */
    fun onTouchCancel() {
        if (isIconDragActive) {
            isIconDragActive = false
            isHoveringDock = false
            onDragCancelled?.invoke()
            dragItem = null
        }
        clearPending()
        hostView = null
    }

    var hostCanvasView: LauncherCanvasView? = null
    private var lastHoveredCell: Pair<Int, Int>? = null

    /** Synchronous position update for external drag sources (e.g. search overlay) — bypasses
     *  the async ViewModel round-trip so the highlight and shadow update every frame. */
    fun directMove(localX: Float, localY: Float) {
        dragX = localX; dragY = localY
        if (!isIconDragActive) isIconDragActive = true
        val canvas = hostCanvasView ?: return
        val cell = CanvasHitTestHelper.getCellAtDrop(canvas, localX, localY)
        if (cell != null && cell != lastHoveredCell) {
            lastHoveredCell = cell
            canvas.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        canvas.invalidate()
    }

    /** Sync with ViewModel DragState (e.g. when cancelled externally). */
    fun setDragState(state: DragState) {
        when (state) {
            is DragState.Idle -> {
                isIconDragActive = false
                dragItem = null
                lastHoveredCell = null
                val canvas = hostCanvasView ?: (hostView as? LauncherCanvasView)
                (canvas?.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
                canvas?.invalidate()
            }
            is DragState.Dragging -> {
                isIconDragActive = true
                dragItem = state.item
                val canvas = hostCanvasView ?: (hostView as? LauncherCanvasView)
                if (canvas != null) {
                    val loc = IntArray(2)
                    canvas.getLocationOnScreen(loc)
                    val localX = state.fingerX - loc[0]
                    val localY = state.fingerY - loc[1]
                    dragX = localX
                    dragY = localY

                    val cell = CanvasHitTestHelper.getCellAtDrop(canvas, localX, localY)
                    if (cell != null && cell != lastHoveredCell) {
                        lastHoveredCell = cell
                        canvas.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }

                    val targetItem = if (cell != null) {
                        canvas.homeScreenItems.find {
                            val pos = canvas.fractionDerivedPositions[it.id] ?: Triple(it.page, it.column, it.row)
                            pos.first == canvas.currentPage && it.containerId == -1L &&
                                cell.first >= pos.second && cell.first < pos.second + it.spanX &&
                                cell.second >= pos.third && cell.second < pos.third + it.spanY
                        }
                    } else null

                    val overlay = (canvas.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout
                    if (targetItem != null && (targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || targetItem.itemType == com.nexus.launcher.data.HomeItemTypes.APP_BOX)) {
                        overlay?.setBoxAcceptanceHover(targetItem.id, true)
                    } else {
                        overlay?.setBoxAcceptanceHover(null, false)
                    }
                    canvas.invalidate()
                } else {
                    dragX = state.fingerX
                    dragY = state.fingerY
                }
            }
            is DragState.Dropping -> {
                isIconDragActive = false
                dragItem = null
                lastHoveredCell = null
                val canvas = hostCanvasView ?: (hostView as? LauncherCanvasView)
                (canvas?.context as? com.nexus.launcher.ui.MainActivity)?.widgetHostLifecycle?.widgetOverlayLayout?.setBoxAcceptanceHover(null, false)
                canvas?.invalidate()
            }
        }
    }

    private fun clearPending() {
        pendingItem = null
        pendingRect = null
        pendingView = null
    }
}
