package com.nexus.launcher.feed

import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Two-finger tap: a full-screen flash that clears an e-ink panel's ghosting.
 *
 * E-ink displays hold a faint impression of what they showed before, and it builds up over a
 * reading session until the page looks smudged. Every e-ink reader answers this the same way — a
 * full black-then-white refresh that resets the particles — and it is the one control an e-ink
 * reader expects and cannot get from an app that only knows how to draw in grey.
 *
 * A phone's LCD or OLED has no ghosting to clear, so the flash is only armed in E-Ink Paper Mode.
 * On a phone it still reads as a deliberate "redraw", which is what a reader expects it to mean.
 *
 * ## Telling the gesture from a pinch
 *
 * Two fingers also mean zoom in the PDF reader. This fires only when both fingers go down and come
 * up again quickly without travelling — a tap, not a pinch — so a zoom is never mistaken for a
 * refresh. It never consumes the event either: the host passes every touch through, and the view
 * underneath handles it as before.
 */
class NexusEInkRefreshGesture(
    private val host: ViewGroup,
    private val isEInk: () -> Boolean,
) {
    private val slop = ViewConfiguration.get(host.context).scaledTouchSlop
    private var candidate = false
    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    private var flashView: View? = null

    /** Feed every touch through here; it never consumes one. */
    fun onTouchEvent(ev: MotionEvent) {
        if (!isEInk()) {
            candidate = false
            return
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (ev.pointerCount == 2) {
                    candidate = true
                    downAt = ev.eventTime
                    downX = ev.getX(0)
                    downY = ev.getY(0)
                } else {
                    candidate = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (candidate && ev.pointerCount >= 1) {
                    val moved = kotlin.math.hypot(ev.getX(0) - downX, ev.getY(0) - downY)
                    if (moved > slop) candidate = false
                }
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                if (candidate && ev.eventTime - downAt <= TAP_TIMEOUT_MS) {
                    candidate = false
                    flash()
                }
            }
            MotionEvent.ACTION_CANCEL -> candidate = false
        }
    }

    /** Black, then white, then back to the page — one pass of the panel. */
    fun flash() {
        val view = flashView ?: View(host.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
        }.also { flashView = it }

        if (view.parent == null) host.addView(view)
        view.bringToFront()
        view.setBackgroundColor(Color.BLACK)
        view.visibility = View.VISIBLE
        host.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        view.postDelayed({
            view.setBackgroundColor(Color.WHITE)
            view.postDelayed({
                view.visibility = View.GONE
                (view.parent as? ViewGroup)?.removeView(view)
                flashView = null
            }, PHASE_MS)
        }, PHASE_MS)
    }

    private companion object {
        /** Each half of the flash. Long enough for e-ink to settle, short enough not to annoy. */
        const val PHASE_MS = 90L
        const val TAP_TIMEOUT_MS = 300L
    }
}
