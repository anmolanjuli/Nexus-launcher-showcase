package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import com.nexus.launcher.ui.folder.FolderDragPreviewRenderer

/** STEP 7–8 drag shadows — extracted to keep [LauncherDrawEngine] under 400 lines. */
object DrawEngineDragShadow {

    /** Scratch rect — this path runs every frame of a drag. */
    private val targetRect = android.graphics.RectF()

    /**
     * Last cell the occupancy test was run for. `findItemAtCell` scans `homeScreenItems`, which
     * is far too expensive to repeat on every frame when the answer only changes when the target
     * cell does.
     */
    private var lastOccupancyKey = Long.MIN_VALUE
    private var lastOccupancyFree = true

    /**
     * `LauncherDrawEngine` reaches [drawGridHighlight] from two places, and both run in the same
     * frame while a drawer-sourced selection is active. Drawing twice is only overdraw, but
     * *stepping* the springs twice would double their rate, so advancement is gated on the draw
     * pass's own timestamp.
     */
    private var lastSteppedFrame = Long.MIN_VALUE
    private var springStepAllowed = false

    fun drawDrawerDrag(canvas: Canvas, view: LauncherCanvasView) {
        if (view.hoveredMergeTarget != null) return
        if (!view.dragHandler.isIconDragActive) return
        val folderId = FolderDragPreviewRenderer.folderIdFromDrag(view)
        if (folderId != null) {
            FolderDragPreviewRenderer.draw(
                canvas, view, folderId, view.dragHandler.dragX, view.dragHandler.dragY
            )
        } else {
            val iconSizePx = view.viewWidth / 5 * 0.6f
            view.dragRenderer.drawDragShadow(
                canvas, view.dragHandler.dragItem?.icon,
                view.dragHandler.dragX, view.dragHandler.dragY, iconSizePx
            )
        }
    }

    fun drawHomeDrag(canvas: Canvas, view: LauncherCanvasView) {
        if (view.hoveredMergeTarget != null) return
        if (!view.isDraggingIcon || view.draggedItem == null) return
        if (view.multiDragHandler.isActive) {
            HomeScreenBatchDragRenderer.drawFlyingIcons(canvas, view)
            return
        }
        val item = view.draggedItem ?: return
        if (item.itemType == 1) {
            FolderDragPreviewRenderer.draw(canvas, view, item.id.toLong(), view.dragX, view.dragY)
            return
        }
        if (item.itemType != 0) return
        var iconDrawable = view.homeScreenRenderer.iconCache[item.packageName]
        if (iconDrawable == null) {
            iconDrawable = com.nexus.launcher.ui.dock.DockLayout
                .findFrom(view)?.cachedIconFor(item.packageName)
        }
        if (iconDrawable == null) return
        canvas.save()
        canvas.translate(view.dragX, view.dragY)
        canvas.scale(1.1f, 1.1f)
        val density = view.resources.displayMetrics.density
        val iconSize = (56f * density).toInt()
        val half = iconSize / 2
        val previousAlpha = iconDrawable.alpha
        iconDrawable.alpha = 255
        iconDrawable.setBounds(-half, -half, half, half)
        iconDrawable.draw(canvas)
        iconDrawable.alpha = previousAlpha
        canvas.restore()
    }

    /**
     * Centre of a widget (or large folder) being dragged in widget edit mode, page-local; null
     * when none is. Those drags draw their own cell outline on the widget overlay, but the dot
     * field lives here on the canvas, so the overlay reports the drag position through this.
     */
    @Volatile var widgetDragPoint: android.graphics.PointF? = null
    @Volatile var widgetDragBounds: android.graphics.RectF? = null
    private val tempContentBox = android.graphics.RectF()

