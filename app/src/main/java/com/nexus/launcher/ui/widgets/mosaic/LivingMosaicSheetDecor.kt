package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

object LivingMosaicSheetDecor {

    fun templateOptions(context: android.content.Context) = listOf(
        "" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_freeform),
        "hero_left_2_stacked" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_hero_l2),
        "even_2x2" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_even_2x2),
        "hero_top_3_row" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_hero_t3),
        "3_even_columns" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_3_cols_short),
        "hero_left_2_cols_stacked" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_hero_l4),
        "3_even_rows" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_3_rows),
        "even_3x3" to context.getString(com.nexus.launcher.R.string.mosaic_tpl_even_3x3),
    )

    fun cardBackground(tokens: NexusColorTokens, dp: Float) =
        com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.buildCardBackground(tokens, dp)

    fun mosaicBounds(view: LivingMosaicView, mainContainer: FrameLayout): Rect {
        val loc = IntArray(2)
        val container = IntArray(2)
        view.getLocationOnScreen(loc)
        mainContainer.getLocationOnScreen(container)
        return Rect(
            loc[0] - container[0],
            loc[1] - container[1],
            loc[0] - container[0] + view.width,
            loc[1] - container[1] + view.height
        )
    }

    fun buttonRow(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        left: String,
        right: String,
        click: (Int) -> Unit
    ) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
        addView(pillButton(context, tokens, dp, left) { click(-1) })
        addView(pillButton(context, tokens, dp, right) { click(1) })
    }

    fun pillButton(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        label: String,
        click: () -> Unit
    ) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 10 * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
        setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = (4 * dp).toInt()
            marginStart = (4 * dp).toInt()
        }
        setOnClickListener { click() }
    }
}
