package com.nexus.launcher.feed

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/** Compact headline-only card for the horizontal strip — title plus a source-abbreviation badge
 *  and time, no thumbnail, matching the source/time context the image cards already show. */
class NexusFeedHeadlineStripCardView(context: Context) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var tokens = ThemeObserver.currentTokens(context)
    var onArticleLongPress: ((FeedArticle) -> Unit)? = null

    private val titleView: TextView
    private val monogramView: TextView
    private val metaView: TextView

    init {
        orientation = VERTICAL
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 18f)
        setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())

        titleView = TextView(context).apply {
            NexusFeedEInkStyler.applyHeadline(this, tokens.textPrimary, isEInk)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.15f)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        }
        addView(titleView)

        val metaRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
            }
        }
        monogramView = TextView(context).apply {
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
        metaRow.addView(monogramView)
        metaView = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        metaRow.addView(metaView)
        addView(metaRow)
    }

    fun bind(article: FeedArticle) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 18f)
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)

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
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 18f)
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)
    }
}
