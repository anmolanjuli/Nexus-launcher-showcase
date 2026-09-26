package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class WidgetResizeOverlay(
    context: Context,
    private val widgetView: View,
    private val overlayLayout: WidgetOverlayLayout,
    private val appWidgetId: Int,
    private var currentXFraction: Float,
    private var currentYFraction: Float,
    private val cachedColumns: Int,
    private val cachedRows: Int,
    private val appWidgetManager: AppWidgetManager,
    private val persistFreeSize: Boolean = true,
    private val boundsFromLayoutParams: Boolean = false,
    private val onConfirm: (spanX: Int, spanY: Int, xFraction: Float, yFraction: Float, wFrac: Float, hFrac: Float) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private var initialLeft = 0f
    private var initialTop = 0f
    private var initialWidth = 0f
    private var initialHeight = 0f
    private var anchorRight = 0f
    private var anchorBottom = 0f

    private var startTouchX = 0f
    private var startTouchY = 0f
    private var isDragging = false
    private var prevDrawSpanX = 0
    private var prevDrawSpanY = 0

    private var currentDragRect: RectF? = null

    var onResizeStarted: (() -> Unit)? = null
    var onResizeFinished: (() -> Unit)? = null
    var onBackPressed: (() -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK
            && event.action == android.view.KeyEvent.ACTION_UP
        ) {
            onBackPressed?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private val handleRadius = 10f * density

    private var activeHandle = WidgetResizeOverlayDrag.Handle.NONE

    private val cellWidth get() = com.nexus.launcher.ui.canvas.GridMetrics.compute(
        availableWidthPx = overlayLayout.viewWidth.toFloat(),
        availableHeightPx = overlayLayout.gridAreaHeight.toFloat(),
        columns = cachedColumns,
        rows = cachedRows,
        paddingLeftRightDp = overlayLayout.canvasView?.homePaddingLeftRightDp ?: 0f,
        paddingTopBottomDp = overlayLayout.canvasView?.homePaddingTopBottomDp ?: 0f,
        gapHorizontalDp = overlayLayout.canvasView?.homeGapHorizontalDp ?: 0f,
        gapVerticalDp = overlayLayout.canvasView?.homeGapVerticalDp ?: 0f,
        density = context.resources.displayMetrics.density
    ).cellWidthPx

    private val cellHeight get() = com.nexus.launcher.ui.canvas.GridMetrics.compute(
        availableWidthPx = overlayLayout.viewWidth.toFloat(),
        availableHeightPx = overlayLayout.gridAreaHeight.toFloat(),
        columns = cachedColumns,
        rows = cachedRows,
        paddingLeftRightDp = overlayLayout.canvasView?.homePaddingLeftRightDp ?: 0f,
        paddingTopBottomDp = overlayLayout.canvasView?.homePaddingTopBottomDp ?: 0f,
        gapHorizontalDp = overlayLayout.canvasView?.homeGapHorizontalDp ?: 0f,
        gapVerticalDp = overlayLayout.canvasView?.homeGapVerticalDp ?: 0f,
        density = context.resources.displayMetrics.density
    ).cellHeightPx

    private fun getBoundsRect(): RectF {
        currentDragRect?.let { return it }
        if (boundsFromLayoutParams || (isDragging && activeHandle != WidgetResizeOverlayDrag.Handle.NONE)) {
            return WidgetResizeOverlayBounds.fromLayoutParams(widgetView, overlayLayout, appWidgetId)
        }
        return WidgetResizeOverlayBounds.fromLiveItem(
            overlayLayout, appWidgetId, cachedColumns, cachedRows
        ) ?: WidgetResizeOverlayBounds.fromLayoutParams(widgetView, overlayLayout, appWidgetId)
    }

    override fun onDraw(canvas: Canvas) {
        val rect = getBoundsRect()
        val cx = rect.centerX()
        val cy = rect.centerY()
        drawHandle(canvas, rect.left, rect.top)
        drawHandle(canvas, rect.right, rect.top)
        drawHandle(canvas, rect.left, rect.bottom)
        drawHandle(canvas, rect.right, rect.bottom)
        drawHandle(canvas, cx, rect.top)
        drawHandle(canvas, cx, rect.bottom)
        drawHandle(canvas, rect.left, cy)
        drawHandle(canvas, rect.right, cy)
    }

    private fun drawHandle(canvas: Canvas, cx: Float, cy: Float) {
        canvas.drawCircle(cx, cy, handleRadius, handleFillPaint)
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint)
    }

    private fun hitTest(x: Float, y: Float): WidgetResizeOverlayDrag.Handle {
        return WidgetResizeOverlayDrag.hitTest(getBoundsRect(), handleRadius, x, y)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = hitTest(event.x, event.y)
                if (activeHandle == WidgetResizeOverlayDrag.Handle.NONE) return false
                onResizeStarted?.invoke()
                requestFocus()
                val params = widgetView.layoutParams as FrameLayout.LayoutParams
                initialLeft = params.leftMargin.toFloat()
                initialTop = params.topMargin.toFloat()
                initialWidth = params.width.toFloat()
                initialHeight = params.height.toFloat()
                prevDrawSpanX = (initialWidth / cellWidth).roundToInt()
                prevDrawSpanY = (initialHeight / cellHeight).roundToInt()
                anchorRight = initialLeft + initialWidth
                anchorBottom = initialTop + initialHeight
                startTouchX = event.rawX
                startTouchY = event.rawY
                isDragging = false
                currentDragRect = null
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle == WidgetResizeOverlayDrag.Handle.NONE) return false
                val rawDX = event.rawX - startTouchX
                val rawDY = event.rawY - startTouchY
                if (!isDragging && (abs(rawDX) > touchSlop || abs(rawDY) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) handleDrag(rawDX, rawDY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != WidgetResizeOverlayDrag.Handle.NONE) {
                    if (isDragging) {
                        finalizeDrag()
                        onResizeFinished?.invoke()
                    }
                    activeHandle = WidgetResizeOverlayDrag.Handle.NONE
                    isDragging = false
                    currentDragRect = null
                    return true
                }
            }
        }
        return false
    }

    private fun handleDrag(rawDX: Float, rawDY: Float) {
        if (cellWidth <= 0f || cellHeight <= 0f) return
        val minW = if (persistFreeSize) max(32f * density, 1f) else max(1 * cellWidth, 1f)
        val minH = if (persistFreeSize) max(32f * density, 1f) else max(1 * cellHeight, 1f)
        val dockReservePx = 140f * density
        val maxW = cachedColumns * cellWidth
        val maxH = cachedRows * cellHeight

        val canvas = overlayLayout.canvasView
        val page = WidgetCoordinateSpace.pageOf(overlayLayout, appWidgetId)
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlayLayout)
        val minLeftBound = canvas?.gridAreaLeft?.toFloat() ?: 0f
        // Resizing obeys the same top as placing does.
        val minTopBound = WidgetCoordinateSpace.minTopOf(canvas).toFloat()
        val maxLeftBound = canvas?.let { it.gridAreaLeft + it.gridAreaWidth }?.toFloat()
            ?: overlayLayout.viewWidth.toFloat()
        val maxTopBound = canvas?.let { it.viewHeight - it.bottomBarHeight - it.dockBottomReserve }?.toFloat()
            ?: (overlayLayout.height - dockReservePx)

        val minLeftAbs = WidgetCoordinateSpace.screenToAbsoluteX(minLeftBound, page, pageW)
        val maxRightAbs = WidgetCoordinateSpace.screenToAbsoluteX(maxLeftBound, page, pageW)

        val result = WidgetResizeOverlayDrag.compute(
            handle = activeHandle,
            dx = rawDX,
            dy = rawDY,
            initialLeft = initialLeft,
            initialTop = initialTop,
            initialWidth = initialWidth,
            initialHeight = initialHeight,
            anchorRight = anchorRight,
            anchorBottom = anchorBottom,
            minW = minW,
            minH = minH,
            maxW = maxW,
            maxH = maxH,
            minLeftAbs = minLeftAbs,
            maxRightAbs = maxRightAbs,
            minTopBound = minTopBound,
            maxBottomBound = maxTopBound,
            cellWidth = cellWidth,
            cellHeight = cellHeight
        )

        if (result.spanX != prevDrawSpanX || result.spanY != prevDrawSpanY) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            prevDrawSpanX = result.spanX
            prevDrawSpanY = result.spanY
        }

        WidgetResizeOverlayDrag.applyLayout(
            widgetView, result.left, result.top, result.width, result.height
        )
        val pageLeft = WidgetCoordinateSpace.absoluteToScreenX(result.left, page, pageW)
        currentDragRect = RectF(
            pageLeft,
            result.top,
            pageLeft + result.width,
            result.top + result.height
        )
        overlayLayout.resyncScroll()
        invalidate()
    }

    private fun finalizeDrag() {
        val params = widgetView.layoutParams as FrameLayout.LayoutParams
        val finalWidth = params.width.toFloat()
        val finalHeight = params.height.toFloat()
        val finalLeft = params.leftMargin.toFloat()
        val finalTop = params.topMargin.toFloat()

        val finalSpanX = (finalWidth / cellWidth).roundToInt().coerceAtLeast(1)
        val finalSpanY = (finalHeight / cellHeight).roundToInt().coerceAtLeast(1)

        val page = WidgetCoordinateSpace.pageOf(overlayLayout, appWidgetId)
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlayLayout)
        val finalXFrac = WidgetCoordinateSpace.xFractionFromAbsoluteCenter(
            finalLeft + finalWidth / 2f, page, pageW
        )
        val finalYFrac = (finalTop + finalHeight / 2f) / overlayLayout.height.toFloat().coerceAtLeast(1f)

        currentXFraction = finalXFrac
        currentYFraction = finalYFrac

        val wFrac = finalWidth / overlayLayout.viewWidth.coerceAtLeast(1f)
        val hFrac = finalHeight / overlayLayout.height.toFloat().coerceAtLeast(1f)

        // Mosaic uses a sentinel id — never call options on it. Children size themselves.
        if (persistFreeSize && appWidgetId > 0) {
            val widthDp = (finalWidth / density).toInt()
            val heightDp = (finalHeight / density).toInt()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            try {
                appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
            } catch (_: Exception) { }
        }

        val item = overlayLayout.liveItems[appWidgetId]
        if (item != null) {
            val updatedItem = if (persistFreeSize) {
                val json = try {
                    org.json.JSONObject(item.folderConfigJson)
                } catch (_: Exception) {
                    org.json.JSONObject()
                }
                json.put(WidgetFreeSize.KEY_W, wFrac.toDouble())
                json.put(WidgetFreeSize.KEY_H, hFrac.toDouble())
                item.copy(
                    spanX = finalSpanX,
                    spanY = finalSpanY,
                    xFraction = finalXFrac,
                    yFraction = finalYFrac,
                    folderConfigJson = json.toString()
                )
            } else {
                item.copy(
                    spanX = finalSpanX,
                    spanY = finalSpanY,
                    xFraction = finalXFrac,
                    yFraction = finalYFrac
                )
            }
            overlayLayout.liveItems[appWidgetId] = updatedItem
            overlayLayout.clearPositionOverride(appWidgetId)
            overlayLayout.resyncScroll()
        }

        onConfirm(finalSpanX, finalSpanY, finalXFrac, finalYFrac, wFrac, hFrac)
    }
}
