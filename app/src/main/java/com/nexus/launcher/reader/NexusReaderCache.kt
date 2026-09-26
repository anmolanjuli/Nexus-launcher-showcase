package com.nexus.launcher.reader

import android.content.Context
import android.util.LruCache
import com.nexus.launcher.feed.FeedBookmarkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Two-tier cache (in-memory LRU + disk JSON files) for extracted reader articles.
 * Retains transient articles for 24 hours and saved (bookmarked) articles indefinitely for offline reading.
 */
object NexusReaderCache {

    // v2: the elements inside an article now carry what kind of element they are. Files written
    // by the version before that deserialise into raw maps and crash whoever reads one, so they are
    // left behind rather than migrated — an article re-extracts in a moment.
    private const val CACHE_DIR_NAME = "nexus_reader_cache_v2"
    private const val LEGACY_CACHE_DIR_NAME = "nexus_reader_cache"
    private const val EXPIRATION_MS = 24 * 60 * 60 * 1000L // 24 hours
    private val memoryCache = LruCache<String, ExtractedArticle>(30)
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapter(ArticleElement::class.java, ArticleElementJson)
        .create()

    fun getMemory(url: String): ExtractedArticle? = memoryCache.get(url)

    suspend fun get(context: Context, url: String): ExtractedArticle? {
        val inMem = memoryCache.get(url)
        if (inMem != null) return inMem

        return withContext(Dispatchers.IO) {
            try {
                val file = getFileForUrl(context, url)
                if (!file.exists()) return@withContext null

                val json = file.readText(Charsets.UTF_8)
                val article = gson.fromJson(json, ExtractedArticle::class.java) ?: return@withContext null
                // An article whose elements did not survive the round trip is not worth keeping:
                // reading one is what crashed the reader before elements carried their kind.
                // Checked through List<*> on purpose: reading the list at its declared type is
                // itself what throws when an element came back as a raw map.
                val elements: List<*> = article.elements
                if (elements.any { it !is ArticleElement }) {
                    file.delete()
                    return@withContext null
                }
                val isBookmarked = FeedBookmarkStore.isBookmarked(context, url)
                val isExpired = (System.currentTimeMillis() - article.extractedAt) > EXPIRATION_MS

                if (isExpired && !isBookmarked) {
                    file.delete()
                    return@withContext null
                }

                memoryCache.put(url, article)
                article
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun put(context: Context, article: ExtractedArticle) {
        memoryCache.put(article.url, article)
        withContext(Dispatchers.IO) {
            try {
                val file = getFileForUrl(context, article.url)
                val json = gson.toJson(article)
                file.writeText(json, Charsets.UTF_8)
            } catch (_: Exception) {
                // Ignore disk write failure
            }
        }
    }

    suspend fun evictExpired(context: Context) = withContext(Dispatchers.IO) {
        try {
            val dir = getCacheDir(context)
            val now = System.currentTimeMillis()
            val files = dir.listFiles() ?: return@withContext
            for (file in files) {
                if (file.extension != "json") continue
                val lastModified = file.lastModified()
                if (now - lastModified > EXPIRATION_MS) {
                    // Check if bookmarked
                    val json = runCatching { file.readText() }.getOrNull() ?: continue
                    val art = runCatching { gson.fromJson(json, ExtractedArticle::class.java) }.getOrNull()
                    if (art != null && FeedBookmarkStore.isBookmarked(context, art.url)) continue
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun getCacheDir(context: Context): File {
        val legacy = File(context.filesDir, LEGACY_CACHE_DIR_NAME)
        if (legacy.exists()) runCatching { legacy.deleteRecursively() }
        val dir = File(context.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getFileForUrl(context: Context, url: String): File {
        val hash = sha256(url)
        return File(getCacheDir(context), "art_$hash.json")
    }

    private fun sha256(str: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
