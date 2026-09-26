package com.nexus.launcher.ui.widgets.mosaic

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.nexus.launcher.ui.NexusDesignSystem

/** Page / focus dots for Living Mosaic — keeps [LivingMosaicView] under the line limit. */
object LivingMosaicPageChrome {

    fun rebuildDots(
        dotsRow: LinearLayout,
        count: Int,
        activeIndex: Int,
        density: Float,
        onSelect: (Int) -> Unit
    ) {
        dotsRow.removeAllViews()
        val show = count > 1
        dotsRow.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val active = activeIndex.coerceIn(0, count - 1)
        for (i in 0 until count) {
            val isActive = i == active
            val w = if (isActive) (16 * density).toInt() else (6 * density).toInt()
            val dot = View(dotsRow.context).apply {
                layoutParams = LinearLayout.LayoutParams(w, (6 * density).toInt()).apply {
                    marginStart = (3 * density).toInt()
                    marginEnd = (3 * density).toInt()
                    gravity = Gravity.CENTER_VERTICAL
                }
                background = GradientDrawable().apply {
                    cornerRadius = 3f * density
                    setColor(
                        if (isActive) Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
                        else Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY)
                    )
                }
                setOnClickListener { onSelect(i) }
            }
            dotsRow.addView(dot)
        }
    }
}
