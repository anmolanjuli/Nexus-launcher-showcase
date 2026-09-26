package com.nexus.launcher.ui.widgets

import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.max
import kotlin.math.roundToInt

enum class ResizeEdge { TOP, BOTTOM, LEFT, RIGHT }
enum class ArrowDir { UP, DOWN, LEFT, RIGHT }

class WidgetResizeArrowView(
    context: Context,
    private val widgetView: View,
    private val overlayLayout: WidgetOverlayLayout,
    private val appWidgetId: Int,
    private val cachedColumns: Int,
    private val cachedRows: Int
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private var dropdownOpen = false
    private var selectedSide = ResizeEdge.TOP
    
    private val panelContainer: LinearLayout
    private val sideLabel: TextView
    private val chevronLabel: TextView
    private val dropdownContainer: LinearLayout
    private val arrowsRow: ArrowsView
    
    private var widgetLeft = 0
    private var widgetTop = 0
    private var widgetWidth = 0
    private var widgetHeight = 0

    private val tokens = try {
        com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        com.nexus.launcher.theme.NexusColorTokens.Dark
    }

    init {
        // Frosted like the long-press menu it opens from; a solid surface in Default/Neumorphism.
        panelContainer = com.nexus.launcher.ui.glass.FrostedPanelLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setCornerRadiusPx(16f * density)
            frostWorkspace = true // the real home behind it, like the menu (FrostedPanelLayout)
            applyStyle(tokens, com.nexus.launcher.ui.glass.FrostedPanelLayout.Density.SHEET)
        }
        
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16f * density).toInt(), 0, (16f * density).toInt(), 0)
            setOnClickListener {
                dropdownOpen = !dropdownOpen
                updateDropdownState()
            }
        }
        
        val sideTitle = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.widget_resize_side)
            com.nexus.launcher.typography.NexusTypeScale.caption.bindTo(
                this,
                tokens.textSecondary
            )
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        sideLabel = TextView(context).apply {
            com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f)
        }
        
        chevronLabel = TextView(context).apply {
            com.nexus.launcher.typography.NexusTypeScale.body.bindTo(
                this,
                tokens.textSecondary
            )
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        row1.addView(sideTitle)
        row1.addView(sideLabel)
        row1.addView(chevronLabel)
        
        dropdownContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        
        ResizeEdge.values().forEach { edge ->
            val edgeRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16f * density).toInt(), 0, (16f * density).toInt(), 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44f * density).toInt())
                setOnClickListener {
                    selectedSide = edge
                    dropdownOpen = false
                    updateDropdownState()
                }
            }
            val checkMark = TextView(context).apply {
                text = "✓"
                setTextColor(tokens.textPrimary)
                visibility = View.INVISIBLE
                layoutParams = LinearLayout.LayoutParams((24f * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val nameLabel = TextView(context).apply {
                text = edge.name.lowercase().replaceFirstChar { it.uppercase() }
                com.nexus.launcher.typography.NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            }
            edgeRow.addView(checkMark)
            edgeRow.addView(nameLabel)
            dropdownContainer.addView(edgeRow)
        }
        
        val divider = View(context).apply {
            setBackgroundColor(tokens.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1f * density).toInt())
        }
        
        arrowsRow = ArrowsView(context)
        
        panelContainer.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48f * density).toInt()))
        panelContainer.addView(dropdownContainer)
        panelContainer.addView(divider)
        panelContainer.addView(arrowsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (60f * density).toInt()))
        
        addView(panelContainer, LayoutParams((200f * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        
        updateDropdownState()
    }
    
    private fun updateDropdownState() {
        sideLabel.text = selectedSide.name.lowercase().replaceFirstChar { it.uppercase() }
        chevronLabel.text = if (dropdownOpen) "▴" else "▾"
        dropdownContainer.visibility = if (dropdownOpen) View.VISIBLE else View.GONE
        
        for (i in 0 until dropdownContainer.childCount) {
            val row = dropdownContainer.getChildAt(i) as LinearLayout
            val check = row.getChildAt(0)
            check.visibility = if (ResizeEdge.values()[i] == selectedSide) View.VISIBLE else View.INVISIBLE
        }
        
        arrowsRow.updateSide(selectedSide)
        updateWidgetBounds(widgetLeft, widgetTop, widgetWidth, widgetHeight)
    }

    fun updateWidgetBounds(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int) {
        widgetLeft = newLeft
        widgetTop = newTop
        widgetWidth = newWidth
        widgetHeight = newHeight
        
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val dockReserve = 140f * density
        
        val panelH = if (dropdownOpen) 200f * density else 108f * density
        val spaceBelow = screenH - dockReserve - (widgetTop + widgetHeight)
        
        val topM = if (spaceBelow >= panelH + 12f * density) {
            widgetTop + widgetHeight + 12f * density
        } else {
            widgetTop - panelH - 12f * density
        }
        
        val widgetCenterX = widgetLeft + widgetWidth / 2f
        var leftM = widgetCenterX - 100f * density
        
        leftM = leftM.coerceIn(16f * density, screenW - 216f * density)
        
        val lp = panelContainer.layoutParams as LayoutParams
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
        lp.leftMargin = leftM.toInt()
        lp.topMargin = topM.toInt()
        panelContainer.layoutParams = lp
    }

    fun persistAndExit(
        widgetViewModel: WidgetViewModel,
        appWidgetManager: AppWidgetManager,
        item: HomeScreenItem
    ) {
        WidgetResizeArrowPersist.persistAndExit(
            widgetView = widgetView,
            overlayLayout = overlayLayout,
            appWidgetId = appWidgetId,
            cachedColumns = cachedColumns,
            cachedRows = cachedRows,
            density = density,
            widgetViewModel = widgetViewModel,
            appWidgetManager = appWidgetManager,
            item = item
        )
    }

    private inner class ArrowsView(context: Context) : View(context) {
        private var leftPressed = false
        private var rightPressed = false
        private var currentSide = ResizeEdge.TOP
        
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = tokens.textPrimary
        }
        
        private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = tokens.divider
        }
        
        private var currentAlpha = 1f
        private var alphaAnimator: ValueAnimator? = null
        
        private val repeatRunnable = object : Runnable {
            var count = 0
            var expand = false
            override fun run() {
                fireNudge(expand)
                count++
                val nextDelay = max(20L, 80L - (count * 2L))
                postDelayed(this, nextDelay)
            }
        }
        
        fun updateSide(side: ResizeEdge) {
            currentSide = side
            invalidate()
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            
            canvas.drawLine(0f, 0f, w, 0f, dividerPaint)
            
            val leftCenter = android.graphics.PointF(w/4f, h/2f)
            val rightCenter = android.graphics.PointF(3f*w/4f, h/2f)
            
            val (leftDir, rightDir) = when (currentSide) {
                ResizeEdge.TOP -> Pair(ArrowDir.UP, ArrowDir.DOWN)
                ResizeEdge.BOTTOM -> Pair(ArrowDir.DOWN, ArrowDir.UP)
                ResizeEdge.LEFT -> Pair(ArrowDir.LEFT, ArrowDir.RIGHT)
                ResizeEdge.RIGHT -> Pair(ArrowDir.RIGHT, ArrowDir.LEFT)
            }
            
            arrowPaint.alpha = if (leftPressed) (255 * currentAlpha).toInt() else 255
            drawChevron(canvas, leftCenter, leftDir, 1f, leftPressed)
            
            arrowPaint.alpha = if (rightPressed) (255 * currentAlpha).toInt() else 255
            drawChevron(canvas, rightCenter, rightDir, 1f, rightPressed)
            
            arrowPaint.alpha = 255
        }
        
        private fun drawChevron(canvas: Canvas, center: android.graphics.PointF, dir: ArrowDir, scale: Float, isPressed: Boolean) {
            val path = Path()
            val s = 7f * density * scale
            val off = 3f * density * scale
            
            when (dir) {
                ArrowDir.LEFT -> {
                    path.moveTo(center.x + off, center.y - s)
                    path.lineTo(center.x - off, center.y)
                    path.lineTo(center.x + off, center.y + s)
                }
                ArrowDir.RIGHT -> {
                    path.moveTo(center.x - off, center.y - s)
                    path.lineTo(center.x + off, center.y)
                    path.lineTo(center.x - off, center.y + s)
                }
                ArrowDir.UP -> {
                    path.moveTo(center.x - s, center.y + off)
                    path.lineTo(center.x, center.y - off)
                    path.lineTo(center.x + s, center.y + off)
                }
                ArrowDir.DOWN -> {
                    path.moveTo(center.x - s, center.y - off)
                    path.lineTo(center.x, center.y + off)
                    path.lineTo(center.x + s, center.y - off)
                }
            }
            
            val originalColor = arrowPaint.color
            if (isPressed) {
                arrowPaint.color = Color.WHITE
            }
            canvas.drawPath(path, arrowPaint)
            arrowPaint.color = originalColor
        }

        private fun fireNudge(expand: Boolean) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            WidgetNudgeHelper.handleResizeNudge(
                edge = currentSide,
                sign = if (expand) 1f else -1f,
                widgetView = widgetView,
                overlayLayout = overlayLayout,
                appWidgetId = appWidgetId,
                cachedColumns = cachedColumns,
                cachedRows = cachedRows,
                density = density
            ) { l, t, w, h ->
                updateWidgetBounds(l, t, w, h)
            }
        }

        private fun animateAlpha(from: Float, to: Float) {
            alphaAnimator?.cancel()
            alphaAnimator = ValueAnimator.ofFloat(from, to).apply {
                duration = 80
                interpolator = LinearInterpolator()
                addUpdateListener {
                    currentAlpha = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val isLeft = event.x < width / 2f
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isLeft) leftPressed = true else rightPressed = true
                    val expand = isLeft
                    
                    animateAlpha(1f, 0.6f)
                    
                    fireNudge(expand)
                    repeatRunnable.count = 0
                    repeatRunnable.expand = expand
                    postDelayed(repeatRunnable, 400)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    leftPressed = false
                    rightPressed = false
                    animateAlpha(0.6f, 1f)
                    removeCallbacks(repeatRunnable)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
