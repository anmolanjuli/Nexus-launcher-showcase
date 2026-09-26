package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.NexusDesignSystem

object SettingsPlateViews {

    fun plate(context: Context, radiusDp: Float = 20f): LinearLayout {
        val density = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = NexusDesignSystem.buildPremiumPlate(density, radiusDp)
            clipToOutline = true
        }
    }

    fun paddedPlate(context: Context, vararg children: View): LinearLayout {
        val density = context.resources.displayMetrics.density
        val card = plate(context)
        val pad = (16 * density).toInt()
        card.setPadding(pad, pad, pad, pad)
        children.forEach { card.addView(it) }
        return card
    }

    fun navRow(
        context: Context,
        title: String,
        subtitle: String,
        iconRes: Int
    ): FrameLayout {
        val density = context.resources.displayMetrics.density
        val accent = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        val row = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (64 * density).toInt()
            )
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = ContextCompat.getDrawable(context, outValue.resourceId)
            isClickable = true
            isFocusable = true
        }
        val content = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = (16 * density).toInt()
            setPadding(hPad, 0, hPad, 0)
        }
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (20 * density).toInt(),
                (20 * density).toInt()
            ).apply {
                marginEnd = (14 * density).toInt()
            }
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY)
            )
        }
        content.addView(iconView)

        val text = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * density).toInt()
            }
        }
        val titleView = TextView(context).apply {
            this.text = title
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(
            titleView,
            Color.parseColor(NexusDesignSystem.COLOR_TEXT_PRIMARY)
        )
        text.addView(titleView)

        val subView = TextView(context).apply {
            this.text = subtitle
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        com.nexus.launcher.typography.NexusTypeScale.caption.bindTo(
            subView,
            Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY)
        )
        text.addView(subView)
        content.addView(text)
        content.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (20 * density).toInt(),
                (20 * density).toInt()
            )
            setImageResource(com.nexus.launcher.R.drawable.ic_chevron_forward)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY))
        })
        row.addView(content)
        return row
    }
}
