package com.nexus.launcher.ui.canvas
import android.view.MotionEvent
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState
import com.nexus.launcher.ui.folder.FolderDrawerTouchHelper
import com.nexus.launcher.ui.folder.FolderHomeTouchHelper

class LauncherTouchHandler(private val view: LauncherCanvasView) {
    private var isHorizontalSwipe = false; private var railTrackingActive = false
    private var touchStartedOnHome = false
    /** True once `onFeedSwipeBegan` has fired for the current gesture — i.e. the Feed panel (and
     *  its workspace blur) is actually live and needs a matching close/settle call on release,
     *  regardless of where the finger ends up. See the release-time check below for why this
     *  can't just be inferred from `rawOffset > 0` at ACTION_UP. */
    private var feedSwipeArmed = false
    private var isFeedSwipeEligible = false
    private val pageSwipeTracker = PageSwipeTracker(view)
    private val dragTouchHandler = DragTouchHandler(view)
    private val dockDrawerSync = com.nexus.launcher.ui.dock.DockDrawerSync(view)
    private val gestureHelper = LauncherGestureHelper(view)
    private val pageAdvanceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pageAdvanceRunnable: Runnable? = null
    private val doubleTapState = LauncherTouchDownHelper.DoubleTapState()
    private val twoFingerTracker = LauncherTwoFingerSwipeTracker()
    fun onTouchEvent(event: MotionEvent): Boolean {
        val activity = view.context as? com.nexus.launcher.ui.MainActivity
        if (activity?.homeEditController?.isHomeEditMode == true) {
            val mainContainer = activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.main_container)
            val sheet = mainContainer?.findViewWithTag<android.view.View>("edit_mode_sheet")
            if (sheet != null) {
                sheet.dispatchTouchEvent(event)
                return true
            }
        }
        if (event.pointerCount > 2) return true
        if (view.velocityTracker == null) view.velocityTracker = android.view.VelocityTracker.obtain()
        view.velocityTracker?.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartedOnHome = view.uiState == LauncherState.HOME &&
                    (view.viewHeight <= 0 || view.drawerTranslationY >= view.viewHeight - 1f)
                twoFingerTracker.onActionDown()
                dragTouchHandler.onActionDown()
                // Defensive reset: a prior gesture that armed this (onFeedSwipeBegan fired) but
                // then exited through a path other than the ACTION_UP/CANCEL isHorizontalSwipe
                // branch below (e.g. view.draggedItem taking over mid-gesture) would otherwise
                // leave this stuck true, wrongly arming the very next unrelated swipe's release.
                feedSwipeArmed = false
                isFeedSwipeEligible = view.currentPage == 0 && kotlin.math.abs(view.dragScrollOffset) < 1f &&
                    !view.isPageMotionRunning && view.showFeed && !SelectionModeTransform.blocksFeed(view)
                view.startTouchX = event.x
                view.startTouchY = event.y
                view.lastY = event.y
                view.cancelDrawerMotion()
                view.scroller.abortAnimation()
                view.cancelDrawerPrewarmInFlight()
                view.isDragging = false
                isHorizontalSwipe = false
                pageSwipeTracker.onActionDown(event)
                IconSwipeGestureDetector.reset()
                view.homeLongPressConsumed = false
                view.drawerLongPressConsumed = false
                LauncherTouchDownHelper.armPressTargets(view, event, dragTouchHandler, doubleTapState)
                view.pageFoldHandler.onActionDown(event)
                if (LauncherRailTouchHelper.handleRailTouchDown(view, event)) {
                    view.isDragging = true
                    railTrackingActive = true
                    return true
                } else {
                    view.railRenderer.updateTouchY(null, view)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (twoFingerTracker.onPointerDown(view, event)) {
                    isHorizontalSwipe = false
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Do not reset two-finger tracking here, so that ACTION_MOVE doesn't fall through when one finger lifts
            }
            MotionEvent.ACTION_MOVE -> {
                if (twoFingerTracker.onMove(view, event)) {
                    return true // Block other drag handling while tracking two fingers
                }
                
                doubleTapState.detector?.onTouchEvent(event)
                val totalDx = event.x - view.startTouchX
                val totalDy = event.y - view.startTouchY
                FolderHomeTouchHelper.onFolderMove(view, totalDx, totalDy)
                FolderDrawerTouchHelper.onDrawerScrollIntent(view, totalDx, totalDy)
                FolderDrawerTouchHelper.onMove(view, totalDx, totalDy)

                // Icon drag takes priority over scroll/rail handling,
                // unless selection mode is active (user should scroll freely)
                val wasDragActive = view.dragHandler.isIconDragActive
                val isInSelectionMode = view.selectionState is SelectionState.Selecting
                val allowIconDrag = !isInSelectionMode || view.multiDragHandler.isActive
                if (allowIconDrag && view.dragHandler.onTouchMove(event.x, event.y, view.startTouchX, view.startTouchY, view.touchSlop)) {
                    if (view.dragHandler.isIconDragActive && !wasDragActive) view.onDismissContextMenu?.invoke()
                    if (view.dragHandler.isIconDragActive) {
                        PageAdvanceTouchHelper.handleDragMoveEdgeZone(
                            view, event, pageAdvanceHandler, pageAdvanceRunnable
                        ) { r -> pageAdvanceRunnable = r }
                    } else {
                        pageAdvanceRunnable?.let { pageAdvanceHandler.removeCallbacks(it) }
                        pageAdvanceRunnable = null
                    }
                    view.postInvalidateOnAnimation()
                    dockDrawerSync.sync()
                    return true
                }
                if (railTrackingActive) {
                    return LauncherRailTouchHelper.handleRailTouchMove(view, event)
                } else {
                    view.railRenderer.updateTouchY(null)
                }
                var dy = event.y - view.lastY
                view.lastY = event.y

                // If we have an item lifted, completely abort the standard gesture engine.
                // Do NOT calculate horizontal swipes, do NOT apply dragScrollOffset.
                if (view.draggedItem != null) {
                    if (SelectionModeTransform.isCardTrackActive(view)) {
                        PageAdvanceTouchHelper.handleDragMoveEdgeZone(
                            view, event, pageAdvanceHandler, pageAdvanceRunnable
                        ) { r -> pageAdvanceRunnable = r }
                    }
                    dragTouchHandler.handleHomeDragMove(event)
                    dockDrawerSync.sync()
                    return true
                }

                if (!isHorizontalSwipe && pageSwipeTracker.tryBegin(event, totalDx, totalDy)) {
                    isHorizontalSwipe = true
                    view.isDragging = true
                    if (isFeedSwipeEligible && totalDx > 0) {
                        feedSwipeArmed = true
                        view.onFeedSwipeBegan?.invoke()
                    }
                    view.removeCallbacks(view.longPressRunnable)
                    view.homeLongPressRunnable?.let { view.homeHandler.removeCallbacks(it) }
                    FolderHomeTouchHelper.cancelFolderLongPress(view)
                }
                if (!view.isDragging) {
                    val cancelFn = { view.removeCallbacks(view.longPressRunnable); view.homeLongPressRunnable?.let { view.homeHandler.removeCallbacks(it) }; FolderHomeTouchHelper.cancelFolderLongPress(view) }
                    if (!IconSwipeGestureDetector.checkBranch(totalDx, totalDy, view.touchSlop, cancelFn)) {
                        if (!FolderDrawerTouchHelper.isArmingLongPress() && !FolderHomeTouchHelper.isArmingLongPress() && kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble()) > view.touchSlop) {
                            view.isDragging = true
                            // Consume the touch slop inside the arming frame itself — lastY is
                            // already event.y above. The old `lastY = startTouchY` rewind ran
                            // AFTER dy had been computed, so the next MOVE re-applied the whole
                            // slop distance in one step and the drawer popped ~touchSlop off the
                            // finger on frame two of every drag. Most visible closing the drawer,
                            // where the icons sit at a position the eye is already tracking.
                            dy = 0f
                            cancelFn()
                        }
                    }
                }
                if (view.isDragging) {
                    if (isHorizontalSwipe) {
                        val rawOffset = pageSwipeTracker.offsetAt(event)
                        if (feedSwipeArmed) {
                            if (view.pageFoldHandler.isFoldGestureOwned) {
                                view.pageFoldHandler.onActionCancel()
                            }
                            view.onSwipeRightPageZero?.invoke(rawOffset.coerceAtLeast(0f), view.viewWidth.toFloat())
                            view.dragScrollOffset = 0f
                            return true
                        }
                        // Selection mode is always a direct card track. A configured home
                        // fold effect must not capture its touch stream or make cards lag.
                        val isCardTrack = SelectionModeTransform.isCardTrackActive(view)
                        if (!isCardTrack && (view.pageFoldHandler.isFoldGestureOwned ||
                            view.pageFoldHandler.tryClaimHorizontalSwipe(event))
                        ) {
                            view.pageFoldHandler.onActionMove(event)
                            return true
                        }
                        // 1:1 finger tracking — the pages follow the finger in real time.
                        // Hold isMutatingState so DB emissions can't interrupt the cube.
                        view.isMutatingState = true
                        view.dragScrollOffset = pageSwipeTracker.resistedOffsetAt(event)
                        view.onPageScroll?.invoke(view.currentPage, view.dragScrollOffset)
                        view.invalidate()
                        return true
                    }
                    if (DrawerTouchMoveHandler.handleDragMove(view, dy, event.y, dockDrawerSync)) {
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTwoFinger = twoFingerTracker.onUpOrCancel()
                doubleTapState.detector?.onTouchEvent(event)
                view.railRenderer.updateTouchY(null, view)
                val dx = event.x - view.startTouchX
                val dyTotal = event.y - view.startTouchY
                
                if (event.action == MotionEvent.ACTION_UP && !isHorizontalSwipe && view.draggedItem == null && !view.dragHandler.isIconDragActive && !wasTwoFinger) {
                    // Only a gesture that began on settled home may fire a global swipe action —
                    // a swipe down that closes the drawer must not also open search. Both
                    // directions stay live: swipe up can be mapped to something other than the drawer.
                    if (touchStartedOnHome) {
                        if (LauncherGlobalSwipeHelper.handleSingleFingerSwipe(view, event, dx, dyTotal, isHorizontalSwipe)) {
                            view.isDragging = false
                            view.velocityTracker?.recycle(); view.velocityTracker = null
                            return true
                        }
                    }
                }
                
                if (IconSwipeGestureDetector.isIconSwipeCandidate) {
                    view.velocityTracker?.computeCurrentVelocity(1000)
                    val iconActionFired = if (event.action == MotionEvent.ACTION_UP) {
                        val act = view.context as? com.nexus.launcher.ui.MainActivity
                        IconSwipeGestureDetector.handleUp(view.context, dyTotal, view.velocityTracker?.yVelocity ?: 0f, act?.let { androidx.lifecycle.ViewModelProvider(it)[com.nexus.launcher.ui.MainViewModel::class.java] })
                    } else false
                    IconSwipeGestureDetector.reset(); view.isDragging = false
                    if (iconActionFired) {
                        // Per-icon gesture was dispatched — consume the event entirely.
                        view.velocityTracker?.recycle(); view.velocityTracker = null
                        return true
                    }
                    // Per-icon threshold not met — fall through to the normal drawer snap path
                    // so a swipe-up over an icon that has a gesture assigned can still open the drawer.
                    view.velocityTracker?.recycle(); view.velocityTracker = null
                } else {
                    IconSwipeGestureDetector.reset()
                }
                
                if (FolderHomeTouchHelper.onFolderUp(view)) {
                    view.velocityTracker?.recycle(); view.velocityTracker = null
                    return true
                }
                if (event.action == MotionEvent.ACTION_UP && view.dragHandler.onTouchUp(event.x, event.y)) {
                    FolderDrawerTouchHelper.cancel(view); view.isDragging = false
                    view.velocityTracker?.recycle(); view.velocityTracker = null
                    return true
                }
                if (FolderDrawerTouchHelper.onUp(view)) {
                    view.dragHandler.onTouchCancel(); view.isDragging = false; view.drawerLongPressConsumed = true
                    view.velocityTracker?.recycle(); view.velocityTracker = null
                    return true
                }
                if (view.draggedItem != null) {
                    pageAdvanceRunnable?.let { pageAdvanceHandler.removeCallbacks(it) }
                    pageAdvanceRunnable = null
                    dragTouchHandler.handleHomeDragDrop(event)
                    FolderHomeTouchHelper.onDragDropEnded(view)
                    view.isMutatingState = false
                    view.dragScrollOffset = 0f
                    return true
                }
                if (railTrackingActive) {
                    val density = view.resources.displayMetrics.density
                    val railEdge = if (view.isRtl) (20f * density) else (view.viewWidth.toFloat() - (20f * density))
                    view.railRenderer.releaseSpring(railEdge, view)
                    view.railRenderer.updateTouchY(null, view)
                    view.fastScrollLetter = null
                    railTrackingActive = false
                }
                view.removeCallbacks(view.longPressRunnable)
                view.homeLongPressRunnable?.let { view.homeHandler.removeCallbacks(it) }
                FolderHomeTouchHelper.cancelFolderLongPress(view)
                FolderDrawerTouchHelper.cancelPending(view)
                view.railRenderer.updateTouchY(null, view)
                view.postInvalidateOnAnimation()
                if (isHorizontalSwipe) {
                    isHorizontalSwipe = false
                    view.isDragging = false
                    val rawOffset = event.x - view.startTouchX
                    if (feedSwipeArmed) {
                        feedSwipeArmed = false
                        view.isMutatingState = false
                        view.velocityTracker?.computeCurrentVelocity(1000)
                        val vx = view.velocityTracker?.xVelocity ?: 0f
                        view.onReleaseSwipeRightPageZero?.invoke(rawOffset, vx, view.viewWidth.toFloat())
                        view.velocityTracker?.recycle(); view.velocityTracker = null
                        return true
                    }
                    if (view.pageFoldHandler.isFoldGestureOwned) {
                        if (event.action == MotionEvent.ACTION_CANCEL) {
                            view.pageFoldHandler.onActionCancel()
                        } else {
                            view.pageFoldHandler.onActionUp(event)
                        }
                        return true
                    }
                    gestureHelper.settlePageSwipe()
                    return true
                }
                // No swipe this gesture — never leave the canvas locked (1-3-2-1 / stuck fix).
                if (!view.isPageMotionRunning) view.isMutatingState = false else view.postDelayed({
                    if (!view.isPageMotionRunning) view.isMutatingState = false
                }, 400)
                if (view.selectionState is SelectionState.Selecting) {
                    return gestureHelper.handleSelectionModeTapOrScroll(event)
                }
                view.velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = view.velocityTracker?.yVelocity ?: 0f
                view.velocityTracker?.recycle(); view.velocityTracker = null
                // Cancel any remaining drag state
                if (event.action == MotionEvent.ACTION_CANCEL) {
                    view.dragHandler.onTouchCancel()
                    pageAdvanceRunnable?.let { pageAdvanceHandler.removeCallbacks(it) }
                    pageAdvanceRunnable = null
                    view.homeLongPressConsumed = false
                    view.drawerLongPressConsumed = false
                }
                // Long press already handled — suppress tap that would launch the app
                if (view.homeLongPressConsumed) {
                    view.homeLongPressConsumed = false
                    view.isDragging = false
                    return true
                }
                if (view.drawerLongPressConsumed) {
                    view.drawerLongPressConsumed = false
                    view.isDragging = false
                    return true
                }
                if (kotlin.math.hypot(dx.toDouble(), dyTotal.toDouble()) <= view.touchSlop &&
                    Math.abs(velocityY) < 500f && !view.isDragging
                ) {
                    if (doubleTapState.detector == null) {
                        if (LauncherTapHelper.handleTap(view, event, dragTouchHandler)) return true
                    } else {
                        return true
                    }
                }
                view.isDragging = false
                val totalMovement = kotlin.math.hypot(
                    (event.x - view.startTouchX).toDouble(),
                    (event.y - view.startTouchY).toDouble()
                ).toFloat()
                val wasPureTap = totalMovement < view.touchSlop
                DrawerFlingHandler.handle(
                    view = view,
                    velocityY = velocityY,
                    wasPureTap = wasPureTap,
                    railTrackingActive = railTrackingActive,
                    dragTouchHandler = dragTouchHandler,
                    dockDrawerSync = dockDrawerSync,
                    onRailReset = { railTrackingActive = false }
                )
            }
        }
        return true
    }
}
