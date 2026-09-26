package com.nexus.launcher.ui.premium.showcase

import android.os.SystemClock
import android.view.View

/**
 * A frame loop for the Premium page's moving pictures.
 *
 * Not a ValueAnimator: animators obey the system Animator duration scale, and with animations
 * reduced or off they jump straight to their last frame — the drawer loop then sat still on its
 * grid. These are demonstrations the user opened on purpose, so they run on the display's frame
 * clock instead. [onFrame] gets the seconds since the loop began, and the view is invalidated
 * after each call.
 *
 * Two things keep the cost down, since a tile grid can hold several of these at once:
 *  - [startAt] offsets the clock so a picture is already moving when it appears. Every loop holds
 *    before its first change, and starting at zero showed that hold as a still picture.
 *  - frames are limited to [FRAME_MS] and, while the view is off screen, the loop idles instead of
 *    drawing — a page scrolled past costs nothing.
 */
internal class ShowcaseTicker(
    private val view: View,
    private val startAt: Float = 0f,
    private val onFrame: (seconds: Float) -> Unit,
) : Runnable {

    private var startedAt = 0L
    private var running = false

    fun start() {
        if (running) return
        running = true
        startedAt = SystemClock.uptimeMillis() - (startAt * 1000).toLong()
        view.postOnAnimation(this)
    }

    fun stop() {
        running = false
        view.removeCallbacks(this)
    }

    override fun run() {
        if (!running) return
        if (!view.isShown) {
            // Scrolled away or the page is hidden: keep the clock, stop drawing.
            view.postOnAnimationDelayed(this, IDLE_MS)
            return
        }
        onFrame((SystemClock.uptimeMillis() - startedAt) / 1000f)
        view.invalidate()
        view.postOnAnimationDelayed(this, FRAME_MS)
    }

    private companion object {
        /** ~33 fps: smooth for a drifting sheet or a morphing grid, half the work of every frame. */
        const val FRAME_MS = 30L
        const val IDLE_MS = 250L
    }
}
