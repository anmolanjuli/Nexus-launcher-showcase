package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.typography.NexusTypeScale

/**
 * The strip of categories under the card stack. Only the category being shown is named; the rest
 * are their icon alone, so a long list of categories still fits across the strip.
 */
class SpatialBottomRail(context: Context) : LinearLayout(context) {
    var onCategoryChosen: ((Int) -> Unit)? = null
    private val palette = CategoryPalette(context)
    private val labels = mutableListOf<TextView>()
    private val badges = mutableListOf<CategoryBadge>()
    private val columns = mutableListOf<LinearLayout>()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isBaselineAligned = false
    }

    fun bind(categories: List<CategoryGroup>, selected: Int) {
        val density = resources.displayMetrics.density
        if (labels.isEmpty()) {
            categories.forEachIndexed { index, category ->
                val column = LinearLayout(context).apply {
                    orientation = VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val side = (3f * density).toInt()
                    setPadding(side, 0, side, 0)
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    // The named one is wider than the icons, so it takes a larger share.
                    setOnClickListener { onCategoryChosen?.invoke(index) }
                }
                val badge = CategoryBadge(context)
                val label = TextView(context).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    text = category.name
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (4f * density).toInt()
                    }
                }
                NexusTypeScale.labelSmall.bindTo(label, palette.textSecondary)
                column.addView(badge)
                column.addView(label)
                addView(column)
                badges.add(badge)
                labels.add(label)
                columns.add(column)
            }
        }
        badges.forEachIndexed { index, badge ->
            val selectedNow = index == selected
            val key = categories.getOrNull(index)?.key.orEmpty()
            badge.bind(
                key, palette, selectedNow,
                sizeDp = if (selectedNow) 32f else 24f,
            )
            val label = labels[index]
            label.visibility = if (selectedNow) VISIBLE else GONE
            // The named one needs the room its name takes; the icons share what is left.
            (columns[index].layoutParams as LayoutParams).weight = if (selectedNow) 3f else 1f
            columns[index].requestLayout()
            if (selectedNow) {
                label.text = categories.getOrNull(index)?.name.orEmpty()
                NexusTypeScale.bodyStrong.bindTo(label, palette.textPrimary)
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            val scale = if (selectedNow) 1.12f else 1f
            val column = columns[index]
            column.post {
                column.pivotX = column.width / 2f
                column.pivotY = column.height / 2f
                column.scaleX = scale
                column.scaleY = scale
            }
        }
    }
}
