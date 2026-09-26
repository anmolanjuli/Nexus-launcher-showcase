package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.ui.canvas.DrawerRailRenderer
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.model.DisplayItem

class StripAlphabetRail(context: Context) : View(context) {
    var onLetter: ((Char) -> Unit)? = null
    var canvasView: LauncherCanvasView? = null
    private val density = resources.displayMetrics.density
    private val renderer = DrawerRailRenderer(density)
    private val palette = CategoryPalette(context)
    private var sources: List<DisplayItem> = emptyList()
    private var tracking = false
    private val hitZonePx = TOUCH_DP * density
    private val loc = IntArray(2)

    init {
        setWillNotDraw(false)
        renderer.setAccentColor(palette.textPrimary)
        renderer.setIdleColor(palette.textSecondary)
        renderer.setBubbleTextColor(palette.base)
        renderer.onActiveLetterChanged = { letter ->
            if (letter != null) onLetter?.invoke(letter)
        }
    }

    fun bindSources(labels: List<String>) {
        sources = labels.map { DisplayItem(it, null, null) }
        invalidate()
    }

    fun isInHitZone(localX: Float): Boolean = localX >= width - hitZonePx

    /**
     * The rail spans the drawer's whole width even though its letters live at the right edge:
     * the bubble follows the finger inward, and a view only as wide as the letters clipped it
     * away. Touches outside the letters' lane are refused ([isInHitZone]), so everything under
     * it still works.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentH = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        val parentW = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        setMeasuredDimension(parentW, parentH)
    }

    override fun onDraw(canvas: Canvas) {
        val top = letterTop()
        val bottom = letterBottom()
        renderer.drawRail(canvas, width, height, top, bottom, sources, context)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isInHitZone(event.x)) return false
                val top = letterTop()
                val bottom = letterBottom()
                if (event.y < top || event.y > bottom) return false
                tracking = true
                parent?.requestDisallowInterceptTouchEvent(true)
                scrub(event, top, bottom)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                scrub(event, letterTop(), letterBottom())
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return false
                tracking = false
                renderer.releaseSpring(width - hitZonePx, this)
                renderer.updateTouchY(null, this)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return tracking
    }

    private fun scrub(event: MotionEvent, top: Int, bottom: Int) {
        renderer.updateTouchY(event.y, this)
        renderer.handleRailTouch(
            this, event.y, height, top, bottom, 0f, sources, 1f, 1,
        )
        renderer.updateFingerPosition(event.x, event.y, width - hitZonePx)
        postInvalidateOnAnimation()
    }

    private fun letterTop(): Int {
        getLocationOnScreen(loc)
        val canvas = canvasView
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val topScreen = when {
            canvas != null && (canvas.isLandscape || canvas.railTopY <= 0) -> loc[1]
            canvas != null -> canvas.railTopY
            landscape -> loc[1]
            else -> loc[1] + height / 2
        }
        return (topScreen - loc[1]).coerceIn(0, height)
    }

    private fun letterBottom(): Int {
        getLocationOnScreen(loc)
        val canvas = canvasView
        val bottomScreen = if (canvas != null && canvas.railBottomY > 0) {
            canvas.railBottomY
        } else {
            loc[1] + height
        }
        val top = letterTop()
        return (bottomScreen - loc[1]).coerceIn(top + 1, height)
    }

    companion object {
        const val TOUCH_DP = 44f
        private const val BUBBLE_DP = 48f
        private const val BUBBLE_GAP_DP = 8f

        fun endParams(): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END,
            )
        }
    }
}
