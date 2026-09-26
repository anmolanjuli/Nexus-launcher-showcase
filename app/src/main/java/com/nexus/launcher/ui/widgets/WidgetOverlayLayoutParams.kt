package com.nexus.launcher.ui.widgets

import android.view.View
import kotlin.math.roundToInt

/** LayoutParams updates for hosted widgets — split from [WidgetOverlayLayout] for line-limit. */
internal object WidgetOverlayLayoutParams {

    fun update(
        overlay: WidgetOverlayLayout,
        appWidgetId: Int,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        liveItems: MutableMap<Int, com.nexus.launcher.data.HomeScreenItem>,
        positionOverrides: MutableMap<Int, Pair<Float, Float>>,
        viewWidth: Float,
        columns: Int,
        rows: Int,
        singlePageWidth: Float,
        gridAreaHeight: Float,
        targetPage: Int = liveItems[appWidgetId]?.page ?: 0
    ) {
        val currentItem = liveItems[appWidgetId]
        if (currentItem != null) {
            liveItems[appWidgetId] = currentItem.copy(
                page = targetPage, xFraction = xFraction, yFraction = yFraction, spanX = spanX, spanY = spanY
            )
        }
        positionOverrides[appWidgetId] = Pair(xFraction, yFraction)
        var targetChild: View? = null
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is android.appwidget.AppWidgetHostView && child.appWidgetId == appWidgetId) {
                targetChild = child
                break
            } else if (child is com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView && child.currentItem()?.id == appWidgetId) {
                targetChild = child
                break
            } else if (child is com.nexus.launcher.ui.widgets.appbox.AppBoxView && child.currentItem()?.id == appWidgetId) {
                targetChild = child
                break
            } else if (child is com.nexus.launcher.ui.widgets.liveapp.LiveAppView && child.currentItem()?.id == appWidgetId) {
                targetChild = child
                break
            }
        }
        if (targetChild == null) return

        val cv = overlay.canvasView
        val isLandscape = cv?.isLandscape ?: false
        val cols = cv?.currentGridCols ?: columns
        val rows = cv?.currentGridRows ?: rows
        val gridW = cv?.gridAreaWidth?.toFloat() ?: cv?.width?.toFloat() ?: viewWidth
        val gridH = cv?.let { (it.viewHeight - it.topInset - it.dockBottomReserve).toFloat().coerceAtLeast(0f) } ?: gridAreaHeight
        val metrics = if (cv != null && cols > 0 && rows > 0) {
            com.nexus.launcher.ui.canvas.GridMetrics.compute(
                availableWidthPx = gridW,
                availableHeightPx = gridH,
                columns = cols,
                rows = rows,
                paddingLeftRightDp = cv.homePaddingLeftRightDp,
                paddingTopBottomDp = cv.homePaddingTopBottomDp,
                gapHorizontalDp = cv.homeGapHorizontalDp,
                gapVerticalDp = cv.homeGapVerticalDp,
                density = overlay.resources.displayMetrics.density
            )
        } else null
        val cw = metrics?.cellWidthPx ?: (gridW / cols.toFloat().coerceAtLeast(1f))
        val ch = metrics?.cellHeightPx ?: (gridH / rows.toFloat().coerceAtLeast(1f))
        val item = liveItems[appWidgetId]
        val (width, heightPx) = WidgetFreeSize.pixelSize(
            item?.folderConfigJson, spanX, spanY,
            viewWidth, overlay.height.toFloat(), cw, ch, isLandscape,
            page = item?.page ?: -1,
        )
        val dockRes = (cv?.dockBottomReserve ?: (140f * overlay.resources.displayMetrics.density).toInt())
        val clampedWidth = width.coerceIn(1, singlePageWidth.toInt())
        val clampedHeight = heightPx.coerceIn(1, (overlay.height - dockRes).coerceAtLeast(1))
        val page = targetPage
        val cx = (page * singlePageWidth) + WidgetCoordinateSpace.centerXFromFraction(xFraction, singlePageWidth)
        val cy = yFraction * overlay.height.toFloat()
        val lp = targetChild.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
        lp.width = clampedWidth
        lp.height = clampedHeight
        lp.leftMargin = (cx - clampedWidth / 2f).roundToInt()
        // The shared rule. This is the path a rebind takes when the same widgets are already
        // bound — the common one — so a copy of it here is a copy that goes stale.
        lp.topMargin = WidgetCoordinateSpace.clampTop(
            (cy - clampedHeight / 2f).roundToInt(), clampedHeight,
            overlay.canvasView, overlay.height, dockRes,
        )
        targetChild.layoutParams = lp
        targetChild.requestLayout()
        if (targetChild is android.appwidget.AppWidgetHostView) {
            syncGlassBackdropParams(overlay, appWidgetId, lp)
            NexusWidgetHostPoke.sendSize(targetChild, clampedWidth, clampedHeight)
        }
        overlay.resyncScroll()
    }

    /** Keeps a widget's [WidgetGlassLiveBackdropView] sibling (if any) in lockstep during drag/nudge. */
    private fun syncGlassBackdropParams(
        overlay: WidgetOverlayLayout,
        appWidgetId: Int,
        hostParams: android.widget.FrameLayout.LayoutParams
    ) {
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is WidgetGlassLiveBackdropView && child.appWidgetId == appWidgetId) {
                val lp = child.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
                lp.width = hostParams.width
                lp.height = hostParams.height
                lp.leftMargin = hostParams.leftMargin
                lp.topMargin = hostParams.topMargin
                child.layoutParams = lp
                return
            }
        }
    }

    fun updateMosaic(
        overlay: WidgetOverlayLayout,
        itemId: Int,
        xFraction: Float,
        yFraction: Float,
        spanX: Int,
        spanY: Int,
        liveMosaics: MutableMap<Int, com.nexus.launcher.data.HomeScreenItem>,
        viewWidth: Float,
        columns: Int,
        rows: Int,
        singlePageWidth: Float,
        gridAreaHeight: Float,
        targetPage: Int = liveMosaics[itemId]?.page ?: 0
    ) {
        val currentItem = liveMosaics[itemId]
        if (currentItem != null) {
            liveMosaics[itemId] = currentItem.copy(
                page = targetPage, xFraction = xFraction, yFraction = yFraction, spanX = spanX, spanY = spanY
            )
        }
        var target: View? = null
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            val isMosaicMatch = child is com.nexus.launcher.ui.widgets.mosaic.LivingMosaicView && child.currentItem()?.id == itemId
            val isBoxMatch = child is com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView && child.currentItem()?.id == itemId
            val isAppBoxMatch = child is com.nexus.launcher.ui.widgets.appbox.AppBoxView && child.currentItem()?.id == itemId
            val isLiveAppMatch = child is com.nexus.launcher.ui.widgets.liveapp.LiveAppView && child.currentItem()?.id == itemId
            if (isMosaicMatch || isBoxMatch || isAppBoxMatch || isLiveAppMatch) {
                target = child
                break
            }
        }
        if (target == null) return

        val cv = overlay.canvasView
        val isLandscape = cv?.isLandscape ?: false
        val cols = cv?.currentGridCols ?: columns
        val rows = cv?.currentGridRows ?: rows
        val gridW = cv?.gridAreaWidth?.toFloat() ?: cv?.width?.toFloat() ?: viewWidth
        val gridH = cv?.let { (it.viewHeight - it.topInset - it.dockBottomReserve).toFloat().coerceAtLeast(0f) } ?: gridAreaHeight
        val metrics = if (cv != null && cols > 0 && rows > 0) {
            com.nexus.launcher.ui.canvas.GridMetrics.compute(
                availableWidthPx = gridW,
                availableHeightPx = gridH,
                columns = cols,
                rows = rows,
                paddingLeftRightDp = cv.homePaddingLeftRightDp,
                paddingTopBottomDp = cv.homePaddingTopBottomDp,
                gapHorizontalDp = cv.homeGapHorizontalDp,
                gapVerticalDp = cv.homeGapVerticalDp,
                density = overlay.resources.displayMetrics.density
            )
        } else null
        val cw = metrics?.cellWidthPx ?: (gridW / cols.toFloat().coerceAtLeast(1f))
        val ch = metrics?.cellHeightPx ?: (gridH / rows.toFloat().coerceAtLeast(1f))
        val item = liveMosaics[itemId]
        val (width, heightPx) = WidgetFreeSize.pixelSize(
            item?.folderConfigJson, spanX, spanY,
            viewWidth, overlay.height.toFloat(), cw, ch, isLandscape,
            page = item?.page ?: -1,
        )
        val dockRes = (cv?.dockBottomReserve ?: (140f * overlay.resources.displayMetrics.density).toInt())
        val clampedWidth = width.coerceIn(1, singlePageWidth.toInt().coerceAtLeast(1))
        val clampedHeight = heightPx.coerceIn(1, (overlay.height - dockRes).coerceAtLeast(1))
        val page = targetPage
        val cx = (page * singlePageWidth) + WidgetCoordinateSpace.centerXFromFraction(xFraction, singlePageWidth)
        val cy = yFraction * overlay.height.toFloat()
        val lp = target.layoutParams as android.widget.FrameLayout.LayoutParams
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
        lp.width = clampedWidth
        lp.height = clampedHeight
        lp.leftMargin = (cx - clampedWidth / 2f).roundToInt()
        lp.topMargin = WidgetCoordinateSpace.clampTop(
            (cy - clampedHeight / 2f).roundToInt(), clampedHeight, cv, overlay.height, dockRes,
        )
        target.layoutParams = lp
        target.requestLayout()
        overlay.resyncScroll()
    }
}
