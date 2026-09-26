package com.nexus.launcher.feed

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.nexus.launcher.data.FeedArticle

/**
 * Manages article rendering, key-based diff/render caching, empty state presentation,
 * and horizontal scroll strip registrations for [NexusFeedPage].
 */
class NexusFeedArticleDisplayManager(
    private val context: Context,
    private val dp: Float,
    private val tabHost: NexusFeedTabContentHost,
    private val emptyView: TextView,
    private val onArticleLongPress: (FeedArticle) -> Unit,
    private val onStripsUpdated: (List<HorizontalScrollView>) -> Unit
) {
    private val lastArticleKeysByTab = mutableMapOf<NexusFeedBottomBar.Tab, List<String>>()
    private val stripsByTab = mutableMapOf<NexusFeedBottomBar.Tab, List<HorizontalScrollView>>()

    fun clearCache() {
        lastArticleKeysByTab.clear()
    }

    fun stripsForTab(tab: NexusFeedBottomBar.Tab): List<HorizontalScrollView> =
        stripsByTab[tab].orEmpty()

    fun setStripsForTab(tab: NexusFeedBottomBar.Tab, strips: List<HorizontalScrollView>, isCurrentTab: Boolean) {
        stripsByTab[tab] = strips
        if (isCurrentTab) onStripsUpdated(strips)
    }

    fun render(
        articles: List<FeedArticle>,
        activeTab: NexusFeedBottomBar.Tab,
        isHeadlineOnly: Boolean,
        emptyText: String
    ) {
        if (activeTab == NexusFeedBottomBar.Tab.SETTINGS || activeTab == NexusFeedBottomBar.Tab.LIBRARY) return
        val container = tabHost.container(activeTab)
        val currentKeys = articles.map { it.link.ifBlank { it.title } } + "_headline_$isHeadlineOnly" + "_tab_$activeTab"
        if (currentKeys == lastArticleKeysByTab[activeTab]) {
            onStripsUpdated(stripsByTab[activeTab].orEmpty())
            return
        }
        lastArticleKeysByTab[activeTab] = currentKeys

        if (articles.isEmpty()) {
            setStripsForTab(activeTab, emptyList(), isCurrentTab = true)
            container.removeAllViews()
            emptyView.visibility = View.VISIBLE
            emptyView.text = emptyText
            (emptyView.parent as? ViewGroup)?.removeView(emptyView)
            container.addView(emptyView)
            return
        }

        emptyView.visibility = View.GONE
        val strips = NexusFeedArticleRenderer.render(
            context = context,
            cardsContainer = container,
            dp = dp,
            articles = articles,
            activeTab = activeTab,
            isHeadlineOnly = isHeadlineOnly,
            onArticleLongPress = onArticleLongPress
        )
        setStripsForTab(activeTab, strips, isCurrentTab = true)
    }
}
