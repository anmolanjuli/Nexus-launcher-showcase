package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenResizeHelper
import com.nexus.launcher.ui.HomeScreenViewModel
import kotlinx.coroutines.launch

/**
 * Corner-drag resize for home-screen app icons (grid pages only).
 * Rectangular spans allowed; grows only into empty cells via [HomeScreenResizeHelper.canOccupy].
 */
class IconResizeOverlayView(
    context: Context,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel,
    private var originalItem: HomeScreenItem,
    private val onExit: () -> Unit
) : View(context) {

    private var currentSpanX = originalItem.spanX.coerceAtLeast(1)
    private var currentSpanY = originalItem.spanY.coerceAtLeast(1)
    private val visualPos = canvasView.fractionDerivedPositions[originalItem.id]
        ?: Triple(originalItem.page, originalItem.column, originalItem.row)
    private var currentCol = visualPos.second
    private var currentRow = visualPos.third

    private enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private var activeHandle: Handle? = null

    private val animLeft = com.nexus.launcher.ui.dock.DockSpringPhysics.Channel(0f)
    private val animTop = com.nexus.launcher.ui.dock.DockSpringPhysics.Channel(0f)
    private val animRight = com.nexus.launcher.ui.dock.DockSpringPhysics.Channel(0f)
    private val animBottom = com.nexus.launcher.ui.dock.DockSpringPhysics.Channel(0f)

    private var isDragging = false
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var startSpanX = 1
    private var startSpanY = 1
    private var startCol = 0
    private var startRow = 0

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val chromeColors = ResizeOverlayChrome.chromeStrokeColors(canvasView.currentThemeTokens)
    private val dashedPaint = ResizeOverlayChrome.createDashedPaint(density, chromeColors.first)
    private val bracketPaint = ResizeOverlayChrome.createBracketPaint(density, chromeColors.second)
    private val pillPaint = ResizeOverlayChrome.createPillPaint()
    private val textPaint = ResizeOverlayChrome.createTextPaint(density)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun cancelAndExit() {
        if (currentSpanX != originalItem.spanX || currentSpanY != originalItem.spanY ||
            currentCol != originalItem.column || currentRow != originalItem.row
        ) {
            updateDraftItem(
                originalItem.column, originalItem.row,
                originalItem.spanX, originalItem.spanY
            )
        }
        onExit()
    }

    private fun getCurrentRect(): android.graphics.Rect {
        val gridCells = canvasView.homeGridCells
        val startCellIndex = currentRow * canvasView.currentGridCols + currentCol
        var cell = gridCells.getOrNull(startCellIndex) ?: RectF()
        if (currentSpanX > 1 || currentSpanY > 1) {
            val endCol = (currentCol + currentSpanX - 1).coerceAtMost(canvasView.currentGridCols - 1)
            val endRow = (currentRow + currentSpanY - 1).coerceAtMost(canvasView.currentGridRows - 1)
            val endCellIndex = endRow * canvasView.currentGridCols + endCol
            if (endCellIndex in gridCells.indices) {
                val endCell = gridCells[endCellIndex]
                cell = RectF(cell.left, cell.top, endCell.right, endCell.bottom)
            }
        }
        val draft = originalItem.copy(
            spanX = currentSpanX, spanY = currentSpanY,
            column = currentCol, row = currentRow
        )
        return canvasView.homeScreenRenderer.getIconRect(draft, cell)
    }

    override fun onDraw(canvas: Canvas) {
        val targetRect = getCurrentRect()
        if (targetRect.isEmpty) return
        if (animLeft.position == 0f && animTop.position == 0f) {
            com.nexus.launcher.ui.dock.DockSpringPhysics.snap(animLeft, targetRect.left.toFloat())
            com.nexus.launcher.ui.dock.DockSpringPhysics.snap(animTop, targetRect.top.toFloat())
            com.nexus.launcher.ui.dock.DockSpringPhysics.snap(animRight, targetRect.right.toFloat())
            com.nexus.launcher.ui.dock.DockSpringPhysics.snap(animBottom, targetRect.bottom.toFloat())
        }
        var stillMoving = false
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(
            animLeft, targetRect.left.toFloat(), false, 1f
        ) || stillMoving
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(
            animTop, targetRect.top.toFloat(), false, 1f
        ) || stillMoving
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(
            animRight, targetRect.right.toFloat(), false, 1f
        ) || stillMoving
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(
            animBottom, targetRect.bottom.toFloat(), false, 1f
        ) || stillMoving
        if (stillMoving) postInvalidateOnAnimation()
        val rectF = RectF(animLeft.position, animTop.position, animRight.position, animBottom.position)
        val corner = minOf(rectF.width(), rectF.height()) * 0.2f
        canvas.drawRoundRect(rectF, corner, corner, dashedPaint)
        ResizeOverlayChrome.drawBrackets(canvas, rectF, density, bracketPaint)
        if (isDragging) {
            ResizeOverlayChrome.drawSpanPill(
                canvas, rectF, currentSpanX, currentSpanY, density, pillPaint, textPaint
            )
        }
    }

    private fun hitTest(x: Float, y: Float): Handle? {
        val rectRaw = getCurrentRect()
        if (rectRaw.isEmpty) return null
        val rectF = RectF(
            rectRaw.left.toFloat(), rectRaw.top.toFloat(),
            rectRaw.right.toFloat(), rectRaw.bottom.toFloat()
        )
        return when (ResizeOverlayChrome.hitTestCorner(x, y, rectF, density)) {
            ResizeOverlayChrome.Corner.TOP_LEFT -> Handle.TOP_LEFT
            ResizeOverlayChrome.Corner.TOP_RIGHT -> Handle.TOP_RIGHT
            ResizeOverlayChrome.Corner.BOTTOM_LEFT -> Handle.BOTTOM_LEFT
            ResizeOverlayChrome.Corner.BOTTOM_RIGHT -> Handle.BOTTOM_RIGHT
            null -> null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = hitTest(event.x, event.y)
                if (activeHandle == null) return false
                isDragging = false
                startTouchX = event.x
                startTouchY = event.y
                startCol = currentCol
                startRow = currentRow
                startSpanX = currentSpanX
                startSpanY = currentSpanY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeHandle == null) return false
                val dx = event.x - startTouchX
                val dy = event.y - startTouchY
                if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    isDragging = true
                    invalidate()
                }
                if (isDragging) applyDrag(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeHandle != null) {
                    if (isDragging) {
                        isDragging = false
                        invalidate()
                        finalizeResize()
                    }
                    activeHandle = null
                    return true
                }
            }
        }
        return false
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val cellW = canvasView.gridAreaWidth / canvasView.currentGridCols.toFloat()
        val cellH = (canvasView.viewHeight - canvasView.topInset -
            canvasView.bottomBarHeight - canvasView.dockBottomReserve) /
            canvasView.currentGridRows.toFloat()
        if (cellW <= 0f || cellH <= 0f) return
        val deltaColX = Math.round(dx / cellW).toInt()
        val deltaColY = Math.round(dy / cellH).toInt()
        var newCol = startCol
        var newRow = startRow
        var newSpanX = startSpanX
        var newSpanY = startSpanY
        val maxSpan = maxOf(canvasView.currentGridCols, canvasView.currentGridRows)
        when (activeHandle) {
            Handle.TOP_LEFT -> {
                newSpanX = (startSpanX - deltaColX).coerceIn(1, maxSpan)
                newSpanY = (startSpanY - deltaColY).coerceIn(1, maxSpan)
                newCol = startCol - (newSpanX - startSpanX)
                newRow = startRow - (newSpanY - startSpanY)
            }
            Handle.BOTTOM_RIGHT -> {
                newSpanX = (startSpanX + deltaColX).coerceIn(1, maxSpan)
                newSpanY = (startSpanY + deltaColY).coerceIn(1, maxSpan)
            }
            Handle.TOP_RIGHT -> {
                newSpanX = (startSpanX + deltaColX).coerceIn(1, maxSpan)
                newSpanY = (startSpanY - deltaColY).coerceIn(1, maxSpan)
                newRow = startRow - (newSpanY - startSpanY)
            }
            Handle.BOTTOM_LEFT -> {
                newSpanX = (startSpanX - deltaColX).coerceIn(1, maxSpan)
                newSpanY = (startSpanY + deltaColY).coerceIn(1, maxSpan)
                newCol = startCol - (newSpanX - startSpanX)
            }
            else -> return
        }
        if (newCol < 0) { newSpanX += newCol; newCol = 0 }
        if (newCol + newSpanX > canvasView.currentGridCols) {
            newSpanX = canvasView.currentGridCols - newCol
        }
        if (newRow < 0) { newSpanY += newRow; newRow = 0 }
        if (newRow + newSpanY > canvasView.currentGridRows) {
            newSpanY = canvasView.currentGridRows - newRow
        }
        if (newSpanX < 1 || newSpanY < 1) return
        if (newCol == currentCol && newRow == currentRow &&
            newSpanX == currentSpanX && newSpanY == currentSpanY
        ) return
        // Grow only into empty neighbors — reject blocked footprints
        val free = HomeScreenResizeHelper.canOccupy(
            context, canvasView.homeScreenItems, originalItem.page,
            newCol, newRow, newSpanX, newSpanY, originalItem.id,
            canvasView.fractionDerivedPositions,
            canvasView.currentGridCols, canvasView.currentGridRows
        )
        if (!free) return
        currentCol = newCol
        currentRow = newRow
        currentSpanX = newSpanX
        currentSpanY = newSpanY
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        updateDraftItem(currentCol, currentRow, currentSpanX, currentSpanY)
        invalidate()
    }

    private fun updateDraftItem(col: Int, row: Int, spanX: Int, spanY: Int) {
        val effCols = canvasView.currentGridCols
        val effRows = canvasView.currentGridRows
        val (newXF, newYF) = if (effCols > 0 && effRows > 0) {
            DrawEngineLayout.cellToFraction(col, row, effCols, effRows, context, spanX, spanY)
        } else Pair(0f, 0f)
        canvasView.draftResizeItem = originalItem.copy(
            spanX = spanX, spanY = spanY, column = col, row = row,
            xFraction = newXF, yFraction = newYF
        )
        canvasView.invalidate()
    }

    private fun finalizeResize() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val success = homeScreenViewModel.attemptResizeIcon(
                originalItem, currentSpanX, currentSpanY, currentCol, currentRow,
                canvasView.currentGridCols, canvasView.currentGridRows,
                canvasView.fractionDerivedPositions
            )
            if (success) {
                originalItem = originalItem.copy(
                    spanX = currentSpanX, spanY = currentSpanY,
                    column = currentCol, row = currentRow
                )
            } else {
                canvasView.draftResizeItem = null
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                currentSpanX = originalItem.spanX
                currentSpanY = originalItem.spanY
                currentCol = originalItem.column
                currentRow = originalItem.row
            }
            canvasView.recalculateLayout()
            invalidate()
        }
    }
}
