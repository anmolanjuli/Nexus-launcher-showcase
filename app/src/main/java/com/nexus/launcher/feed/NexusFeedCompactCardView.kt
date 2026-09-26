package com.nexus.launcher.feed

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NexusFeedCompactCardView(context: Context) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null
    private var tokens = ThemeObserver.currentTokens(context)

    private val titleView = TextView(context).apply {
        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(0f, 1.15f)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
    }

    private val monogramView = TextView(context).apply {
        NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 9999f
        }
        layoutParams = LayoutParams((18 * dp).toInt(), (18 * dp).toInt()).apply {
            marginEnd = (6 * dp).toInt()
        }
    }

    private val metaView = TextView(context).apply {
        NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    private val thumbnailCard = FrameLayout(context).apply {
        layoutParams = LayoutParams((84 * dp).toInt(), (84 * dp).toInt()).apply {
            marginStart = (14 * dp).toInt()
        }
        clipToOutline = true
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 16 * dp
        }
    }

    private val thumbnailImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
    }

    private val textColumn = LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = LayoutParams(0, (84 * dp).toInt(), 1f)
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (12 * dp).toInt()
        }
        setPadding((16 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
        thumbnailCard.background = NexusFeedEInkStyler.createImageContainerBackground(isEInk, isDark, tokens.surfaceRaised, dp, 16f)
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)

        textColumn.addView(titleView)
        val metaRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(monogramView)
            addView(metaView)
        }
        textColumn.addView(metaRow)
        addView(textColumn)

        thumbnailCard.addView(thumbnailImageView)
        addView(thumbnailCard)
    }

    /** Two-row horizontal strip cards share the column height — let the text column grow with it. */
    fun configureForHorizontalStrip() {
        textColumn.layoutParams = (textColumn.layoutParams as LayoutParams).apply {
            height = LayoutParams.MATCH_PARENT
        }
        titleView.maxLines = 4
        val thumbSize = (72 * dp).toInt()
        thumbnailCard.layoutParams = (thumbnailCard.layoutParams as LayoutParams).apply {
            width = thumbSize
            height = thumbSize
        }
        setPadding((12 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
    }

    var onArticleLongPress: ((FeedArticle) -> Unit)? = null

    fun bind(article: FeedArticle) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
        thumbnailCard.background = NexusFeedEInkStyler.createImageContainerBackground(isEInk, isDark, tokens.surfaceRaised, dp, 16f)
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)
        NexusFeedEInkStyler.applyImageGrayscale(thumbnailImageView, isEInk)

        titleView.text = article.title
        monogramView.text = NexusFeedImageLoader.getMonogram(article.sourceName)
        metaView.text = buildMeta(article)

        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            com.nexus.launcher.reader.NexusReaderLauncher.openArticle(context, article)
        }

        setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onArticleLongPress?.invoke(article)
            true
        }

        loadJob?.cancel()
        val key = article.imageUrl ?: article.imageUrlFallback ?: article.link
        val cached = NexusFeedImageLoader.getCached(key)
        if (cached != null) {
            thumbnailImageView.alpha = 1f
            thumbnailImageView.setImageBitmap(com.nexus.launcher.util.BitmapSizeGuard.guard("NexusFeedCompactCardView.cached", cached))
            thumbnailCard.visibility = View.VISIBLE
            return
        }

        thumbnailImageView.alpha = 0f
        loadJob = scope.launch {
            val primary = article.imageUrl
            val fallback = article.imageUrlFallback
            val bmp = (primary?.let { NexusFeedImageLoader.fetchBitmap(it, 300, 300) }
                ?: fallback?.let { NexusFeedImageLoader.fetchBitmap(it, 300, 300) }
                ?: NexusFeedImageLoader.extractOgImage(article.link)?.let { NexusFeedImageLoader.fetchBitmap(it, 300, 300) })

            if (bmp != null) {
                NexusFeedImageLoader.putCached(key, bmp)
                thumbnailImageView.setImageBitmap(com.nexus.launcher.util.BitmapSizeGuard.guard("NexusFeedCompactCardView.fresh", bmp))
                if (NexusFeedEInkCoordinator.shouldReduceMotion(context)) {
                    thumbnailImageView.alpha = 1f
                } else {
                    thumbnailImageView.animate().alpha(1f).setDuration(160).start()
                }
                thumbnailCard.visibility = View.VISIBLE
            } else {
                thumbnailCard.visibility = View.GONE
            }
        }
    }

    private fun buildMeta(article: FeedArticle): String {
        val ago = formatTimeAgo(article.publishedAt)
        return if (ago.isNotBlank()) "${article.sourceName} • $ago" else article.sourceName
    }

    private fun formatTimeAgo(epochMs: Long): String = NexusFeedTimeAgo.format(context, epochMs)

    fun applyTokens(newTokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
        thumbnailCard.background = NexusFeedEInkStyler.createImageContainerBackground(isEInk, isDark, tokens.surfaceRaised, dp, 16f)
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)
        NexusFeedEInkStyler.applyImageGrayscale(thumbnailImageView, isEInk)
    }
}
