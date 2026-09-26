package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import kotlin.math.abs

/**
 * Interactive horizontal carousel for selecting clock styles in the widget settings sheet.
 * Features page-fling snapping, zero accent color (monochromatic tokens), and instant tap selection.
 */
class NexusClockStyleCarouselView(
    context: Context,
    private val tokens: NexusColorTokens,
    private val currentStyle: Int,
    private var latestConfig: NexusWidgetConfig.InstanceConfig,
    private val onStyleSelected: (Int) -> Unit
) : HorizontalScrollView(context) {

    private val dp = resources.displayMetrics.density
    private var selectedStyle = currentStyle
    private val cardsContainer: LinearLayout
    private val styleCards = mutableListOf<LinearLayout>()
    private val previewViews = mutableListOf<ImageView>()

    private val startSpacer: View
    private val endSpacer: View

    private var cardW = (230 * dp).toInt()
    private val cardH = (110 * dp).toInt()
    private val cardMargin = (8 * dp).toInt()
    private var isUserTouching = false

    private val snapRunnable = Runnable { snapToNearest() }

    private val styles = listOf(
        Pair(NexusClockRenderer.STYLE_GREETING_ANALOG, context.getString(R.string.widget_style_nexus)),
        Pair(NexusClockRenderer.STYLE_RETRO_LCD, context.getString(R.string.clock_style_retro_lcd)),
        Pair(NexusClockRenderer.STYLE_MINIMAL_BAUHAUS, context.getString(R.string.clock_style_minimal_bauhaus)),
        Pair(NexusClockRenderer.STYLE_BOLD_TYPOGRAPHY, context.getString(R.string.clock_style_bold_typography)),
        Pair(NexusClockRenderer.STYLE_LACROSSE_LCD, context.getString(R.string.clock_style_lacrosse_lcd))
    )

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        clipChildren = false
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (12 * dp).toInt()
        }

        cardsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, (4 * dp).toInt(), 0, (8 * dp).toInt())
        }

        startSpacer = View(context)
        endSpacer = View(context)

        cardsContainer.addView(startSpacer, LinearLayout.LayoutParams(0, 1))

        styles.forEachIndexed { _, (styleId, label) ->
            val card = createStyleCard(styleId, label)
            styleCards.add(card)
            cardsContainer.addView(card)
        }

        cardsContainer.addView(endSpacer, LinearLayout.LayoutParams(0, 1))

        addView(cardsContainer)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        cardW = minOf((230 * dp).toInt(), (w * 0.70f).toInt())
        val sideSpacerWidth = ((w - cardW) / 2 - cardMargin).coerceAtLeast(0)

        val startLp = startSpacer.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(sideSpacerWidth, 1)
        startLp.width = sideSpacerWidth
        startSpacer.layoutParams = startLp

        val endLp = endSpacer.layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(sideSpacerWidth, 1)
        endLp.width = sideSpacerWidth
        endSpacer.layoutParams = endLp

        styleCards.forEach { card ->
            val lp = card.layoutParams as? LinearLayout.LayoutParams
            if (lp != null && lp.width != cardW) {
                lp.width = cardW
                card.layoutParams = lp
            }
        }

        post {
            scrollToStyle(selectedStyle, smooth = false)
            applyDynamicFading()
        }
    }

    private fun createStyleCard(styleId: Int, label: String): LinearLayout {
        val isSelected = styleId == selectedStyle

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(cardMargin, 0, cardMargin, 0)
            }
            background = cardBgDrawable(isSelected)
            val p = (8 * dp).toInt()
            setPadding(p, p, p, (6 * dp).toInt())
            isClickable = true
            isFocusable = true
        }

        val preview = ImageView(context).apply {
            val pw = (cardW - (16 * dp).toInt()).coerceAtLeast(1)
            layoutParams = LinearLayout.LayoutParams(pw, cardH)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = true
            isFocusable = false
            renderPreviewInto(this, styleId)
        }
        previewViews.add(preview)

        val labelView = TextView(context).apply {
            text = label
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (isSelected) tokens.textPrimary else tokens.textSecondary)
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            }
        }

        val clickListener = OnClickListener {
            selectStyle(styleId)
        }
        card.setOnClickListener(clickListener)
        preview.setOnClickListener(clickListener)
        labelView.setOnClickListener(clickListener)

        card.addView(preview)
        card.addView(labelView)

        return card
    }

    private fun renderPreviewInto(view: ImageView, styleId: Int) {
        val pw = (cardW - (16 * dp).toInt()).coerceAtLeast(1)
        val cfg = latestConfig.copy(clockStyle = styleId)
        val bmp = NexusClockRenderer().render(
            context,
            pw,
            cardH,
            cfg,
            (cardW / dp).toInt(),
            (cardH / dp).toInt(),
            -1f
        )
        view.setImageBitmap(bmp)
        com.nexus.launcher.ui.widgets.WidgetGlassPreviewDrawable.bind(view, cfg, bmp)
    }

    fun updateLiveConfig(config: NexusWidgetConfig.InstanceConfig) {
        latestConfig = config
        styles.forEachIndexed { index, (styleId, _) ->
            previewViews.getOrNull(index)?.let { preview ->
                renderPreviewInto(preview, styleId)
            }
        }
        applyDynamicFading()
    }

    private fun cardBgDrawable(isSelected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 16f * dp
            setColor(tokens.surface)
            if (isSelected) {
                setStroke((2f * dp).toInt().coerceAtLeast(1), tokens.textPrimary)
            } else {
                setStroke((1f * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        applyDynamicFading()
        if (!isUserTouching) {
            removeCallbacks(snapRunnable)
            postDelayed(snapRunnable, 30)
        }
    }

    private fun applyDynamicFading() {
        if (width <= 0 || styleCards.isEmpty()) return
        val viewportCenter = scrollX + width / 2
        val maxScroll = (cardsContainer.width - width).coerceAtLeast(0)

        var closestIndex = when {
            scrollX <= (12 * dp).toInt() -> 0
            scrollX >= maxScroll - (12 * dp).toInt() -> styles.size - 1
            else -> {
                var bestIdx = 0
                var minD = Float.MAX_VALUE
                styleCards.forEachIndexed { i, card ->
                    val cardCenter = card.left + card.width / 2
                    val dist = abs(cardCenter - viewportCenter).toFloat()
                    if (dist < minD) {
                        minD = dist
                        bestIdx = i
                    }
                }
                bestIdx
            }
        }

        styleCards.forEachIndexed { i, card ->
            val cardCenter = card.left + card.width / 2
            val dist = abs(cardCenter - viewportCenter).toFloat()
            val maxDist = (cardW + (16 * dp)).coerceAtLeast(1f)
            val fraction = (dist / maxDist).coerceIn(0f, 1f)

            val scale = 1.04f - (fraction * 0.14f)
            val alpha = 1.0f - (fraction * 0.55f)

            card.scaleX = scale
            card.scaleY = scale
            card.alpha = alpha

            val isCurrent = i == closestIndex || styles[i].first == selectedStyle
            card.background = cardBgDrawable(isCurrent)

            val labelView = card.getChildAt(1) as? TextView
            labelView?.setTextColor(if (isCurrent) tokens.textPrimary else tokens.textSecondary)
        }

        val nearestStyleId = styles[closestIndex].first
        if (selectedStyle != nearestStyleId && isUserTouching) {
            selectedStyle = nearestStyleId
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onStyleSelected(selectedStyle)
        }
    }

    override fun fling(velocityX: Int) {
        if (width <= 0 || styleCards.isEmpty()) {
            super.fling(velocityX)
            return
        }
        val viewportCenter = scrollX + width / 2
        var currentIndex = 0
        var minDistance = Float.MAX_VALUE

        styleCards.forEachIndexed { i, card ->
            val cardCenter = card.left + card.width / 2
            val dist = abs(cardCenter - viewportCenter).toFloat()
            if (dist < minDistance) {
                minDistance = dist
                currentIndex = i
            }
        }

        val targetIndex = when {
            velocityX > 200 -> (currentIndex + 1).coerceAtMost(styles.size - 1)
            velocityX < -200 -> (currentIndex - 1).coerceAtLeast(0)
            else -> currentIndex
        }
        selectStyle(styles[targetIndex].first)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouching = true
                removeCallbacks(snapRunnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isUserTouching = false
                removeCallbacks(snapRunnable)
                postDelayed(snapRunnable, 20)
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun snapToNearest() {
        if (width <= 0 || styleCards.isEmpty()) return
        val viewportCenter = scrollX + width / 2
        val maxScroll = (cardsContainer.width - width).coerceAtLeast(0)

        val closestIndex = when {
            scrollX <= (12 * dp).toInt() -> 0
            scrollX >= maxScroll - (12 * dp).toInt() -> styles.size - 1
            else -> {
                var bestIdx = 0
                var minD = Float.MAX_VALUE
                styleCards.forEachIndexed { i, card ->
                    val cardCenter = card.left + card.width / 2
                    val dist = abs(cardCenter - viewportCenter).toFloat()
                    if (dist < minD) {
                        minD = dist
                        bestIdx = i
                    }
                }
                bestIdx
            }
        }

        val targetStyle = styles[closestIndex].first
        val targetCard = styleCards[closestIndex]
        val targetScrollX = (targetCard.left + targetCard.width / 2) - width / 2

        smoothScrollTo(targetScrollX.coerceAtLeast(0), 0)

        if (selectedStyle != targetStyle) {
            selectedStyle = targetStyle
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onStyleSelected(selectedStyle)
        }
    }

    fun selectStyle(styleId: Int) {
        selectedStyle = styleId
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        scrollToStyle(styleId, smooth = true)
        onStyleSelected(styleId)
    }

    private fun scrollToStyle(styleId: Int, smooth: Boolean) {
        val index = styles.indexOfFirst { it.first == styleId }.takeIf { it >= 0 } ?: 0
        val card = styleCards.getOrNull(index) ?: return
        val targetScrollX = ((card.left + card.width / 2) - width / 2).coerceAtLeast(0)
        if (smooth) {
            smoothScrollTo(targetScrollX, 0)
        } else {
            scrollTo(targetScrollX, 0)
        }
    }
}
