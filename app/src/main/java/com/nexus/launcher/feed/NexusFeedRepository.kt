package com.nexus.launcher.feed

import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.data.FeedSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexusFeedRepository @Inject constructor(
    private val feedDao: FeedDao
) {
    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val CONNECT_TIMEOUT_MS = 8000
        private const val READ_TIMEOUT_MS = 10000
        private const val RETENTION_CUTOFF_MS = 48 * 3600 * 1000L // 48 hours
    }

    suspend fun fetchFeed(source: FeedSource): Result<List<FeedArticle>> = withContext(Dispatchers.IO) {
        try {
            val parsedFeed = fetchAndParse(source.url)
            val articles = parsedFeed.articles.map { parsed ->
                FeedArticle(
                    sourceId = source.id,
                    title = parsed.title,
                    link = parsed.link,
                    imageUrl = parsed.imageUrl,
                    imageUrlFallback = parsed.imageUrlFallback,
                    sourceName = source.title.ifBlank { parsedFeed.channelTitle },
                    publishedAt = parsed.publishedAt,
                    cachedAt = System.currentTimeMillis()
                )
            }
            Result.success(articles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchFeedDirect(url: String): Result<ParsedFeed> = withContext(Dispatchers.IO) {
        try {
            val parsed = fetchAndParse(url)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchAndParse(urlStr: String): ParsedFeed {
        var currentUrl = urlStr
        var redirects = 0
        while (redirects < 4) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, application/atom+xml, */*")
                setRequestProperty("Accept-Encoding", "gzip")
            }

            val status = connection.responseCode
            if (status in 301..308 && status != 304) {
                val loc = connection.getHeaderField("Location")
                if (!loc.isNullOrBlank()) {
                    currentUrl = loc
                    redirects++
                    continue
                }
            }

            if (status !in 200..299) {
                throw java.io.IOException("HTTP $status loading feed $currentUrl")
            }

            val rawStream: InputStream = connection.inputStream
            val stream: InputStream = if ("gzip".equals(connection.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }

            return stream.use {
                FeedXmlParser.parse(it, currentUrl)
            }
        }
        throw java.io.IOException("Too many redirects fetching $urlStr")
    }

    suspend fun refreshAllSources(sources: List<FeedSource>) = withContext(Dispatchers.IO) {
        coroutineScope {
            sources.map { source ->
                async {
                    val result = fetchFeed(source)
                    if (result.isSuccess) {
                        val articles = result.getOrNull().orEmpty()
                        if (articles.isNotEmpty()) {
                            feedDao.deleteArticlesForSource(source.id)
                            feedDao.insertArticles(articles)
                        }
                        feedDao.updateSource(
                            source.copy(
                                lastFetchedAt = System.currentTimeMillis(),
                                lastError = null
                            )
                        )
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Fetch failed"
                        feedDao.updateSource(source.copy(lastError = error))
                    }
                }
            }.awaitAll()
        }

        // Clean up cached articles older than 48 hours
        val cutoff = System.currentTimeMillis() - RETENTION_CUTOFF_MS
        feedDao.deleteArticlesOlderThan(cutoff)
    }

    suspend fun seedDefaultSources(dao: FeedDao) = withContext(Dispatchers.IO) {
        if (dao.getSourceCount() == 0) {
            dao.insertSource(
                FeedSource(
                    title = "BBC News",
                    url = "https://feeds.bbci.co.uk/news/world/rss.xml",
                    category = "WORLD",
                    isDefault = true,
                    isEnabled = true,
                    sortOrder = 0,
                    addedAt = System.currentTimeMillis()
                )
            )
        }
    }
}
