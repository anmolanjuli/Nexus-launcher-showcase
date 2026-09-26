package com.nexus.launcher.feed

import android.content.Context
import android.content.SharedPreferences
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.data.FeedSource
import com.nexus.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates source observation, article filtering/caching, and safe refresh dispatch for NexusFeedPage.
 */
class NexusFeedRefreshController(
    private val context: Context,
    private val repository: NexusFeedRepository,
    private val feedDao: FeedDao,
    private val feedPrefs: SharedPreferences
) {
    var isRefreshing: Boolean = false
        private set

    var currentSources: List<FeedSource> = emptyList()
        private set

    var currentArticlesCache: List<FeedArticle> = emptyList()
        private set

    val hiddenArticleLinks = mutableSetOf<String>()
    private var articlesJob: Job? = null
    private var sourcesJob: Job? = null
    private var hasCheckedTimeBasedRefresh = false
    /** Identity of the source set the last refresh check ran against. */
    private var lastCheckedSourceKey: String? = null

    fun resetTimeRefreshCheck() {
        hasCheckedTimeBasedRefresh = false
        lastCheckedSourceKey = null
    }

    /** True while [observeSources]' collector is live. Source observation is what feeds the
     *  category pills, the article filter's category/ordering maps, and the post-restore refresh
     *  — it is not per-tab state and must outlive any tab switch. */
    val isObservingSources: Boolean
        get() = sourcesJob?.isActive == true

    fun observeSources(
        scope: CoroutineScope,
        onSourcesUpdated: (List<FeedSource>) -> Unit
    ) {
        sourcesJob?.cancel()
        sourcesJob = scope.launch {
            feedDao.getEnabledSourcesFlow().collectLatest { sources ->
                currentSources = sources
                onSourcesUpdated(sources)
                if (sources.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        repository.seedDefaultSources(feedDao)
                    }
                    return@collectLatest
                }
                // A restore leaves an explicit instruction, because none of the heuristics below
                // can see it: on a fresh install the defaults are seeded before the restore runs,
                // so merge-by-URL adds nothing and the source set never changes — yet the feed
                // still holds no articles, since article bodies are not part of a backup.
                if (consumePendingRestoreRefresh(scope, sources)) return@collectLatest
                // The check was otherwise a once-per-instance latch, so sources arriving later in
                // the same session were never fetched for. Re-arm when the source set changes.
                val sourceKey = sources.map { it.url.trim().lowercase() }.sorted().joinToString("|")
                if (sourceKey != lastCheckedSourceKey) {
                    lastCheckedSourceKey = sourceKey
                    hasCheckedTimeBasedRefresh = false
                }
                if (!hasCheckedTimeBasedRefresh) {
                    hasCheckedTimeBasedRefresh = true
                    checkTimeBasedRefresh(scope)
                }
            }
        }
    }

    fun observeArticles(
        scope: CoroutineScope,
        searchQuery: String,
        activeTab: NexusFeedBottomBar.Tab,
        selectedCategory: String,
        onResult: (List<FeedArticle>, Map<String, Int>) -> Unit
    ) {
        if (activeTab == NexusFeedBottomBar.Tab.SETTINGS) return
        articlesJob?.cancel()
        articlesJob = scope.launch {
            val flow = if (searchQuery.isBlank()) {
                feedDao.getAllEnabledArticlesFlow()
            } else {
                feedDao.searchArticlesFlow(searchQuery)
            }
            flow.collectLatest { rawList ->
                val result = NexusFeedArticleFilter.filter(
                    context = context,
                    rawList = rawList,
                    currentSources = currentSources,
                    activeTab = activeTab,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    hiddenLinks = hiddenArticleLinks
                )
                currentArticlesCache = result.filteredArticles
                onResult(result.filteredArticles, result.counts)
            }
        }
    }

    /**
     * Runs the post-restore refresh if one is still pending, and reports whether it did.
     *
     * Also callable from outside the sources collector, because a restore writes its flag *after*
     * inserting the sources: if the DAO's emission for those inserts happens to land first, the
     * collector reads the flag before it is set and nothing re-reads it for the rest of the
     * session — leaving the restored sources with no articles behind them.
     */
    fun consumePendingRestoreRefresh(
        scope: CoroutineScope,
        sources: List<FeedSource> = currentSources
    ): Boolean {
        if (!feedPrefs.getBoolean(NexusFeedTimeRefreshHelper.KEY_PENDING_RESTORE_REFRESH, false)) return false
        feedPrefs.edit()
            .remove(NexusFeedTimeRefreshHelper.KEY_PENDING_RESTORE_REFRESH)
            .apply()
        hasCheckedTimeBasedRefresh = true
        lastCheckedSourceKey = sources.map { it.url.trim().lowercase() }.sorted().joinToString("|")
        triggerRefresh(scope)
        return true
    }

    /** Cancels only the article collector — see [isObservingSources] for why the source one is
     *  not a tab-scoped concern. */
    fun cancelArticlesJob() {
        articlesJob?.cancel()
        articlesJob = null
    }

    fun checkTimeBasedRefresh(scope: CoroutineScope) {
        if (NexusFeedTimeRefreshHelper.shouldRefresh(feedPrefs)) {
            triggerRefresh(scope)
        }
    }

    fun triggerRefresh(scope: CoroutineScope, onFinished: (() -> Unit)? = null) {
        if (isRefreshing) return
        isRefreshing = true
        scope.launch {
            try {
                var sourcesToRefresh = currentSources
                if (sourcesToRefresh.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        repository.seedDefaultSources(feedDao)
                    }
                    sourcesToRefresh = currentSources
                }
                if (sourcesToRefresh.isNotEmpty()) {
                    repository.refreshAllSources(sourcesToRefresh)
                    feedPrefs.edit().putLong("last_refresh_time", System.currentTimeMillis()).apply()
                }
            } catch (_: Exception) {
            } finally {
                isRefreshing = false
                onFinished?.invoke()
            }
        }
    }

    fun showArticleMenu(
        activity: MainActivity,
        article: FeedArticle,
        onArticlesChanged: () -> Unit
    ) {
        val sheet = NexusFeedArticleMenuSheet().apply {
            this.article = article
            this.isBookmarked = FeedBookmarkStore.isBookmarked(this@NexusFeedRefreshController.context, article.link)
            this.onBookmarkToggle = {
                FeedBookmarkStore.toggleBookmark(this@NexusFeedRefreshController.context, article.link)
                onArticlesChanged()
            }
            this.onHideArticle = {
                hiddenArticleLinks.add(article.link)
                onArticlesChanged()
            }
        }
        sheet.show(activity.supportFragmentManager, NexusFeedArticleMenuSheet.TAG)
    }

    fun cancelActiveJobs() {
        articlesJob?.cancel()
        articlesJob = null
        sourcesJob?.cancel()
        sourcesJob = null
    }
}
