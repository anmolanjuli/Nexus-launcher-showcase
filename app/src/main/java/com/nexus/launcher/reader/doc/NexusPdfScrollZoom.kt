package com.nexus.launcher.reader.doc

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Pinch-to-zoom for the continuously scrolling reader.
 *
 * ## Two stages, for two different reasons
 *
 * While the fingers are moving, the whole page column is scaled as a view — immediate, and it costs
 * nothing, but it is the bitmap being stretched, so the text softens as it grows. When the fingers
 * lift, the zoom is *committed*: the caller re-lays the pages out at the new width and renders them
 * again at that size, and the text comes back sharp at whatever magnification was reached. This is
 * the difference between a page that blurs as you zoom and one that reveals more of itself.
 *
 * ## Why the scroll view has to be held off
 *
 * A vertical ScrollView claims the gesture on the first finger, so by the time a second one lands
 * the view is already dragging, and it went on scrolling through the pinch — the zoom fought the
 * scroll and lost as soon as the fingers left the glass. [isPinching] is what the host checks to
 * stop feeding itself touches, and [onPinchStart] is its chance to cancel the drag it had begun.
 */
class NexusPdfScrollZoom(
    context: Context,
    private val onPinchStart: () -> Unit,
    /** Live feedback during the pinch: scale the column by this, pivoting on the fingers. */
    private val onLiveScale: (scale: Float, focusX: Float, focusY: Float) -> Unit,
    /** [previous] is the zoom being left behind — the caller needs both to keep its place. */
    private val onCommit: (previous: Float, zoom: Float, focusX: Float, focusY: Float) -> Unit,
) {
    /** The committed magnification: what the pages are actually laid out and rendered at. */
    var zoom: Float = 1f
        private set

    var isPinching: Boolean = false
        private set

    private var liveScale = 1f
    private var focusX = 0f
    private var focusY = 0f

    val detector: ScaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isPinching = true
                liveScale = 1f
                onPinchStart()
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // Clamped against the committed zoom so the pair can never leave the allowed range.
                val wanted = (zoom * liveScale * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                liveScale = wanted / zoom
                focusX = detector.focusX
                focusY = detector.focusY
                onLiveScale(liveScale, focusX, focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isPinching = false
                commit(zoom * liveScale)
            }
        }
    )

    fun onTouchEvent(event: MotionEvent) {
        detector.onTouchEvent(event)
    }

    /** Double-tap: straight to [DOUBLE_TAP_ZOOM], or back to the full page. */
    fun toggle(focusX: Float, focusY: Float) {
        commit(if (zoom > MIN_ZOOM + 0.05f) MIN_ZOOM else DOUBLE_TAP_ZOOM, focusX, focusY)
    }

    fun reset() {
        liveScale = 1f
        zoom = MIN_ZOOM
        onLiveScale(1f, 0f, 0f)
    }

    private fun commit(target: Float, fx: Float = focusX, fy: Float = focusY) {
        val committed = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val previous = zoom
        liveScale = 1f
        zoom = committed
        // The live scale goes away in the same frame the caller re-lays out at the new size.
        onLiveScale(1f, fx, fy)
        onCommit(previous, committed, fx, fy)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
        const val DOUBLE_TAP_ZOOM = 2f

        /**
         * Zoom beyond this is shown by scaling the rendered page rather than rendering it larger:
         * at four times the width a single page bitmap would be sixteen times the pixels.
         */
        const val MAX_RENDER_ZOOM = 2.5f
    }
}
