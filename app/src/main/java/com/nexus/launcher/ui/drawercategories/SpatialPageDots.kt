package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

class SpatialPageDots(context: Context) : LinearLayout(context) {
    private val palette = CategoryPalette(context)
    private val dots = mutableListOf<View>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    fun bind(categories: List<CategoryGroup>, selected: Int) {
        bind(categories.size, selected)
    }

    fun bind(count: Int, selected: Int) {
        if (dots.size != count) {
            removeAllViews()
            dots.clear()
            val density = resources.displayMetrics.density
            val size = (6f * density).toInt()
            repeat(count) {
                val dot = View(context)
                val lp = LayoutParams(size, size).apply {
                    marginStart = (3f * density).toInt()
                    marginEnd = (3f * density).toInt()
                }
                addView(dot, lp)
                dots.add(dot)
            }
        }
        val density = resources.displayMetrics.density
        dots.forEachIndexed { index, dot ->
            dot.background = CategorySurfaces.categoryChip(palette, density, index == selected)
        }
    }
}
