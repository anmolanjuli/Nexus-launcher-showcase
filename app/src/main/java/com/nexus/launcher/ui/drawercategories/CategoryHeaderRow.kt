package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class CategoryHeaderRow(context: Context) : LinearLayout(context) {
    private val badge = CategoryBadge(context)
    private val nameView = TextView(context)
    private val countView = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        val density = resources.displayMetrics.density
        badge.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (12f * density).toInt() }
        addView(badge)

        val textCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(nameView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        textCol.addView(countView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(textCol)
    }

    fun bind(category: CategoryGroup, palette: CategoryPalette) {
        badge.bind(category.key, palette, selected = true, sizeDp = 28f, iconResOverride = category.badgeIcon)
        NexusTypeScale.bodyStrong.bindTo(nameView, palette.textPrimary)
        nameView.text = category.name
        NexusTypeScale.caption.bindTo(countView, palette.textSecondary)
        countView.text = resources.getQuantityString(
            R.plurals.drawer_category_app_count,
            category.apps.size,
            category.apps.size,
        )
    }
}
