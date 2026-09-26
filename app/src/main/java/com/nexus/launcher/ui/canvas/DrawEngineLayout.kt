package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.model.GridItem
import com.nexus.launcher.ui.model.LauncherState

/**
 * Layout measurement for the drawer grid, dock and home grid.
 * Extracted verbatim from [LauncherDrawEngine] — behavior unchanged.
 */
class DrawEngineLayout(private val view: LauncherCanvasView) {

    fun recalculateLayout() {
        if (view.dragHandler.isIconDragActive ||
            (view.isDraggingIcon && view.draggedItem != null)
        ) {
            view.layoutDirty = true
            return
        }
        if (view.isDrawerMotionRunning) {
            view.layoutDirty = true
            return
        }
        view.layoutDirty = false
        if (view.viewWidth > 0 && view.viewHeight > 0) {
            val labelMargin = 16f * view.resources.displayMetrics.density
            val gridBottom = if (view.isSearchMode) {
                view.viewHeight - view.currentOverlayTotalHeight - labelMargin.toInt()
            } else if (view.drawerChrome.bottomReservePx > 0) {
                // Bottom-docked chrome sits above the nav bar, so the grid has to clear both —
                // clearing only the bars left the A–Z rail's last letters behind them.
                view.viewHeight - view.bottomBarHeight - view.drawerChrome.bottomReservePx
            } else {
                view.viewHeight
            }

            // The dock hides while the drawer is open, so the drawer spans the full width even
            // in phone landscape — it does not leave the home grid's dock strip empty.
            // It still keeps clear of the camera cutout / side nav bar (0 outside landscape).
            val drawerAreaWidth = view.viewWidth - SideInsets.left - SideInsets.right
            // In landscape use more columns to fill the wider grid area
            val effectiveColumns = if (view.isLandscape) {
                val portraitCellWidth = view.viewHeight / view.gridRenderer.columnCount
                (drawerAreaWidth / portraitCellWidth).toInt()
                    .coerceIn(view.gridRenderer.columnCount, 10)
            } else {
                view.gridRenderer.columnCount
            }

            val cellWidth = com.nexus.launcher.ui.canvas.GridMetrics.compute(
                availableWidthPx = drawerAreaWidth.toFloat(),
                availableHeightPx = 1f,
                columns = effectiveColumns,
                rows = 1
            ).cellWidthPx
            val effectiveDrawerMultiplier = if (view.isLandscape)
                view.drawerIconSizeMultiplier * 0.7f
            else view.drawerIconSizeMultiplier
            val iconSize = (cellWidth * effectiveDrawerMultiplier).toInt()
            val labelHeight = 40f
            val subtitleHeight = 24f
            val padding = 24f
            val currentCellHeight = (iconSize + labelHeight + subtitleHeight + padding).toInt()
            // drawerChrome.topReservePx is drawer-only extra clearance — added here, NOT
            // folded into view.topInset itself, since topInset is also read directly by the HOME
            // SCREEN's grid layout (calculateHomeGrid below); doing it there previously shrank
            // the home icon grid, which was never intended.
            val drawerTop = view.topInset + view.drawerChrome.topReservePx
            val startY  = drawerTop

            // Room for the A-Z rail (16dp edge margin + 20dp rail). Portrait columns are wide
            // enough that 32dp never clipped an icon; landscape's narrower ones need the full
            // rail width plus a gap.
            val gutter = (if (view.isLandscape) 48f else 32f) * view.resources.displayMetrics.density
            val gridWidth = if (view.railRenderer.isVisible) {
                (drawerAreaWidth - gutter).toInt()
            } else {
                drawerAreaWidth
            }

            val leftOffset = SideInsets.left +
                if (view.isRtl && view.railRenderer.isVisible) gutter.toInt() else 0

            val savedColumns = view.gridRenderer.columnCount
            if (view.isLandscape) view.gridRenderer.setColumns(effectiveColumns)

            view.drawerItems = if (view.gridRenderer.layoutMode == com.nexus.launcher.data.prefs.DrawerLayoutModes.CATEGORIES && !view.isSearchMode) {
                emptyList()
            } else {
                view.gridRenderer.calculateGrid(
                    view.rawDrawerApps, gridWidth, gridBottom,
                    view.topInset, startY, view.isSearchMode,
                    effectiveDrawerMultiplier, leftOffset,
                    density = view.resources.displayMetrics.density,
                    showLabels = view.showDrawerLabels,
                    twoLineLabels = view.drawerTwoLineLabels)
            }

            val dockIconSize = if (!view.isLandscape) {
                ((view.viewWidth / 5) * 0.55f).toInt()
                    .coerceAtMost((56f * view.resources.displayMetrics.density).toInt())
            } else {
                ((view.dockStripWidth) * 0.65f).toInt()
                    .coerceAtMost((48f * view.resources.displayMetrics.density).toInt())
            }

            // List mode is single or two-column; search mode always lays out as grid.
            val scrollColumns = when {
                view.isSearchMode -> effectiveColumns
                view.gridRenderer.layoutMode == "list" || view.gridRenderer.layoutMode == "list_1" -> 1
                view.gridRenderer.layoutMode == "list_2" -> 2
                else -> effectiveColumns
            }
            val totalRowsFloat = Math.ceil(
                view.rawDrawerApps.size.toDouble() / scrollColumns).toFloat()
            val totalContentHeight = totalRowsFloat * view.gridRenderer.cellHeight

            val tempDockReserve = if (view.isLandscape) 0
            else dockIconSize + (8f * view.resources.displayMetrics.density).toInt()

            val availableScrollableHeight = gridBottom - drawerTop - tempDockReserve
            view.maxScrollY = (totalContentHeight - availableScrollableHeight)
                .coerceAtLeast(0f)

            view.drawerGridTop = startY
            view.drawerGridBottom = Math.min(gridBottom.toFloat(), startY + totalContentHeight).toInt()
            // The rail's extent is a property of the drawer, not of how many apps happen to be
            // showing. It used to fall back to drawerGridBottom, which hugs content height — so
            // picking a sparse category collapsed the rail upward and overlapped its letters.
            // The clearance below it is the 56dp the (now-retired) search FAB used to reserve,
            // or the pill's own reserve where that is larger — i.e. whichever chrome the rail
            // actually has to stop above.
            val railClearance = (56f * view.resources.displayMetrics.density).toInt()
                .coerceAtLeast(view.drawerChrome.bottomReservePx)
            view.railBottomY = view.viewHeight - view.bottomBarHeight - railClearance

            if (view.isLandscape) view.gridRenderer.setColumns(savedColumns)

            if (!view.isLandscape) {
                val dockSlots = 5
                val dockCellWidth = view.viewWidth / dockSlots
                val dockTop = view.viewHeight -
                        dockIconSize - (40f * view.resources.displayMetrics.density).toInt()
                view.dockItems = view.rawDockApps.take(dockSlots)
                    .mapIndexed { index, app ->
                        val marginX = (dockCellWidth - dockIconSize) / 2
                        val left = index * dockCellWidth + marginX
                        GridItem(app.label, app.icon, app.intent,
                            android.graphics.Rect(left, dockTop,
                                left + dockIconSize, dockTop + dockIconSize))
                    }
            } else {
                view.dockItems = emptyList()
            }

            if (!view.isDrawerMotionRunning && !view.isDragging) {
                if (view.uiState == LauncherState.DRAWER) {
                    if (view.drawerTranslationY < view.viewHeight / 2f) {
                        view.drawerTranslationY = 0f
                    }
                } else if (view.uiState == LauncherState.HOME && !view.folderOverlayFrozen) {
                    view.drawerTranslationY = view.viewHeight.toFloat()
                    view.scrollY = 0f
                }
            }

            val density = view.resources.displayMetrics.density
            // Prefer real DockLayout top (height + margins + labels); fall back to estimate.
            com.nexus.launcher.ui.dock.DockHomeGridSync.applyReserve(
                view, dockIconSize, density
            )
            // "Effective" = the grid as laid out right now (the landscape grid in phone
            // landscape). Drag highlight, widget edit and hit-testing index homeGridCells with it.
            view.effectiveHomeColumns = view.currentGridCols
            view.effectiveHomeRows = view.currentGridRows

            // In landscape, cols/rows swap for homeGridCells so the grid
            // dimensions match the rotated coordinate space — the swap lives
            // in LauncherCanvasView.currentGridCols/currentGridRows so every
            // consumer indexes homeGridCells with the same dims it was built from
            val homeGridCols = view.currentGridCols
            val homeGridRows = view.currentGridRows

            view.homeGridCells = view.gridRenderer.calculateHomeGrid(
                view.gridAreaWidth, view.viewHeight, view.topInset,
                view.dockBottomReserve,
                homeGridCols,
                homeGridRows,
                paddingLeftRightDp = view.homePaddingLeftRightDp,
                paddingTopBottomDp = view.homePaddingTopBottomDp,
                gapHorizontalDp = view.homeGapHorizontalDp,
                gapVerticalDp = view.homeGapVerticalDp,
                density = view.resources.displayMetrics.density,
                leftOffset = view.gridAreaLeft)

            val fCols = view.currentGridCols
            val fRows = view.currentGridRows
            // Collision-resolving deserialization. Items are placed top-left first; if a grid
            // shrink forces two onto the same cell, the later one is linearly reflowed to the
            // next free cell on the SAME page, so nothing stacks or vanishes on density change.
            val occupiedCells = mutableSetOf<String>()
            val derived = mutableMapOf<Int, Triple<Int, Int, Int>>()
            val pendingItems = mutableListOf<com.nexus.launcher.data.HomeScreenItem>()

            // Sort items so widgets claim their spatial cells first before 1x1 icons
            val sortedItems = view.homeScreenItems.sortedWith(
                compareBy(
                    { it.page },
                    { if (isWidgetType(it.itemType)) 0 else 1 },
                    { it.yFraction },
                    { it.xFraction }
                )
            )

            // PASS 1: Let every item attempt to claim its ideal mathematical cell.
            for (item in sortedItems) {
                if (item.itemType == 3 && item.appWidgetId == -1) continue
                val isWidget = isWidgetType(item.itemType)
                val hasFractions = !(item.xFraction == 0f && item.yFraction == 0f)
                val isFractionBound = (isWidget || item.id == view.draggedItem?.id) && hasFractions
                val widgetRange = if (isWidget && hasFractions) {
                    OverlayFractionGrid.cellRange(view, item)
                } else null
                val (targetCol, targetRow) = when {
                    widgetRange != null -> widgetRange.col0 to widgetRange.row0
                    isFractionBound -> fractionToCell(
                        item.xFraction, item.yFraction, fCols, fRows, view.context, item.spanX, item.spanY
                    )
                    else -> Pair(item.column.coerceIn(0, fCols - 1), item.row.coerceIn(0, fRows - 1))
                }
                val claimSpanX = if (widgetRange != null) widgetRange.col1 - widgetRange.col0 + 1 else item.spanX
                val claimSpanY = if (widgetRange != null) widgetRange.row1 - widgetRange.row0 + 1 else item.spanY

                var canPlace = true
                for (r in 0 until claimSpanY) {
                    for (c in 0 until claimSpanX) {
                        if (occupiedCells.contains("${item.page}_${targetCol + c}_${targetRow + r}")) {
                            canPlace = false
                        }
                    }
                }

                if (canPlace || isWidget) {
                    for (r in 0 until claimSpanY) {
                        for (c in 0 until claimSpanX) {
                            occupiedCells.add("${item.page}_${targetCol + c}_${targetRow + r}")
                        }
                    }
                    derived[item.id] = Triple(item.page, targetCol, targetRow)
                } else {
                    pendingItems.add(item)
                }
            }

            // PASS 2: Find homes for the bumped apps using the Bi-Directional Spillover logic
            for (item in pendingItems) {
                if (item.itemType == 3 && item.appWidgetId == -1) continue
                var (targetCol, targetRow) = if (item.id == view.draggedItem?.id && !(item.xFraction == 0f && item.yFraction == 0f)) {
                    fractionToCell(item.xFraction, item.yFraction, fCols, fRows, view.context, item.spanX, item.spanY)
                } else {
                    Pair(item.column.coerceIn(0, fCols - 1), item.row.coerceIn(0, fRows - 1))
                }
                var targetPage = item.page
                var loopCount = 0
                var cellsCheckedOnCurrentPage = 0
                val cellsPerPage = fCols * fRows

                val isBottomHeavy = item.yFraction >= 0.5f || (item.yFraction == 0f && item.row >= fRows / 2)
                var rowStep = if (isBottomHeavy) -1 else 1

                // Execute the exact same wrap-around / spill-over while loop
                var canPlace = false
                while (!canPlace && loopCount < 1000) {
                    canPlace = true
                    for (r in 0 until item.spanY) {
                        for (c in 0 until item.spanX) {
                            if (occupiedCells.contains("${targetPage}_${targetCol + c}_${targetRow + r}")) {
                                canPlace = false
                            }
                        }
                    }
                    
                    if (!canPlace) {
                        targetCol++
                        if (targetCol >= fCols) {
                            targetCol = 0
                            targetRow += rowStep
                            if (targetRow >= fRows) targetRow = 0
                            else if (targetRow < 0) targetRow = fRows - 1
                        }

                        cellsCheckedOnCurrentPage++
                        if (cellsCheckedOnCurrentPage >= cellsPerPage) {
                            targetPage++
                            targetCol = 0
                            targetRow = fRows - 1
                            rowStep = -1 // Force bottom-up for new spillover pages
                            cellsCheckedOnCurrentPage = 0
                        }
                        loopCount++
                    }
                }

                for (r in 0 until item.spanY) {
                    for (c in 0 until item.spanX) {
                        occupiedCells.add("${targetPage}_${targetCol + c}_${targetRow + r}")
                    }
                }
                derived[item.id] = Triple(targetPage, targetCol, targetRow)
            }
            view.fractionDerivedPositions = derived
            // TEMPORARY — HomeGridPlacementDiag; remove with the diag object.
            HomeGridPlacementDiag.logReflow(
                liveHomeColumns = view.gridRenderer.homeColumns,
                liveHomeRows = view.gridRenderer.homeRows,
                layoutCols = fCols,
                layoutRows = fRows,
                items = view.homeScreenItems,
                derived = derived
            )
            view.updateTotalPages() // recount pages — spillover may have added visual page(s)

            if (view.folderOverlayFrozen) {
                view.scrollY = view.frozenScrollY
                if (com.nexus.launcher.ui.folder.DrawerFolderGesture.isDrawerInteractive(view)) {
                    view.drawerTranslationY = 0f
                }
            }

            view.invalidate()
        }
    }

    companion object {
        fun fractionToCell(
            xF: Float, yF: Float,
            cols: Int, rows: Int, context: android.content.Context,
            spanX: Int = 1, spanY: Int = 1
        ): Pair<Int, Int> = CellFractionConverter.fractionToCell(xF, yF, cols, rows, context, spanX, spanY)

        fun cellToFraction(
            col: Int, row: Int,
            cols: Int, rows: Int, context: android.content.Context,
            spanX: Int = 1, spanY: Int = 1
        ): Pair<Float, Float> = CellFractionConverter.cellToFraction(col, row, cols, rows, context, spanX, spanY)

        private fun isWidgetType(type: Int): Boolean =
            type == 3 || type == com.nexus.launcher.data.HomeItemTypes.MOSAIC || type == com.nexus.launcher.data.HomeItemTypes.SHORTCUT_BOX || type == com.nexus.launcher.data.HomeItemTypes.APP_BOX || type == com.nexus.launcher.data.HomeItemTypes.LIVE_APP_BOX
    }
}