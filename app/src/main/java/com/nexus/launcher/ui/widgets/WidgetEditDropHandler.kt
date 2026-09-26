package com.nexus.launcher.ui.widgets

import android.util.Log
import android.view.View
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicAbsorbDrop
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicDropPreview

/** Completes a standalone-widget drag, including the optional Mosaic absorb. */
object WidgetEditDropHandler {

    fun handle(
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        widgetOverlayLayout: WidgetOverlayLayout,
        widgetViewModel: WidgetViewModel,
        item: HomeScreenItem,
        widgetView: View,
        targetPage: Int,
        newXFraction: Float,
        newYFraction: Float,
        col: Int,
        row: Int,
        onAbsorbedOrRejected: () -> Unit
    ) {
        val targetId = if (item.appWidgetId != -1) item.appWidgetId else item.id
        val currentItem = widgetOverlayLayout.liveItems[targetId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
        Log.d("WidgetLive", "Dropping item $targetId at page=$targetPage xFrac=$newXFraction yFrac=$newYFraction col=$col row=$row")
        widgetOverlayLayout.clearDragHighlight()
        val highlightedTarget = LivingMosaicDropPreview.consumeHoveredTarget()
        widgetView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(100).start()

        when (LivingMosaicAbsorbDrop.tryAbsorb(
            activity, widgetOverlayLayout, widgetView, currentItem, highlightedTarget, onAbsorbedOrRejected
        )) {
            LivingMosaicAbsorbDrop.Result.ABSORBED -> {
                canvasView.setDragOverviewActive(false)
                return
            }
            LivingMosaicAbsorbDrop.Result.REJECTED -> {
                widgetView.translationX = 0f
                widgetView.translationY = 0f
                canvasView.setDragOverviewActive(false)
                onAbsorbedOrRejected()
                return
            }
            LivingMosaicAbsorbDrop.Result.NO_TARGET -> Unit
        }

        val dropTargetPage = targetPage.coerceIn(0, (canvasView.totalPages - 1).coerceAtLeast(0))
        val spanX = currentItem.spanX
        val spanY = currentItem.spanY

        widgetView.translationX = 0f
        widgetView.translationY = 0f

        if (widgetOverlayLayout.liveMosaics.containsKey(item.id)) {
            widgetOverlayLayout.updateMosaicLayoutParams(item.id, newXFraction, newYFraction, spanX, spanY, dropTargetPage)
        } else {
            widgetOverlayLayout.updateWidgetLayoutParams(targetId, newXFraction, newYFraction, spanX, spanY, dropTargetPage)
        }
        widgetViewModel.updateWidgetPosition(currentItem, newXFraction, newYFraction, activity, dropTargetPage)

        canvasView.fractionDerivedPositions = canvasView.fractionDerivedPositions + (item.id to Triple(dropTargetPage, col, row))

        val prefs = canvasView.context.getSharedPreferences("nexus_prefs", android.content.Context.MODE_PRIVATE)
        val pageCountKey = "home_page_count"
        val currentExplicit = prefs.getInt(pageCountKey, 1)
        if (dropTargetPage + 1 > currentExplicit) {
            prefs.edit().putInt(pageCountKey, dropTargetPage + 1).apply()
        }

        if (canvasView.currentPage != dropTargetPage) {
            canvasView.setCurrentPage(dropTargetPage)
            canvasView.onPageSwipe?.invoke(0)
        }

        canvasView.setDragOverviewActive(false)
        widgetOverlayLayout.resyncScroll()
    }
}
