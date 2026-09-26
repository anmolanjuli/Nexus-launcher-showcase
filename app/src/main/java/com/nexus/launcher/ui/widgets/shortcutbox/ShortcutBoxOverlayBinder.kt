package com.nexus.launcher.ui.widgets.shortcutbox

import android.view.Gravity
import android.widget.FrameLayout
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.widgets.WidgetFreeSize
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/** Binds [ShortcutBoxView]s to [WidgetOverlayLayout]. */
object ShortcutBoxOverlayBinder {

    fun bindBoxes(
        overlay: WidgetOverlayLayout,
        items: List<HomeScreenItem>,
        membersByContainer: Map<Long, List<HomeScreenItem>>,
        columns: Int,
        rows: Int,
        onLongPress: (HomeScreenItem, ShortcutBoxView) -> Unit
    ) {
        val boxes = items.filter { it.itemType == HomeItemTypes.SHORTCUT_BOX }.sortedBy { it.zIndex }
        val canvasView = overlay.canvasView
        val pageW = canvasView?.width?.toFloat()?.takeIf { it > 0f } ?: overlay.viewWidth
        if (pageW <= 0f || overlay.height == 0) return

        // 1. Remove views whose items are no longer present
        for (i in overlay.childCount - 1 downTo 0) {
            val child = overlay.getChildAt(i)
            if (child is ShortcutBoxView) {
                val id = child.currentItem()?.id
                if (id == null || boxes.none { it.id == id }) {
                    overlay.removeViewAt(i)
                }
            }
        }

        // 2. Compute metrics
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

        // 3. Bind each box
        for (item in boxes) {
            val existing = findBox(overlay, item.id)
            val view = existing ?: ShortcutBoxView(overlay.context).also {
                overlay.addView(it)
            }
            view.onLongPressDetected = { onLongPress(item, view) }

            val memberList = membersByContainer[item.id.toLong()] ?: emptyList()
            view.bind(item, memberList)

            val (width, heightPx) = WidgetFreeSize.pixelSize(
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
            params.gravity = Gravity.TOP or Gravity.LEFT
            params.width = clampedW
            params.height = clampedH
            params.leftMargin = (absoluteCenterX - clampedW / 2f).toInt()
            params.topMargin = com.nexus.launcher.ui.widgets.WidgetCoordinateSpace.clampTop(
                (cy - clampedH / 2f).toInt(), clampedH, canvasView, overlay.height, dockReserve,
            )
            view.layoutParams = params

            val sentinel = com.nexus.launcher.ui.widgets.mosaic.LivingMosaicEditSession.SENTINEL_BASE - item.id
            overlay.liveMosaics[item.id] = item
            overlay.liveItems[item.id] = item
            overlay.liveItems[sentinel] = item
        }
        val alive = boxes.map { it.id }.toSet()
        overlay.liveMosaics.keys.removeAll { it in alive }
    }

    private fun findBox(overlay: WidgetOverlayLayout, id: Int): ShortcutBoxView? {
        for (i in 0 until overlay.childCount) {
            val child = overlay.getChildAt(i)
            if (child is ShortcutBoxView && child.currentItem()?.id == id) return child
        }
        return null
    }
}
