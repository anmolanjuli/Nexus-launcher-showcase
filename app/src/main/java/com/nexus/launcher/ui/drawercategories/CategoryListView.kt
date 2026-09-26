package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.canvas.DrawerAlphabetHelper

class CategoryListView(
    context: Context,
    categories: List<CategoryGroup>,
    untaggedFolders: List<CategoryApp> = emptyList(),
    private val onAppClick: ((CategoryApp) -> Unit)? = null,
    private val onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
    reserveRail: Boolean = true,
) : ScrollView(context) {
    private val palette = CategoryPalette(context)
    private val content = LinearLayout(context)
    private val letterAnchors = mutableListOf<Pair<Char, Int>>()
    private val categoryAnchors = mutableListOf<Pair<Int, android.view.View>>()

    init {
        isFillViewport = true
        clipToPadding = true
        clipChildren = true
        isVerticalScrollBarEnabled = false
        val density = resources.displayMetrics.density
        val pad = (16f * density).toInt()
        // The rail sits over this edge — leave its full lane clear so letters never land on icons.
        val end = if (reserveRail) (44f * density).toInt() else pad
        setPadding(pad, (8f * density).toInt(), end, pad)
        content.orientation = LinearLayout.VERTICAL
        content.isBaselineAligned = false
        content.clipChildren = true
        addView(
            content,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        val blocks = buildList {
            if (untaggedFolders.isNotEmpty()) {
                add(
                    CategoryGroup(
                        name = context.getString(R.string.drawer_category_untagged_folders),
                        subtitle = "",
                        accent = palette.mistBlue,
                        apps = untaggedFolders,
                        quickActions = emptyList(),
                        recents = emptyList(),
                        badgeIcon = R.drawable.ic_folder_solid,
                    )
                )
            }
            addAll(categories)
        }
        blocks.forEachIndexed { index, category ->
            addBlock(category, index == 0, density)
        }
    }

    /** Scrolls [categoryId]'s header to the top; ignored when it is not in this list. */
    fun scrollToCategory(categoryId: Int) {
        val header = categoryAnchors.firstOrNull { it.first == categoryId }?.second ?: return
        post { scrollTo(0, (header.top - paddingTop).coerceAtLeast(0)) }
    }

    fun scrollToLetter(letter: Char) {
        val y = letterAnchors.firstOrNull { it.first == letter }?.second ?: return
        scrollTo(0, y)
    }

    private fun addBlock(category: CategoryGroup, first: Boolean, density: Float) {
        val header = CategoryHeaderRow(context)
        header.bind(category, palette)
        val headerLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = if (first) 0 else (20f * density).toInt()
            bottomMargin = (8f * density).toInt()
        }
        content.addView(header, headerLp)
        if (category.id > 0) categoryAnchors.add(category.id to header)
        recordLetters(category, header)
        val grid = CategoryAppGrid.create(context)
        CategoryAppGrid.bind(
            grid,
            category.apps,
            lightPlate = true,
            onAppClick = onAppClick,
            onAppLongPress = onAppLongPress,
        )
        content.addView(
            grid,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun recordLetters(category: CategoryGroup, header: android.view.View) {
        val locale = LocaleObserver.getEffectiveLocale(context)
        header.post {
            val y = header.top
            category.apps.forEach { app ->
                val letter = DrawerAlphabetHelper.getRailLetter(app.label, locale)
                if (letterAnchors.none { it.first == letter }) {
                    letterAnchors.add(letter to y)
                }
            }
        }
    }
}
