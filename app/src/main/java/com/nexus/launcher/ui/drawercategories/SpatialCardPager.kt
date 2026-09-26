package com.nexus.launcher.ui.drawercategories

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class SpatialCardPager(
    context: Context,
    private val categories: List<CategoryGroup>,
    private val colorEditable: Boolean = true,
) : FrameLayout(context) {
    var onPageChanged: ((Int) -> Unit)? = null
    var onColorClick: ((Int) -> Unit)? = null
    var onAppClick: ((CategoryApp) -> Unit)? = null
    var onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null
    private val palette = CategoryPalette(context)
    private val cards: List<SpatialCategoryCard>
    private val detector: GestureDetector
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private var position = if (categories.size > 1) 1f else 0f
    private var downX = 0f
    private var downY = 0f
    private var intercepting = false
    private var flung = false
    private var animator: ValueAnimator? = null

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        clipChildren = false
        clipToPadding = false
        clipToOutline = false
        val density = resources.displayMetrics.density
        val padV = (12f * density).toInt()
        setPadding(0, padV, 0, padV)
        cards = categories.mapIndexed { index, category ->
            SpatialCategoryCard(context).also { card ->
                card.clipToOutline = false
                card.setLayerType(LAYER_TYPE_HARDWARE, null)
                card.colorEditable = colorEditable
                card.onColorClick = if (colorEditable) {
                    { onColorClick?.invoke(index) }
                } else {
                    null
                }
                card.onAppClick = { app -> onAppClick?.invoke(app) }
                card.onAppLongPress = { app, view -> onAppLongPress?.invoke(app, view) }
                card.bind(category, palette)
                addView(card)
            }
        }
        detector = GestureDetector(context, GestureListener())
        detector.setIsLongpressEnabled(false)
        isClickable = true
    }

    fun pageCount(): Int = cards.size

    fun currentPage(): Int = position.roundToInt().coerceIn(0, cards.lastIndex.coerceAtLeast(0))

    fun rebind(index: Int) {
        val card = cards.getOrNull(index) ?: return
        val category = categories.getOrNull(index) ?: return
        card.bind(category, palette)
    }

    fun setPage(index: Int) {
        position = index.toFloat().coerceIn(0f, cards.lastIndex.toFloat().coerceAtLeast(0f))
        requestLayout()
        onPageChanged?.invoke(currentPage())
    }

    fun snapTo(index: Int) {
        animateTo(index.toFloat().coerceIn(0f, cards.lastIndex.toFloat().coerceAtLeast(0f)))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val height = MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1)
        val cardW = (width * CARD_WIDTH_FRACTION).toInt().coerceAtLeast(1)
        val cardH = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val childW = MeasureSpec.makeMeasureSpec(cardW, MeasureSpec.EXACTLY)
        val childH = MeasureSpec.makeMeasureSpec(cardH, MeasureSpec.EXACTLY)
        cards.forEach { it.measure(childW, childH) }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val cardW = cards.firstOrNull()?.measuredWidth ?: 0
        val cardH = cards.firstOrNull()?.measuredHeight ?: 0
        val x = (width - cardW) / 2
        val y = paddingTop
        cards.forEach { card ->
            card.layout(x, y, x + cardW, y + cardH)
        }
        val camera = height * CAMERA_HEIGHT_FACTOR
        cards.forEach { it.cameraDistance = camera }
        applyTransforms()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                intercepting = false
                flung = false
                animator?.cancel()
                detector.onTouchEvent(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - downX)
                val dy = abs(ev.y - downY)
                if (dx > touchSlop && dx > dy) {
                    intercepting = true
                    parent.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!flung) settle(0f)
                intercepting = false
                flung = false
            }
        }
        return handled || intercepting || event.actionMasked == MotionEvent.ACTION_DOWN
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun settle(velocityX: Float) {
        val max = cards.lastIndex.toFloat().coerceAtLeast(0f)
        val target = when {
            velocityX > minFling -> ceil(position - 0.05f)
            velocityX < -minFling -> floor(position + 0.05f)
            else -> position.roundToInt().toFloat()
        }.coerceIn(0f, max)
        animateTo(target)
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        val start = position
        if (abs(target - start) < 0.001f) {
            position = target
            applyTransforms()
            onPageChanged?.invoke(currentPage())
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val f = animator.animatedValue as Float
                position = start + (target - start) * f
                applyTransforms()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onPageChanged?.invoke(currentPage())
                }
            })
            start()
        }
    }

    private fun applyTransforms() {
        if (width == 0 || cards.isEmpty()) return
        val density = resources.displayMetrics.density
        cards.forEachIndexed { index, card ->
            val offset = index - position
            val absOff = abs(offset)
            if (absOff > 1.55f) {
                card.visibility = INVISIBLE
                return@forEachIndexed
            }
            val t = absOff.coerceAtMost(1f)
            val scaleX = 1f - 0.28f * t
            card.visibility = VISIBLE
            card.pivotX = card.width * 0.5f
            card.pivotY = card.height * 0.5f
            card.rotationY = -offset * SIDE_ROTATION_DEG
            card.scaleX = scaleX
            card.scaleY = 1f - 0.34f * t
            val abut = (card.width / 2f) * (1f + scaleX)
            card.translationX = kotlin.math.sign(offset) * abut * t
            card.alpha = 1f
            card.translationZ = (1f - t) * 20f * density
            card.elevation = (1f - t) * 10f * density
        }
        cards.mapIndexed { index, card -> card to abs(index - position) }
            .sortedByDescending { it.second }
            .forEach { (card, _) -> bringChildToFront(card) }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float,
        ): Boolean {
            val w = width.coerceAtLeast(1).toFloat()
            position = (position + distanceX / w).coerceIn(0f, cards.lastIndex.toFloat())
            applyTransforms()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            flung = true
            settle(-velocityX)
            return true
        }
    }

    companion object {
        private const val CARD_WIDTH_FRACTION = 0.84f
        private const val SIDE_ROTATION_DEG = 22f
        private const val CAMERA_HEIGHT_FACTOR = 8f
    }
}
