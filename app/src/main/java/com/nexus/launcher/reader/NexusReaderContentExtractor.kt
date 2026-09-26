package com.nexus.launcher.reader

import android.os.Build
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

/**
 * Background network fetcher and Readability-style HTML content extractor.
 * Strips ads, scripts, navbars, sidebars, and comments to isolate clean article body and metadata.
 */
object NexusReaderContentExtractor {

    private val TITLE_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE)
    private val OG_TITLE = Pattern.compile("<meta\\s+[^>]*property=[\"']og:title[\"'][^>]*content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
    private val OG_TITLE_ALT = Pattern.compile("<meta\\s+[^>]*content=[\"'](.*?)[\"'][^>]*property=[\"']og:title[\"']", Pattern.CASE_INSENSITIVE)
    private val OG_IMAGE = Pattern.compile("<meta\\s+[^>]*property=[\"']og:image[\"'][^>]*content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
    private val OG_IMAGE_ALT = Pattern.compile("<meta\\s+[^>]*content=[\"'](.*?)[\"'][^>]*property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE)
    private val TWITTER_IMAGE = Pattern.compile("<meta\\s+[^>]*name=[\"']twitter:image[\"'][^>]*content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
    private val AUTHOR_PATTERN = Pattern.compile("<meta\\s+[^>]*name=[\"']author[\"'][^>]*content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
    private val DATE_PATTERN = Pattern.compile("<meta\\s+[^>]*property=[\"']article:published_time[\"'][^>]*content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)

    private val SCRIPT_STYLE_PATTERN = Pattern.compile("<(script|style|svg|noscript|header|footer|nav|aside|iframe|form)[^>]*>[\\s\\S]*?</\\1>", Pattern.CASE_INSENSITIVE)
    private val ARTICLE_TAG_PATTERN = Pattern.compile("<article[^>]*>([\\s\\S]*?)</article>", Pattern.CASE_INSENSITIVE)
    private val MAIN_TAG_PATTERN = Pattern.compile("<main[^>]*>([\\s\\S]*?)</main>", Pattern.CASE_INSENSITIVE)
    // Images are matched alongside the text blocks, in one pass, so a picture keeps its place in
    // the article instead of being collected separately and losing it.
    private val PARAGRAPH_PATTERN = Pattern.compile(
        "<(p|h2|h3|blockquote)[^>]*>([\\s\\S]*?)</\\1>|<img[^>]+>",
        Pattern.CASE_INSENSITIVE
    )
    private val IMG_SRC_PATTERN = Pattern.compile(
        "\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE
    )
    private val IMG_ALT_PATTERN = Pattern.compile(
        "\\balt\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE
    )

    suspend fun extract(
        url: String,
        fallbackTitle: String? = null,
        fallbackHeroImage: String? = null
    ): ExtractedArticle? = withContext(Dispatchers.IO) {
        try {
            val html = fetchHtml(url) ?: return@withContext null

            val title = findMeta(html, OG_TITLE)
                ?: findMeta(html, OG_TITLE_ALT)
                ?: findTag(html, TITLE_PATTERN)
                ?: fallbackTitle ?: ""

            val cleanTitle = cleanHtmlText(title).substringBefore(" - ").substringBefore(" | ")
            val author = findMeta(html, AUTHOR_PATTERN)?.let(::cleanHtmlText)
            val publishDate = findMeta(html, DATE_PATTERN)?.take(10)
            val heroImage = findMeta(html, OG_IMAGE)
                ?: findMeta(html, OG_IMAGE_ALT)
                ?: findMeta(html, TWITTER_IMAGE)
                ?: fallbackHeroImage

            val contentArea = locateContentBlock(html)
            val elements = parseElements(contentArea)

            val totalWords = elements.sumOf { elem ->
                when (elem) {
                    is ArticleElement.Paragraph -> elem.text.split("\\s+".toRegex()).size
                    is ArticleElement.Heading -> elem.text.split("\\s+".toRegex()).size
                    is ArticleElement.Blockquote -> elem.text.split("\\s+".toRegex()).size
                    else -> 0
                }
            }

            // If extraction yielded insufficient text, signal fallback to WebView
            if (totalWords < 40 && elements.size < 2) return@withContext null

            val readingTime = maxOf(1, (totalWords + 100) / 200)

            ExtractedArticle(
                url = url,
                title = cleanTitle.ifBlank { fallbackTitle ?: "Article" },
                author = author,
                publishDate = publishDate,
                heroImageUrl = heroImage,
                elements = elements,
                wordCount = totalWords,
                readingTimeMinutes = readingTime
            )
        } catch (_: Throwable) {
            null
        }
    }

    private val CONTAINER_CLASS_PATTERN = Pattern.compile(
        "<div[^>]+(?:class|id)=[\"'][^\"']*(?:article-body|post-content|entry-content|story-body|article__content|article-content|caas-body)[^\"']*[\"'][^>]*>([\\s\\S]*?)</div>",
        Pattern.CASE_INSENSITIVE
    )

    private fun locateContentBlock(html: String): String {
        // Strip heavy non-content wrappers first
        val sanitized = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("")

        // Check for semantic <article> or <main>
        val articleMatcher = ARTICLE_TAG_PATTERN.matcher(sanitized)
        if (articleMatcher.find()) return articleMatcher.group(1) ?: ""

        val mainMatcher = MAIN_TAG_PATTERN.matcher(sanitized)
        if (mainMatcher.find()) return mainMatcher.group(1) ?: ""

        val containerMatcher = CONTAINER_CLASS_PATTERN.matcher(sanitized)
        if (containerMatcher.find()) return containerMatcher.group(1) ?: ""

        val bodyStart = sanitized.indexOf("<body", ignoreCase = true)
        val bodyEnd = sanitized.indexOf("</body>", ignoreCase = true)
        return if (bodyStart >= 0 && bodyEnd > bodyStart) {
            sanitized.substring(bodyStart, bodyEnd)
        } else if (bodyStart >= 0) {
            sanitized.substring(bodyStart)
        } else {
            sanitized
        }
    }

    private fun parseElements(contentHtml: String): List<ArticleElement> {
        val elements = mutableListOf<ArticleElement>()
        val matcher = PARAGRAPH_PATTERN.matcher(contentHtml)

        var images = 0
        while (matcher.find()) {
            val tag = matcher.group(1)?.lowercase()
            if (tag == null) {
                // An <img>: keep it where it sits in the article, up to MAX_IMAGES of them.
                if (images >= MAX_IMAGES) continue
                val imageTag = matcher.group() ?: continue
                val src = firstGroup(IMG_SRC_PATTERN, imageTag) ?: continue
                if (!isArticleImage(src)) continue
                images++
                elements.add(
                    ArticleElement.Image(src, firstGroup(IMG_ALT_PATTERN, imageTag)?.let(::cleanHtmlText))
                )
                continue
            }
            val rawInner = matcher.group(2) ?: continue
            val clean = cleanHtmlText(rawInner).trim()

            // Skip empty, copyright, or cookie boilerplate
            if (clean.length < 15) continue
            if (isBoilerplate(clean)) continue

            when (tag) {
                "p" -> elements.add(ArticleElement.Paragraph(clean))
                "h2" -> elements.add(ArticleElement.Heading(clean, level = 2))
                "h3" -> elements.add(ArticleElement.Heading(clean, level = 3))
                "blockquote" -> elements.add(ArticleElement.Blockquote(clean))
            }
        }
        return elements
    }

    private fun firstGroup(pattern: Pattern, input: String): String? {
        val m = pattern.matcher(input)
        return if (m.find()) m.group(1)?.trim()?.takeIf { it.isNotBlank() } else null
    }

    /**
     * Whether a `src` is a picture from the article rather than a spacer, an icon or a tracking
     * pixel. A page carries many more <img> tags than it has photographs.
     */
    private fun isArticleImage(src: String): Boolean {
        if (!src.startsWith("http")) return false
        val lower = src.lowercase()
        if (lower.endsWith(".svg") || lower.endsWith(".gif")) return false
        return NON_ARTICLE_IMAGE.none { lower.contains(it) }
    }

    private val NON_ARTICLE_IMAGE = listOf(
        "sprite", "icon", "logo", "avatar", "placeholder", "spacer", "pixel", "tracking",
        "1x1", "blank", "/ads/", "advert", "badge", "emoji",
    )

    /** Enough to illustrate an article; past this a page is decorating, not illustrating. */
    private const val MAX_IMAGES = 6

    private fun isBoilerplate(text: String): Boolean {
        val lower = text.lowercase()
        return lower.startsWith("subscribe") ||
                lower.startsWith("sign up") ||
                lower.startsWith("cookie") ||
                lower.contains("rights reserved") ||
                lower.contains("terms of service") ||
                lower.contains("privacy policy") ||
                lower.startsWith("advertisement") ||
                lower.startsWith("follow us")
    }

    private fun cleanHtmlText(raw: String): String {
        val unscripted = raw.replace("<[^>]*>".toRegex(), " ")
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(unscripted, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(unscripted).toString()
        }
        return decoded.replace("\\s+".toRegex(), " ").trim()
    }

    private fun fetchHtml(targetUrl: String): String? {
        var currentUrl = targetUrl
        for (redirect in 0..4) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                setRequestProperty("Sec-Ch-Ua-Mobile", "?1")
                setRequestProperty("Sec-Ch-Ua-Platform", "\"Android\"")
                setRequestProperty("Sec-Fetch-Dest", "document")
                setRequestProperty("Sec-Fetch-Mode", "navigate")
                setRequestProperty("Sec-Fetch-Site", "none")
                setRequestProperty("Sec-Fetch-User", "?1")
                setRequestProperty("Upgrade-Insecure-Requests", "1")
                instanceFollowRedirects = false
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 301..308) {
                val newLoc = conn.getHeaderField("Location")
                conn.disconnect()
                if (newLoc.isNullOrBlank()) return null
                currentUrl = if (newLoc.startsWith("http")) newLoc else URL(URL(currentUrl), newLoc).toString()
                continue
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return null
            }
            val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
            val inputStream = if (isGzip) GZIPInputStream(conn.inputStream) else conn.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val sb = StringBuilder()
            val chunk = CharArray(8192)
            var totalRead = 0
            val maxChars = 1048576 // 1MB buffer allows full modern articles without truncation

            while (totalRead < maxChars) {
                val read = reader.read(chunk, 0, chunk.size)
                if (read == -1) break
                sb.append(chunk, 0, read)
                totalRead += read
                // Fast-exit if the article container has closed
                if (totalRead > 49152 && (sb.contains("</article>", ignoreCase = true) || sb.contains("</main>", ignoreCase = true))) {
                    break
                }
            }
            reader.close()
            conn.disconnect()
            return sb.toString()
        }
        return null
    }

    private fun findMeta(html: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun findTag(html: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }
}
