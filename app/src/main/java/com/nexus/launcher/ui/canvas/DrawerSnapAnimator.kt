package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.model.LauncherState

/**
 * Owns all drawer and page-transition ValueAnimator creation.
 * Extracted from DragTouchHandler to keep that class under 400 lines.
 */
internal object DrawerSnapAnimator {

    /**
     * Animates [view.drawerTranslationY] to its target position.
     *
     * Timing and curve both come from [SettlePhysics], so the drawer leaves the finger at
     * exactly the speed the finger was moving. The old pairing — a `distance / velocity`
     * duration under a `DecelerateInterpolator(1.6f)`, whose opening slope is 3.2x its own
     * average — departed anywhere from 3x to 45x the release speed once the 280 ms clamp bit,
     * which is what made a slow drag-and-release snap out from under the finger.
     */
    fun snapDrawer(
        view: LauncherCanvasView,
        forcedState: LauncherState? = null,
        velocityY: Float = 0f
    ) {
        val targetY: Float
        val nextState: LauncherState

        if (forcedState != null) {
            nextState = forcedState
            targetY = if (forcedState == LauncherState.HOME) view.viewHeight.toFloat() else 0f
        } else {
            // Choose the side the finger was heading for, not the pixel it stopped on. A bare
            // midpoint test ignores momentum entirely, so a deliberate but unhurried swipe that
            // ended just shy of halfway sprang back open.
            val projected = SettlePhysics.project(view.drawerTranslationY, velocityY)
            val threshold = view.viewHeight / 2f
            if (projected > threshold) {
                targetY = view.viewHeight.toFloat()
                nextState = LauncherState.HOME
            } else {
                targetY = 0f
                nextState = LauncherState.DRAWER
            }
        }

        if (view.drawerTranslationY != targetY) {
            if (nextState == LauncherState.DRAWER) {
                DrawerHaptics.onDrawerOpen(view)
            } else if (nextState == LauncherState.HOME) {
                DrawerHaptics.onDrawerClose(view)
            }
            val settle = SettlePhysics.settle(
                fromPx = view.drawerTranslationY,
                toPx = targetY,
                releaseVelocityPxPerSec = velocityY,
                speedMultiplier = MotionSpeed.multiplier(view.context),
                // Crisp channel (~200 ms). The drawer's background cross-dissolves in place
                // while its icons translate, and several surfaces ramp their blur alongside;
                // a shorter settle keeps that composite from being on screen long enough to
                // read as separate motions. Move this to OMEGA for a more relaxed close.
                omega = SettlePhysics.OMEGA_CRISP
            )
            DrawerPhysicsLog.onSnap(view, targetY, nextState.name, settle.durationMs)
            val dockSync = com.nexus.launcher.ui.dock.DockDrawerSync(view)
            val finish = {
                view.drawerTranslationY = targetY
                if (view.uiState != nextState) {
                    // Claim the state before announcing it. The announcement comes back through
                    // CanvasStateAnimator, which would otherwise treat this as a state it has not
                    // applied yet: a second haptic for one swipe, and an animation from where the
                    // drawer already is. Everything that block would have done for a gesture —
                    // the haptic, the dock, the prewarm, the deferred layout — is done here.
                    view.uiState = nextState
                    if (view.layoutDirty) view.recalculateLayout()
                    view.onStateChanged?.invoke(nextState)
                }
                dockSync.sync()
                // Warm icons after the snap — never during finger tracking (CPU fight).
                if (nextState == LauncherState.DRAWER) {
                    view.triggerDrawerPrewarm()
                    view.postInvalidateOnAnimation()
                }
            }

            view.animator?.cancel()
            view.animator = null
            view.drawerSettle.start(
                view.drawerTranslationY, targetY, settle.durationMs, settle.interpolator,
                onUpdate = { value ->
                    view.drawerTranslationY = value
                    dockSync.sync()
                    view.postInvalidateOnAnimation()
                },
            ) { cancelled -> if (!cancelled) finish() }
        } else {
            DrawerPhysicsLog.onSnap(view, targetY, nextState.name, 0L)
            // Nothing to animate, but the dock still has to match the state it lands in.
            com.nexus.launcher.ui.dock.DockDrawerSync(view).sync()
            if (view.uiState != nextState) {
                view.onStateChanged?.invoke(nextState)
            }
            if (nextState == LauncherState.DRAWER) {
                view.triggerDrawerPrewarm()
                view.postInvalidateOnAnimation()
            }
        }
    }

    /** Animates a page transition during icon drag edge-scroll. */
    fun animatePageTransition(view: LauncherCanvasView, targetPage: Int) {
        if (targetPage !in 0 until view.totalPages || targetPage == view.currentPage) return
        val direction = if (targetPage > view.currentPage) 1 else -1
        view.cancelPageMotion()
        view.isMutatingState = true
        val span = if (SelectionModeTransform.isCardTrackActive(view)) {
            SelectionModeCardTrack.swipeSpanPx(view)
        } else {
            view.viewWidth.toFloat()
        }
        val targetOffset = -direction * span
        val settle = SettlePhysics.settle(
            fromPx = 0f,
            toPx = targetOffset,
            releaseVelocityPxPerSec = 0f,
            speedMultiplier = MotionSpeed.multiplier(view.context),
            omega = SettlePhysics.OMEGA_GLIDE,
            decay = SettlePhysics.DECAY_GLIDE,
        )
        view.pageSettle.start(
            fromPx = 0f,
            toPx = targetOffset,
            durationMs = settle.durationMs,
            interpolator = settle.interpolator,
            onUpdate = { value ->
                view.dragScrollOffset = value
                view.onPageScroll?.invoke(view.currentPage, view.dragScrollOffset)
                view.postInvalidateOnAnimation()
            },
            onEnd = { cancelled ->
                if (cancelled) {
                    view.isMutatingState = false
                    return@start
                }
                view.commitPageSwipe(targetPage)
            }
        )
    }

    fun closeDrawerInstantly(view: LauncherCanvasView) {
        view.cancelDrawerMotion()
        view.scroller.abortAnimation()
        view.drawerTranslationY = view.viewHeight.toFloat()
        view.scrollY = 0f
        if (view.uiState != LauncherState.HOME) {
            view.uiState = LauncherState.HOME
            view.onStateChanged?.invoke(LauncherState.HOME)
        }
        DrawerHaptics.onDrawerClose(view)
        view.railRenderer.updateFingerPosition(-1f, -1f, view.viewWidth.toFloat())
        if (view.layoutDirty) view.recalculateLayout()
        com.nexus.launcher.ui.dock.DockDrawerSync(view).sync()
        view.invalidate()
    }
}
