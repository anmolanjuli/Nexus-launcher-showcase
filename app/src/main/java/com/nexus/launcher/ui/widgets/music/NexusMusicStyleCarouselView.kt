package com.nexus.launcher.ui.widgets.music

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
 * Interactive horizontal carousel for selecting Music widget styles in widget settings.
 * Offers Modern, Winamp, Amplifier, Terminal, and Cassette styles with live previews and page snapping.
 */
class NexusMusicStyleCarouselView(
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
    private var isProgrammaticScroll = true
    private var hasUserScrolled = false

    private val snapRunnable = Runnable { snapToNearest() }

    private val styles = listOf(
        Pair(RetroMusicConfig.STYLE_MODERN, context.getString(R.string.widget_style_nexus)),
        Pair(RetroMusicConfig.STYLE_WINAMP, context.getString(R.string.music_style_winamp)),
        Pair(RetroMusicConfig.STYLE_AMPLIFIER, context.getString(R.string.music_style_amplifier)),
        Pair(RetroMusicConfig.STYLE_TERMINAL, context.getString(R.string.music_style_terminal)),
        Pair(RetroMusicConfig.STYLE_CASSETTE, context.getString(R.string.music_style_cassette))
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
        addView(cardsContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))

        post {
            scrollToStyle(selectedStyle, smooth = false)
            postDelayed({ isProgrammaticScroll = false }, 200)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return

        val sidePadding = ((w - cardW) / 2).coerceAtLeast(0)
        startSpacer.layoutParams = LinearLayout.LayoutParams(sidePadding, 1)
        endSpacer.layoutParams = LinearLayout.LayoutParams(sidePadding, 1)

        post {
            scrollToStyle(selectedStyle, smooth = false)
            isProgrammaticScroll = false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouching = true
                isProgrammaticScroll = false
                hasUserScrolled = false
                removeCallbacks(snapRunnable)
            }
            MotionEvent.ACTION_MOVE -> {
                hasUserScrolled = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isUserTouching = false
                postDelayed(snapRunnable, 60)
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isUserTouching && !isProgrammaticScroll && hasUserScrolled) {
            removeCallbacks(snapRunnable)
            postDelayed(snapRunnable, 80)
        }
    }

    private fun snapToNearest() {
        if (width <= 0) return
        val viewportCenter = scrollX + width / 2

        var closestIndex = 0
        var minDistance = Int.MAX_VALUE

        for (i in styleCards.indices) {
            val card = styleCards[i]
            val cardCenter = card.left + card.width / 2
            val dist = abs(viewportCenter - cardCenter)
            if (dist < minDistance) {
                minDistance = dist
                closestIndex = i
            }
        }

        val targetStyle = styles[closestIndex].first
        if (targetStyle != selectedStyle) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            selectStyle(targetStyle)
            onStyleSelected(targetStyle)
        }
        scrollToStyle(targetStyle, smooth = true)
    }

    private fun createStyleCard(styleId: Int, label: String): LinearLayout {
        val isSelected = styleId == selectedStyle
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val lp = LinearLayout.LayoutParams(cardW, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = cardMargin
                rightMargin = cardMargin
            }
            layoutParams = lp
            background = cardBackground(isSelected)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }

        val preview = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(cardW - (24 * dp).toInt(), cardH).apply {
                bottomMargin = (8 * dp).toInt()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = label
        }
        previewViews.add(preview)
        renderCardPreview(preview, styleId)
        card.addView(preview)

        val titleView = TextView(context).apply {
            text = label
            textSize = 13f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(if (isSelected) tokens.textPrimary else tokens.textSecondary)
            gravity = Gravity.CENTER
        }
        card.addView(titleView)

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

        val previewConfig = latestConfig.copy(clockStyle = styleId)
        val bmp = NexusMusicRenderer().render(context, wPx, hPx, previewConfig, minWDp, minHDp, 0.45f)
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

    private fun scrollToStyle(styleId: Int, smooth: Boolean) {
        val index = styles.indexOfFirst { it.first == styleId }.coerceAtLeast(0)
        val card = styleCards.getOrNull(index) ?: return
        val cardCenter = card.left + card.width / 2
        val targetScrollX = cardCenter - width / 2
        if (smooth) {
            smoothScrollTo(targetScrollX, 0)
        } else {
            scrollTo(targetScrollX, 0)
        }
    }

    private fun cardBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 16f * dp
            setColor(tokens.surfaceRaised)
            if (selected) {
                setStroke((2 * dp).toInt().coerceAtLeast(2), tokens.accent)
            } else {
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }
}
