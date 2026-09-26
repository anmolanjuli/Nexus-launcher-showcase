package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.model.GridItem

/** Shared drawer-folder hit-testing and coordinate mapping. */
internal object DrawerFolderGesture {

    data class ScreenAnchor(
        val iconX: Float,
        val iconY: Float,
        val iconSize: Float,
        val highlightBounds: FolderScrimHighlight.Bounds?
    )

    fun isDrawerInteractive(view: LauncherCanvasView): Boolean =
        !view.isSearchMode && view.drawerTranslationY < view.viewHeight / 2f

    fun layoutTouchY(view: LauncherCanvasView, touchY: Float): Int =
        (touchY + view.scrollY - view.drawerTranslationY).toInt()

    fun findFolderAt(view: LauncherCanvasView, touchX: Float, touchY: Float): GridItem? {
        if (!isDrawerInteractive(view)) return null
        val xInt = touchX.toInt()
        val layoutY = layoutTouchY(view, touchY)
        return view.drawerItems.firstOrNull {
            it.intent?.action == "nexus.folder.OPEN" && it.hitRect.contains(xInt, layoutY)
        }
    }

    fun screenAnchorForFolder(view: LauncherCanvasView, folderId: Long): ScreenAnchor? {
        val gridItem = view.drawerItems.firstOrNull {
            it.intent?.action == "nexus.folder.OPEN" &&
                it.intent.getLongExtra("folderId", -1L) == folderId
        } ?: return null
        return screenAnchorForItem(view, gridItem, folderId)
    }

    fun screenAnchorForItem(
        view: LauncherCanvasView,
        gridItem: GridItem,
        folderId: Long
    ): ScreenAnchor {
        val itemBounds = gridItem.drawRect
        val canvasLoc = IntArray(2)
        view.getLocationOnScreen(canvasLoc)
        val scrollOffset = view.scrollY
        val iconScreenX = itemBounds.exactCenterX() + canvasLoc[0]
        val iconScreenY = itemBounds.exactCenterY() + canvasLoc[1] - scrollOffset
        val iconSize = itemBounds.width().toFloat().coerceAtLeast(itemBounds.height().toFloat())
        val density = view.resources.displayMetrics.density
        val left = itemBounds.left.toFloat() + canvasLoc[0]
        val top = itemBounds.top - scrollOffset + canvasLoc[1]
        val right = itemBounds.right.toFloat() + canvasLoc[0]
        val bottom = if (view.showDrawerLabels) {
            itemBounds.bottom - scrollOffset + canvasLoc[1] + 30f + 10f * density
        } else {
            itemBounds.bottom - scrollOffset + canvasLoc[1]
        }
        val folderItem = view.homeScreenItems.find { it.id.toLong() == folderId }
        val config = folderItem?.let { FolderConfigCodec.parse(it.folderConfigJson) } ?: FolderConfig()
        val highlight = FolderScrimHighlight.boundsForConfig(
            left, top, right, bottom,
            config
        )
        return ScreenAnchor(iconScreenX, iconScreenY, iconSize, highlight)
    }
}
