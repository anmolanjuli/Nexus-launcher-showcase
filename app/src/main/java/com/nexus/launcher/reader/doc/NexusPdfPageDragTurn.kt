package com.nexus.launcher.reader.doc

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlin.math.abs

/**
 * A page turn that follows the finger, and can be taken back.
 *
 * The reader used to decide at the end of a swipe: past a threshold the page changed, otherwise
 * nothing happened, and in between the page never moved at all. A paper page does not work that
 * way — it lifts as you push it, and if you change your mind halfway you put it back. Every
 * dedicated reader does this, and it is most of what makes page turning feel like handling a book
 * rather than pressing a button.
 *
 * [begin] shows the neighbouring page beside the current one, [drag] moves the pair with the
 * finger, and [finish] either carries the turn through or returns it, whichever the finger asked
 * for — past [COMMIT_FRACTION] of the screen, or a flick faster than [COMMIT_VELOCITY] dp/s.
 */
class NexusPdfPageDragTurn(
    private val active: ImageView,
    private val incoming: ImageView,
    private val density: Float,
) {
    /** Which way the pages are moving; null when no drag is in flight. */
    var forward: Boolean? = null
        private set

    var isDragging = false
        private set

    private var width = 0f

    /**
     * Starts a turn towards [forward]. [neighbour] is the page being turned to; without one — the
     * first or last page — there is nothing to show and the drag does not start.
     */
    fun begin(forward: Boolean, neighbour: android.graphics.Bitmap?, viewWidth: Int): Boolean {
        if (neighbour == null || viewWidth <= 0) return false
        this.forward = forward
        this.width = viewWidth.toFloat()
        isDragging = true
        incoming.setImageBitmap(neighbour)
        incoming.visibility = View.VISIBLE
        incoming.translationX = if (forward) width else -width
        return true
    }

    /** [dx] is the distance from where the finger went down, in pixels. */
    fun drag(dx: Float) {
        if (!isDragging) return
        val goingForward = forward ?: return
        // A drag the other way from the one that started this turn is already a cancel: clamp it
        // at zero rather than letting the pages separate in the wrong direction.
        val travel = if (goingForward) dx.coerceIn(-width, 0f) else dx.coerceIn(0f, width)
        active.translationX = travel
        incoming.translationX = if (goingForward) width + travel else -width + travel
    }

    /**
     * Ends the drag. Returns true when the turn completed, and the caller should move to the next
     * or previous page; false when it was taken back.
     */
    fun finish(dx: Float, velocityX: Float, onSettled: (committed: Boolean) -> Unit) {
        if (!isDragging) {
            onSettled(false)
            return
        }
        val goingForward = forward ?: false
        isDragging = false
        val travelled = abs(dx).coerceAtMost(width)
        val flicked = abs(velocityX) > COMMIT_VELOCITY * density &&
            (velocityX < 0f) == goingForward
        val commit = travelled > width * COMMIT_FRACTION || flicked

        val activeTarget = when {
            !commit -> 0f
            goingForward -> -width
            else -> width
        }
        val incomingTarget = if (commit) 0f else if (goingForward) width else -width
        val duration = if (commit) SETTLE_MS else CANCEL_MS

        incoming.animate().translationX(incomingTarget).setDuration(duration)
            .setInterpolator(DecelerateInterpolator()).start()
        active.animate().translationX(activeTarget).setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                // The caller swaps the bitmaps; both views go back to their resting places.
                active.translationX = 0f
                incoming.visibility = View.GONE
                incoming.translationX = 0f
                forward = null
                onSettled(commit)
            }
            .start()
    }

    /** Puts both views back with no animation — for a cancelled gesture or a rebuild. */
    fun reset() {
        isDragging = false
        forward = null
        active.animate().cancel()
        incoming.animate().cancel()
        active.translationX = 0f
        incoming.translationX = 0f
        incoming.visibility = View.GONE
    }

    private companion object {
        const val COMMIT_FRACTION = 0.28f
        const val COMMIT_VELOCITY = 420f
        const val SETTLE_MS = 180L
        const val CANCEL_MS = 160L
    }
}
