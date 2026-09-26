package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.folder.FolderConfigCodec
import com.nexus.launcher.ui.folder.FolderScrimHighlight

internal data class FolderScreenAnchor(
    val screenX: Float,
    val screenY: Float,
    val canonicalIconSize: Float,
    val realIconSize: Float,
    val highlightBounds: FolderScrimHighlight.Bounds? = null
)

internal object FolderOpenAnchor {

    fun homeFolderAnchorOnScreen(view: LauncherCanvasView, item: HomeScreenItem): FolderScreenAnchor? {
        val pos = view.fractionDerivedPositions[item.id] ?: Triple(item.page, item.column, item.row)
        if (pos.first != view.currentPage) return null
        val cellIndex = pos.third * view.currentGridCols + pos.second
        if (cellIndex !in view.homeGridCells.indices) return null
        var cell = android.graphics.RectF(view.homeGridCells[cellIndex])
        if (item.spanX > 1 || item.spanY > 1) {
            val endCol = (pos.second + item.spanX - 1).coerceAtMost(view.currentGridCols - 1)
            val endRow = (pos.third + item.spanY - 1).coerceAtMost(view.currentGridRows - 1)
            val endCellIndex = endRow * view.currentGridCols + endCol
            if (endCellIndex in view.homeGridCells.indices) {
                val endCell = view.homeGridCells[endCellIndex]
                cell = android.graphics.RectF(cell.left, cell.top, endCell.right, endCell.bottom)
            }
        }
        val itemBounds = view.homeScreenRenderer.getIconRect(item, cell)
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val homeOffset = 0f // Home screen is stationary, offset is always 0f
        val cx = itemBounds.left + itemBounds.width() / 2f + loc[0]
        val cy = itemBounds.top + itemBounds.height() / 2f + loc[1] + homeOffset
        
        val canonicalBounds = view.homeScreenRenderer.getIconRect(
            item.copy(spanX = 1, spanY = 1),
            android.graphics.RectF(view.homeGridCells[cellIndex])
        )
        val canonicalIconSize = canonicalBounds.width().toFloat().coerceAtLeast(canonicalBounds.height().toFloat())
        val realIconSize = itemBounds.width().toFloat().coerceAtLeast(itemBounds.height().toFloat())
        
        val config = FolderConfigCodec.parse(item.folderConfigJson)
        val density = view.resources.displayMetrics.density
        
        val left = cx - canonicalIconSize / 2f
        val top = cy - canonicalIconSize / 2f
        val right = cx + canonicalIconSize / 2f
        val bottom = if (view.homeScreenRenderer.showLabels) {
            val extra = if (view.homeScreenRenderer.twoLineLabels) {
                HomeGridAvailableSpace.LABEL_TEXT_SIZE_PX
            } else {
                0f
            }
            cy + canonicalIconSize / 2f + 30f + 10f * density + extra
        } else {
            cy + canonicalIconSize / 2f
        }
        val highlight = FolderScrimHighlight.boundsForConfig(
            left, top, right, bottom,
            config
        )
        return FolderScreenAnchor(cx, cy, canonicalIconSize, realIconSize, highlight)
    }

    /**
     * Screen rect of the tile a folder is about to open from, for a drawer that is not drawn on
     * the canvas (the Categories modes): there is no canvas item to measure. Consumed by the
     * next [drawerFolderAnchorOnScreen] for that folder.
     */
    private var pendingTile: Pair<Long, android.graphics.Rect>? = null

    fun setPendingTileAnchor(folderId: Long, screenRect: android.graphics.Rect) {
        pendingTile = folderId to android.graphics.Rect(screenRect)
    }

    fun drawerFolderAnchorOnScreen(view: LauncherCanvasView, folderId: Long): FolderScreenAnchor? {
        pendingTile?.takeIf { it.first == folderId }?.let { (_, rect) ->
            pendingTile = null
            val size = rect.width().toFloat().coerceAtLeast(rect.height().toFloat())
            val folder = view.homeScreenItems.find { it.id.toLong() == folderId }
            val cfg = folder?.let { FolderConfigCodec.parse(it.folderConfigJson) } ?: FolderConfig()
            val bounds = FolderScrimHighlight.boundsForConfig(
                rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat(), cfg,
            )
            return FolderScreenAnchor(
                rect.exactCenterX(), rect.exactCenterY(),
                canonicalIconSize = size, realIconSize = size, highlightBounds = bounds,
            )
        }
        val gridItem = view.drawerItems.firstOrNull {
            it.intent?.action == "nexus.folder.OPEN" &&
                it.intent.getLongExtra("folderId", -1L) == folderId
        } ?: return null
        val rect = gridItem.drawRect
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val scroll = view.effectiveScrollY()
        val cx = rect.exactCenterX() + loc[0]
        val cy = rect.exactCenterY() - scroll + view.drawerTranslationY + loc[1]
        val iconSize = rect.width().toFloat().coerceAtLeast(rect.height().toFloat())
        val density = view.resources.displayMetrics.density
        val left = rect.left.toFloat() + loc[0]
        val top = rect.top - scroll + view.drawerTranslationY + loc[1]
        val right = rect.right.toFloat() + loc[0]
        val bottom = if (view.showDrawerLabels) {
            val extra = if (view.drawerTwoLineLabels) {
                HomeGridAvailableSpace.LABEL_TEXT_SIZE_PX
            } else {
                0f
            }
            rect.bottom - scroll + view.drawerTranslationY + loc[1] + 30f + 10f * density + extra
        } else {
            rect.bottom - scroll + view.drawerTranslationY + loc[1]
        }
        val folderItem = view.homeScreenItems.find { it.id.toLong() == folderId }
        val config = folderItem?.let { FolderConfigCodec.parse(it.folderConfigJson) } ?: FolderConfig()
        val highlight = FolderScrimHighlight.boundsForConfig(
            left, top, right, bottom,
            config
        )
        return FolderScreenAnchor(cx, cy, canonicalIconSize = iconSize, realIconSize = iconSize, highlight)
    }
}
