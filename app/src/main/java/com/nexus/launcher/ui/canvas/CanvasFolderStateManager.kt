package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.HomeScreenItem

class CanvasFolderStateManager(private val canvasView: LauncherCanvasView) {
    var folderOverlayFrozen = false
    var frozenScrollY = 0f
    var openFolderItemId: Int? = null
    var folderMorphProgress: Float = 0f
    var isFolderClosing = false

    fun setFolderOpenState(itemId: Int?, morphProgress: Float) {
        openFolderItemId = itemId
        folderMorphProgress = morphProgress.coerceIn(0f, 1f)
        canvasView.invalidate()
    }

    fun effectiveScrollY(currentScrollY: Float): Float {
        return if (folderOverlayFrozen) frozenScrollY else currentScrollY
    }
}
