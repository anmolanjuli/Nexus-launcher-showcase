package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.model.LauncherState

/**
 * Animates the drawer translation when the launcher state (HOME/DRAWER) changes.
 * Extracted verbatim from [LauncherCanvasView] — behavior unchanged.
 */
class CanvasStateAnimator(private val view: LauncherCanvasView) {

    fun setUiState(state: LauncherState) {
        if (view.uiState != state) {
            view.uiState = state
            
            if (view.viewHeight > 0) {
                val targetY = if (state == LauncherState.HOME) view.viewHeight.toFloat() else 0f
                if (state == LauncherState.DRAWER) {
                    DrawerHaptics.onDrawerOpen(view)
                } else if (state == LauncherState.HOME) {
                    DrawerHaptics.onDrawerClose(view)
                }
                // Same spring the drag-release path uses (DrawerSnapAnimator), so a drawer
                // opened by tap and a drawer opened by swipe settle on one identical curve.
                // These were previously 300 ms/DecelerateInterpolator(1.0) versus
                // 280 ms/DecelerateInterpolator(1.6) — visibly two different drawers.
                val settle = SettlePhysics.settle(
                    fromPx = view.drawerTranslationY,
                    toPx = targetY,
                    releaseVelocityPxPerSec = 0f,
                    speedMultiplier = MotionSpeed.multiplier(view.context),
                    omega = SettlePhysics.OMEGA_CRISP
                )
                // Tap-triggered opens (search FAB, gesture actions, etc.) settle through this
                // animator instead of the drag-release path (DrawerSnapAnimator + DrawerFlingHandler,
                // which already pairs with DockDrawerSync.attachToDrawerAnimator()) — without its
                // own dock sync here, the dock stayed fully visible until the next drawer scroll
                // forced a sync, which read as "dock only disappears once I start scrolling."
                val dockSync = com.nexus.launcher.ui.dock.DockDrawerSync(view)
                view.cancelDrawerMotion()
                // Already where it is going: the drag release (DrawerSnapAnimator) has just
                // finished carrying it there and is only now telling us the state changed.
                // Animating from a place to itself is 120ms of invalidating for no movement,
                // with the drawer's own settle frame landing in the middle of it.
                if (kotlin.math.abs(view.drawerTranslationY - targetY) < 1.0f) {
                    view.drawerTranslationY = targetY
                    dockSync.sync()
                    if (view.layoutDirty) view.recalculateLayout()
                    view.postInvalidateOnAnimation()
                    if (state == LauncherState.DRAWER) view.triggerDrawerPrewarm()
                    return
                }
                view.drawerSettle.start(
                    view.drawerTranslationY, targetY, settle.durationMs, settle.interpolator,
                    onUpdate = { value ->
                        view.drawerTranslationY = value
                        dockSync.sync()
                        view.postInvalidateOnAnimation()
                    },
                ) { cancelled ->
                    if (!cancelled) {
                        view.drawerTranslationY = targetY
                        dockSync.sync()
                        if (view.layoutDirty) view.recalculateLayout()
                        view.postInvalidateOnAnimation()
                        if (view.uiState == LauncherState.DRAWER) {
                            view.triggerDrawerPrewarm()
                        }
                    }
                }
            } else {
                view.invalidate()
            }
        }
    }
}
