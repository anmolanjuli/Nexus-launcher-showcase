package com.nexus.launcher.feed

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

data class ParsedArticle(
    val title: String,
    val link: String,
    val imageUrl: String?,
    val imageUrlFallback: String?,
    val publishedAt: Long
)

data class ParsedFeed(
    val channelTitle: String,
    val articles: List<ParsedArticle>
)

object FeedXmlParser {

    private val IMG_SRC_PATTERN = Pattern.compile(
        """<img[^>]+src\s*=\s*['"]([^'"]+)['"]""",
        Pattern.CASE_INSENSITIVE
    )

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yy HH:mm:ss z",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd HH:mm:ss"
    )

    fun parse(inputStream: InputStream, sourceUrl: String): ParsedFeed {
        val factory = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        }
        val parser = factory.newPullParser()
        parser.setInput(inputStream, null)

        var channelTitle = ""
        val articles = mutableListOf<ParsedArticle>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name?.lowercase(Locale.ROOT) ?: ""
                when (name) {
                    "title" -> {
                        if (channelTitle.isBlank()) {
                            channelTitle = readText(parser)
                        }
                    }
                    "item" -> {
                        parseRssItem(parser)?.let { articles.add(it) }
                    }
                    "entry" -> {
                        parseAtomEntry(parser)?.let { articles.add(it) }
                    }
                }
            }
            eventType = parser.next()
        }

        // For Reddit URLs, format channel title as r/Subreddit
        if (sourceUrl.contains("reddit.com/r/") || sourceUrl.startsWith("r/")) {
            val sub = extractRedditSub(sourceUrl)
            if (sub.isNotBlank()) {
                channelTitle = "r/$sub"
            }
        }

        return ParsedFeed(channelTitle.trim(), articles)
    }

    private fun parseRssItem(parser: XmlPullParser): ParsedArticle? {
        var title = ""
        var link = ""
        var publishedAt = 0L
        val imgCandidates = mutableListOf<Pair<Int, String>>() // priority to url

        while (parser.next() != XmlPullParser.END_TAG || parser.name?.lowercase(Locale.ROOT) != "item") {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            val name = parser.name?.lowercase(Locale.ROOT) ?: ""
            val ns = parser.namespace?.lowercase(Locale.ROOT) ?: ""
            val isMedia = ns.contains("media") || ns.contains("mrss")

            when {
                name == "title" -> title = readText(parser)
                name == "link" && ns.isEmpty() -> link = readText(parser)
                name == "pubdate" || name == "date" -> {
                    val dateStr = readText(parser)
                    publishedAt = parseDate(dateStr)
                }
                (isMedia && name == "thumbnail") || name == "thumbnail" -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank()) imgCandidates.add(1 to normalizeUrl(url))
                    skipTag(parser)
                }
                (isMedia && name == "content") || (name == "content" && isMedia) -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank()) imgCandidates.add(2 to normalizeUrl(url))
                    skipTag(parser)
                }
                (ns.contains("wp") || ns.contains("wordpress")) && name == "featured_image" -> {
                    val url = parser.getAttributeValue(null, "url") ?: readText(parser)
                    if (url.isNotBlank()) imgCandidates.add(3 to normalizeUrl(url))
                }
                (ns == "http://purl.org/rss/1.0/modules/content/" || ns.contains("content") || name == "content:encoded") && (name == "encoded" || name == "content:encoded") -> {
                    val html = readText(parser)
                    extractFirstImg(html)?.let { imgCandidates.add(4 to normalizeUrl(it)) }
                }
                name == "description" -> {
                    val text = readText(parser)
                    extractFirstImg(text)?.let { imgCandidates.add(5 to normalizeUrl(it)) }
                }
                name == "enclosure" -> {
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank() && (type.startsWith("image/") || isImageUrl(url))) {
                        imgCandidates.add(6 to normalizeUrl(url))
                    }
                    skipTag(parser)
                }
                else -> skipTag(parser)
            }
        }

        if (title.isBlank() && link.isBlank()) return null
        val (primaryImg, fallbackImg) = resolveImages(imgCandidates)
        android.util.Log.d("FeedXmlParser", "Parsed article '$title': ${imgCandidates.size} image candidates found, primary='$primaryImg'")
        return ParsedArticle(
            title = sanitizeHtml(title),
            link = link.trim(),
            imageUrl = primaryImg,
            imageUrlFallback = fallbackImg,
            publishedAt = if (publishedAt > 0L) publishedAt else System.currentTimeMillis()
        )
    }

    private fun parseAtomEntry(parser: XmlPullParser): ParsedArticle? {
        var title = ""
        var link = ""
        var publishedAt = 0L
        val imgCandidates = mutableListOf<Pair<Int, String>>()

        while (parser.next() != XmlPullParser.END_TAG || parser.name?.lowercase(Locale.ROOT) != "entry") {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            val name = parser.name?.lowercase(Locale.ROOT) ?: ""
            val ns = parser.namespace?.lowercase(Locale.ROOT) ?: ""
            val isMedia = ns.contains("media") || ns.contains("mrss")

            when {
                name == "title" -> title = readText(parser)
                name == "link" -> {
                    val rel = parser.getAttributeValue(null, "rel")
                    val href = parser.getAttributeValue(null, "href")
                    if ((rel == null || rel == "alternate") && !href.isNullOrBlank()) {
                        link = href
                    }
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    if (!href.isNullOrBlank() && type.startsWith("image/")) {
                        imgCandidates.add(6 to normalizeUrl(href))
                    }
                    skipTag(parser)
                }
                name == "published" || name == "updated" -> {
                    val dateStr = readText(parser)
                    if (publishedAt == 0L) publishedAt = parseDate(dateStr)
                }
                (isMedia && name == "thumbnail") || name == "thumbnail" -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank()) imgCandidates.add(1 to normalizeUrl(url))
                    skipTag(parser)
                }
                (isMedia && name == "content") || (name == "content" && isMedia) -> {
                    val url = parser.getAttributeValue(null, "url")
                    if (!url.isNullOrBlank()) imgCandidates.add(2 to normalizeUrl(url))
                    skipTag(parser)
                }
                name == "content" || name == "summary" -> {
                    val text = readText(parser)
                    extractFirstImg(text)?.let { imgCandidates.add(5 to normalizeUrl(it)) }
                }
                else -> skipTag(parser)
            }
        }

        if (title.isBlank() && link.isBlank()) return null
        val (primaryImg, fallbackImg) = resolveImages(imgCandidates)
        return ParsedArticle(
            title = sanitizeHtml(title),
            link = link.trim(),
            imageUrl = primaryImg,
            imageUrlFallback = fallbackImg,
            publishedAt = if (publishedAt > 0L) publishedAt else System.currentTimeMillis()
        )
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_TAG) {
            if (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT) {
                sb.append(parser.text)
            } else if (eventType == XmlPullParser.START_TAG) {
                skipTag(parser)
            }
            eventType = parser.next()
        }
        return sb.toString().trim()
    }

    private fun skipTag(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }

    private fun resolveImages(candidates: List<Pair<Int, String>>): Pair<String?, String?> {
        val sorted = candidates
            .sortedBy { it.first }
            .map { it.second }
            .filter { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
            .distinct()

        val primary = sorted.getOrNull(0)
        val fallback = sorted.getOrNull(1)
        return Pair(primary, fallback)
    }

    private fun extractFirstImg(html: String): String? {
        if (html.isBlank()) return null
        val matcher = IMG_SRC_PATTERN.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT).substringBefore("?")
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")
    }

    fun normalizeUrl(raw: String): String {
        var url = raw.trim()
            .replace("&amp;", "&")
            .replace("&#038;", "&")
            .replace("&#38;", "&")
        if (url.startsWith("//")) {
            url = "https:$url"
        }
        return url
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        val trimmed = dateStr.trim()
        for (pattern in DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(trimmed)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return 0L
    }

    private fun sanitizeHtml(input: String): String {
        return input.replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#038;", "&")
            .trim()
    }

    private fun extractRedditSub(url: String): String {
        val parts = url.split("/")
        val rIndex = parts.indexOf("r")
        return if (rIndex != -1 && rIndex + 1 < parts.size) {
            parts[rIndex + 1].substringBefore(".rss").substringBefore("?")
        } else ""
    }
}
