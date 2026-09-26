package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.ui.NexusDesignSystem

/** Static Mosaic page chrome construction. */
internal object LivingMosaicChrome {
    fun modeButton(context: Context, dp: Float, onTap: () -> Unit): TextView = TextView(context).apply {
        text = "⇆"
        contentDescription = context.getString(com.nexus.launcher.R.string.content_desc_switch_mosaic_mode)
        textSize = 21f
        gravity = Gravity.CENTER
        setTextColor(Color.parseColor(NexusDesignSystem.COLOR_ACCENT))
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE))
            setStroke((1.5f * dp).toInt().coerceAtLeast(1), Color.parseColor(NexusDesignSystem.COLOR_ACCENT))
        }
        layoutParams = FrameLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt()).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = (6 * dp).toInt()
            marginEnd = (6 * dp).toInt()
        }
        visibility = View.GONE
        setOnClickListener { onTap() }
    }

    fun dots(context: Context, dp: Float): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = (4 * dp).toInt()
        }
        visibility = android.view.View.GONE
    }
}
