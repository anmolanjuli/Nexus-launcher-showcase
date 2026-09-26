package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.widget.FrameLayout

/**
 * Full-screen geometry and transforms for the selection-mode page-card track.
 * Cards are centered inside the selection-mode safe area. Every overview consumer must
 * use these bounds instead of independently anchoring itself to the screen bottom.
 */
internal object SelectionModeCardTrack {

    const val PEEK_FRAC = 0.06f
    const val GAP_FRAC = 0.02f
    private const val TOP_SAFE_INSET_DP = 72f
    private const val BOTTOM_SAFE_INSET_DP = 24f
    private const val DOCK_BOTTOM_INSET_DP = 16f

    private val clipPath = Path()
    private val cardRect = RectF()

    fun visualWidth(view: LauncherCanvasView): Float =
        view.viewWidth * SelectionModeTransform.SCALE

    fun visualHeight(view: LauncherCanvasView): Float =
        view.viewHeight * SelectionModeTransform.SCALE

    fun peekPx(view: LauncherCanvasView): Float = view.viewWidth * PEEK_FRAC

    fun gapPx(view: LauncherCanvasView): Float = view.viewWidth * GAP_FRAC

    fun stridePx(view: LauncherCanvasView): Float = visualWidth(view) + gapPx(view)

    fun swipeSpanPx(view: LauncherCanvasView): Float = stridePx(view)

    /** Screen Y of the card top, clear of selection chrome and system navigation. */
    fun cardTopPx(view: LauncherCanvasView): Float {
        val h = view.viewHeight.toFloat()
        val density = view.resources.displayMetrics.density
        val safeTop = TOP_SAFE_INSET_DP * density
        val safeBottom = BOTTOM_SAFE_INSET_DP * density
        val extraSpace = (h - safeTop - safeBottom - visualHeight(view)).coerceAtLeast(0f)
        val desiredTop = safeTop + extraSpace / 2f
        val maxTop = (h - safeBottom - visualHeight(view)).coerceAtLeast(0f)
        return desiredTop.coerceAtMost(maxTop)
    }

    fun cardBottomPx(view: LauncherCanvasView): Float =
        cardTopPx(view) + visualHeight(view)

    /** Space between the dock's visual bottom and the selection card edge. */
    fun dockBottomInsetPx(view: LauncherCanvasView): Float =
        DOCK_BOTTOM_INSET_DP * view.resources.displayMetrics.density

    /** Screen Y for the page indicator dots in selection overview mode. */
    fun indicatorYPx(view: LauncherCanvasView): Float {
        val density = view.resources.displayMetrics.density
        val dock = com.nexus.launcher.ui.dock.DockLayout.findFrom(view)
        return if (dock != null && dock.height > 0) {
            val dockTop = (cardBottomPx(view) - dockBottomInsetPx(view)) - (dock.height * SelectionModeTransform.SCALE)
            dockTop - 8f * density
        } else {
            cardBottomPx(view) - 12f * density
        }
    }

    fun restingCenterX(view: LauncherCanvasView, pageIndex: Int): Float {
        val w = view.viewWidth.toFloat()
        val stride = stridePx(view)
        val anchor = w / 2f
        return anchor + (pageIndex - view.currentPage) * stride
    }

    fun centerX(view: LauncherCanvasView, pageIndex: Int, dragOffsetPx: Float): Float =
        restingCenterX(view, pageIndex) + dragOffsetPx

    fun centerY(view: LauncherCanvasView): Float =
        cardTopPx(view) + visualHeight(view) / 2f

    fun pageWidthPx(view: LauncherCanvasView): Float = view.viewWidth.toFloat()

    fun pageHeightPx(view: LauncherCanvasView): Float = view.viewHeight.toFloat()

    fun screenBounds(
        view: LauncherCanvasView,
        pageIndex: Int,
        dragOffsetPx: Float,
        out: RectF
    ) {
        val cx = centerX(view, pageIndex, dragOffsetPx)
        val halfW = visualWidth(view) / 2f
        val top = cardTopPx(view)
        val bottom = cardBottomPx(view)
        out.set(cx - halfW, top, cx + halfW, bottom)
    }

    fun pagesToRender(view: LauncherCanvasView, dragOffsetPx: Float): List<Int> {
        val w = view.viewWidth.toFloat()
        val h = view.viewHeight.toFloat()
        return (0 until view.totalPages).filter { page ->
            val bounds = RectF()
            screenBounds(view, page, dragOffsetPx, bounds)
            bounds.right > 0f && bounds.left < w && bounds.bottom > 0f && bounds.top < h
        }
    }

    fun mapTouchToPage(
        view: LauncherCanvasView,
        screenX: Float,
        screenY: Float,
        pageIndex: Int = view.currentPage
    ): Pair<Float, Float> {
        val cx = centerX(view, pageIndex, view.dragScrollOffset)
        val cy = centerY(view)
        val w = pageWidthPx(view)
        val h = pageHeightPx(view)
        val scale = SelectionModeTransform.SCALE
        val pageX = (screenX - cx) / scale + w / 2f
        val pageY = (screenY - cy) / scale + h / 2f
        return pageX to pageY
    }

