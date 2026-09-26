package com.nexus.launcher.feed

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ScrollView

class NexusFeedGestureDetector(
    private val context: Context,
    private val scrollView: ScrollView,
    private val cardsContainer: View,
    private val isRefreshingProvider: () -> Boolean,
    private val onTriggerRefresh: () -> Unit,
    private val onFeedDismissProgress: (dx: Float, width: Float) -> Unit,
    private val onFeedDismissCommit: (commitDismiss: Boolean, velocityX: Float) -> Unit
) {
    private val dp = context.resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    /** Set by the page whenever the Feed tab shows its per-source horizontal card strips (empty
     *  otherwise — one per source now, not a single strip). A drag that starts on whichever strip
     *  sits under the touch point, and that strip can still scroll further in that direction, is
     *  left alone here — only once that strip is scrolled to its edge does this page-level
     *  swipe-to-dismiss gesture take over, so the two never fight over the same drag. */
    var horizontalStrips: List<HorizontalScrollView> = emptyList()

    /** Page chrome that scrolls horizontally but is never torn down and rebuilt by an article
     *  render — today just the category pill row. Kept in its own list precisely because
     *  [horizontalStrips] is reassigned wholesale on every render and on every tab switch, so a
     *  permanently-present strip parked in there would be dropped the first time either happened. */
    var chromeStrips: List<HorizontalScrollView> = emptyList()

    private val hitRect = Rect()
    private var startX = 0f
    private var startY = 0f
    private var touchStartedOnStrip: HorizontalScrollView? = null
    private var isDismissDrag = false
    private var isPullDrag = false
    private var velocityTracker: VelocityTracker? = null

    private fun stripAtPoint(rawX: Float, rawY: Float): HorizontalScrollView? =
        stripAtPointIn(horizontalStrips, rawX, rawY) ?: stripAtPointIn(chromeStrips, rawX, rawY)

    private fun stripAtPointIn(
        strips: List<HorizontalScrollView>,
        rawX: Float,
        rawY: Float
    ): HorizontalScrollView? {
        for (strip in strips) {
            if (strip.getGlobalVisibleRect(hitRect) && hitRect.contains(rawX.toInt(), rawY.toInt())) return strip
        }
        return null
    }

    private fun trackMovement(ev: MotionEvent) {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        val rawEvent = MotionEvent.obtain(ev).apply { setLocation(ev.rawX, ev.rawY) }
        velocityTracker?.addMovement(rawEvent)
        rawEvent.recycle()
    }

    fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX; startY = ev.rawY
                touchStartedOnStrip = stripAtPoint(ev.rawX, ev.rawY)
                isDismissDrag = false; isPullDrag = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                val absDx = Math.abs(dx)
                val absDy = Math.abs(dy)

                if (absDx > touchSlop && dx < 0 && absDx > absDy * 1.1f) {
                    val isEdgeSwipe = startX > context.resources.displayMetrics.widthPixels - (52 * dp)
                    val stripStillScrollable = !isEdgeSwipe && touchStartedOnStrip?.canScrollHorizontally(1) == true
                    if (stripStillScrollable) return false
                    isDismissDrag = true
                    startX = ev.rawX
                    return true
                }
                if (scrollView.scrollY == 0 && dy > touchSlop && dy > absDx * 1.5f && !isRefreshingProvider()) {
                    isPullDrag = true
                    return true
                }
            }
        }
        return false
    }

    fun onTouchEvent(ev: MotionEvent, totalWidth: Float): Boolean {
        trackMovement(ev)
        val width = totalWidth.coerceAtLeast(1f)

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (isDismissDrag) {
                    onFeedDismissProgress(dx.coerceIn(-width, 0f), width)
                    return true
                } else if (isPullDrag) {
                    val reduceMotion = NexusFeedEInkCoordinator.shouldReduceMotion(context)
                    if (reduceMotion) {
                        cardsContainer.translationY = (dy * 0.35f).coerceIn(0f, 40 * dp)
                    } else {
                        cardsContainer.translationY = (dy * 0.35f).coerceIn(0f, 100 * dp)
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDismissDrag) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val dx = ev.rawX - startX
                    // One projected test instead of three disjoint rules. The old ladder had a
                    // dead band: between -500 and +500 px/s only raw distance counted, so an
                    // unhurried but committed drag was judged the same as a stalled one.
                    val shouldClose =
                        com.nexus.launcher.ui.canvas.SettlePhysics.project(dx, vx) < -width * 0.30f
                    onFeedDismissCommit(shouldClose, vx)
                } else if (isPullDrag) {
                    val pulled = cardsContainer.translationY
                    val reduceMotion = NexusFeedEInkCoordinator.shouldReduceMotion(context)
                    if (reduceMotion) {
                        cardsContainer.translationY = 0f
                    } else {
                        ValueAnimator.ofFloat(pulled, 0f).apply {
                            duration = 200
                            interpolator = DecelerateInterpolator(1.0f)
                            addUpdateListener { cardsContainer.translationY = it.animatedValue as Float }
                            start()
                        }
                    }
                    if (pulled >= 40 * dp) {
                        cardsContainer.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTriggerRefresh()
                    }
                }
                isDismissDrag = false; isPullDrag = false
                velocityTracker?.recycle(); velocityTracker = null
            }
        }
        return isDismissDrag || isPullDrag
    }
}
