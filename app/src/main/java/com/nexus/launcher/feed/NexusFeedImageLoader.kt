package com.nexus.launcher.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

object NexusFeedImageLoader {

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val ogPattern = Pattern.compile("<meta\\s+[^>]*property=[\"']og:image[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    private val ogAltPattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE)
    private val twitterPattern = Pattern.compile("<meta\\s+[^>]*name=[\"']twitter:image[\"'][^>]*content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    private val twitterAltPattern = Pattern.compile("<meta\\s+[^>]*content=[\"']([^\"']+)[\"'][^>]*name=[\"']twitter:image[\"']", Pattern.CASE_INSENSITIVE)

    fun getCached(key: String): Bitmap? = bitmapCache[key]

    fun putCached(key: String, bitmap: Bitmap) {
        bitmapCache[key] = bitmap
    }

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
        maxDimension: Int = 1920
    ): Int {
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) return 1

        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2

        if (reqWidth > 0 && reqHeight > 0) {
            while ((halfWidth / inSampleSize) >= reqWidth && (halfHeight / inSampleSize) >= reqHeight) {
                inSampleSize *= 2
            }
        }

        // Hard cap ceiling defense-in-depth: ensure no decoded dimension exceeds maxDimension
        while ((width / inSampleSize) > maxDimension || (height / inSampleSize) > maxDimension) {
            inSampleSize *= 2
        }

        return inSampleSize.coerceAtLeast(1)
    }

    suspend fun fetchBitmap(
        url: String,
        reqWidth: Int = 1080,
        reqHeight: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            var conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode in 300..399) {
                val redirectedUrl = conn.getHeaderField("Location")
                if (!redirectedUrl.isNullOrBlank()) {
                    conn.disconnect()
                    conn = (URL(redirectedUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout = 6000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                        setRequestProperty("Accept-Encoding", "gzip, deflate")
                    }
                    conn.connect()
                }
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }
            val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
            val rawStream = conn.inputStream
            val stream = if (isGzip) GZIPInputStream(rawStream) else rawStream
            val bytes = stream.use { it.readBytes() }
            conn.disconnect()

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

            val sampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            com.nexus.launcher.util.BitmapSizeGuard.guard("NexusFeedImageLoader.fetchBitmap($url)", decoded)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun extractOgImage(articleUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(articleUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }
            val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
            val inputStream = if (isGzip) GZIPInputStream(conn.inputStream) else conn.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val sb = StringBuilder()
            val chunk = CharArray(4096)
            var totalChars = 0
            val maxChars = 131072

            while (totalChars < maxChars) {
                val read = reader.read(chunk, 0, chunk.size)
                if (read == -1) break
                sb.append(chunk, 0, read)
                totalChars += read
                if (sb.contains("</head>", ignoreCase = true)) break
            }
            reader.close()
            conn.disconnect()

            val html = sb.toString()
            findMetaImage(html, ogPattern)
                ?: findMetaImage(html, ogAltPattern)
                ?: findMetaImage(html, twitterPattern)
                ?: findMetaImage(html, twitterAltPattern)
        } catch (_: Exception) {
            null
        }
    }

    private fun findMetaImage(html: String, pattern: Pattern): String? {
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val url = matcher.group(1)?.trim()
            if (!url.isNullOrBlank() && url.startsWith("http")) return url
        }
        return null
    }

    fun getMonogram(sourceName: String): String {
        val clean = sourceName.replace(Regex("[^a-zA-Z0-9\\s]"), "").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "N"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first()}${words[1].first()}".uppercase()
        }
    }

    fun launchCustomTab(context: Context, url: String) {
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)).apply {
                putExtra("android.support.customtabs.extra.TOOLBAR_COLOR", Color.parseColor("#0B1326"))
                putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
                putExtra("androidx.browser.customtabs.extra.COLOR_SCHEME_PARAMS", android.os.Bundle().apply {
                    putInt("androidx.browser.customtabs.extra.TOOLBAR_COLOR", Color.parseColor("#0B1326"))
                })
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
