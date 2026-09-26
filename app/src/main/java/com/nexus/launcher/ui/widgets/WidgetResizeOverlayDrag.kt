package com.nexus.launcher.ui.widgets

import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Edge-anchored resize math for [WidgetResizeOverlay].
 * Only the dragged edge(s) move; opposite edges stay fixed unless a hard bound forces it.
 */
internal object WidgetResizeOverlayDrag {

    enum class Handle {
        NONE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    data class Result(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val spanX: Int,
        val spanY: Int
    )

    fun compute(
        handle: Handle,
        dx: Float,
        dy: Float,
        initialLeft: Float,
        initialTop: Float,
        initialWidth: Float,
        initialHeight: Float,
        anchorRight: Float,
        anchorBottom: Float,
        minW: Float,
        minH: Float,
        maxW: Float,
        maxH: Float,
        minLeftAbs: Float,
        maxRightAbs: Float,
        minTopBound: Float,
        maxBottomBound: Float,
        cellWidth: Float,
        cellHeight: Float
    ): Result {
        var left = initialLeft
        var top = initialTop
        var right = anchorRight
        var bottom = anchorBottom

        val movesLeft = handle == Handle.LEFT || handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT
        val movesRight = handle == Handle.RIGHT || handle == Handle.TOP_RIGHT || handle == Handle.BOTTOM_RIGHT
        val movesTop = handle == Handle.TOP || handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
        val movesBottom = handle == Handle.BOTTOM || handle == Handle.BOTTOM_LEFT || handle == Handle.BOTTOM_RIGHT

        if (movesRight) {
            right = (anchorRight + dx).coerceIn(left + minW, left + maxW)
            right = right.coerceAtMost(maxRightAbs)
            if (right - left < minW) right = left + minW
        }
        if (movesLeft) {
            left = (initialLeft + dx).coerceIn(right - maxW, right - minW)
            left = left.coerceAtLeast(minLeftAbs)
            if (right - left < minW) left = right - minW
        }
        if (movesBottom) {
            bottom = (anchorBottom + dy).coerceIn(top + minH, top + maxH)
            bottom = bottom.coerceAtMost(maxBottomBound)
            if (bottom - top < minH) bottom = top + minH
        }
        if (movesTop) {
            top = (initialTop + dy).coerceIn(bottom - maxH, bottom - minH)
            top = top.coerceAtLeast(minTopBound)
            if (bottom - top < minH) top = bottom - minH
        }

        // Hard page clamp without sliding the opposite edge when only one side moves
        if (movesRight && !movesLeft) {
            left = initialLeft
            right = min(right, maxRightAbs).coerceAtLeast(left + minW)
        }
        if (movesLeft && !movesRight) {
            right = anchorRight
            left = max(left, minLeftAbs).coerceAtMost(right - minW)
        }
        if (movesBottom && !movesTop) {
            top = initialTop
            bottom = min(bottom, maxBottomBound).coerceAtLeast(top + minH)
        }
        if (movesTop && !movesBottom) {
            bottom = anchorBottom
            top = max(top, minTopBound).coerceAtMost(bottom - minH)
        }

        val width = (right - left).coerceIn(minW, maxW)
        val height = (bottom - top).coerceIn(minH, maxH)
        val spanX = (width / cellWidth).roundToInt().coerceAtLeast(1)
        val spanY = (height / cellHeight).roundToInt().coerceAtLeast(1)
        return Result(left, top, width, height, spanX, spanY)
    }

    fun applyLayout(widgetView: View, left: Float, top: Float, width: Float, height: Float) {
        val params = widgetView.layoutParams as FrameLayout.LayoutParams
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
        params.width = width.roundToInt()
        params.height = height.roundToInt()
        params.leftMargin = left.roundToInt()
        params.topMargin = top.roundToInt()
        widgetView.layoutParams = params
        widgetView.requestLayout()
    }

    fun hitTest(
        rect: RectF,
        handleRadius: Float,
        x: Float,
        y: Float
    ): Handle {
        val hit = handleRadius * 2.8f
        val cx = rect.centerX()
        val cy = rect.centerY()
        val nearTop = abs(y - rect.top) <= hit
        val nearBottom = abs(y - rect.bottom) <= hit
        val nearLeft = abs(x - rect.left) <= hit
        val nearRight = abs(x - rect.right) <= hit
        val nearCx = abs(x - cx) <= hit
        val nearCy = abs(y - cy) <= hit

        val preferTop = abs(y - rect.top) <= abs(y - rect.bottom)
        if (preferTop) {
            if (nearTop && nearLeft) return Handle.TOP_LEFT
            if (nearTop && nearRight) return Handle.TOP_RIGHT
            if (nearTop && nearCx) return Handle.TOP
            if (nearBottom && nearLeft) return Handle.BOTTOM_LEFT
            if (nearBottom && nearRight) return Handle.BOTTOM_RIGHT
            if (nearBottom && nearCx) return Handle.BOTTOM
        } else {
            if (nearBottom && nearLeft) return Handle.BOTTOM_LEFT
            if (nearBottom && nearRight) return Handle.BOTTOM_RIGHT
            if (nearBottom && nearCx) return Handle.BOTTOM
            if (nearTop && nearLeft) return Handle.TOP_LEFT
            if (nearTop && nearRight) return Handle.TOP_RIGHT
            if (nearTop && nearCx) return Handle.TOP
        }
        if (nearLeft && nearCy) return Handle.LEFT
        if (nearRight && nearCy) return Handle.RIGHT
        return Handle.NONE
    }
}
