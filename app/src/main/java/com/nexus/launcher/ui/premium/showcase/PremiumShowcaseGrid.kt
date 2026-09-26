package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.PremiumFeatureCardView

/**
 * The Premium page's picture tiles, and the expander holding every remaining feature as a row.
 *
 * [register] hands each feature's view back to the page, so closing a preview can scroll to it.
 */
internal object PremiumShowcaseGrid {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun tiles(
        context: Context,
        features: List<PremiumFeature>,
        tokens: NexusColorTokens,
        onOpen: (PremiumFeature) -> Unit,
        register: (PremiumFeature, View) -> Unit,
    ): View {
        val dp = context.resources.displayMetrics.density
        val gap = (12 * dp).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = (16 * dp).toInt() }
            features.chunked(2).forEachIndexed { index, pair ->
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    pair.forEachIndexed { i, feature ->
                        val tile = tile(context, feature, tokens, onOpen)
                        register(feature, tile)
                        addView(tile, LinearLayout.LayoutParams(0, WRAP, 1f).apply { if (i > 0) marginStart = gap })
                    }
                    if (pair.size == 1) addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f).apply { marginStart = gap })
                }, LinearLayout.LayoutParams(MATCH, WRAP).apply { if (index > 0) topMargin = gap })
            }
        }
    }

    private fun tile(
        context: Context,
        feature: PremiumFeature,
        tokens: NexusColorTokens,
        onOpen: (PremiumFeature) -> Unit,
    ): View {
        val dp = context.resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            PremiumShowcaseSections.surface(this, tokens, 20f)
            val thumb = (120 * dp).toInt()
            addView(PremiumShowcaseSections.pictureFrame(context, tokens, 16f).apply {
                addView(PremiumShowcaseArt.view(context, feature, tokens), MATCH, MATCH)
            }, LinearLayout.LayoutParams(thumb, thumb))
            addView(TextView(context).apply {
                text = context.getString(feature.titleRes)
                gravity = Gravity.CENTER
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = (10 * dp).toInt() })
            addView(TextView(context).apply {
                text = context.getString(feature.descriptionRes)
                gravity = Gravity.CENTER
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = (2 * dp).toInt() })
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onOpen(feature)
            }
        }
    }

    fun more(
        context: Context,
        features: List<PremiumFeature>,
        tokens: NexusColorTokens,
        expanded: Boolean,
        onExpandedChange: (Boolean) -> Unit,
        onOpen: (PremiumFeature) -> Unit,
        register: (PremiumFeature, View) -> Unit,
    ): View {
        val dp = context.resources.displayMetrics.density
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (expanded) View.VISIBLE else View.GONE
            features.forEach { feature ->
                val row = PremiumFeatureCardView(context, feature, onOpen).apply { applyTokens(tokens) }
                register(feature, row)
                addView(row)
            }
        }
        fun label(open: Boolean) = if (open) {
            context.getString(R.string.premium_showcase_less)
        } else {
            context.getString(R.string.premium_showcase_more, features.size)
        }
        val header = TextView(context).apply {
            text = label(expanded)
            gravity = Gravity.CENTER
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            PremiumShowcaseSections.surface(this, tokens, 20f)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val open = list.visibility != View.VISIBLE
                list.visibility = if (open) View.VISIBLE else View.GONE
                text = label(open)
                onExpandedChange(open)
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = (16 * dp).toInt() }
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(list, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = (8 * dp).toInt() })
        }
    }
}
