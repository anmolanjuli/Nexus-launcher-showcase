package com.nexus.launcher.ui.canvas

class LauncherGestureHelper(private val view: LauncherCanvasView) {
    
    fun handleSelectionModeTapOrScroll(event: android.view.MotionEvent): Boolean {
        val totalMovement = kotlin.math.hypot(
            (event.x - view.startTouchX).toDouble(),
            (event.y - view.startTouchY).toDouble()
        ).toFloat()
        val wasPureTap = totalMovement < view.touchSlop
        if (wasPureTap) {
            if (view.uiState == com.nexus.launcher.ui.model.LauncherState.DRAWER) {
                val adjustedY = event.y + view.scrollY - view.drawerTranslationY
                val tappedPkg = view.drawerItems.firstOrNull { item ->
                    item.hitRect.contains(event.x.toInt(), adjustedY.toInt())
                }?.intent?.component?.packageName
                if (tappedPkg != null) {
                    view.onToggleSelection?.invoke(tappedPkg)
                }
            } else {
                CanvasHitTestHelper.getItemAt(view, event.x, event.y)?.let {
                    view.onToggleHomeSelection?.invoke(it.id)
                }
            }
        }
        if (!wasPureTap && view.uiState == com.nexus.launcher.ui.model.LauncherState.DRAWER) {
            view.velocityTracker?.computeCurrentVelocity(1000)
            val vy = view.velocityTracker?.yVelocity ?: 0f
            view.velocityTracker?.recycle()
            view.velocityTracker = null
            if (Math.abs(vy) > 200f) {
                view.scroller.fling(0, view.scrollY.toInt(), 0, (-vy).toInt(), 0, 0, 0, view.maxScrollY.toInt())
                view.postInvalidateOnAnimation()
            }
        } else {
            view.velocityTracker?.recycle()
            view.velocityTracker = null
        }
        view.isDragging = false
        return true
    }

    /**
     * Finger lifted after a horizontal drag. If [dragScrollOffset] crossed a quarter of the
     * screen, animate it the rest of the way and commit the page; otherwise snap it back to 0.
     * The page index is swapped only at the very end so the hand-off is seamless (no flicker).
     */
    fun settlePageSwipe() {
        val width = if (SelectionModeTransform.isCardTrackActive(view)) {
            SelectionModeCardTrack.swipeSpanPx(view)
        } else {
            view.viewWidth.toFloat()
        }
        val offset = view.dragScrollOffset
        val maxPage = view.totalPages - 1

        view.velocityTracker?.computeCurrentVelocity(1000)
        val vx = view.velocityTracker?.xVelocity ?: 0f
        view.velocityTracker?.recycle()
        view.velocityTracker = null

        // Decide on where the finger is *heading*, not where it happened to stop. A short, fast
        // flick reads as a page turn to the hand long before it has covered a quarter of the
        // screen, and the old hard 500 px/s cutoff either caught that or missed it outright with
        // nothing in between.
        // A fifth of the screen, not a quarter: a slow, deliberate drag carries no velocity for
        // the projection to work with, so the distance is all it has to go on, and a page pushed
        // a fifth of the way across was meant.
        val commitAt = width * 0.2f
        val projected = SettlePhysics.project(offset, vx)
        val delta = when {
            projected <= -commitAt && view.currentPage < maxPage -> 1
            projected >= commitAt && view.currentPage > 0 -> -1
            else -> 0
        }
        val target = if (delta == 0) 0f else -delta * width
        view.cancelPageMotion()

        val settle = SettlePhysics.settle(
            fromPx = offset,
            toPx = target,
            releaseVelocityPxPerSec = vx,
            speedMultiplier = MotionSpeed.multiplier(view.context),
            // A page is the largest thing that moves, and the only one the eye follows all the
            // way across; it glides rather than snapping.
            omega = SettlePhysics.OMEGA_GLIDE,
            decay = SettlePhysics.DECAY_GLIDE,
        )

        view.isMutatingState = true
        view.pageSettle.start(
            offset, target, settle.durationMs, settle.interpolator,
            onUpdate = { value ->
                view.dragScrollOffset = value
                view.onPageScroll?.invoke(view.currentPage, view.dragScrollOffset)
                view.postInvalidateOnAnimation()
            },
        ) { cancelled ->
            if (cancelled) {
                // A new drag grabbed it — keep tracking, do not commit.
                view.isMutatingState = false
                return@start
            }
            val maxPage = view.totalPages - 1
            val newPage = (view.currentPage + delta).coerceIn(0, maxPage)
            view.commitPageSwipe(newPage)
        }
    }
}
