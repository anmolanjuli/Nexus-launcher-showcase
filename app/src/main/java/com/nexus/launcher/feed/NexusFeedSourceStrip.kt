package com.nexus.launcher.feed

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.data.FeedSource
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

class NexusFeedSourceStrip(context: Context) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var tokens = ThemeObserver.currentTokens(context)

    private val titleView = TextView(context).apply {
        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
    }

    private val countView = TextView(context).apply {
        NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 9999f
        }
        setPadding((8 * dp).toInt(), (3 * dp).toInt(), (8 * dp).toInt(), (3 * dp).toInt())
    }

    private val headerLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        addView(titleView)
        addView(View(context).apply { layoutParams = LayoutParams((8 * dp).toInt(), 1) })
        addView(countView)
    }

    val horizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), (16 * dp).toInt())
    }

    private val cardsContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
    }

    init {
        orientation = VERTICAL
        horizontalScrollView.addView(cardsContainer)
        addView(headerLayout)
        addView(horizontalScrollView)
    }

    private var lastArticleLinks = listOf<String>()

    fun bind(source: FeedSource, rawArticles: List<FeedArticle>) {
        val articles = rawArticles.distinctBy { if (it.link.isNotBlank()) it.link else it.title }
        titleView.text = source.title
        countView.text = if (articles.isNotEmpty()) context.resources.getQuantityString(com.nexus.launcher.R.plurals.feed_article_count, articles.size, articles.size) else ""
        countView.visibility = if (articles.isNotEmpty()) View.VISIBLE else View.GONE

        val currentLinks = articles.map { it.link.ifBlank { it.title } }
        if (currentLinks == lastArticleLinks && cardsContainer.childCount == articles.size) {
            return
        }
        lastArticleLinks = currentLinks

        cardsContainer.removeAllViews()
        if (articles.isEmpty()) {
            val emptyView = TextView(context).apply {
                text = if (source.lastError != null) context.getString(com.nexus.launcher.R.string.feed_load_failed) else context.getString(com.nexus.launcher.R.string.feed_fetching)
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            }
            cardsContainer.addView(emptyView)
            return
        }

        for (article in articles) {
            val card = NexusFeedCardView(context)
            card.bind(article)
            cardsContainer.addView(card)
        }
    }

    fun applyTokens(newTokens: NexusColorTokens) {
        tokens = newTokens
        NexusTypeScale.bodyStrong.bindTo(titleView, newTokens.textPrimary)
        NexusTypeScale.labelSmall.bindTo(countView, newTokens.textPrimary)
        (countView.background as? android.graphics.drawable.GradientDrawable)?.setColor(newTokens.surfaceRaised)
        for (i in 0 until cardsContainer.childCount) {
            (cardsContainer.getChildAt(i) as? NexusFeedCardView)?.applyTokens(newTokens)
        }
    }
}
