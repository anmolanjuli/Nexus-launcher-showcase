package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.dock.DockDrawerSync
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState

/**
 * Handles drawer translation and scroll updates during touch drag moves.
 * Extracted from LauncherTouchHandler to keep that file well below 400 lines.
 */
internal object DrawerTouchMoveHandler {

    private const val EARLY_REVEAL_TRAVEL_FRACTION = 0.45f

    /**
     * Updates drawerTranslationY or scrollY for a given vertical delta.
     * Drawer open/close travel completes within 45% of screen height for early reveal.
     */
    fun handleDragMove(
        view: LauncherCanvasView,
        dy: Float,
        eventY: Float,
        dockDrawerSync: DockDrawerSync
    ): Boolean {
        // Swiping down from idle home screen should not move the drawer
        if (view.uiState == LauncherState.HOME && view.drawerTranslationY >= view.viewHeight.toFloat() && dy > 0) {
            return false
        }

        val effectiveDy = dy / EARLY_REVEAL_TRAVEL_FRACTION

        // Worked out locally and written once: the drawerTranslationY setter announces drawer
        // progress, and a transient value below 0 announces "settled home" while uiState is
        // still HOME — the window blur dropped to 0 and the search pill went GONE for the rest
        // of the drag the moment the sheet reached the top.
        var translation = view.drawerTranslationY
        if (dy < 0) {
            view.cancelDrawerPrewarmInFlight()
            if (translation > 0) {
                translation += effectiveDy
                if (translation < 0) {
                    // Hand the overshoot to the list in finger units, not amplified ones.
                    view.scrollY -= translation * EARLY_REVEAL_TRAVEL_FRACTION
                    translation = 0f
                }
            } else {
                view.scrollY -= dy
            }
        } else if (dy > 0) {
            if (view.scrollY > 0) {
                view.scrollY -= dy
                if (view.scrollY < 0) {
                    view.scrollY = 0f
                }
            } else {
                translation += effectiveDy
            }
        }
        view.drawerTranslationY = when {
            view.selectionState is SelectionState.Selecting && view.uiState == LauncherState.DRAWER -> 0f
            view.selectionState is SelectionState.Selecting && view.uiState == LauncherState.HOME -> view.viewHeight.toFloat()
            else -> translation.coerceIn(0f, view.viewHeight.toFloat())
        }
        view.scrollY = Math.max(0f, Math.min(view.maxScrollY, view.scrollY))
        DrawerHaptics.checkScrollBoundaries(view)
        DrawerPhysicsLog.onDragMove(view, dy, eventY)
        dockDrawerSync.sync()
        view.postInvalidateOnAnimation()
        return false
    }
}
