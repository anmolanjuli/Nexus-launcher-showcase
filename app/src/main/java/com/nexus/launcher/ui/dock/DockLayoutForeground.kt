package com.nexus.launcher.ui.dock

import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.nexus.launcher.R

/** Foreground chrome, icons, labels, and page dots for [DockLayout]. */
internal object DockLayoutForeground {

    fun draw(dock: DockLayout, canvas: Canvas) {
        val dbDrawItems = DockLayoutRenderer.dockItemsForDraw(dock.getItemsSnapshot())
        val drawItems = DockSearchSlot.mergeForLayout(
            dbDrawItems, dock.maxDockIcons, dock.currentPage
        )
        if (drawItems.isEmpty() && !DockHighlightRenderer.shouldDraw()) return
        dock.recomputeLayout()
        val pageCount = dock.pageCountInternal
        if (pageCount <= 0 && drawItems.isEmpty()) return
        val density = dock.resources.displayMetrics.density
        val iconSizePx = dock.iconSizePxInternal
        val bottomPaddingPx = dock.bottomPaddingPxInternal
        val top = dock.height - bottomPaddingPx - iconSizePx
        val isVertical = DockAxis.isVertical(dock)
        val axis = DockAxis.mainSize(dock.width, dock.height, isVertical)
        val cross = DockAxis.crossSize(dock.width, dock.height, isVertical)
        val dockBgColor = DockBackgroundRenderer.currentDockBackgroundColor(dock.context, dock)
        
        val canvasView = DockLayoutHelper.findCanvasView(dock)
        val badgeStyleApp = canvasView?.badgeStyleApp ?: 1
        val badgeStyleFolder = canvasView?.badgeStyleFolder ?: 1
        val badgeCounts = com.nexus.launcher.service.NexusNotificationService.badgeCounts.value
        val badgeRenderer = canvasView?.badgeRenderer


        DockLayoutRenderer.drawIcons(
            canvas,
            axis,
            cross,
            drawItems,
            dock.maxDockIcons,
            dock.currentPage,
            iconSizePx,
            dock.effectiveBottomPaddingPxInternal(),
            dock.iconCacheInternal,
            dock.outboundDraggedItemId,
            dock.folderContentsSnapshotInternal,
            dock.hiddenOpenFolderId,
            badgeCounts,
            badgeStyleApp,
            badgeStyleFolder,
            badgeRenderer,
            isVertical = isVertical
        )
        if (drawItems.isEmpty()) return
        DockLayoutRenderer.drawLabels(
            canvas,
            axis,
            cross,
            drawItems,
            dock.maxDockIcons,
            iconSizePx,
            dock.effectiveBottomPaddingPxInternal(),
            dockBgColor,
            { item ->
                if (item.id == dock.hiddenOpenFolderId) null
                else dock.labelForItemInternal(item)
            }
        )
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ContextCompat.getColor(dock.context, R.color.nexus_mist_blue)
        }
        DockLayoutRenderer.drawPageDots(
            canvas, dock.width, pageCount, dock.scrollOffsetXInternal, top, density, dotPaint
        )
    }
}
