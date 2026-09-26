package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.ui.DrawerCategories

class CategoryBadge(context: Context) : ImageView(context) {
    init {
        scaleType = ScaleType.CENTER_INSIDE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = null
    }

    fun bind(
        key: String,
        palette: CategoryPalette,
        selected: Boolean = true,
        sizeDp: Float = 36f,
        iconResOverride: Int = 0,
    ) {
        val density = resources.displayMetrics.density
        val size = (sizeDp * density).toInt()
        val next = (layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(size, size)
        next.width = size
        next.height = size
        layoutParams = next
        setPadding(0, 0, 0, 0)
        val iconRes = when {
            iconResOverride != 0 -> iconResOverride
            key.isNotEmpty() -> DrawerCategories.iconFor(context, key)
            else -> R.drawable.ic_category_custom
        }
        setImageDrawable(ContextCompat.getDrawable(context, iconRes)?.mutate())
        background = null
        // The unselected ones are the same ink, just quieter: the secondary grey disappeared
        // against a light theme's plate.
        imageTintList = ColorStateList.valueOf(
            if (selected) palette.textPrimary
            else androidx.core.graphics.ColorUtils.setAlphaComponent(palette.textPrimary, 0xB3),
        )
    }
}
