package com.nexus.launcher.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * Shared placement maths for the drawer's anchored menu cards — the overflow menu and the
 * category dropdown.
 *
 * A card opens *away* from the edge its trigger is nearest: downward from a trigger near the top
 * of the screen, upward from one near the bottom. Anchoring upward is done with a bottom-gravity
 * margin rather than a computed `topMargin`, because the card's height isn't known until it has
 * been measured — the old code assumed a fixed 200dp and ran a bottom-docked menu off-screen.
 */
object AnchoredMenuPlacement {

    /** Fraction of screen height within which a trigger counts as hugging that edge. */
    private const val EDGE_ZONE = 0.25f

    enum class Direction { DOWN, UP, SIDE }

    fun directionFor(context: Context, anchorTopOnScreen: Int, anchorHeight: Int): Direction {
        val screenHeight = context.resources.displayMetrics.heightPixels
        val anchorBottom = anchorTopOnScreen + anchorHeight
        return when {
            anchorTopOnScreen < screenHeight * EDGE_ZONE -> Direction.DOWN
            anchorBottom > screenHeight * (1f - EDGE_ZONE) -> Direction.UP
            else -> Direction.SIDE
        }
    }

    /**
     * Raw screen coordinates for a card of known size, for surfaces positioned by absolute
     * position rather than layout params (a [android.widget.PopupWindow]). Same rules as
     * [layoutParamsFor]; SIDE is folded into DOWN here since the only SIDE trigger is the
     * legacy rail button, which no popup uses.
     */
    fun screenPositionFor(
        context: Context,
        anchorView: View,
        anchorPos: IntArray,
        cardWidth: Int,
        cardHeight: Int,
        isRtl: Boolean,
        direction: Direction
    ): IntArray {
        val dp = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val edgeMargin = (12 * dp).toInt()
        val gap = (8 * dp).toInt()
        val anchorWidth = anchorView.width.takeIf { it > 0 } ?: (36 * dp).toInt()
        val anchorHeight = anchorView.height.takeIf { it > 0 } ?: anchorWidth

        val x = (if (isRtl) anchorPos[0] else anchorPos[0] + anchorWidth - cardWidth)
            .coerceIn(edgeMargin, (screenWidth - cardWidth - edgeMargin).coerceAtLeast(edgeMargin))
        val y = if (direction == Direction.UP) {
            anchorPos[1] - cardHeight - gap
        } else {
            anchorPos[1] + anchorHeight + gap
        }
        return intArrayOf(x, y.coerceAtLeast(edgeMargin))
    }

    /**
     * Builds layout params that place a [cardWidth]-wide card relative to [anchorView].
     *
     * DOWN/UP align the card's near edge to the anchor's, clamped inside the screen; SIDE keeps
     * the legacy behavior for a mid-height trigger (beside the anchor, roughly centered on it).
     * Mirrors to the opposite horizontal side under RTL.
     */
    fun layoutParamsFor(
        context: Context,
        anchorView: View,
        anchorPos: IntArray,
        cardWidth: Int,
        isRtl: Boolean,
        direction: Direction
    ): FrameLayout.LayoutParams {
        val dp = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val edgeMargin = (12 * dp).toInt()
        val gap = (8 * dp).toInt()
        val anchorWidth = anchorView.width.takeIf { it > 0 } ?: (36 * dp).toInt()
        val anchorHeight = anchorView.height.takeIf { it > 0 } ?: anchorWidth

        fun clampX(x: Int) = x.coerceIn(edgeMargin, (screenWidth - cardWidth - edgeMargin).coerceAtLeast(edgeMargin))

        // Aligned so the card grows inward from the anchor's outer edge.
        val alignedLeft = if (isRtl) {
            clampX(anchorPos[0])
        } else {
            clampX(anchorPos[0] + anchorWidth - cardWidth)
        }

        return when (direction) {
            Direction.DOWN -> FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = alignedLeft
                topMargin = anchorPos[1] + anchorHeight + gap
            }
            Direction.UP -> FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                leftMargin = alignedLeft
                bottomMargin = (screenHeight - anchorPos[1] + gap).coerceAtLeast(edgeMargin)
            }
            Direction.SIDE -> FrameLayout.LayoutParams(cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = if (isRtl) {
                    (anchorPos[0] + anchorWidth).coerceAtMost(screenWidth - cardWidth - edgeMargin)
                } else {
                    (anchorPos[0] - cardWidth).coerceAtLeast(edgeMargin)
                }
                topMargin = (anchorPos[1] + anchorHeight / 2 - (100 * dp).toInt()).coerceAtLeast(edgeMargin)
            }
        }
    }
}
