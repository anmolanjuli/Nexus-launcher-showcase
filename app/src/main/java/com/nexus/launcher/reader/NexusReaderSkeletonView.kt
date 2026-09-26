package com.nexus.launcher.reader

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.nexus.launcher.feed.NexusFeedEInkCoordinator

/**
 * Skeleton loading placeholder showing pulsing title, image, and paragraph lines during article extraction.
 */
class NexusReaderSkeletonView(context: Context) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var animator: ValueAnimator? = null
    private val placeholderViews = mutableListOf<View>()

    init {
        orientation = VERTICAL
        val pad = (20 * dp).toInt()
        setPadding(pad, pad, pad, pad)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    fun build(surfaceColor: Int, isEInk: Boolean) {
        removeAllViews()
        placeholderViews.clear()

        // Hero image box
        val imageBox = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (200 * dp).toInt()).apply {
                bottomMargin = (24 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(surfaceColor)
                cornerRadius = if (isEInk) 0f else 16 * dp
            }
        }
        addView(imageBox)
        placeholderViews.add(imageBox)

        // Title lines
        val titleLine1 = createBar(ViewGroup.LayoutParams.MATCH_PARENT, (26 * dp).toInt(), (10 * dp).toInt(), surfaceColor, isEInk)
        val titleLine2 = createBar((240 * dp).toInt(), (26 * dp).toInt(), (20 * dp).toInt(), surfaceColor, isEInk)
        addView(titleLine1); placeholderViews.add(titleLine1)
        addView(titleLine2); placeholderViews.add(titleLine2)

        // Meta line
        val metaLine = createBar((140 * dp).toInt(), (14 * dp).toInt(), (28 * dp).toInt(), surfaceColor, isEInk)
        addView(metaLine); placeholderViews.add(metaLine)

        // Body lines
        repeat(8) { i ->
            val w = if (i % 3 == 2) (220 * dp).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
            val bar = createBar(w, (16 * dp).toInt(), (12 * dp).toInt(), surfaceColor, isEInk)
            addView(bar)
            placeholderViews.add(bar)
        }

        startPulse(isEInk)
    }

    private fun createBar(width: Int, height: Int, bottomMarginPx: Int, color: Int, isEInk: Boolean): View {
        return View(context).apply {
            layoutParams = LayoutParams(width, height).apply {
                bottomMargin = bottomMarginPx
            }
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = if (isEInk) 0f else 6 * dp
            }
        }
    }

    private fun startPulse(isEInk: Boolean) {
        animator?.cancel()
        if (isEInk || NexusFeedEInkCoordinator.shouldReduceMotion(context)) {
            placeholderViews.forEach { it.alpha = 0.5f }
            return
        }

        animator = ValueAnimator.ofFloat(0.35f, 0.85f).apply {
            duration = 800L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                placeholderViews.forEach { it.alpha = v }
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }
}
