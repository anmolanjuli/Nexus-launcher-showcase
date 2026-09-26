package com.nexus.launcher.ui.canvas

import android.view.MotionEvent

class RadialMenuTouchController(
    private val itemCount: Int,
    private val density: Float,
    private val totalSweepAngle: Float = 360f,
    private val startAngle: Float = 0f,
    private val baseR: Float,
    private val onHoverChanged: (Int) -> Unit,
    private val onActionSelected: (Int) -> Unit,
    private val onDismiss: () -> Unit
) {
    var startX: Float = 0f
    var startY: Float = 0f
    private var touchDownX: Float = 0f
    private var touchDownY: Float = 0f

    private var activeIndex = -1
    private var isInitialGesture = true
    private var hasDraggedOut = false

    fun initialize(anchorX: Float, anchorY: Float, rawTouchX: Float = anchorX, rawTouchY: Float = anchorY) {
        startX = anchorX
        startY = anchorY
        touchDownX = rawTouchX
        touchDownY = rawTouchY
        activeIndex = -1
        isInitialGesture = true
        hasDraggedOut = false
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        val dx = x - startX
        val dy = y - startY
        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        if (isInitialGesture) {
            val dragDx = x - touchDownX
            val dragDy = y - touchDownY
            val dragDistance = Math.sqrt((dragDx * dragDx + dragDy * dragDy).toDouble()).toFloat()
            if (dragDistance > 15f * density) {
                hasDraggedOut = true
            }
        } else {
            hasDraggedOut = true
        }

        val hoverInner = baseR - 45f * density
        val hoverOuter = baseR + 55f * density
        val dismissOuter = baseR + 65f * density

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (distance > dismissOuter) {
                onDismiss()
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (hasDraggedOut && distance >= hoverInner && distance <= hoverOuter) {
                    val targetIndex = RadialGeometryCalculator.getTargetSliceIndex(dx, dy, density, itemCount, totalSweepAngle, startAngle)
                    setHoverIndex(targetIndex)
                } else {
                    setHoverIndex(-1)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (distance > dismissOuter) {
                    setHoverIndex(-1)
                    return true
                }
                
                if (hasDraggedOut && distance >= hoverInner && distance <= hoverOuter) {
                    val targetIndex = RadialGeometryCalculator.getTargetSliceIndex(dx, dy, density, itemCount, totalSweepAngle, startAngle)
                    setHoverIndex(targetIndex)
                } else {
                    setHoverIndex(-1)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isInitialGesture = false
                if (activeIndex != -1) {
                    onActionSelected(activeIndex)
                }
                setHoverIndex(-1)
            }
        }
        return true
    }

    private fun setHoverIndex(index: Int) {
        if (activeIndex != index) {
            activeIndex = index
            onHoverChanged(activeIndex)
        }
    }
    
    fun getActiveIndex(): Int = activeIndex

    /** Drops the hovered slice while a touch belongs to something drawn over the ring. */
    fun clearHover() = setHoverIndex(-1)

    /** Ends a gesture the ring never saw the release of, as its own ACTION_UP would. */
    fun releaseGesture() {
        isInitialGesture = false
        setHoverIndex(-1)
    }
}
