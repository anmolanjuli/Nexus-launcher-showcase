package com.nexus.launcher.ui.canvas

import android.graphics.Canvas

/** Home-page icon pass for [LauncherDrawEngine]. */
object DrawEngineHomeIcons {
    fun drawPage(
        view: LauncherCanvasView,
        targetCanvas: Canvas,
        pageIndex: Int,
        drawHomeAlpha: Int,
        bounceScale: Float
    ) {
        val itemsToRender = if (view.draftResizeItem != null) {
            view.homeScreenItems.map { if (it.id == view.draftResizeItem?.id) view.draftResizeItem!! else it }
        } else {
            view.homeScreenItems
        }

        view.homeScreenRenderer.draw(
            targetCanvas, itemsToRender,
            view.homeGridCells,
            view.currentGridCols, view.currentGridRows,
            drawHomeAlpha,
            pageIndex,
            view.fractionDerivedPositions,
            labelColor = view.homeLabelColor,
            labelBold = view.homeLabelBold,
            badgeCounts = view.badgeCounts,
            badgeStyle = view.badgeStyle,
            badgeRenderer = view.badgeRenderer,
            draggedItemId = view.draggedItem?.id,
            draggedDisplayItem = view.dragHandler.dragItem,
            isDraggingIcon = view.isDraggingIcon || view.dragHandler.isIconDragActive,
            hoveredMergeTargetId = view.hoveredMergeTarget?.id,
            hiddenFolderItemId = view.openFolderItemId,
            folderMorphProgress = view.folderMorphProgress,
            folderGlowId = view.folderGlowManager.folderGlowId,
            folderGlowColor = view.folderGlowManager.folderGlowColor,
            folderGlowAlpha = view.folderGlowManager.folderGlowAlpha,
            mergeBounceScale = bounceScale,
            draftResizeItemId = view.draftResizeItem?.id,
            ghostedItemIds = if (view.multiDragHandler.isActive && view.isDraggingIcon) {
                view.multiDragHandler.memberIds()
            } else {
                emptySet()
            }
        )
    }
}
