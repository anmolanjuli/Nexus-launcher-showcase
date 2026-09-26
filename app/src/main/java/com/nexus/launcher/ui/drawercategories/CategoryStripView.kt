package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nexus.launcher.R

class CategoryStripView(
    context: Context,
    private val categories: List<CategoryGroup>,
    frequent: List<CategoryApp>,
    untaggedFolders: List<CategoryApp> = emptyList(),
    private val showSearch: Boolean = true,
    private val onAppClick: ((CategoryApp) -> Unit)? = null,
    private val onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
) : LinearLayout(context) {
    private val cardsHost = LinearLayout(context)
    private val palette = CategoryPalette(context)
    private val cardAnchors = mutableListOf<Pair<Int, android.view.View>>()
    private var scrollHost: ScrollView? = null

    init {
        orientation = VERTICAL
        isBaselineAligned = false
        clipToPadding = true
        clipChildren = true
        val density = resources.displayMetrics.density
        val pad = (16f * density).toInt()
        setPadding(pad, (4f * density).toInt(), pad, pad)
        if (showSearch) {
            addView(StripSearchBar(context), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10f * density).toInt()
            })
        }
        cardsHost.orientation = VERTICAL
        cardsHost.isBaselineAligned = false
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = true
            clipChildren = true
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            addView(cardsHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        scrollHost = scroll
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val gap = (10f * density).toInt()
        if (frequent.isNotEmpty()) {
            addCard(
                CategoryGroup(
                    name = context.getString(R.string.drawer_category_recent),
                    subtitle = "",
                    accent = palette.mistBlue,
                    apps = frequent,
                    quickActions = emptyList(),
                    recents = emptyList(),
                    badgeIcon = R.drawable.ic_recent,
                ),
                frequent,
                gap,
            )
        }
        if (untaggedFolders.isNotEmpty()) {
            addCard(
                CategoryGroup(
                    name = context.getString(R.string.drawer_category_untagged_folders),
                    subtitle = "",
                    accent = palette.mistBlue,
                    apps = untaggedFolders,
                    quickActions = emptyList(),
                    recents = emptyList(),
                    badgeIcon = R.drawable.ic_folder_solid,
                ),
                untaggedFolders,
                gap,
            )
        }
        categories.forEach { category ->
            addCard(category, category.apps, gap)
        }
    }

    /** Scrolls [categoryId]'s card to the top; ignored when it is not in this strip. */
    fun scrollToCategory(categoryId: Int) {
        val card = cardAnchors.firstOrNull { it.first == categoryId }?.second ?: return
        val host = scrollHost ?: return
        host.post { host.smoothScrollTo(0, card.top) }
    }

    private fun addCard(category: CategoryGroup, apps: List<CategoryApp>, gap: Int) {
        val card = StripCategoryCard(context)
        card.bind(category, apps, onAppClick, onAppLongPress, showHeader = true)
        if (category.id > 0) cardAnchors.add(category.id to card)
        cardsHost.addView(
            card,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = gap
            },
        )
    }
}
