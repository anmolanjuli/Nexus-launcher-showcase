package com.nexus.launcher.ui.widgets.mosaic

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/** Places [LivingMosaicView]s on the widget overlay without touching top-level widget bind. */
object LivingMosaicOverlayBinder {

    fun bindFromOverlay(
        overlay: WidgetOverlayLayout,
        items: List<HomeScreenItem>,
        appWidgetManager: AppWidgetManager,
        appWidgetHost: AppWidgetHost,
        columns: Int,
        rows: Int
    ) {
        val onChrome = overlay.onMosaicLongPress ?: return
        val onCfg = overlay.onMosaicConfigChanged ?: return
        val onPlaceholderTapped = overlay.onMosaicPlaceholderTapped
        val onTileFocus = overlay.onMosaicTileFocusRequested
        bindMosaics(
            overlay, items, appWidgetManager, appWidgetHost, columns, rows,
            onChrome, onCfg, onPlaceholderTapped, onTileFocus
        )
    }

    fun bindMosaics(
        overlay: WidgetOverlayLayout,
        items: List<HomeScreenItem>,
        appWidgetManager: AppWidgetManager,
        appWidgetHost: AppWidgetHost,
        columns: Int,
        rows: Int,
        onLongPressChrome: (HomeScreenItem, LivingMosaicView) -> Unit,
        onConfigChanged: (HomeScreenItem, MosaicConfig) -> Unit,
        onPlaceholderTapped: ((HomeScreenItem, Int) -> Unit)? = null,
        onTileFocusRequested: ((HomeScreenItem, LivingMosaicView, Int) -> Unit)? = null
    ) {
        val mosaics = items.filter { it.itemType == HomeItemTypes.MOSAIC }.sortedBy { it.zIndex }
        val canvasView = overlay.canvasView
        val pageW = canvasView?.width?.toFloat()?.takeIf { it > 0f } ?: overlay.viewWidth
        if (pageW <= 0f || overlay.height == 0) return

        for (i in overlay.childCount - 1 downTo 0) {
            val child = overlay.getChildAt(i)
            if (child is LivingMosaicView) {
                val id = child.currentItem()?.id
                if (id == null || mosaics.none { it.id == id }) {
                    overlay.removeViewAt(i)
                }
            }
        }

        val isLandscape = canvasView?.isLandscape ?: false
        val cols = canvasView?.currentGridCols ?: columns
        val rows = canvasView?.currentGridRows ?: rows
        val gridW = canvasView?.gridAreaWidth?.toFloat() ?: pageW
        val gridH = canvasView?.let { (it.viewHeight - it.topInset - it.dockBottomReserve).toFloat().coerceAtLeast(0f) } ?: overlay.gridAreaHeight
        val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = gridW,
            availableHeightPx = gridH,
            columns = cols,
            rows = rows,
            paddingLeftRightDp = canvasView?.homePaddingLeftRightDp ?: 0f,
            paddingTopBottomDp = canvasView?.homePaddingTopBottomDp ?: 0f,
            gapHorizontalDp = canvasView?.homeGapHorizontalDp ?: 0f,
            gapVerticalDp = canvasView?.homeGapVerticalDp ?: 0f,
            density = overlay.resources.displayMetrics.density
        )
        val cellW = metrics.cellWidthPx
        val cellH = metrics.cellHeightPx

        for (item in mosaics) {
            val existing = findMosaic(overlay, item.id)
            val view = existing ?: LivingMosaicView(overlay.context).also {
                wireCallbacks(it, onLongPressChrome, onConfigChanged, onPlaceholderTapped, onTileFocusRequested)
                overlay.addView(it)
            }
            if (existing != null) {
                wireCallbacks(view, onLongPressChrome, onConfigChanged, onPlaceholderTapped, onTileFocusRequested)
            }

            val (width, heightPx) = com.nexus.launcher.ui.widgets.WidgetFreeSize.pixelSize(
                item.folderConfigJson, item.spanX, item.spanY,
                pageW, overlay.height.toFloat(), cellW, cellH, isLandscape,
                page = item.page,
            )
            val clampedW = width.coerceIn(1, pageW.toInt())
            val dockReserve = (canvasView?.dockBottomReserve ?: (140f * overlay.resources.displayMetrics.density).toInt())
            val clampedH = heightPx.coerceIn(1, (overlay.height - dockReserve).coerceAtLeast(1))

            val cx = com.nexus.launcher.ui.widgets.WidgetCoordinateSpace.centerXFromFraction(item.xFraction, pageW)
            val cy = item.yFraction * overlay.height.toFloat()
            val absoluteCenterX = (item.page * pageW) + cx

            val params = (view.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(clampedW, clampedH)
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
            params.width = clampedW
            params.height = clampedH
            params.leftMargin = (absoluteCenterX - clampedW / 2f).toInt()
            params.topMargin = (cy - clampedH / 2f).toInt()

            val pageLeft = (item.page * pageW).toInt()
            val pageRight = (pageLeft + pageW).toInt()
            params.leftMargin = params.leftMargin.coerceIn(pageLeft, (pageRight - clampedW).coerceAtLeast(pageLeft))

            params.topMargin = com.nexus.launcher.ui.widgets.WidgetCoordinateSpace.clampTop(
                params.topMargin, clampedH, canvasView, overlay.height, dockReserve,
            )

            view.layoutParams = params
            overlay.liveMosaics[item.id] = item
            val prev = existing?.currentConfig()?.currentChildren()?.size
            val next = MosaicConfig.parse(item.folderConfigJson).currentChildren().size
            val animate = existing != null && prev != null && prev != next
            // This whole function is called from WidgetOverlayLayout.bindWidgets, which ALSO
            // binds ShortcutBox/AppBox items right after it — and, when triggered from a settings
            // change, from inside a shared coroutineScope collector where an uncaught exception
            // here would cancel every sibling collector too. One misbehaving mosaic must not be
            // able to take any of that down with it.
            try {
                view.bind(item, appWidgetHost, appWidgetManager, animate = animate)
            } catch (e: Exception) {
                android.util.Log.e("LivingMosaicOverlayBinder", "bind() failed for mosaic ${item.id}", e)
            }
        }
        val alive = mosaics.map { it.id }.toSet()
        overlay.liveMosaics.keys.retainAll(alive)
    }

    private fun findMosaic(overlay: WidgetOverlayLayout, id: Int): LivingMosaicView? =
        overlay.findMosaic(id)

    private fun wireCallbacks(
        view: LivingMosaicView,
        onLongPressChrome: (HomeScreenItem, LivingMosaicView) -> Unit,
        onConfigChanged: (HomeScreenItem, MosaicConfig) -> Unit,
        onPlaceholderTapped: ((HomeScreenItem, Int) -> Unit)?,
        onTileFocusRequested: ((HomeScreenItem, LivingMosaicView, Int) -> Unit)?
    ) {
        view.onLongPressDetected = {
            view.currentItem()?.let { item -> onLongPressChrome(item, view) }
        }
        view.onConfigChanged = onConfigChanged
        view.onPlaceholderTapped = { slot ->
            view.currentItem()?.let { item -> onPlaceholderTapped?.invoke(item, slot) }
        }
        view.onTileFocusRequested = { index ->
            view.currentItem()?.let { item -> onTileFocusRequested?.invoke(item, view, index) }
        }
    }

    fun removeAllMosaics(overlay: WidgetOverlayLayout) {
        for (i in overlay.childCount - 1 downTo 0) {
            if (overlay.getChildAt(i) is LivingMosaicView) {
                overlay.removeViewAt(i)
            }
        }
    }
}
