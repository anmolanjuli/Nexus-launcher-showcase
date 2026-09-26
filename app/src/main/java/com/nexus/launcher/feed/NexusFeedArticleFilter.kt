package com.nexus.launcher.feed

import android.content.Context
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.data.FeedSource
import java.util.Locale

/** Helper for filtering, categorizing, and sorting feed articles across tabs. */
object NexusFeedArticleFilter {

    data class FilterResult(
        val counts: Map<String, Int>,
        val filteredArticles: List<FeedArticle>
    )

    fun filter(
        context: Context,
        rawList: List<FeedArticle>,
        currentSources: List<FeedSource>,
        activeTab: NexusFeedBottomBar.Tab,
        searchQuery: String,
        selectedCategory: String,
        hiddenLinks: Set<String>
    ): FilterResult {
        val feedWindowStart = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        val counts = mutableMapOf<String, Int>()
        val sourceCatMap = currentSources.associate { it.id to it.category.uppercase(Locale.ROOT) }
        var totalCount = 0

        for (art in rawList) {
            if (hiddenLinks.contains(art.link)) continue
            if (activeTab == NexusFeedBottomBar.Tab.FEED && searchQuery.isBlank() && art.publishedAt < feedWindowStart) continue
            totalCount++
            val cat = sourceCatMap[art.sourceId] ?: "OTHER"
            counts[cat] = (counts[cat] ?: 0) + 1
        }
        counts["ALL"] = totalCount

        var filtered = rawList.filter { !hiddenLinks.contains(it.link) }
        if (activeTab == NexusFeedBottomBar.Tab.SAVED) {
            val savedLinks = FeedBookmarkStore.getBookmarkedLinks(context)
            filtered = filtered.filter { savedLinks.contains(it.link) }
        } else {
            if (!selectedCategory.equals("All", ignoreCase = true)) {
                val catSourceIds = currentSources.filter { it.category.equals(selectedCategory, ignoreCase = true) }.map { it.id }.toSet()
                filtered = filtered.filter { catSourceIds.contains(it.sourceId) }
            }
            if (activeTab == NexusFeedBottomBar.Tab.FEED && searchQuery.isBlank()) {
                filtered = filtered.filter { it.publishedAt >= feedWindowStart }
            }
        }
        if (activeTab != NexusFeedBottomBar.Tab.SAVED) {
            val curatedUrls = CuratedFeedSources.ALL_SOURCES.map { it.url }.toSet()
            val sourceUrlMap = currentSources.associate { it.id to it.url }
            filtered = filtered.sortedByDescending { sourceUrlMap[it.sourceId] !in curatedUrls }
        }
        return FilterResult(counts, filtered)
    }
}
