package com.nexus.launcher.ui.widgets

import android.view.View
import android.widget.FrameLayout
import kotlin.math.roundToInt

object WidgetNudgeHelper {
    fun handleResizeNudge(
        edge: ResizeEdge,
        sign: Float,
        widgetView: View,
        overlayLayout: WidgetOverlayLayout,
        appWidgetId: Int,
        cachedColumns: Int,
        cachedRows: Int,
        density: Float,
        onRepositionPanel: (Int, Int, Int, Int) -> Unit
    ) {
        val lp = widgetView.layoutParams as FrameLayout.LayoutParams
        val delta = 2f * density * sign
        val page = WidgetCoordinateSpace.pageOf(overlayLayout, appWidgetId)
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlayLayout)

        var newTop = lp.topMargin.toFloat()
        var newLeft = lp.leftMargin.toFloat()
        var newWidth = lp.width.toFloat()
        var newHeight = lp.height.toFloat()

        val minW = density * 32f
        val minH = density * 32f
        val canvas = overlayLayout.canvasView
        val minLeftLocal = canvas?.gridAreaLeft?.toFloat() ?: 0f
        // Dragging obeys the same top as placing does.
        val minTop = WidgetCoordinateSpace.minTopOf(canvas).toFloat()
        val maxRightLocal = canvas?.let { it.gridAreaLeft + it.gridAreaWidth }?.toFloat()
            ?: overlayLayout.viewWidth.toFloat()
        val maxH = canvas?.let { it.viewHeight - it.bottomBarHeight - it.dockBottomReserve }?.toFloat()
            ?: (overlayLayout.height - 140f * density)

        val minLeftAbs = WidgetCoordinateSpace.screenToAbsoluteX(minLeftLocal, page, pageW)
        val maxRightAbs = WidgetCoordinateSpace.screenToAbsoluteX(maxRightLocal, page, pageW)

        when (edge) {
            ResizeEdge.TOP -> {
                newTop = (lp.topMargin - delta).coerceAtLeast(minTop)
                newHeight = (lp.topMargin + lp.height - newTop).coerceAtLeast(minH)
            }
            ResizeEdge.BOTTOM -> {
                newHeight = (lp.height + delta).coerceIn(minH, maxH - lp.topMargin)
            }
            ResizeEdge.LEFT -> {
                newLeft = (lp.leftMargin - delta).coerceAtLeast(minLeftAbs)
                newWidth = (lp.leftMargin + lp.width - newLeft).coerceAtLeast(minW)
            }
            ResizeEdge.RIGHT -> {
                newWidth = (lp.width + delta).coerceIn(minW, maxRightAbs - lp.leftMargin)
            }
        }

        lp.leftMargin = newLeft.roundToInt()
        lp.topMargin = newTop.roundToInt()
        lp.width = newWidth.roundToInt()
        lp.height = newHeight.roundToInt()
        widgetView.layoutParams = lp
        widgetView.requestLayout()
        overlayLayout.resyncScroll()

        val cellW = overlayLayout.viewWidth / cachedColumns.toFloat()
        val cellH = overlayLayout.gridAreaHeight / cachedRows.toFloat()
        val xFrac = WidgetCoordinateSpace.xFractionFromAbsoluteCenter(
            newLeft + newWidth / 2f, page, pageW
        )
        val yFrac = (newTop + newHeight / 2f) / overlayLayout.height
        val spanX = (newWidth / cellW).roundToInt().coerceAtLeast(1)
        val spanY = (newHeight / cellH).roundToInt().coerceAtLeast(1)

        val currentItem = overlayLayout.liveItems[appWidgetId]
        if (currentItem != null) {
            val json = try {
                org.json.JSONObject(currentItem.folderConfigJson)
            } catch (_: Exception) {
                org.json.JSONObject()
            }
            val wFrac = newWidth / overlayLayout.viewWidth.coerceAtLeast(1f)
            val hFrac = newHeight / overlayLayout.height.toFloat().coerceAtLeast(1f)
            json.put(WidgetFreeSize.KEY_W, wFrac.toDouble())
            json.put(WidgetFreeSize.KEY_H, hFrac.toDouble())
            overlayLayout.liveItems[appWidgetId] = currentItem.copy(
                xFraction = xFrac, yFraction = yFrac,
                spanX = spanX, spanY = spanY,
                folderConfigJson = json.toString()
            )
        }
        overlayLayout.clearPositionOverride(appWidgetId)

        val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(newLeft, page, pageW)
        onRepositionPanel(screenLeft.roundToInt(), newTop.roundToInt(), newWidth.roundToInt(), newHeight.roundToInt())
    }
}
