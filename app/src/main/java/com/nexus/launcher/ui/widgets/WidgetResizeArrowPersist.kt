package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.roundToInt

/** Free-size persist for menu arrow resize — mirrors handle-drag finalize. */
object WidgetResizeArrowPersist {

    fun persistAndExit(
        widgetView: View,
        overlayLayout: WidgetOverlayLayout,
        appWidgetId: Int,
        cachedColumns: Int,
        cachedRows: Int,
        density: Float,
        widgetViewModel: WidgetViewModel,
        appWidgetManager: AppWidgetManager,
        item: HomeScreenItem
    ) {
        val p = widgetView.layoutParams as FrameLayout.LayoutParams
        val finalWidth = p.width.toFloat().coerceAtLeast(1f)
        val finalHeight = p.height.toFloat().coerceAtLeast(1f)
        val cellW = overlayLayout.viewWidth / cachedColumns.toFloat().coerceAtLeast(1f)
        val cellH = overlayLayout.gridAreaHeight / cachedRows.toFloat().coerceAtLeast(1f)
        val finalSpanX = (finalWidth / cellW).roundToInt().coerceAtLeast(1)
        val finalSpanY = (finalHeight / cellH).roundToInt().coerceAtLeast(1)

        val page = WidgetCoordinateSpace.pageOf(overlayLayout, appWidgetId, item.page)
        val pageW = WidgetCoordinateSpace.pageWidthOf(overlayLayout)
        val finalXFrac = WidgetCoordinateSpace.xFractionFromAbsoluteCenter(
            p.leftMargin + finalWidth / 2f, page, pageW
        )
        val finalYFrac = (p.topMargin + finalHeight / 2f) /
            overlayLayout.height.toFloat().coerceAtLeast(1f)
        val wFrac = finalWidth / overlayLayout.viewWidth.coerceAtLeast(1f)
        val hFrac = finalHeight / overlayLayout.height.toFloat().coerceAtLeast(1f)

        val json = try {
            org.json.JSONObject(item.folderConfigJson)
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        json.put(WidgetFreeSize.KEY_W, wFrac.toDouble())
        json.put(WidgetFreeSize.KEY_H, hFrac.toDouble())
        val updated = (overlayLayout.liveItems[appWidgetId] ?: item).copy(
            spanX = finalSpanX,
            spanY = finalSpanY,
            xFraction = finalXFrac,
            yFraction = finalYFrac,
            folderConfigJson = json.toString()
        )
        overlayLayout.liveItems[appWidgetId] = updated
        overlayLayout.clearPositionOverride(appWidgetId)
        overlayLayout.resyncScroll()

        widgetViewModel.updateWidgetFreeSize(
            item, finalSpanX, finalSpanY, finalXFrac, finalYFrac, wFrac, hFrac, widgetView.context
        )

        if (appWidgetId > 0) {
            try {
                appWidgetManager.updateAppWidgetOptions(
                    appWidgetId,
                    Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (finalWidth / density).toInt())
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (finalHeight / density).toInt())
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (finalWidth / density).toInt())
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (finalHeight / density).toInt())
                    }
                )
            } catch (_: Exception) { }
        }
    }
}