    fun drawGridHighlight(canvas: Canvas, view: LauncherCanvasView) {
        val widgetDrag = widgetDragPoint
        val isHomeIconDrag = view.draggedItem != null && view.isDraggingIcon
        // External drags (App Drawer, search overlay) carry their finger position on DragHandler,
        // not on the view's home-icon drag fields — they get the same target-cell highlight.
        val isExternalDrag = !isHomeIconDrag && view.dragHandler.isIconDragActive &&
            view.dragHandler.dragItem != null && view.uiState == com.nexus.launcher.ui.model.LauncherState.HOME
        val dragInFlight = isHomeIconDrag || isExternalDrag || widgetDrag != null

        // The grid is only a landing surface while the drop would actually go to a cell — not
        // while hovering the dock, and not while hovering an icon that would merge into a folder.
        val overGrid = dragInFlight &&
            view.hoveredMergeTarget == null &&
            !view.dragHandler.isHoveringDock &&
            !com.nexus.launcher.ui.dock.DockLayoutRenderer.isInternalDrag

        if (!dragInFlight) {
            // Nothing in flight — unarm so the next drag snaps to its first cell rather than
            // gliding in from wherever the previous one ended.
            view.dragRenderer.gridHighlight.reset()
            lastOccupancyKey = Long.MIN_VALUE
        }

        val rawFingerX = when {
            isHomeIconDrag -> view.dragX
            widgetDrag != null -> widgetDrag.x
            else -> view.dragHandler.dragX
        }
        val rawFingerY = when {
            isHomeIconDrag -> view.dragY
            widgetDrag != null -> widgetDrag.y
            else -> view.dragHandler.dragY
        }
        val isCardTrack = SelectionModeTransform.isCardTrackActive(view)
        val (fingerX, fingerY) = if (isCardTrack && widgetDrag == null) {
            SelectionModeCardTrack.mapTouchToPage(view, rawFingerX, rawFingerY, view.currentPage)
        } else {
            rawFingerX to rawFingerY
        }

        // Stepped unconditionally, and before the early-out below, so the field can still fade
        // out on the frames after the finger has lifted — by then nothing else is asking for
        // frames, so it has to keep requesting its own.
        val frame = view.drawingTime
        springStepAllowed = frame != lastSteppedFrame
        if (springStepAllowed) lastSteppedFrame = frame

        val field = view.dragRenderer.gridField
        if (springStepAllowed && field.step(overGrid)) view.postInvalidateOnAnimation()
        if (!field.isVisible && !overGrid) return

        val contentBox = when {
            widgetDragBounds != null -> widgetDragBounds
            isHomeIconDrag -> {
                val item = view.draggedItem
                val cell = view.homeGridCells.firstOrNull()
                val cellW = cell?.width() ?: (60f * view.resources.displayMetrics.density)
                val cellH = cell?.height() ?: (60f * view.resources.displayMetrics.density)
                val spanX = item?.spanX ?: 1
                val spanY = item?.spanY ?: 1
                tempContentBox.set(
                    fingerX - spanX * cellW / 2f,
                    fingerY - spanY * cellH / 2f,
                    fingerX + spanX * cellW / 2f,
                    fingerY + spanY * cellH / 2f
                )
                tempContentBox
            }
            view.dragHandler.isIconDragActive -> {
                val cell = view.homeGridCells.firstOrNull()
                val cellW = cell?.width() ?: (60f * view.resources.displayMetrics.density)
                val cellH = cell?.height() ?: (60f * view.resources.displayMetrics.density)
                tempContentBox.set(
                    fingerX - cellW / 2f,
                    fingerY - cellH / 2f,
                    fingerX + cellW / 2f,
                    fingerY + cellH / 2f
                )
                tempContentBox
            }
            else -> null
        }

        val draw = {
            field.draw(canvas, view, fingerX, fingerY, contentBox)
            if (overGrid) {
                if (isHomeIconDrag && view.multiDragHandler.isActive) {
                    HomeScreenBatchDragRenderer.drawHighlights(canvas, view)
                } else if (isHomeIconDrag) {
                    val item = view.draggedItem
                    drawSingleCellHighlight(
                        canvas, view, view.dragX, view.dragY,
                        item?.spanX ?: 1, item?.spanY ?: 1, listOfNotNull(item?.id)
                    )
                } else if (widgetDrag == null) {
                    drawSingleCellHighlight(
                        canvas, view, view.dragHandler.dragX, view.dragHandler.dragY,
                        1, 1, emptyList()
                    )
                }
            }
        }
        if (SelectionModeTransform.isCardTrackActive(view)) {
            SelectionModeCardTrack.withCurrentPageCanvas(view, canvas, draw)
        } else {
            draw()
        }
    }

    private fun drawSingleCellHighlight(
        canvas: Canvas,
        view: LauncherCanvasView,
        fingerX: Float,
        fingerY: Float,
        draggedSpanX: Int,
        draggedSpanY: Int,
        excludeIds: List<Int>
    ) {
        val targetCell = CanvasHitTestHelper.getCellAtDrop(view, fingerX, fingerY) ?: return
        val rawCol = targetCell.first - (draggedSpanX - 1) / 2f
        val rawRow = targetCell.second - (draggedSpanY - 1) / 2f
        val clampedCol = Math.round(rawCol).coerceIn(
            0, (view.effectiveHomeColumns - draggedSpanX).coerceAtLeast(0)
        )
        val clampedRow = Math.round(rawRow).coerceIn(
            0, (view.effectiveHomeRows - draggedSpanY).coerceAtLeast(0)
        )
        val cellIndex = clampedRow * view.effectiveHomeColumns + clampedCol
        val cellIndexRect = view.homeGridCells.getOrNull(cellIndex) ?: return
        targetRect.set(cellIndexRect)
        if (draggedSpanX > 1 || draggedSpanY > 1) {
            val endCol = clampedCol + draggedSpanX - 1
            val endRow = clampedRow + draggedSpanY - 1
            val endCellIndex = endRow * view.effectiveHomeColumns + endCol
            if (endCellIndex in view.homeGridCells.indices) {
                val endRect = view.homeGridCells[endCellIndex]
                targetRect.set(targetRect.left, targetRect.top, endRect.right, endRect.bottom)
            }
        }

        // Occupancy scans homeScreenItems, so only re-run it when the answer can have changed.
        // Page is part of the key: an edge-scroll advance mid-drag lands the same cell on a
        // different page, where the occupant is not the same.
        val occupancyKey = (view.currentPage.toLong() shl 40) or
            (clampedRow.toLong() shl 24) or
            (clampedCol.toLong() shl 8) or
            ((draggedSpanX * 16 + draggedSpanY).toLong() and 0xFF)
        if (occupancyKey != lastOccupancyKey) {
            lastOccupancyKey = occupancyKey
            lastOccupancyFree = GridOccupancyHelper.findItemAtCell(
                view.context, view.homeScreenItems, view.currentPage,
                clampedCol, clampedRow, view.fractionDerivedPositions,
                excludeIds, draggedSpanX, draggedSpanY
            ) == null
        }

        val padding = 4f * view.resources.displayMetrics.density
        targetRect.set(
            targetRect.left + padding,
            targetRect.top + padding,
            targetRect.right - padding,
            targetRect.bottom - padding
        )

        // Glide toward the target instead of cutting to it. While the spring is still moving the
        // finger may already have stopped, so keep frames coming until it settles.
        val highlight = view.dragRenderer.gridHighlight
        if (springStepAllowed && highlight.step(targetRect)) {
            view.postInvalidateOnAnimation()
        }
        view.dragRenderer.drawCellHighlight(canvas, highlight.rect, lastOccupancyFree)
    }
}