    fun containsCurrentPageTouch(view: LauncherCanvasView, screenX: Float, screenY: Float): Boolean {
        val bounds = RectF()
        screenBounds(view, view.currentPage, view.dragScrollOffset, bounds)
        return bounds.contains(screenX, screenY)
    }

    fun pageAtScreenPoint(view: LauncherCanvasView, screenX: Float, screenY: Float): Int? {
        val bounds = RectF()
        for (page in 0 until view.totalPages) {
            screenBounds(view, page, view.dragScrollOffset, bounds)
            if (bounds.contains(screenX, screenY)) {
                return page
            }
        }
        return null
    }

    fun nearestPageAtScreenPoint(view: LauncherCanvasView, screenX: Float): Int {
        if (view.totalPages <= 1) return 0
        var closestPage = 0
        var minDistance = Float.MAX_VALUE
        for (page in 0 until view.totalPages) {
            val cx = centerX(view, page, view.dragScrollOffset)
            val dist = kotlin.math.abs(screenX - cx)
            if (dist < minDistance) {
                minDistance = dist
                closestPage = page
            }
        }
        return closestPage
    }

    /** Full-screen overlay (widgets) — same pivot as canvas page transform. */
    fun applyOverlayViewTransform(view: LauncherCanvasView, target: android.view.View) {
        val w = pageWidthPx(view)
        val h = pageHeightPx(view)
        val cx = centerX(view, view.currentPage, view.dragScrollOffset)
        val cy = centerY(view)
        target.pivotX = w / 2f
        target.pivotY = h / 2f
        target.scaleX = SelectionModeTransform.SCALE
        target.scaleY = SelectionModeTransform.SCALE
        target.translationX = cx - w / 2f
        target.translationY = cy - h / 2f
    }

    /** Bottom dock strip — sits beneath the card track, centered on screen. */
    fun applyDockViewTransform(view: LauncherCanvasView, dock: android.view.View) {
        val cardBottom = cardBottomPx(view)
        val s = SelectionModeTransform.SCALE
        dock.pivotX = dock.width / 2f
        dock.pivotY = dock.height.toFloat()
        dock.scaleX = s
        dock.scaleY = s
        dock.translationX = 0f
        dock.translationY = cardBottom - dock.bottom.toFloat() - dockBottomInsetPx(view)
    }

    /** Status bar row — sits at the top (or bottom) edge of the active card track. */
    fun applyStatusBarViewTransform(view: LauncherCanvasView, statusBar: android.view.View) {
        val w = pageWidthPx(view)
        val s = SelectionModeTransform.SCALE
        val isBottom = (statusBar.layoutParams as? FrameLayout.LayoutParams)?.gravity == android.view.Gravity.BOTTOM
        statusBar.scaleX = s
        statusBar.scaleY = s
        statusBar.pivotX = w / 2f
        if (isBottom) {
            val cardBottom = cardBottomPx(view)
            val h = pageHeightPx(view)
            val statusH = if (statusBar.height > 0) statusBar.height.toFloat() else (28f * view.resources.displayMetrics.density)
            statusBar.pivotY = statusH
            statusBar.translationX = 0f
            statusBar.translationY = cardBottom - h
        } else {
            val cardTop = cardTopPx(view)
            statusBar.pivotY = 0f
            statusBar.translationX = 0f
            statusBar.translationY = cardTop
        }
    }

    fun clearViewTransform(target: android.view.View) {
        target.scaleX = 1f
        target.scaleY = 1f
        target.translationX = 0f
        target.translationY = 0f
        target.pivotX = target.width / 2f
        target.pivotY = target.height / 2f
    }

    fun withPageCanvas(
        view: LauncherCanvasView,
        canvas: Canvas,
        pageIndex: Int,
        dragOffsetPx: Float,
        block: () -> Unit
    ) {
        val w = pageWidthPx(view)
        val h = pageHeightPx(view)
        val cx = centerX(view, pageIndex, dragOffsetPx)
        val cy = centerY(view)
        val corner = SelectionModeTransform.CORNER_RADIUS_DP * view.resources.displayMetrics.density
        screenBounds(view, pageIndex, dragOffsetPx, cardRect)

        clipPath.reset()
        clipPath.addRoundRect(cardRect, corner, corner, Path.Direction.CW)

        val save = canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(cx, cy)
        canvas.scale(SelectionModeTransform.SCALE, SelectionModeTransform.SCALE)
        canvas.translate(-w / 2f, -h / 2f)
        block()
        canvas.restoreToCount(save)
    }

    fun withCurrentPageCanvas(view: LauncherCanvasView, canvas: Canvas, block: () -> Unit) {
        withPageCanvas(view, canvas, view.currentPage, view.dragScrollOffset, block)
    }
}
