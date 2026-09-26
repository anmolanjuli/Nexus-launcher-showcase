package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout

class SpatialCardStack(
    context: Context,
    private val categories: List<CategoryGroup>,
    private val showTopBar: Boolean = true,
    private val showColorPicker: Boolean = true,
    private val showPageDots: Boolean = true,
    var onAppClick: ((CategoryApp) -> Unit)? = null,
    var onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
    var onAccentChanged: ((Int, Int) -> Unit)? = null,
    var onPageChanged: ((Int) -> Unit)? = null,
) : FrameLayout(context) {
    private val column = LinearLayout(context)
    private val pager = SpatialCardPager(context, categories, colorEditable = showColorPicker)
    private val rail = SpatialBottomRail(context)
    private val dots = SpatialPageDots(context)
    private val picker = SpatialColorPickerOverlay(context)

    fun currentPage(): Int = pager.currentPage()

    /** Page showing [categoryId], or null when this stack has no such category. */
    fun pageOf(categoryId: Int): Int? =
        categories.indexOfFirst { it.id == categoryId }.takeIf { it >= 0 }

    /** Without animating: used to put a rebuilt stack back on the page the reader was on. */
    fun setPage(index: Int) = pager.setPage(index)

    init {
        clipChildren = false
        clipToPadding = false
        column.orientation = LinearLayout.VERTICAL
        column.clipChildren = false
        column.clipToPadding = false
        val density = resources.displayMetrics.density
        if (showTopBar) {
            column.addView(SpatialTopBar(context), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (12f * density).toInt()
                marginEnd = (12f * density).toInt()
                bottomMargin = (8f * density).toInt()
            })
        }
        column.addView(pager, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(rail, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (6f * density).toInt()
            marginStart = (8f * density).toInt()
            marginEnd = (8f * density).toInt()
            bottomMargin = if (showPageDots) 0 else (4f * density).toInt()
        })
        if (showPageDots) {
            column.addView(dots, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6f * density).toInt()
                bottomMargin = (8f * density).toInt()
            })
        }
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        pager.onAppClick = onAppClick
        pager.onAppLongPress = onAppLongPress
        pager.onPageChanged = { page ->
            rail.bind(categories, page)
            if (showPageDots) dots.bind(categories, page)
            onPageChanged?.invoke(page)
        }
        if (showColorPicker) {
            addView(picker, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            pager.onColorClick = { index -> picker.show(categories[index].accent) }
            picker.onColorPicked = { color ->
                val index = pager.currentPage()
                categories[index].accent = color
                pager.rebind(index)
                rail.bind(categories, index)
                if (showPageDots) dots.bind(categories, index)
                onAccentChanged?.invoke(categories[index].id, color)
                picker.hide()
            }
        }
        rail.onCategoryChosen = { index -> pager.snapTo(index) }
        val startPage = pager.currentPage()
        rail.bind(categories, startPage)
        if (showPageDots) dots.bind(categories, startPage)
        onPageChanged?.invoke(startPage)
    }
}
