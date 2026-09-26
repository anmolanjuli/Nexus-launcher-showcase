package com.nexus.launcher.ui.folder

import android.graphics.Canvas

/** App-count badge drawn in the scrim punch-out while a folder is open. */
object FolderScrimOpenBadge {

    fun draw(
        canvas: Canvas,
        bounds: FolderScrimHighlight.Bounds,
        appCount: Int,
        folderId: Int,
        density: Float
    ) {
        FolderScrimSpotDraw.drawOpenCount(canvas, bounds, appCount, folderId, density)
    }
}
