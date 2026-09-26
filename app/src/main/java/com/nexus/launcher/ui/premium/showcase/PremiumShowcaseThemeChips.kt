package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.CalmPalette
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * The Extra themes preview's chip row: AMOLED and every Calm palette. Picking one redraws the
 * preview cards in that theme's own tokens, so what is shown is what the theme would paint.
 */
internal object PremiumShowcaseThemeChips {

    private class Choice(val label: String, val tokens: NexusColorTokens, val palette: CalmPalette?)

    fun build(context: Context, pageTokens: NexusColorTokens, mock: PremiumShowcaseMock): View {
        val dp = context.resources.displayMetrics.density
        val choices = listOf(Choice(context.getString(R.string.theme_mode_amoled), NexusColorTokens.Amoled, null)) +
            CalmPalette.entries.map { Choice(context.getString(it.displayNameRes), NexusColorTokens.getCalmTokens(it), it) }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = mutableListOf<TextView>()

        fun select(index: Int) {
            val choice = choices[index]
            mock.setPreviewTheme(choice.tokens, choice.palette)
            chips.forEachIndexed { i, chip ->
                val on = i == index
                (chip.background as GradientDrawable).setColor(if (on) pageTokens.textPrimary else pageTokens.surfaceRaised)
                chip.setTextColor(if (on) pageTokens.bg else pageTokens.textSecondary)
            }
        }

        choices.forEachIndexed { index, choice ->
            val chip = TextView(context).apply {
                text = choice.label
                NexusTypeScale.caption.bindTo(this, pageTokens.textSecondary)
                val h = (14 * dp).toInt()
                val v = (8 * dp).toInt()
                setPadding(h, v, h, v)
                background = GradientDrawable().apply { cornerRadius = 999f * dp }
                setOnClickListener { select(index) }
            }
            chips += chip
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { if (index > 0) marginStart = (8 * dp).toInt() })
        }
        // Calm's Ocean Blue first, as the tile shows it.
        select(choices.indexOfFirst { it.palette == CalmPalette.OCEAN_BLUE })

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
    }
}
