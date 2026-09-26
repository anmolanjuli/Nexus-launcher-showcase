package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class SpatialCardTitle(context: Context) : LinearLayout(context) {
    var onColorClick: (() -> Unit)? = null
    private val badge = CategoryBadge(context)
    private val nameView = TextView(context)
    private val subtitleView = TextView(context)
    private val chevron = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        val density = resources.displayMetrics.density
        badge.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (12f * density).toInt() }
        badge.setOnClickListener { onColorClick?.invoke() }
        addView(badge)
        val textCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        textCol.addView(nameView)
        textCol.addView(subtitleView)
        addView(textCol)
        chevron.text = context.getString(R.string.drawer_category_chevron)
        addView(chevron)
    }

    fun bind(category: CategoryGroup, palette: CategoryPalette, colorEditable: Boolean) {
        badge.bind(category.key, palette, selected = true, sizeDp = 36f)
        badge.isClickable = colorEditable
        badge.isFocusable = colorEditable
        badge.contentDescription = if (colorEditable) {
            context.getString(R.string.drawer_category_color_change)
        } else {
            category.name
        }
        NexusTypeScale.bodyStrong.bindTo(nameView, palette.textPrimary)
        nameView.text = category.name
        NexusTypeScale.caption.bindTo(subtitleView, palette.textSecondary)
        subtitleView.text = category.subtitle
        NexusTypeScale.bodyStrong.bindTo(chevron, palette.textSecondary)
    }
}
