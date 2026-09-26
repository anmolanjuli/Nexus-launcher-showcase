package com.nexus.launcher.reader.doc

import android.content.Context
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * Manages pinch-to-zoom scaling and panning transformations for PDF pages.
 */
class NexusPdfPageZoomHelper(
    context: Context,
    private val targetViewProvider: () -> ImageView,
    /**
     * Called when a pinch ends, with the scale it settled on. Scaling a view scales the bitmap it
     * is already holding, so the page goes soft as it grows; the caller re-renders the page at the
     * new scale to bring the text back to full sharpness.
     */
    private val onZoomSettled: ((Float) -> Unit)? = null,
) {

    var scaleFactor: Float = 1.0f
        private set

    val isZoomed: Boolean
        get() = scaleFactor > 1.05f

    private var lastPanX: Float = 0f
    private var lastPanY: Float = 0f
    private var twoFingerStartX: Float = 0f

    /** True once the fingers have changed the scale in this gesture; a pinch is not a swipe. */
    private var pinchedThisGesture: Boolean = false
    private val density = context.resources.displayMetrics.density

    val scaleDetector: ScaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                pinchedThisGesture = true
                val target = targetViewProvider()
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1.0f, 5.0f)
                target.pivotX = detector.focusX
                target.pivotY = detector.focusY
                target.scaleX = scaleFactor
                target.scaleY = scaleFactor
                if (scaleFactor <= 1.05f) {
                    target.translationX = 0f
                    target.translationY = 0f
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                onZoomSettled?.invoke(scaleFactor)
            }
        }
    )

    /**
     * Puts the current zoom back on the view — after the page has been re-rendered at a sharper
     * size. Re-rendering ends with a fit pass that sets scaleX/scaleY, which silently undid the
     * pinch that asked for the sharper page in the first place: the page snapped back to 1x the
     * moment it got crisp.
     */
    fun reapply() {
        val target = targetViewProvider()
        target.scaleX = scaleFactor
        target.scaleY = scaleFactor
    }

    fun resetZoom() {
        scaleFactor = 1.0f
        val target = targetViewProvider()
        target.translationX = 0f
        target.translationY = 0f
        target.scaleX = 1.0f
        target.scaleY = 1.0f
    }

    fun setZoom(targetScale: Float, focusX: Float, focusY: Float) {
        scaleFactor = targetScale.coerceIn(1.0f, 5.0f)
        val target = targetViewProvider()
        target.pivotX = focusX
        target.pivotY = focusY
        target.scaleX = scaleFactor
        target.scaleY = scaleFactor
    }

    /**
     * Handles a touch while the page is zoomed: one finger pans it, two fingers swiped across turn
     * the page. Returns true when it took the event, which it does for every touch in this state —
     * a zoomed page has no use for the tap zones, and panning must not be read as a page turn.
     *
     * [turn] is given true for the next page and false for the previous, already resolved for
     * layout direction by the caller's own [android.view.View.getLayoutDirection].
     */
    fun onZoomedTouch(event: android.view.MotionEvent, width: Int, height: Int, turn: (forward: Boolean) -> Unit): Boolean {
        if (!isZoomed) return false
        if (event.pointerCount >= 2) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    twoFingerStartX = (event.getX(0) + event.getX(1)) / 2f
                    pinchedThisGesture = false
                }
                android.view.MotionEvent.ACTION_POINTER_UP, android.view.MotionEvent.ACTION_UP -> {
                    // Only a genuine two-finger *swipe* turns the page. A pinch moves its midpoint
                    // too — usually well past the threshold — so zooming in or out used to turn the
                    // page as the fingers came off.
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val diff = midX - twoFingerStartX
                    val swiped = !pinchedThisGesture && !scaleDetector.isInProgress &&
                        kotlin.math.abs(diff) > TWO_FINGER_TURN_DP * density
                    if (swiped) turn(diff < 0f)
                }
            }
            return true
        }
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> onPanDown(event.x, event.y)
            android.view.MotionEvent.ACTION_MOVE -> onPanMove(event.x, event.y, width, height)
        }
        return true
    }

    fun onPanDown(x: Float, y: Float) {
        lastPanX = x
        lastPanY = y
    }

    fun onPanMove(x: Float, y: Float, containerWidth: Int, containerHeight: Int) {
        if (!scaleDetector.isInProgress) {
            val target = targetViewProvider()
            val dx = x - lastPanX
            val dy = y - lastPanY
            lastPanX = x
            lastPanY = y

            val maxPanX = ((containerWidth * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
            val maxPanY = ((containerHeight * (scaleFactor - 1f)) / 2f).coerceAtLeast(0f)
            target.translationX = (target.translationX + dx).coerceIn(-maxPanX, maxPanX)
            target.translationY = (target.translationY + dy).coerceIn(-maxPanY, maxPanY)
        }
    }

    private companion object {
        /** How far two fingers must travel together before it counts as a page turn. */
        const val TWO_FINGER_TURN_DP = 40f
    }
}
