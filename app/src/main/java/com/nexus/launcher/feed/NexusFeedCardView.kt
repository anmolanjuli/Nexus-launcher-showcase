package com.nexus.launcher.feed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class NexusFeedCardView(context: Context) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null
    private var tokens = ThemeObserver.currentTokens(context)

    private val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (92 * dp).toInt())
    }

    private val titleView = TextView(context).apply {
        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private val metaView = TextView(context).apply {
        NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        letterSpacing = 0.03f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (4 * dp).toInt()
        }
    }

    private val textContainer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        addView(titleView)
        addView(metaView)
    }

    init {
        layoutParams = ViewGroup.MarginLayoutParams((270 * dp).toInt(), (170 * dp).toInt()).apply {
            marginEnd = (16 * dp).toInt()
        }
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)

        addView(imageView)
        addView(textContainer)
    }

    fun bind(article: FeedArticle) {
        titleView.text = article.title
        metaView.text = buildMetaString(article)

        setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            com.nexus.launcher.reader.NexusReaderLauncher.openArticle(context, article)
        }

        loadJob?.cancel()
        val primary = article.imageUrl
        val fallback = article.imageUrlFallback
        val cacheKey = primary ?: fallback ?: article.link

        val cached = bitmapCache[cacheKey]
        if (cached != null) {
            showImage(cached)
            return
        }

        loadJob = scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                loadImage(primary) ?: loadImage(fallback) ?: extractOgImage(article.link)?.let { loadImage(it) }
            }
            if (bmp != null) {
                bitmapCache[cacheKey] = bmp
                showImage(bmp)
            } else {
                hideImage()
            }
        }
    }

    private fun showImage(bmp: Bitmap) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        NexusFeedEInkStyler.applyImageGrayscale(imageView, isEInk)
        imageView.visibility = View.VISIBLE
        imageView.setImageBitmap(com.nexus.launcher.util.BitmapSizeGuard.guard("NexusFeedCardView.showImage", bmp))
        if (NexusFeedEInkCoordinator.shouldReduceMotion(context)) {
            imageView.alpha = 1f
        } else {
            imageView.alpha = 0f
            imageView.animate().alpha(1f).setDuration(160).start()
        }
        val lp = textContainer.layoutParams as LayoutParams
        lp.topMargin = (92 * dp).toInt()
        textContainer.setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
        titleView.maxLines = 2
        textContainer.layoutParams = lp
    }

    private fun hideImage() {
        imageView.visibility = View.GONE
        val lp = textContainer.layoutParams as LayoutParams
        lp.topMargin = 0
        textContainer.setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        titleView.maxLines = 4
        textContainer.layoutParams = lp
    }

    private fun buildMetaString(article: FeedArticle): String {
        val timeAgo = formatTimeAgo(article.publishedAt)
        return if (timeAgo.isNotBlank()) {
            "${article.sourceName} • $timeAgo"
        } else {
            article.sourceName
        }
    }

    private fun formatTimeAgo(timeMs: Long): String = NexusFeedTimeAgo.format(context, timeMs)

    private fun extractOgImage(link: String): String? {
        if (link.isBlank()) return null
        try {
            val conn = URL(link).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            val code = conn.responseCode
            if (code in 200..299) {
                val encoding = conn.contentEncoding
                val rawStream = conn.inputStream
                val stream = if (encoding != null && encoding.equals("gzip", ignoreCase = true)) {
                    java.util.zip.GZIPInputStream(rawStream)
                } else {
                    rawStream
                }
                val sb = StringBuilder()
                val buf = CharArray(8192)
                stream.bufferedReader().use { reader ->
                    var r = reader.read(buf)
                    while (r > 0 && sb.length < 131072) {
                        sb.append(buf, 0, r)
                        if (sb.contains("</head>", ignoreCase = true)) break
                        r = reader.read(buf)
                    }
                }
                val html = sb.toString()
                for (pattern in OG_PATTERNS) {
                    val m = pattern.matcher(html)
                    if (m.find()) {
                        val img = m.group(1)
                        if (!img.isNullOrBlank() && img.startsWith("http")) return img
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun loadImage(urlStr: String?): Bitmap? {
        if (urlStr.isNullOrBlank()) return null
        var current = urlStr.trim()
        var redirects = 0
        while (redirects < 3) {
            try {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                conn.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.9,*/*;q=0.8")
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
                val code = conn.responseCode
                if (code in 301..308) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        current = loc
                        redirects++
                        continue
                    }
                }
                if (code in 200..299) {
                    val encoding = conn.contentEncoding
                    val rawStream = conn.inputStream
                    val stream = if (encoding != null && encoding.equals("gzip", ignoreCase = true)) {
                        java.util.zip.GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }
                    return stream.use { s ->
                        val bytes = s.readBytes()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        val sample = NexusFeedImageLoader.calculateInSampleSize(options, 300, 200)
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    }
                }
                return null
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    fun applyTokens(newTokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens
        NexusFeedEInkStyler.applyHeadline(titleView, tokens.textPrimary, isEInk)
        NexusFeedEInkStyler.applyMeta(metaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyImageGrayscale(imageView, isEInk)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
    }

    companion object {
        private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
        private val OG_PATTERNS = listOf(
            Pattern.compile("""<meta[^>]+(?:property|name)\s*=\s*['"](?:og:image|twitter:image|twitter:image:src)['"][^>]+content\s*=\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<meta[^>]+content\s*=\s*['"]([^'"]+)['"][^>]+(?:property|name)\s*=\s*['"](?:og:image|twitter:image|twitter:image:src)['"]""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<link[^>]+rel\s*=\s*['"]image_src['"][^>]+href\s*=\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE)
        )
    }
}
