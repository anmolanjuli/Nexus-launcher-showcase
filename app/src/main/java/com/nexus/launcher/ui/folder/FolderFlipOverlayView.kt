package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlinx.coroutines.launch

class FolderFlipOverlayView(
    context: Context,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel,
    private var originalFolderItem: HomeScreenItem,
    private val onExit: () -> Unit
) : View(context) {

    var onConfigChanged: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var startTouchX = 0f
    private var startTouchY = 0f

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EB0D1117")
        style = Paint.Style.FILL
    }

    private val pillBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x99, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0xCC, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arrowPath = android.graphics.Path()

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun getHighlightBounds(): com.nexus.launcher.ui.folder.FolderScrimHighlight.Bounds {
        val rect = getIconRect()
        val rectF = android.graphics.RectF(rect)
        
        val cx = rectF.centerX()
        val cy = rectF.centerY()
        val size = Math.min(rectF.width(), rectF.height())
        var halfW = size / 2f
        var halfH = size / 2f
        
        if (originalFolderItem.spanX == 1 && originalFolderItem.spanY == 1) {
            val scale = 2.0f
            halfW *= scale
            halfH *= scale
        }
        rectF.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)

        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val config = com.nexus.launcher.ui.folder.FolderConfigCodec.parse(originalFolderItem.folderConfigJson)
        val shapeStyle = com.nexus.launcher.ui.folder.FolderScrimHighlight.shapeStyle(config)
        
        return com.nexus.launcher.ui.folder.FolderScrimHighlight.Bounds(
            left = rectF.left + loc[0],
            top = rectF.top + loc[1],
            right = rectF.right + loc[0],
            bottom = rectF.bottom + loc[1],
            shapeStyle = shapeStyle,
            flipHorizontal = config.flipHorizontal,
            flipVertical = config.flipVertical,
            isGeneric = true
        )
    }

    private fun getIconRect(): android.graphics.Rect {
        val gridCells = canvasView.homeGridCells
        val visualPos = canvasView.fractionDerivedPositions[originalFolderItem.id] ?: Triple(originalFolderItem.page, originalFolderItem.column, originalFolderItem.row)
        val effCol = visualPos.second
        val effRow = visualPos.third
        val cellIndex = effRow * canvasView.currentGridCols + effCol
        var cell = gridCells.getOrNull(cellIndex) ?: android.graphics.RectF()

        if (originalFolderItem.spanX > 1 || originalFolderItem.spanY > 1) {
            val endCol = effCol + originalFolderItem.spanX - 1
            val endRow = effRow + originalFolderItem.spanY - 1
            val endCellIndex = endRow * canvasView.currentGridCols + endCol
            val endCell = gridCells.getOrNull(endCellIndex)
            if (endCell != null) {
                cell = android.graphics.RectF(cell.left, cell.top, endCell.right, endCell.bottom)
            }
        }
        return canvasView.homeScreenRenderer.getIconRect(originalFolderItem, cell)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus() // Fix for back-button focus bypass
                startTouchX = event.x
                startTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startTouchX
                val dy = event.y - startTouchY
                if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    handleTap(event.x, event.y)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getPillRects(rectF: android.graphics.RectF): Pair<android.graphics.RectF, android.graphics.RectF> {
        val gap = 32f * density
        val pillW = 88f * density
        val pillH = 40f * density
        
        // Horizontal Pill
        val hCx = rectF.centerX()
        var hCy = rectF.top - gap - pillH / 2f
        val safeTop = canvasView.topInset.toFloat()
        if (hCy - pillH / 2f < safeTop) {
            hCy = rectF.bottom + gap + pillH / 2f
        }
        val hRect = android.graphics.RectF(hCx - pillW / 2f, hCy - pillH / 2f, hCx + pillW / 2f, hCy + pillH / 2f)
        
        // Vertical Pill
        val vCxDefault = rectF.right + gap + pillH / 2f
        val vCx = if (vCxDefault + pillH / 2f > width) rectF.left - gap - pillH / 2f else vCxDefault
        val vCy = rectF.centerY()
        val vRect = android.graphics.RectF(vCx - pillH / 2f, vCy - pillW / 2f, vCx + pillH / 2f, vCy + pillW / 2f)
        
        return Pair(hRect, vRect)
    }

    private fun handleTap(x: Float, y: Float) {
        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val rect = getIconRect()
        val rectF = android.graphics.RectF(rect)
        rectF.offset(loc[0].toFloat(), loc[1].toFloat())
        
        val (hRect, vRect) = getPillRects(rectF)
        
        val touchPadding = 12f * density
        val hHit = android.graphics.RectF(hRect).apply { inset(-touchPadding, -touchPadding) }
        val vHit = android.graphics.RectF(vRect).apply { inset(-touchPadding, -touchPadding) }
        
        if (vHit.contains(x, y)) {
            toggleFlip(vertical = true)
            return
        }
        
        if (hHit.contains(x, y)) {
            toggleFlip(vertical = false)
            return
        }

        // Tap outside exits
        onExit()
    }

    private fun toggleFlip(vertical: Boolean) {
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        val configJson = originalFolderItem.folderConfigJson.ifBlank { "{}" }
        val config = FolderConfigCodec.parse(configJson)
        
        val newConfig = if (vertical) {
            config.copy(flipVertical = !config.flipVertical)
        } else {
            config.copy(flipHorizontal = !config.flipHorizontal)
        }
        
        val newJson = FolderConfigCodec.toJson(newConfig)
        
        // Update live data
        val updatedItem = originalFolderItem.copy(folderConfigJson = newJson)
        originalFolderItem = updatedItem
        
        val list = canvasView.homeScreenItems.toMutableList()
        val index = list.indexOfFirst { it.id == updatedItem.id }
        if (index >= 0) {
            list[index] = updatedItem
        }
        canvasView.homeScreenItems = list
        
        // Evict cache to redraw correctly
        canvasView.homeScreenRenderer.folderIconRenderer.evictConfig(updatedItem.id)
        
        canvasView.invalidate()
        invalidate()
        onConfigChanged?.invoke()
        
        // Persist
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.nexus.launcher.di.DaoEntryPoint::class.java
            )
            val dao = entryPoint.homeScreenDao()
            val title = updatedItem.folderTitle ?: ""
            FolderActionEngine.saveFolderConfig(updatedItem.id.toLong(), title, newConfig, dao)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val loc = IntArray(2)
        canvasView.getLocationOnScreen(loc)
        val offsetX = loc[0].toFloat()
        val offsetY = loc[1].toFloat()

        val rect = getIconRect()
        val rectF = android.graphics.RectF(rect)
        
        val cx = rectF.centerX()
        val cy = rectF.centerY()
        val size = Math.min(rectF.width(), rectF.height())
        val half = size / 2f
        rectF.set(cx - half, cy - half, cx + half, cy + half)
        
        rectF.offset(offsetX, offsetY)

        val isSmallFolder = originalFolderItem.spanX == 1 && originalFolderItem.spanY == 1
        if (isSmallFolder) {
            canvas.save()
            val scale = 2.0f
            canvas.scale(scale, scale, rectF.centerX(), rectF.centerY())
        }

        val contents = homeScreenViewModel.folderContents.value[originalFolderItem.resolveFolderContentsId()] ?: emptyList()
        
        canvasView.homeScreenRenderer.folderIconRenderer.drawFolder(
            canvas = canvas,
            cx = rectF.centerX(),
            cy = rectF.centerY(),
            folderItem = originalFolderItem,
            contents = contents,
            iconCache = canvasView.homeScreenRenderer.iconCache,
            density = density,
            folderRadiusOverride = Math.min(rect.width(), rect.height()) / 2f,
            folderBoundsOverride = rectF
        )

        if (isSmallFolder) {
            canvas.restore()
        }

        val (hRect, vRect) = getPillRects(rectF)
        val cornerRadius = 20f * density
        val btnOffset = 24f * density
        
        // Draw Horizontal Pill
        canvas.drawRoundRect(hRect, cornerRadius, cornerRadius, pillBgPaint)
        canvas.drawRoundRect(hRect, cornerRadius, cornerRadius, pillBorderPaint)
        
        // Draw left arrow
        drawCircularButton(canvas, hRect.centerX() - btnOffset, hRect.centerY(), 90f)
        // Draw right arrow
        drawCircularButton(canvas, hRect.centerX() + btnOffset, hRect.centerY(), 270f)
        
        // Draw Vertical Pill
        canvas.drawRoundRect(vRect, cornerRadius, cornerRadius, pillBgPaint)
        canvas.drawRoundRect(vRect, cornerRadius, cornerRadius, pillBorderPaint)
        
        // Draw top arrow
        drawCircularButton(canvas, vRect.centerX(), vRect.centerY() - btnOffset, 180f)
        // Draw bottom arrow
        drawCircularButton(canvas, vRect.centerX(), vRect.centerY() + btnOffset, 0f)
    }

    private fun drawCircularButton(canvas: Canvas, cx: Float, cy: Float, rotation: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation)
        
        val radius = 16f * density
        canvas.drawCircle(0f, 0f, radius, buttonBgPaint)
        
        val size = 4.5f * density
        arrowPath.reset()
        arrowPath.moveTo(-size, -size)
        arrowPath.lineTo(0f, size)
        arrowPath.lineTo(size, -size)
        
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
    }
}
