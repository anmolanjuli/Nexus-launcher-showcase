package com.nexus.launcher.feed

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Title/meta/monogram/bookmark sit over a photo behind a fixed dark scrim for legibility —
 * they intentionally stay light-on-dark regardless of theme, matching the scrim, not the app
 * surface. Only the pre-image card background/border react to tokens.
 */
class NexusFeedHeroCardView(context: Context) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null
    private var isBookmarked = false
    private var tokens = ThemeObserver.currentTokens(context)

    private val coverImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val scrimView = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        background = GradientDrawable(
            GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(
                0xFA060E20.toInt(),
                0xD0060E20.toInt(),
                0x60060E20,
                0x00060E20
            )
        )
    }

    private val monogramView = TextView(context).apply {
        NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 9999f
        }
        layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
            marginEnd = (8 * dp).toInt()
        }
    }

    private val sourceMetaView = TextView(context).apply {
        NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private val titleView = TextView(context).apply {
        NexusTypeScale.title.bindTo(this, Color.WHITE)
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setLineSpacing(0f, 1.15f)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            topMargin = (8 * dp).toInt()
            marginEnd = (12 * dp).toInt()
        }
    }

    private val bookmarkBtn = ImageView(context).apply {
        setImageResource(R.drawable.ic_bookmark)
        setColorFilter(tokens.textPrimary)
        layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
            gravity = Gravity.BOTTOM
        }
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            toggleBookmark()
        }
    }

    private fun updateHeroScrim(isEInk: Boolean, isDark: Boolean) {
        if (isEInk) {
            // Paper behind the words, not over the picture. A flat 88% sheet across the whole image
            // left the photograph as a rumour — the point of a hero card is the photograph. The
            // paper now climbs from the bottom, solid under the title and gone by halfway up.
            val paperColor = if (isDark) NexusFeedEInkCoordinator.COLOR_DARK_BG else NexusFeedEInkCoordinator.COLOR_LIGHT_BG
            val rgb = paperColor and 0x00FFFFFF
            scrimView.background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    (0xF7 shl 24) or rgb,
                    (0xE0 shl 24) or rgb,
                    (0x66 shl 24) or rgb,
                    rgb,
                )
            )
        } else {
            scrimView.background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(
                    0xFA060E20.toInt(),
                    0xD0060E20.toInt(),
                    0x60060E20,
                    0x00060E20
                )
            )
        }
    }

    init {
        layoutParams = MarginLayoutParams(LayoutParams.MATCH_PARENT, (280 * dp).toInt()).apply {
            bottomMargin = (16 * dp).toInt()
        }
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
        updateHeroScrim(isEInk, isDark)
        NexusFeedEInkStyler.applyHeroTitle(titleView, if (isEInk) tokens.textPrimary else Color.WHITE, isEInk)
        NexusFeedEInkStyler.applyMeta(sourceMetaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)

        addView(coverImageView)
        addView(scrimView)

        val bottomContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }

            val metaRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(monogramView)
                addView(sourceMetaView)
            }
            addView(metaRow)

            val headlineRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(titleView)
                addView(bookmarkBtn)
            }
            addView(headlineRow)
        }
        addView(bottomContent)
    }

    private var currentArticleLink: String? = null
    var onArticleLongPress: ((FeedArticle) -> Unit)? = null

    fun bind(article: FeedArticle) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        if (isEInk) tokens = NexusFeedEInkCoordinator.getTokens(context)
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
        updateHeroScrim(isEInk, isDark)
        NexusFeedEInkStyler.applyHeroTitle(titleView, if (isEInk) tokens.textPrimary else Color.WHITE, isEInk)
        NexusFeedEInkStyler.applyMeta(sourceMetaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)
        NexusFeedEInkStyler.applyImageGrayscale(coverImageView, isEInk)

        currentArticleLink = article.link
        titleView.text = article.title
        monogramView.text = NexusFeedImageLoader.getMonogram(article.sourceName)
        sourceMetaView.text = buildMeta(article)

        isBookmarked = FeedBookmarkStore.isBookmarked(context, article.link)
        updateBookmarkIcon()

        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            com.nexus.launcher.reader.NexusReaderLauncher.openArticle(context, article)
        }

        setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onArticleLongPress?.invoke(article)
            true
        }

        loadJob?.cancel()
        val key = article.imageUrl ?: article.imageUrlFallback ?: article.link
        val cached = NexusFeedImageLoader.getCached(key)
        if (cached != null) {
            coverImageView.alpha = 1f
            coverImageView.setImageBitmap(com.nexus.launcher.util.BitmapSizeGuard.guard("NexusFeedHeroCardView.cached", cached))
            return
        }

        coverImageView.alpha = 0f
        loadJob = scope.launch {
            val primary = article.imageUrl
            val fallback = article.imageUrlFallback
            val bmp = (primary?.let { NexusFeedImageLoader.fetchBitmap(it, 1080, 600) }
                ?: fallback?.let { NexusFeedImageLoader.fetchBitmap(it, 1080, 600) }
                ?: NexusFeedImageLoader.extractOgImage(article.link)?.let { NexusFeedImageLoader.fetchBitmap(it, 1080, 600) })

            if (bmp != null) {
                NexusFeedImageLoader.putCached(key, bmp)
                coverImageView.setImageBitmap(com.nexus.launcher.util.BitmapSizeGuard.guard("NexusFeedHeroCardView.fresh", bmp))
                if (NexusFeedEInkCoordinator.shouldReduceMotion(context)) {
                    coverImageView.alpha = 1f
                } else {
                    coverImageView.animate().alpha(1f).setDuration(180).start()
                }
            } else {
                coverImageView.alpha = 0f
            }
        }
    }

    private fun toggleBookmark(link: String? = currentArticleLink) {
        if (link.isNullOrBlank()) return
        isBookmarked = FeedBookmarkStore.toggleBookmark(context, link)
        updateBookmarkIcon()
    }

    private fun updateBookmarkIcon() {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        bookmarkBtn.setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        bookmarkBtn.setColorFilter(if (isBookmarked && !isEInk) tokens.accent else tokens.textPrimary)
    }

    private fun buildMeta(article: FeedArticle): String {
        val ago = formatTimeAgo(article.publishedAt)
        return if (ago.isNotBlank()) "${article.sourceName} • $ago" else article.sourceName
    }

    private fun formatTimeAgo(epochMs: Long): String = NexusFeedTimeAgo.format(context, epochMs)

    fun applyTokens(newTokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens
        updateHeroScrim(isEInk, isDark)
        NexusFeedEInkStyler.applyHeroTitle(titleView, if (isEInk) tokens.textPrimary else android.graphics.Color.WHITE, isEInk)
        NexusFeedEInkStyler.applyMeta(sourceMetaView, tokens.textSecondary, isEInk)
        NexusFeedEInkStyler.applyMonogram(monogramView, tokens.textPrimary, tokens.surfaceRaised, isEInk, dp)
        NexusFeedEInkStyler.applyImageGrayscale(coverImageView, isEInk)
        updateBookmarkIcon()
        background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 24f)
    }
}
