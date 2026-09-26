package com.nexus.launcher.ui.widgets.battery

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
 * Interactive horizontal carousel for selecting Battery widget styles in widget settings.
 * Offers 5 distinct styles with live preview thumbnails, snapping, and instant persistence.
 */
class NexusBatteryStyleCarouselView(
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

    private var cardW = (220 * dp).toInt()
    private val cardH = (110 * dp).toInt()
    private val cardMargin = (8 * dp).toInt()
    private var isUserTouching = false
    private var isProgrammaticScroll = true
    private var hasUserScrolled = false

    private val snapRunnable = Runnable { snapToNearest() }

    private val styles = listOf(
        Pair(NexusBatteryStyleDrawers.STYLE_FLUID, context.getString(R.string.widget_style_nexus)),
        Pair(NexusBatteryStyleDrawers.STYLE_CAPSULE, context.getString(R.string.battery_style_capsule)),
        Pair(NexusBatteryStyleDrawers.STYLE_RADIAL, context.getString(R.string.battery_style_radial)),
        Pair(NexusBatteryStyleDrawers.STYLE_TECH_CELL, context.getString(R.string.battery_style_cell)),
        Pair(NexusBatteryStyleDrawers.STYLE_DASHBOARD, context.getString(R.string.battery_style_dashboard))
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

        styles.forEach { (styleId, label) ->
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
        cardW = minOf((220 * dp).toInt(), (w * 0.65f).toInt())
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
            isProgrammaticScroll = true
            scrollToStyle(selectedStyle, smooth = false)
            applyDynamicFading()
            postDelayed({ isProgrammaticScroll = false }, 150)
        }
    }

    private fun createStyleCard(styleId: Int, label: String): LinearLayout {
        val isSelected = styleId == selectedStyle

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = cardMargin
                marginEnd = cardMargin
            }
            background = cardBackground(isSelected)
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        }

        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cardH)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = label
        }
        previewViews.add(preview)
        renderCardPreview(preview, styleId)
        card.addView(preview)

        val title = TextView(context).apply {
            text = label
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (isSelected) tokens.textPrimary else tokens.textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, (6 * dp).toInt(), 0, (2 * dp).toInt())
        }
        card.addView(title)

        card.setOnClickListener {
            removeCallbacks(snapRunnable)
            isProgrammaticScroll = true
            hasUserScrolled = false
            if (selectedStyle != styleId) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                selectStyle(styleId)
                onStyleSelected(styleId)
            }
            scrollToStyle(styleId, smooth = true)
            postDelayed({ isProgrammaticScroll = false }, 350)
        }

        return card
    }

    private fun renderCardPreview(imageView: ImageView, styleId: Int) {
        val wPx = cardW.coerceAtLeast(1)
        val hPx = cardH.coerceAtLeast(1)
        val minWDp = (wPx / dp).toInt().coerceAtLeast(1)
        val minHDp = (hPx / dp).toInt().coerceAtLeast(1)

        val mockConsumers = NexusBatteryUsageHelper.createMockConsumers(context)
        val mockBattery = BatterySnapshot(
            percent = 82,
            isCharging = true,
            statusText = context.getString(R.string.battery_status_charging),
            topConsumers = mockConsumers
        )
        val previewConfig = latestConfig.copy(clockStyle = styleId)
        val bmp = NexusBatteryRenderer(mockBattery).render(context, wPx, hPx, previewConfig, minWDp, minHDp, -1f)
        imageView.setImageBitmap(bmp)
        com.nexus.launcher.ui.widgets.WidgetGlassPreviewDrawable.bind(imageView, previewConfig, bmp)
    }

    fun updateLiveConfig(config: NexusWidgetConfig.InstanceConfig) {
        latestConfig = config
        previewViews.forEachIndexed { index, preview ->
            val styleId = styles.getOrNull(index)?.first ?: index
            renderCardPreview(preview, styleId)
        }
    }

    fun selectStyle(styleId: Int) {
        selectedStyle = styleId
        styleCards.forEachIndexed { index, card ->
            val id = styles.getOrNull(index)?.first ?: index
            val isSel = id == styleId
            card.background = cardBackground(isSel)
            val titleView = card.getChildAt(1) as? TextView
            titleView?.setTextColor(if (isSel) tokens.textPrimary else tokens.textSecondary)
        }
    }

    fun scrollToStyle(styleId: Int, smooth: Boolean = true) {
        val index = styles.indexOfFirst { it.first == styleId }.takeIf { it >= 0 } ?: 0
        val targetScrollX = index * (cardW + cardMargin * 2)
        if (smooth) smoothScrollTo(targetScrollX, 0) else scrollTo(targetScrollX, 0)
    }

    private fun cardBackground(isSelected: Boolean) = GradientDrawable().apply {
        cornerRadius = 16f * dp
        setColor(tokens.surfaceRaised)
        if (isSelected) {
            setStroke((2 * dp).toInt().coerceAtLeast(2), tokens.accent)
        } else {
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouching = true
                hasUserScrolled = true
                isProgrammaticScroll = false
                removeCallbacks(snapRunnable)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isUserTouching = false
                postDelayed(snapRunnable, 100)
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        applyDynamicFading()

        if (isUserTouching) {
            hasUserScrolled = true
        }

        if (!isUserTouching && !isProgrammaticScroll && hasUserScrolled) {
            removeCallbacks(snapRunnable)
            postDelayed(snapRunnable, 80)
        }
    }

    private fun snapToNearest() {
        if (width <= 0 || isProgrammaticScroll || !hasUserScrolled) return
        val cardInterval = cardW + cardMargin * 2
        val scrollOffset = scrollX
        val rawIndex = (scrollOffset + cardInterval / 2) / cardInterval
        val targetIndex = rawIndex.coerceIn(0, styles.size - 1)

        val targetStyleId = styles[targetIndex].first
        if (selectedStyle != targetStyleId) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            selectStyle(targetStyleId)
            onStyleSelected(targetStyleId)
        }
        hasUserScrolled = false
        scrollToStyle(targetStyleId, smooth = true)
    }

    private fun applyDynamicFading() {
        if (width <= 0) return
        val center = scrollX + width / 2f
        styleCards.forEach { card ->
            val cardCenter = card.left + card.width / 2f
            val dist = abs(center - cardCenter)
            val maxDist = width / 2f
            val fraction = (1f - (dist / maxDist) * 0.4f).coerceIn(0.6f, 1f)
            card.alpha = fraction
            card.scaleX = fraction
            card.scaleY = fraction
        }
    }
}
