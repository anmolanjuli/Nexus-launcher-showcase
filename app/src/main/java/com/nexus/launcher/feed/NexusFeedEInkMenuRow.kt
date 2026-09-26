package com.nexus.launcher.feed

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusElasticSwitch

/**
 * Builds the E-Ink Paper Mode toggle rows for Nexus Feed's header menu and settings sheets.
 * Handles Premium paywall gating, mode persistence, and Dark Paper sub-toggle visibility.
 */
object NexusFeedEInkMenuRow {

    fun build(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        onModeChanged: () -> Unit
    ): LinearLayout {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        var isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        var isDark = NexusFeedEInkCoordinator.isEInkDark(context)

        val darkPaperRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            visibility = if (isEInk) View.VISIBLE else View.GONE

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (10 * dp).toInt()
                }
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_eink_dark_title)
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_eink_dark_desc)
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (2 * dp).toInt()
                    }
                })
            }
            addView(textCol)

            val darkToggle = NexusElasticSwitch(context).apply {
                paperMode = isEInk
                applyTokens(tokens)
                setChecked(isDark, animate = false)
                onCheckedChange = { checked ->
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (!PremiumGate.allow(context, PremiumFeature.EINK_FEED)) {
                        setChecked(false, animate = true)
                    } else {
                        isDark = checked
                        NexusFeedEInkCoordinator.setEInkDark(context, checked)
                        onModeChanged()
                    }
                }
            }
            addView(darkToggle)
        }

        val einkRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (10 * dp).toInt()
                }
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(TextView(context).apply {
                        text = context.getString(R.string.nexus_feed_eink_title)
                        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    })
                    val pill = com.nexus.launcher.ui.premium.PremiumBadges.pill(context, tokens)
                    addView(pill, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = (8 * dp).toInt() })
                    com.nexus.launcher.ui.premium.PremiumBadges.bindPill(pill, PremiumFeature.EINK_FEED)
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_eink_desc)
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (2 * dp).toInt()
                    }
                })
            }
            addView(textCol)

            val einkToggle = NexusElasticSwitch(context).apply {
                paperMode = isEInk
                applyTokens(tokens)
                setChecked(isEInk, animate = false)
                onCheckedChange = { checked ->
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (checked && !PremiumGate.allow(context, PremiumFeature.EINK_FEED)) {
                        setChecked(false, animate = true)
                    } else {
                        isEInk = checked
                        NexusFeedEInkCoordinator.setEInkMode(context, checked)
                        darkPaperRow.visibility = if (checked) View.VISIBLE else View.GONE
                        onModeChanged()
                    }
                }
            }
            addView(einkToggle)
        }

        root.addView(einkRow)
        root.addView(darkPaperRow)

        return root
    }
}
