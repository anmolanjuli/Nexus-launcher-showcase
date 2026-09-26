package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.ResizeOverlayChrome
import kotlinx.coroutines.launch

class FolderResizeOverlayView(
    context: Context,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel,
    private var originalFolderItem: HomeScreenItem,
    private val onExit: () -> Unit
) : View(context) {

    private var currentSpanX = originalFolderItem.spanX
    private var currentSpanY = originalFolderItem.spanY
    private val visualPos = canvasView.fractionDerivedPositions[originalFolderItem.id] ?: Triple(originalFolderItem.page, originalFolderItem.column, originalFolderItem.row)
    private var currentCol = visualPos.second
    private var currentRow = visualPos.third

    enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
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

    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6111111")
        style = Paint.Style.FILL
    }

    private val textPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * density
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun cancelAndExit() {
        if (currentSpanX != originalFolderItem.spanX || currentSpanY != originalFolderItem.spanY || currentCol != originalFolderItem.column || currentRow != originalFolderItem.row) {
            updateDraftItem(originalFolderItem.column, originalFolderItem.row, originalFolderItem.spanX, originalFolderItem.spanY)
        }
        onExit()
    }
    private fun getCurrentRect(): android.graphics.Rect {
        val gridCells = canvasView.homeGridCells
        val startCellIndex = currentRow * canvasView.currentGridCols + currentCol
        var cell = gridCells.getOrNull(startCellIndex) ?: android.graphics.RectF()

        if (currentSpanX > 1 || currentSpanY > 1) {
            val endCol = (currentCol + currentSpanX - 1).coerceAtMost(canvasView.currentGridCols - 1)
            val endRow = (currentRow + currentSpanY - 1).coerceAtMost(canvasView.currentGridRows - 1)
            val endCellIndex = endRow * canvasView.currentGridCols + endCol
            if (endCellIndex in gridCells.indices) {
                val endCell = gridCells[endCellIndex]
                cell = android.graphics.RectF(cell.left, cell.top, endCell.right, endCell.bottom)
            }
        }
        
        val draftItem = originalFolderItem.copy(spanX = currentSpanX, spanY = currentSpanY, column = currentCol, row = currentRow)
        return canvasView.homeScreenRenderer.getIconRect(draftItem, cell)
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
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(animLeft, targetRect.left.toFloat(), false, 1f) || stillMoving
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(animTop, targetRect.top.toFloat(), false, 1f) || stillMoving
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(animRight, targetRect.right.toFloat(), false, 1f) || stillMoving
        stillMoving = com.nexus.launcher.ui.dock.DockSpringPhysics.step(animBottom, targetRect.bottom.toFloat(), false, 1f) || stillMoving
        
        if (stillMoving) {
            postInvalidateOnAnimation()
        }
        
        val rectF = android.graphics.RectF(animLeft.position, animTop.position, animRight.position, animBottom.position)
        
        val configJson = originalFolderItem.folderConfigJson.ifBlank { "{}" }
        val config = com.nexus.launcher.ui.folder.FolderConfigCodec.parse(configJson)
        val shapeRaw = FolderShapeStyle.normalize(config.shapeStyle)
        val shape = if (shapeRaw == 6) 1 else shapeRaw
        
        val radiusX = rectF.width() / 2f
        val radiusY = rectF.height() / 2f
        val radius = Math.min(radiusX, radiusY)
        val sx = if (config.flipHorizontal) -1f else 1f
        val sy = if (config.flipVertical) -1f else 1f
        if (sx != 1f || sy != 1f) {
            canvas.save()
            canvas.scale(sx, sy, rectF.centerX(), rectF.centerY())
        }
        val shapePath = com.nexus.launcher.ui.folder.FolderIconShapeDraw.getShapePath(rectF, radius, shape)
        canvas.drawPath(shapePath, dashedPaint)
        if (sx != 1f || sy != 1f) canvas.restore()
        com.nexus.launcher.ui.canvas.ResizeOverlayChrome.drawBrackets(canvas, rectF, density, bracketPaint)
        if (isDragging) {
            com.nexus.launcher.ui.canvas.ResizeOverlayChrome.drawSpanPill(
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
                
                if (isDragging) {
                    val cellW = canvasView.gridAreaWidth / canvasView.currentGridCols.toFloat()
                    val cellH = (canvasView.viewHeight - canvasView.topInset - canvasView.bottomBarHeight - canvasView.dockBottomReserve) / canvasView.currentGridRows.toFloat()
                    
                    if (cellW <= 0f || cellH <= 0f) return true

                    val deltaColX = Math.round(dx / cellW).toInt()
                    val deltaColY = Math.round(dy / cellH).toInt()

                    var newCol = startCol
                    var newRow = startRow
                    var newSpanX = startSpanX
                    var newSpanY = startSpanY

                    when (activeHandle) {
                        Handle.TOP_LEFT -> {
                            val dxSpan = -deltaColX
                            val dySpan = -deltaColY
                            newSpanX = (startSpanX + dxSpan).coerceIn(1, 3)
                            newSpanY = (startSpanY + dySpan).coerceIn(1, 3)
                            newCol = startCol - (newSpanX - startSpanX)
                            newRow = startRow - (newSpanY - startSpanY)
                        }
                        Handle.BOTTOM_RIGHT -> {
                            val dxSpan = deltaColX
                            val dySpan = deltaColY
                            newSpanX = (startSpanX + dxSpan).coerceIn(1, 3)
                            newSpanY = (startSpanY + dySpan).coerceIn(1, 3)
                        }
                        Handle.TOP_RIGHT -> {
                            val dxSpan = deltaColX
                            val dySpan = -deltaColY
                            newSpanX = (startSpanX + dxSpan).coerceIn(1, 3)
                            newSpanY = (startSpanY + dySpan).coerceIn(1, 3)
                            newRow = startRow - (newSpanY - startSpanY)
                        }
                        Handle.BOTTOM_LEFT -> {
                            val dxSpan = -deltaColX
                            val dySpan = deltaColY
                            newSpanX = (startSpanX + dxSpan).coerceIn(1, 3)
                            newSpanY = (startSpanY + dySpan).coerceIn(1, 3)
                            newCol = startCol - (newSpanX - startSpanX)
                        }
                        else -> {}
                    }

                    if (newCol < 0) {
                        newSpanX += newCol
                        newCol = 0
                    }
                    if (newCol + newSpanX > canvasView.currentGridCols) {
                        newSpanX = canvasView.currentGridCols - newCol
                    }
                    if (newRow < 0) {
                        newSpanY += newRow
                        newRow = 0
                    }
                    if (newRow + newSpanY > canvasView.currentGridRows) {
                        newSpanY = canvasView.currentGridRows - newRow
                    }

                    val finalSpan = minOf(newSpanX, newSpanY, 3).coerceAtLeast(1)
                    
                    when (activeHandle) {
                        Handle.TOP_LEFT -> {
                            newCol = startCol - (finalSpan - startSpanX)
                            newRow = startRow - (finalSpan - startSpanY)
                        }
                        Handle.BOTTOM_RIGHT -> {
                            newCol = startCol
                            newRow = startRow
                        }
                        Handle.TOP_RIGHT -> {
                            newCol = startCol
                            newRow = startRow - (finalSpan - startSpanY)
                        }
                        Handle.BOTTOM_LEFT -> {
                            newCol = startCol - (finalSpan - startSpanX)
                            newRow = startRow
                        }
                        else -> {}
                    }

                    if (newCol < 0 || newRow < 0 || newCol + finalSpan > canvasView.currentGridCols || newRow + finalSpan > canvasView.currentGridRows) {
                    } else if (finalSpan != currentSpanX || finalSpan != currentSpanY || newCol != currentCol || newRow != currentRow) {
                        currentSpanX = finalSpan
                        currentSpanY = finalSpan
                        currentCol = newCol
                        currentRow = newRow
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        updateDraftItem(currentCol, currentRow, currentSpanX, currentSpanY)
                    }
                    invalidate()
                }
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

    private fun updateDraftItem(col: Int, row: Int, spanX: Int, spanY: Int) {
        val effCols = canvasView.currentGridCols
        val effRows = canvasView.currentGridRows
        val (newXF, newYF) = if (effCols > 0 && effRows > 0) {
            com.nexus.launcher.ui.canvas.DrawEngineLayout.cellToFraction(col, row, effCols, effRows, canvasView.context, spanX, spanY)
        } else {
            Pair(0f, 0f)
        }

        canvasView.draftResizeItem = originalFolderItem.copy(
            spanX = spanX, 
            spanY = spanY, 
            column = col, 
            row = row,
            xFraction = newXF,
            yFraction = newYF
        )
        canvasView.invalidate()
    }

    private fun finalizeResize() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val success = homeScreenViewModel.attemptResizeFolder(
                originalFolderItem, currentSpanX, currentSpanY, currentCol, currentRow,
                canvasView.currentGridCols, canvasView.currentGridRows,
                canvasView.fractionDerivedPositions
            )
            if (success) {
                originalFolderItem = originalFolderItem.copy(spanX = currentSpanX, spanY = currentSpanY, column = currentCol, row = currentRow)
            } else {
                canvasView.draftResizeItem = null
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                currentSpanX = originalFolderItem.spanX
                currentSpanY = originalFolderItem.spanY
                currentCol = originalFolderItem.column
                currentRow = originalFolderItem.row
            }
            canvasView.recalculateLayout()
            invalidate()
        }
    }
}
