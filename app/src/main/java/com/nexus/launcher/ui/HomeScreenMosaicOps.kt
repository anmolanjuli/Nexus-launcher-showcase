package com.nexus.launcher.ui

import android.appwidget.AppWidgetHost
import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.DrawEngineLayout
import com.nexus.launcher.ui.canvas.GridOccupancyHelper
import com.nexus.launcher.ui.canvas.HomeGridBounds
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicPlacement
import com.nexus.launcher.ui.widgets.mosaic.MosaicChild
import com.nexus.launcher.ui.widgets.mosaic.MosaicConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Mosaic Room mutations — keeps [HomeScreenViewModel] under the line-limit. */
internal object HomeScreenMosaicOps {

    fun addMosaic(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        appContext: Context,
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ) {
        scope.launch(Dispatchers.IO) {
            val (maxCols, maxRows) = HomeGridBounds.liveOrDefault(appContext)
            val (col, row) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                appContext, xFraction, yFraction, spanX, spanY, "{}", maxCols, maxRows
            )
            dao.insertItem(
                LivingMosaicPlacement.newItem(page, col, row, spanX, spanY, xFraction, yFraction)
            )
        }
    }

    fun updateConfig(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        item: HomeScreenItem,
        config: MosaicConfig
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(item.id) ?: item
            // Preserve free-size box if caller config omitted it (≤0).
            val prior = MosaicConfig.parse(fresh.folderConfigJson)
            val merged = if (config.wFrac > 0f && config.hFrac > 0f) {
                config
            } else {
                config.copy(
                    wFrac = if (config.wFrac > 0f) config.wFrac else prior.wFrac,
                    hFrac = if (config.hFrac > 0f) config.hFrac else prior.hFrac
                )
            }
            dao.updateItem(fresh.copy(folderConfigJson = merged.toJson()))
        }
    }

    fun updateFreeSize(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        appContext: Context,
        id: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float,
        wFrac: Float,
        hFrac: Float
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(id) ?: return@launch
            val (fCols, fRows) = com.nexus.launcher.ui.canvas.HomeGridBounds.liveOrDefault(appContext)
            val cfg = MosaicConfig.parse(fresh.folderConfigJson).copy(
                wFrac = wFrac, hFrac = hFrac
            )
            val jsonStr = cfg.toJson()
            val (newCol, newRow) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                appContext, xFraction, yFraction, spanX, spanY, jsonStr, fCols, fRows
            )
            dao.updateItem(
                fresh.copy(
                    spanX = spanX,
                    spanY = spanY,
                    xFraction = xFraction,
                    yFraction = yFraction,
                    column = newCol,
                    row = newRow,
                    folderConfigJson = jsonStr
                )
            )
        }
    }

    fun addWidget(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        mosaic: HomeScreenItem,
        appWidgetId: Int,
        providerPackage: String,
        providerClassName: String,
        targetSlot: Int? = null
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(mosaic.id) ?: mosaic
            val cfg = MosaicConfig.parse(fresh.folderConfigJson)
            if (cfg.currentChildren().size >= MosaicConfig.MAX_CHILDREN_PER_PAGE) return@launch
            val child = MosaicChild(
                appWidgetId = appWidgetId,
                providerPackage = providerPackage,
                providerClassName = providerClassName
            )
            val currentKids = cfg.currentChildren().toMutableList()
            if (targetSlot != null) {
                while (currentKids.size <= targetSlot) {
                    currentKids.add(MosaicChild(-1, "", ""))
                }
                currentKids[targetSlot] = child
            } else {
                currentKids.add(child)
            }
            val next = cfg.withCurrentChildren(currentKids)
            dao.updateItem(fresh.copy(folderConfigJson = next.toJson()))
        }
    }

    fun removeWidget(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        mosaic: HomeScreenItem,
        childIndex: Int,
        appWidgetHost: AppWidgetHost
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(mosaic.id) ?: mosaic
            val cfg = MosaicConfig.parse(fresh.folderConfigJson)
            val kids = cfg.currentChildren()
            if (childIndex !in kids.indices) return@launch
            val removed = kids[childIndex]
            if (removed.appWidgetId != -1) {
                try { appWidgetHost.deleteAppWidgetId(removed.appWidgetId) } catch (_: Exception) {}
            }
            val nextKids = kids.toMutableList().also { it.removeAt(childIndex) }
            dao.updateItem(fresh.copy(folderConfigJson = cfg.withCurrentChildren(nextKids).toJson()))
        }
    }

    fun canRestoreWidget(context: Context, items: List<HomeScreenItem>, mosaic: HomeScreenItem, spanX: Int = 2, spanY: Int = 2): Boolean {
        val (maxCols, maxRows) = HomeGridBounds.liveOrDefault(context)
        return GridOccupancyHelper.findNearestEmptySlot(
            context = context,
            desiredCol = mosaic.column,
            desiredRow = mosaic.row,
            spanX = spanX,
            spanY = spanY,
            maxCols = maxCols,
            maxRows = maxRows,
            items = items,
            visualPositions = emptyMap(),
            page = mosaic.page
        ) != null
    }

    /** Moves a child back to Home without deleting its AppWidgetHost id. */
    fun restoreWidgetToHome(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        context: Context,
        mosaic: HomeScreenItem,
        child: MosaicChild,
        nextConfig: MosaicConfig
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(mosaic.id) ?: return@launch
            if (child.appWidgetId == -1) return@launch
            val allItems = dao.getAllItemsDebug()
            val (liveCols, liveRows) = HomeGridBounds.liveOrDefault(context)
            val targetSpanX = child.spanX.coerceIn(1, liveCols)
            val targetSpanY = child.spanY.coerceIn(1, liveRows)
            val spot = GridOccupancyHelper.findNearestEmptySlot(
                context, fresh.column, fresh.row, targetSpanX, targetSpanY,
                liveCols, liveRows, allItems, emptyMap(), page = fresh.page
            ) ?: return@launch
            val (xFraction, yFraction) = computeCellFraction(
                context, spot.first, spot.second, targetSpanX, targetSpanY, liveCols, liveRows
            )
            dao.insertItem(
                HomeScreenItem(
                    packageName = child.providerPackage,
                    page = fresh.page,
                    column = spot.first,
                    row = spot.second,
                    xFraction = xFraction,
                    yFraction = yFraction,
                    itemType = 3,
                    appWidgetId = child.appWidgetId,
                    spanX = targetSpanX,
                    spanY = targetSpanY,
                    providerClassName = child.providerClassName
                )
            )
            dao.updateItem(fresh.copy(folderConfigJson = nextConfig.toJson()))
        }
    }

    private fun computeCellFraction(
        context: Context,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        liveCols: Int,
        liveRows: Int
    ): Pair<Float, Float> {
        val canvas = com.nexus.launcher.ui.folder.FolderBlurCoordinator.findCanvas(context)
        if (canvas != null && canvas.viewWidth > 0 && canvas.viewHeight > 0) {
            val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
                availableWidthPx = canvas.gridAreaWidth.toFloat(),
                availableHeightPx = (canvas.viewHeight - canvas.topInset - canvas.dockBottomReserve).toFloat().coerceAtLeast(0f),
                columns = liveCols,
                rows = liveRows,
                paddingLeftRightDp = canvas.homePaddingLeftRightDp,
                paddingTopBottomDp = canvas.homePaddingTopBottomDp,
                gapHorizontalDp = canvas.homeGapHorizontalDp,
                gapVerticalDp = canvas.homeGapVerticalDp,
                density = canvas.resources.displayMetrics.density
            )
            val bounds = metrics.getCellBounds(
                col, row, spanX, spanY, canvas.gridAreaLeft.toFloat(), canvas.topInset.toFloat()
            )
            val xf = ((bounds.left + bounds.right) / 2f / canvas.viewWidth).coerceIn(0f, 1f)
            val yf = ((bounds.top + bounds.bottom) / 2f / canvas.viewHeight).coerceIn(0f, 1f)
            return Pair(xf, yf)
        }
        val dm = context.resources.displayMetrics
        val density = dm.density
        val dw = dm.widthPixels.toFloat().coerceAtLeast(1f)
        val dh = dm.heightPixels.toFloat().coerceAtLeast(1f)
        val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = dw,
            availableHeightPx = (dh - 48f * density - 96f * density).coerceAtLeast(0f),
            columns = liveCols,
            rows = liveRows,
            paddingLeftRightDp = 16f,
            paddingTopBottomDp = 16f,
            gapHorizontalDp = 8f,
            gapVerticalDp = 8f,
            density = density
        )
        val bounds = metrics.getCellBounds(col, row, spanX, spanY, 0f, 48f * density)
        val xf = ((bounds.left + bounds.right) / 2f / dw).coerceIn(0f, 1f)
        val yf = ((bounds.top + bounds.bottom) / 2f / dh).coerceIn(0f, 1f)
        return Pair(xf, yf)
    }

    fun removeMosaic(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        item: HomeScreenItem,
        appWidgetHost: AppWidgetHost
    ) {
        scope.launch(Dispatchers.IO) {
            val cfg = MosaicConfig.parse(item.folderConfigJson)
            cfg.allChildren().forEach { child ->
                if (child.appWidgetId != -1) {
                    try { appWidgetHost.deleteAppWidgetId(child.appWidgetId) } catch (_: Exception) {}
                }
            }
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDelete(
                source = "HomeScreenMosaicOps.removeMosaic",
                id = item.id,
                extra = com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(item),
                includeStack = true
            )
            dao.removeItemById(item.id)
        }
    }

    /**
     * Move an existing home-screen widget into a mosaic page (same appWidgetId).
     * Does not delete the AppWidgetHost id — only removes the home item row.
     */
    fun absorbHomeWidget(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        mosaic: HomeScreenItem,
        widget: HomeScreenItem
    ) {
        if (widget.itemType != 3 || widget.appWidgetId == -1) return
        if (mosaic.itemType != com.nexus.launcher.data.HomeItemTypes.MOSAIC) return
        scope.launch(Dispatchers.IO) {
            val freshMosaic = dao.getItemById(mosaic.id) ?: mosaic
            val cfg = MosaicConfig.parse(freshMosaic.folderConfigJson)
            if (cfg.currentChildren().size >= MosaicConfig.MAX_CHILDREN_PER_PAGE) return@launch
            if (cfg.allChildren().any { it.appWidgetId == widget.appWidgetId }) return@launch
            val child = MosaicChild(
                appWidgetId = widget.appWidgetId,
                providerPackage = widget.packageName,
                providerClassName = widget.providerClassName.orEmpty(),
                spanX = widget.spanX,
                spanY = widget.spanY
            )
            val next = cfg.withCurrentChildren(cfg.currentChildren() + child)
            dao.updateItem(freshMosaic.copy(folderConfigJson = next.toJson()))
            com.nexus.launcher.ui.canvas.HomeGridDropDiag.logDelete(
                source = "HomeScreenMosaicOps.absorbHomeWidget",
                id = widget.id,
                extra = "mosaicId=${mosaic.id} ${com.nexus.launcher.ui.canvas.HomeGridDropDiag.itemBrief(widget)}",
                includeStack = true
            )
            dao.removeItemById(widget.id)
        }
    }
}
