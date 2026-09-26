package com.nexus.launcher.ui.widgets

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.nexus.launcher.ui.canvas.GridMetrics
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.cancelPageMotion
import com.nexus.launcher.ui.canvas.isPageMotionRunning
import kotlin.math.roundToInt

class WidgetPickerDragHelper(
    private val overlay: WidgetPickerOverlay,
    private val canvasView: LauncherCanvasView,
    private val onDrop: (WidgetProviderEntry, Float, Float) -> Unit
) {
    var isDragging = false
        private set

    var onDragCompleted: (() -> Unit)? = null

    private var draggedEntry: WidgetProviderEntry? = null
    private var dragShadow: ImageView? = null
    private var widgetOverlay: WidgetOverlayLayout? = null

    private var currentX = 0f
    private var currentY = 0f
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var lastPageScrollTime = 0L

    // Cached for both mosaic-reactivation (issue 3) and snapped drop (issue 2)
    private var cachedSpanX = 1
    private var cachedSpanY = 1
    private var highlightActive = false

    fun startDrag(view: View, entry: WidgetProviderEntry, overlayLayout: WidgetOverlayLayout?) {
        if (isDragging) return
        isDragging = true
        draggedEntry = entry
        widgetOverlay = overlayLayout
        if (overlayLayout != null && overlayLayout.canvasView == null) {
            overlayLayout.canvasView = canvasView
        }

        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

        val b = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        view.draw(canvas)

        dragShadow = ImageView(overlay.context).apply {
            setImageBitmap(b)
            alpha = 0.8f
            scaleX = 1.05f
            scaleY = 1.05f
            layoutParams = android.widget.FrameLayout.LayoutParams(view.width, view.height)
        }

        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val overlayLoc = IntArray(2)
        overlay.getLocationOnScreen(overlayLoc)

        val viewX = loc[0] - overlayLoc[0]
        val viewY = loc[1] - overlayLoc[1]
        val eventX = viewX + view.width / 2f
        val eventY = viewY + view.height / 2f
        currentX = eventX
        currentY = eventY
        dragOffsetX = eventX - viewX
        dragOffsetY = eventY - viewY

        updateShadowPosition()
        overlay.addView(dragShadow)

        val (spanX, spanY) = spansFor(entry)
        cachedSpanX = spanX
        cachedSpanY = spanY
        highlightActive = true
        overlayLayout?.startDragHighlight(spanX, spanY)
        updateHighlightFromShadow()
    }

    private fun spansFor(entry: WidgetProviderEntry): Pair<Int, Int> {
        if (entry.isLivingMosaic || entry.isShortcutBox || entry.isAppBox) {
            return NexusWidgetKinds.mosaicSpan(entry.nexusKind)
        }
        val density = canvasView.resources.displayMetrics.density
        return AppWidgetGeometryOps.computeWidgetSpans(
            entry.info, density, canvasView, null, 1, 1
        )
    }

    private fun updateShadowPosition() {
        dragShadow?.let {
            it.translationX = currentX - dragOffsetX
            it.translationY = currentY - dragOffsetY
        }
    }

    private fun updateHighlightFromShadow() {
        val ol = widgetOverlay ?: return
        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val overlayLoc = IntArray(2)
        overlay.getLocationOnScreen(overlayLoc)
        val screenX = currentX + overlayLoc[0]
        val screenY = currentY + overlayLoc[1]
        val overMosaic = com.nexus.launcher.ui.widgets.mosaic.LivingMosaicDropPreview
            .updateAtScreenPoint(ol, screenX, screenY)
        if (overMosaic) {
            ol.setDragHighlightSuppressed(true)
        } else {
            ol.setDragHighlightSuppressed(false)
            ol.updateDragHighlight(screenX - loc[0] - dragOffsetX, screenY - loc[1] - dragOffsetY)
        }
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        if (!isDragging) return false

        currentX = event.x
        currentY = event.y
        updateShadowPosition()

        if (event.action == MotionEvent.ACTION_MOVE) {
            handleDragMove(event)
            updateHighlightFromShadow()
        } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            completeDrag()
        }
        return true
    }

    private fun handleDragMove(event: MotionEvent) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPageScrollTime > com.nexus.launcher.ui.canvas.PageAdvanceTouchHelper.ADVANCE_DELAY_MS) {
            val shadow = dragShadow
            val itemLeft = shadow?.x ?: event.rawX
            val itemRight = shadow?.let { it.x + it.width * it.scaleX } ?: event.rawX
            val delta = com.nexus.launcher.ui.canvas.PageAdvanceTouchHelper.computePageAdvanceDirection(
                view = canvasView,
                screenX = event.rawX,
                itemLeft = itemLeft,
                itemRight = itemRight
            )
            if (delta != null && !canvasView.isPageMotionRunning) {
                val target = canvasView.currentPage + delta
                if (target in 0 until canvasView.totalPages) {
                    com.nexus.launcher.ui.canvas.DrawerSnapAnimator.animatePageTransition(canvasView, target)
                    lastPageScrollTime = currentTime
                }
            }
        }
    }

    private fun completeDrag() {
        if (!isDragging) return
        isDragging = false
        highlightActive = false

        widgetOverlay?.clearDragHighlight()
        com.nexus.launcher.ui.widgets.mosaic.LivingMosaicDropPreview.clear()

        dragShadow?.let { overlay.removeView(it) }
        dragShadow = null

        val entry = draggedEntry
        draggedEntry = null

        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val overlayLoc = IntArray(2)
        overlay.getLocationOnScreen(overlayLoc)

        val screenX = currentX + overlayLoc[0]
        val screenY = currentY + overlayLoc[1]
        val canvasX = screenX - loc[0] - dragOffsetX
        val canvasY = screenY - loc[1] - dragOffsetY

        // Compute snapped drop position so it exactly matches the highlighted cell (issue 2)
        val dropX: Float
        var dropY: Float
        var droppedHalfHeightPx = 0f
        val ol = widgetOverlay
        val snapCanvas = ol?.canvasView
        if (ol != null && snapCanvas != null && ol.cachedColumns > 0 && ol.cachedRows > 0) {
            val snap = WidgetDragHighlight.snapTopLeft(
                canvas = snapCanvas,
                gridAreaHeight = ol.gridAreaHeight,
                columns = ol.cachedColumns,
                rows = ol.cachedRows,
                spanX = cachedSpanX,
                spanY = cachedSpanY,
                hoverPage = snapCanvas.currentPage,
                pageLocalLeft = canvasX,
                pageLocalTop = canvasY,
                pageW = snapCanvas.width.toFloat()
            )
            if (snap != null) {
                val density = snapCanvas.resources.displayMetrics.density
                val metrics = GridMetrics.compute(
                    availableWidthPx = snapCanvas.gridAreaWidth.toFloat(),
                    availableHeightPx = ol.gridAreaHeight,
                    columns = ol.cachedColumns,
                    rows = ol.cachedRows,
                    paddingLeftRightDp = snapCanvas.homePaddingLeftRightDp,
                    paddingTopBottomDp = snapCanvas.homePaddingTopBottomDp,
                    gapHorizontalDp = snapCanvas.homeGapHorizontalDp,
                    gapVerticalDp = snapCanvas.homeGapVerticalDp,
                    density = density
                )
                val bounds = metrics.getCellBounds(
                    snap.col, snap.row, cachedSpanX, cachedSpanY,
                    snapCanvas.gridAreaLeft.toFloat(), snapCanvas.topInset.toFloat()
                )
                dropX = (bounds.left + bounds.right) / 2f
                dropY = (bounds.top + bounds.bottom) / 2f
                droppedHalfHeightPx = (bounds.bottom - bounds.top) / 2f
            } else {
                dropX = canvasX + dragOffsetX
                dropY = canvasY + dragOffsetY
                droppedHalfHeightPx = cachedSpanY * ol.gridAreaHeight / ol.cachedRows.coerceAtLeast(1) / 2f
            }
        } else {
            dropX = canvasX + dragOffsetX
            dropY = canvasY + dragOffsetY
        }

        widgetOverlay = null

        if (entry != null) {
            onDrop(entry, dropX, dropY)
        }

        onDragCompleted?.invoke()
    }
}
