package com.nexus.launcher.feed

import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.data.FeedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexusFeedSourceManager @Inject constructor(
    private val feedDao: FeedDao,
    private val repository: NexusFeedRepository
) {

    suspend fun addCuratedSource(name: String, category: String, url: String): Result<FeedSource> = withContext(Dispatchers.IO) {
        val currentCount = feedDao.getSourceCount()
        val source = FeedSource(
            title = name,
            url = url,
            category = category,
            isDefault = false,
            isEnabled = true,
            sortOrder = currentCount,
            addedAt = System.currentTimeMillis(),
            lastFetchedAt = 0L,
            lastError = null
        )
        val newId = feedDao.insertSource(source)
        val createdSource = source.copy(id = newId.toInt())

        // Fetch articles in background
        CoroutineScope(Dispatchers.IO).launch {
            val result = repository.fetchFeed(createdSource)
            if (result.isSuccess) {
                val articles = result.getOrNull().orEmpty()
                if (articles.isNotEmpty()) {
                    feedDao.deleteArticlesForSource(createdSource.id)
                    feedDao.insertArticles(articles)
                }
                feedDao.updateSource(createdSource.copy(lastFetchedAt = System.currentTimeMillis()))
            } else {
                feedDao.updateSource(createdSource.copy(lastError = result.exceptionOrNull()?.message ?: "Fetch failed"))
            }
        }

        Result.success(createdSource)
    }

    suspend fun addSource(rawUrl: String): Result<FeedSource> = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeSourceUrl(rawUrl)
        if (normalizedUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Invalid URL"))
        }

        val currentCount = feedDao.getSourceCount()
        val fetchResult = repository.fetchFeedDirect(normalizedUrl)
        val parsedFeed = fetchResult.getOrNull()

        val title = parsedFeed?.channelTitle?.ifBlank { extractDomain(normalizedUrl) } ?: extractDomain(normalizedUrl)
        val category = detectCategory(normalizedUrl, title)

        val source = FeedSource(
            title = title,
            url = normalizedUrl,
            category = category,
            isDefault = false,
            isEnabled = true,
            sortOrder = currentCount,
            addedAt = System.currentTimeMillis(),
            lastFetchedAt = if (parsedFeed != null) System.currentTimeMillis() else 0L,
            lastError = if (fetchResult.isFailure) fetchResult.exceptionOrNull()?.message else null
        )

        val newId = feedDao.insertSource(source)
        val createdSource = source.copy(id = newId.toInt())

        if (parsedFeed != null && parsedFeed.articles.isNotEmpty()) {
            val articles = parsedFeed.articles.map { parsed ->
                FeedArticle(
                    sourceId = createdSource.id,
                    title = parsed.title,
                    link = parsed.link,
                    imageUrl = parsed.imageUrl,
                    imageUrlFallback = parsed.imageUrlFallback,
                    sourceName = title,
                    publishedAt = parsed.publishedAt,
                    cachedAt = System.currentTimeMillis()
                )
            }
            feedDao.deleteArticlesForSource(createdSource.id)
            feedDao.insertArticles(articles)
        }

        Result.success(createdSource)
    }

    suspend fun removeSource(source: FeedSource) = withContext(Dispatchers.IO) {
        feedDao.deleteSource(source)
    }

    suspend fun toggleSource(source: FeedSource, enabled: Boolean) = withContext(Dispatchers.IO) {
        feedDao.updateSource(source.copy(isEnabled = enabled))
    }

    suspend fun reorderSources(sourceIds: List<Int>) = withContext(Dispatchers.IO) {
        sourceIds.forEachIndexed { index, id ->
            feedDao.updateSourceOrder(id, index)
        }
    }

    private fun normalizeSourceUrl(raw: String): String {
        var url = raw.trim()
        if (url.startsWith("r/")) {
            val sub = url.removePrefix("r/").trim('/')
            return "https://www.reddit.com/r/$sub/new/.rss"
        }
        if (url.contains("reddit.com/r/") && !url.endsWith(".rss")) {
            val clean = url.trimEnd('/')
            return if (clean.endsWith("/new")) "$clean/.rss" else "$clean/new/.rss"
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    private fun detectCategory(url: String, title: String): String {
        val text = "${url.lowercase(Locale.ROOT)} ${title.lowercase(Locale.ROOT)}"
        return when {
            text.containsAny("tech", "verge", "wired", "arstechnica", "android", "apple", "code", "gizmodo", "engadget", "9to5") -> "TECHNOLOGY"
            text.containsAny("sport", "espn", "athletic", "nba", "nfl", "football", "soccer", "fifa", "f1") -> "SPORTS"
            text.containsAny("variety", "hollywood", "movie", "film", "music", "gaming", "ign", "gamespot", "entertainment") -> "ENTERTAINMENT"
            text.containsAny("finance", "bloomberg", "wsj", "market", "economist", "forbes", "business", "ft.com", "crypto", "yahoo") -> "FINANCE"
            text.containsAny("science", "nature", "space", "nasa", "astronomy", "newscientist", "physics") -> "SCIENCE"
            url.contains("reddit.com") -> "CUSTOM"
            else -> "WORLD"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }

    private fun extractDomain(urlStr: String): String {
        return try {
            val host = URI(urlStr).host ?: ""
            host.removePrefix("www.").substringBefore(".")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        } catch (_: Exception) {
            "Feed"
        }
    }
}
