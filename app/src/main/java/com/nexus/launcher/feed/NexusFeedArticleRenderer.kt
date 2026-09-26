package com.nexus.launcher.feed

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/** Feed tab renders one horizontal strip per source — not one strip cramming every source
 *  together — so the list stays organized as sources grow; the caller already sorts articles so
 *  custom (non-curated) sources lead, and grouping preserves that order, putting their strips
 *  first. Headline-only mode keeps the same per-source strip layout with compact text-only cards
 *  instead of falling back to a vertical list. Explore/Saved tabs keep the original vertical
 *  stack (image cards, or headline rows in headline-only mode). */
object NexusFeedArticleRenderer {

    /** Returns the [HorizontalScrollView]s it created (Feed tab only) — the caller needs these to
     *  teach the page's swipe-to-dismiss gesture detector to yield to a strip's own horizontal
     *  scrolling; empty for the vertical-stack tabs. */
    fun render(
        context: Context,
        cardsContainer: LinearLayout,
        dp: Float,
        articles: List<FeedArticle>,
        activeTab: NexusFeedBottomBar.Tab,
        isHeadlineOnly: Boolean,
        onArticleLongPress: (FeedArticle) -> Unit
    ): List<HorizontalScrollView> {
        cardsContainer.removeAllViews()

        if (activeTab == NexusFeedBottomBar.Tab.FEED) {
            return renderPerSourceStrips(context, cardsContainer, dp, articles, isHeadlineOnly, onArticleLongPress)
        }

        if (isHeadlineOnly) {
            for (article in articles) {
                cardsContainer.addView(NexusFeedHeadlineRowView(context).apply {
                    bind(article)
                    this.onArticleLongPress = onArticleLongPress
                })
            }
        } else {
            renderVerticalStack(context, cardsContainer, articles, onArticleLongPress)
        }
        return emptyList()
    }

    private fun renderVerticalStack(
        context: Context,
        cardsContainer: LinearLayout,
        articles: List<FeedArticle>,
        onArticleLongPress: (FeedArticle) -> Unit
    ) {
        cardsContainer.addView(NexusFeedHeroCardView(context).apply {
            bind(articles[0])
            this.onArticleLongPress = onArticleLongPress
        })
        for (i in 1 until articles.size) {
            cardsContainer.addView(NexusFeedCompactCardView(context).apply {
                bind(articles[i])
                this.onArticleLongPress = onArticleLongPress
            })
        }
    }

    private fun renderPerSourceStrips(
        context: Context,
        cardsContainer: LinearLayout,
        dp: Float,
        articles: List<FeedArticle>,
        isHeadlineOnly: Boolean,
        onArticleLongPress: (FeedArticle) -> Unit
    ): List<HorizontalScrollView> {
        val tokens = ThemeObserver.currentTokens(context)
        val strips = mutableListOf<HorizontalScrollView>()
        val bySource = articles.groupBy { it.sourceId }

        bySource.values.forEachIndexed { index, sourceArticles ->
            if (sourceArticles.isEmpty()) return@forEachIndexed

            cardsContainer.addView(TextView(context).apply {
                text = sourceArticles.first().sourceName
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(0, if (index == 0) 0 else (18 * dp).toInt(), 0, (10 * dp).toInt())
            })

            val hScroll = HorizontalScrollView(context).apply {
                overScrollMode = View.OVER_SCROLL_NEVER
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
            }
            val strip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, (20 * dp).toInt(), 0)
            }
            hScroll.addView(strip)

            if (isHeadlineOnly) {
                for (article in sourceArticles) {
                    strip.addView(NexusFeedHeadlineStripCardView(context).apply {
                        bind(article)
                        this.onArticleLongPress = onArticleLongPress
                        layoutParams = LinearLayout.LayoutParams((220 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = (12 * dp).toInt()
                        }
                    })
                }
            } else {
                addImageCards(context, strip, dp, sourceArticles, onArticleLongPress)
            }

            cardsContainer.addView(hScroll)
            strips.add(hScroll)
        }

        return strips
    }

    /** Hero card first at full size, remaining articles fill two-row columns sized to match the
     *  hero card's height exactly, rather than one long single-height row. */
    private fun addImageCards(
        context: Context,
        strip: LinearLayout,
        dp: Float,
        sourceArticles: List<FeedArticle>,
        onArticleLongPress: (FeedArticle) -> Unit
    ) {
        val heroHeight = (280 * dp).toInt()
        strip.addView(NexusFeedHeroCardView(context).apply {
            bind(sourceArticles[0])
            this.onArticleLongPress = onArticleLongPress
            layoutParams = LinearLayout.LayoutParams((300 * dp).toInt(), heroHeight).apply {
                marginEnd = (14 * dp).toInt()
            }
        })

        var i = 1
        while (i < sourceArticles.size) {
            val top = sourceArticles[i]
            val bottom = sourceArticles.getOrNull(i + 1)
            val columnWidth = (300 * dp).toInt()
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(columnWidth, heroHeight).apply {
                    marginEnd = (14 * dp).toInt()
                }
            }
            column.addView(NexusFeedCompactCardView(context).apply {
                configureForHorizontalStrip()
                bind(top)
                this.onArticleLongPress = onArticleLongPress
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    bottomMargin = (6 * dp).toInt()
                }
            })
            if (bottom != null) {
                column.addView(NexusFeedCompactCardView(context).apply {
                    configureForHorizontalStrip()
                    bind(bottom)
                    this.onArticleLongPress = onArticleLongPress
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                })
            }
            strip.addView(column)
            i += 2
        }
    }
}
