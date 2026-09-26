package com.nexus.launcher.reader

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.feed.NexusFeedEInkStyler
import com.nexus.launcher.feed.NexusFeedImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders the formatted article elements, typography, hero image, and metadata inside the Reader view.
 */
object NexusReaderViewBinder {

    /**
     * One body paragraph, styled for [palette]. Shared so a text document appended a chunk at a
     * time (NexusTxtDocumentBinder) reads exactly like one rendered in a single pass.
     */
    fun paragraphView(
        context: Context,
        text: String,
        palette: NexusReaderThemeHelper.ReaderPalette,
        dp: Float
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = 16f
        setTextColor(palette.textPrimary)
        typeface = if (palette.isEInk) NexusFeedEInkStyler.serifRegular else Typeface.DEFAULT
        setLineSpacing(0f, 1.6f)
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = (18 * dp).toInt() }
    }

    /**
     * A picture from the body of the article. Same treatment as the hero: square on paper, grey on
     * paper, and loaded off the main thread.
     */
    private fun articleImage(
        context: Context,
        scope: CoroutineScope,
        element: ArticleElement.Image,
        palette: NexusReaderThemeHelper.ReaderPalette,
        dp: Float,
    ): ImageView = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (200 * dp).toInt()
        ).apply { bottomMargin = (10 * dp).toInt() }
        scaleType = ImageView.ScaleType.CENTER_CROP
        clipToOutline = true
        background = GradientDrawable().apply {
            setColor(palette.surfaceRaised)
            cornerRadius = if (palette.isEInk) 0f else 12 * dp
        }
        if (palette.isEInk) NexusFeedEInkStyler.applyImageGrayscale(this, isEInk = true)
        scope.launch {
            val bmp = NexusFeedImageLoader.fetchBitmap(element.url, 800, 450)
            if (bmp != null) {
                withContext(Dispatchers.Main) { setImageBitmap(bmp) }
            } else {
                withContext(Dispatchers.Main) { visibility = View.GONE }
            }
        }
    }

    /**
     * The article's picture: the one the feed handed over, or the one its own page declares.
     *
     * The feed's cards have always tried both — a link out of an RSS item is often missing, stale,
     * or refused to anyone but the site itself — while the reader only ever tried the first, which
     * is why an article with a thumbnail in the feed opened without one.
     */
    private suspend fun heroBitmap(article: ExtractedArticle): android.graphics.Bitmap? {
        article.heroImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            NexusFeedImageLoader.fetchBitmap(url, 800, 450)?.let { return it }
        }
        val declared = NexusFeedImageLoader.extractOgImage(article.url) ?: return null
        return NexusFeedImageLoader.fetchBitmap(declared, 800, 450)
    }

    fun bind(
        context: Context,
        scope: CoroutineScope,
        container: LinearLayout,
        article: ExtractedArticle,
        palette: NexusReaderThemeHelper.ReaderPalette,
        dp: Float
    ) {
        container.removeAllViews()

        // Hero Image. Built even without a URL in hand: the article's own page usually declares
        // one, and the view hides itself if nothing arrives.
        run {
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (220 * dp).toInt()).apply {
                    bottomMargin = (20 * dp).toInt()
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GradientDrawable().apply {
                    setColor(palette.surfaceRaised)
                    cornerRadius = if (palette.isEInk) 0f else 16 * dp
                    if (palette.isEInk) {
                        val strokeColor = if (palette.isDarkPaper) 0x2AE8E5DF.toInt() else 0x2A2A2A2A.toInt()
                        setStroke((1 * dp).toInt().coerceAtLeast(1), strokeColor)
                    }
                }
            }
            container.addView(imageView)

            if (palette.isEInk) NexusFeedEInkStyler.applyImageGrayscale(imageView, isEInk = true)

            scope.launch {
                val bmp = heroBitmap(article)
                withContext(Dispatchers.Main) {
                    if (bmp != null) imageView.setImageBitmap(bmp) else imageView.visibility = View.GONE
                }
            }
        }

        // Title
        val titleView = TextView(context).apply {
            text = article.title
            textSize = 24f
            setTextColor(palette.textPrimary)
            typeface = if (palette.isEInk) NexusFeedEInkStyler.serifBold else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setLineSpacing(0f, 1.25f)
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }
        container.addView(titleView)

        // Metadata line
        val metaString = buildMetaLine(context, article)
        val metaView = TextView(context).apply {
            text = metaString
            textSize = 12f
            setTextColor(palette.textSecondary)
            if (palette.isEInk) {
                typeface = Typeface.MONOSPACE
                letterSpacing = 0.05f
            } else {
                typeface = Typeface.DEFAULT
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (16 * dp).toInt()
            }
        }
        container.addView(metaView)

        // Divider
        val divider = View(context).apply {
            background = android.graphics.drawable.ColorDrawable(palette.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                bottomMargin = (20 * dp).toInt()
            }
        }
        container.addView(divider)

        // Paragraphs & Body Elements
        article.elements.forEach { elem ->
            when (elem) {
                is ArticleElement.Paragraph -> {
                    container.addView(paragraphView(context, elem.text, palette, dp))
                }
                is ArticleElement.Heading -> {
                    container.addView(TextView(context).apply {
                        text = elem.text
                        textSize = if (elem.level == 2) 20f else 18f
                        setTextColor(palette.textPrimary)
                        typeface = if (palette.isEInk) NexusFeedEInkStyler.serifBold else Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        setLineSpacing(0f, 1.3f)
                        setTextIsSelectable(true)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = (16 * dp).toInt()
                            bottomMargin = (10 * dp).toInt()
                        }
                    })
                }
                is ArticleElement.Image -> {
                    container.addView(articleImage(context, scope, elem, palette, dp))
                    elem.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                        container.addView(TextView(context).apply {
                            text = caption
                            textSize = 12f
                            setTextColor(palette.textSecondary)
                            typeface = if (palette.isEInk) {
                                Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                            } else {
                                Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                            }
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = (18 * dp).toInt() }
                        })
                    }
                }
                is ArticleElement.Blockquote -> {
                    val quoteContainer = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding((12 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            bottomMargin = (18 * dp).toInt()
                        }
                    }
                    val bar = View(context).apply {
                        background = android.graphics.drawable.ColorDrawable(palette.divider)
                        layoutParams = LinearLayout.LayoutParams((3 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginEnd = (12 * dp).toInt()
                        }
                    }
                    val quoteText = TextView(context).apply {
                        text = elem.text
                        textSize = 15f
                        setTextColor(palette.textSecondary)
                        typeface = if (palette.isEInk) Typeface.create(Typeface.SERIF, Typeface.ITALIC) else Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                        setLineSpacing(0f, 1.5f)
                        setTextIsSelectable(true)
                    }
                    quoteContainer.addView(bar)
                    quoteContainer.addView(quoteText)
                    container.addView(quoteContainer)
                }
                else -> {}
            }
        }
    }

    private fun buildMetaLine(context: Context, article: ExtractedArticle): String {
        val parts = mutableListOf<String>()
        article.author?.let { if (it.isNotBlank()) parts.add(it) }
        article.publishDate?.let { if (it.isNotBlank()) parts.add(it) }
        val readTime = if (article.readingTimeMinutes <= 1) {
            context.getString(R.string.nexus_reader_reading_time_single)
        } else {
            context.getString(R.string.nexus_reader_reading_time, article.readingTimeMinutes)
        }
        parts.add(readTime)
        return parts.joinToString("  ·  ")
    }
}
