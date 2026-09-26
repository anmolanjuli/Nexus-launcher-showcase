package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState

/**
 * Handles the fling-commit decision at ACTION_UP for the drawer.
 * Extracted from LauncherTouchHandler to keep that class under 400 lines.
 *
 * Given the measured velocityY and current drawer position, decides whether to:
 *  - snap the drawer open/closed (velocity-driven),
 *  - fling the drawer content (already fully open),
 *  - or fall back to the midpoint snap (insufficient velocity).
 */
internal object DrawerFlingHandler {

    fun handle(
        view: LauncherCanvasView,
        velocityY: Float,
        wasPureTap: Boolean,
        railTrackingActive: Boolean,
        dragTouchHandler: DragTouchHandler,
        dockDrawerSync: com.nexus.launcher.ui.dock.DockDrawerSync,
        onRailReset: () -> Unit
    ) {
        // Density-scaled threshold: ~200 dp/s converted to px/s for light, natural flicks.
        val density = view.resources.displayMetrics.density
        val velocityThreshold = 200f * density
        DrawerPhysicsLog.onFlingUp(view, velocityY, velocityThreshold, wasPureTap)

        if (!wasPureTap && Math.abs(velocityY) > velocityThreshold) {
            if (view.drawerTranslationY > 0f) {
                val nextState = if (velocityY < 0 && view.selectionState !is SelectionState.Selecting) {
                    LauncherState.DRAWER
                } else {
                    LauncherState.HOME
                }
                if (nextState == LauncherState.HOME) {
                    view.railRenderer.updateFingerPosition(-1f, -1f, view.viewWidth.toFloat())
                    onRailReset()
                    DrawerSnapAnimator.snapDrawer(view, LauncherState.HOME, velocityY)
                    dockDrawerSync.attachToDrawerAnimator()
                } else {
                    DrawerSnapAnimator.snapDrawer(view, nextState, velocityY)
                    dockDrawerSync.attachToDrawerAnimator()
                }
            } else if (view.drawerTranslationY == 0f && view.scrollY == 0f && velocityY > 0) {
                view.railRenderer.updateFingerPosition(-1f, -1f, view.viewWidth.toFloat())
                onRailReset()
                DrawerSnapAnimator.snapDrawer(view, LauncherState.HOME, velocityY)
                dockDrawerSync.attachToDrawerAnimator()
            } else if (view.drawerTranslationY == 0f) {
                view.scroller.abortAnimation()
                // Small overscroll (was 300px) — long bounce felt frictional after dense restores.
                val overY = (48f * density).toInt().coerceIn(24, 96)
                view.scroller.fling(
                    0, view.scrollY.toInt(),
                    0, -velocityY.toInt(),
                    0, 0,
                    0, view.maxScrollY.toInt(),
                    0, overY
                )
                view.postInvalidateOnAnimation()
            }
        } else if (!wasPureTap) {
            if (view.selectionState !is SelectionState.Selecting || view.uiState == LauncherState.DRAWER) {
                val projected = SettlePhysics.project(view.drawerTranslationY, velocityY)
                val threshold = view.viewHeight / 2f
                if (projected > threshold) {
                    view.railRenderer.updateFingerPosition(-1f, -1f, view.viewWidth.toFloat())
                    onRailReset()
                    DrawerSnapAnimator.snapDrawer(view, LauncherState.HOME, velocityY)
                    dockDrawerSync.attachToDrawerAnimator()
                } else {
                    DrawerSnapAnimator.snapDrawer(view, LauncherState.DRAWER, velocityY)
                    dockDrawerSync.attachToDrawerAnimator()
                }
            }
        }
    }
}
