package com.nexus.launcher.ui.widgets

import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout

/** Resolves resize-handle bounds for [WidgetResizeOverlay]. */
object WidgetResizeOverlayBounds {

    fun fromLayoutParams(
        widgetView: View,
        overlayLayout: WidgetOverlayLayout,
        appWidgetId: Int
    ): RectF {
        val lp = (widgetView.layoutParams as? FrameLayout.LayoutParams) ?: return RectF()
        val page = WidgetCoordinateSpace.pageOf(overlayLayout, appWidgetId)
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlayLayout)
        val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
            lp.leftMargin.toFloat(), page, pageW
        )
        return RectF(
            screenLeft,
            lp.topMargin.toFloat(),
            screenLeft + lp.width.toFloat(),
            (lp.topMargin + lp.height).toFloat()
        )
    }

    fun fromLiveItem(
        overlayLayout: WidgetOverlayLayout,
        appWidgetId: Int,
        cachedColumns: Int,
        cachedRows: Int
    ): RectF? {
        val item = overlayLayout.liveItems[appWidgetId] ?: return null
        val cellW = overlayLayout.viewWidth / cachedColumns
        val cellH = overlayLayout.gridAreaHeight / cachedRows
        val (pixelW, pixelH) = WidgetFreeSize.pixelSize(
            item.folderConfigJson, item.spanX, item.spanY,
            overlayLayout.viewWidth, overlayLayout.height.toFloat(), cellW, cellH
        )
        val cx = WidgetCoordinateSpace.centerXFromFraction(item.xFraction, overlayLayout.viewWidth)
        val cy = item.yFraction * overlayLayout.height
        val pw = pixelW.toFloat()
        val ph = pixelH.toFloat()
        return RectF(
            cx - pw / 2f,
            cy - ph / 2f,
            cx + pw / 2f,
            cy + ph / 2f
        )
    }
}
