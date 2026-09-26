package com.nexus.launcher.ui.settings.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Interactive card displaying a specific premium feature with dedicated iconography,
 * descriptive value copy, and an action chip for opening the live interactive preview.
 * Styled cleanly via active [NexusColorTokens] in the minimal Linear-inspired style.
 */
class PremiumFeatureCardView(
    context: Context,
    val feature: PremiumFeature,
    private val onFeatureClick: (PremiumFeature) -> Unit,
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val cardBackground = GradientDrawable()
    private val iconPlateBackground = GradientDrawable()
    private val actionChipBackground = GradientDrawable()
    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val actionChip: TextView

    init {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            val marginV = (4 * density).toInt()
            setMargins(0, marginV, 0, marginV)
        }

        background = cardBackground
        clipToOutline = true

        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        foreground = ContextCompat.getDrawable(context, outValue.resourceId)
        isClickable = true
        isFocusable = true

        setOnClickListener {
            it.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
            onFeatureClick(feature)
        }

        val rowPaddingH = (16 * density).toInt()
        val rowPaddingV = (14 * density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(rowPaddingH, rowPaddingV, rowPaddingH, rowPaddingV)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        val iconPlateSize = (38 * density).toInt()
        val iconPlate = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconPlateSize, iconPlateSize).apply {
                marginEnd = (14 * density).toInt()
            }
            background = iconPlateBackground
        }

        iconView = ImageView(context).apply {
            val iconInner = (20 * density).toInt()
            layoutParams = LayoutParams(iconInner, iconInner, Gravity.CENTER)
            setImageResource(feature.iconRes)
        }
        iconPlate.addView(iconView)
        content.addView(iconPlate)

        val textGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * density).toInt()
            }
        }

        titleView = TextView(context).apply {
            text = context.getString(feature.titleRes)
        }
        textGroup.addView(titleView)

        subtitleView = TextView(context).apply {
            text = context.getString(feature.descriptionRes)
            val topMargin = (2 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { setMargins(0, topMargin, 0, 0) }
        }
        textGroup.addView(subtitleView)

        content.addView(textGroup)

        actionChip = TextView(context).apply {
            text = context.getString(R.string.premium_action_preview)
            setPadding((10 * density).toInt(), (5 * density).toInt(), (10 * density).toInt(), (5 * density).toInt())
            background = actionChipBackground
        }
        content.addView(actionChip)

        addView(content)
    }

    fun applyTokens(tokens: NexusColorTokens) {
        val strokeWidth = (1 * density).toInt().coerceAtLeast(1)

        cardBackground.apply {
            cornerRadius = 16f * density
            setColor(tokens.surface)
            setStroke(strokeWidth, tokens.divider)
        }

        iconPlateBackground.apply {
            cornerRadius = 10f * density
            setColor(tokens.surfaceRaised)
            setStroke(strokeWidth, tokens.divider)
        }

        actionChipBackground.apply {
            cornerRadius = 8f * density
            setColor(tokens.surfaceRaised)
            setStroke(strokeWidth, tokens.divider)
        }

        NexusTypeScale.bodyStrong.bindTo(titleView, tokens.textPrimary)
        NexusTypeScale.caption.bindTo(subtitleView, tokens.textSecondary)
        NexusTypeScale.caption.bindTo(actionChip, tokens.textSecondary)
        iconView.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
    }
}
