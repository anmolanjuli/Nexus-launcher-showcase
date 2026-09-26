package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/** Resolves folder [HomeScreenItem] by id from canvas state. */
object FolderItemLookup {

    /**
     * O(1) via [LauncherCanvasView.folderById]. Never scan [LauncherCanvasView.homeScreenItems]
     * on the hot draw path — that was O(N) per drawer folder per frame.
     */
    fun findOnCanvas(view: LauncherCanvasView, folderId: Long): HomeScreenItem? {
        if (folderId <= 0L) return null
        return view.folderById[folderId]
    }

    fun contentsFor(view: LauncherCanvasView, folderItem: HomeScreenItem): List<HomeScreenItem> =
        view.homeScreenRenderer.folderContentsForItem(folderItem)
}
