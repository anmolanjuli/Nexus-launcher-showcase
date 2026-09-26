package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/** Nexus / All segment control for the widget picker with live theme token support. */
class WidgetPickerTabBar(
    context: Context,
    private val onTabSelected: (Tab) -> Unit
) : LinearLayout(context) {

    enum class Tab { NEXUS, ALL }

    private val dp = resources.displayMetrics.density
    private var selected = Tab.NEXUS
    private val nexusTab: TextView
    private val allTab: TextView
    private var currentTokens: NexusColorTokens = resolveTokens()

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (12 * dp).toInt()
        }

        nexusTab = buildTab("Nexus")
        allTab = buildTab(context.getString(com.nexus.launcher.R.string.category_all))
        addView(nexusTab, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(allTab, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        
        applyThemeTokens(currentTokens)
        nexusTab.setOnClickListener { select(Tab.NEXUS) }
        // Nexus widgets are Premium (Living Mosaic aside); the lock rides on the tab that lists them.
        com.nexus.launcher.ui.premium.PremiumBadges.bind(nexusTab) { applySelection() }
        allTab.setOnClickListener { select(Tab.ALL) }
    }

    private fun resolveTokens(): NexusColorTokens {
        return try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    }

    fun applyThemeTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        // Neumorphism: a sunken track with the active tab raised out of it, as in the drawer menu.
        background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.sunkenOr(this, tokens, 22f * dp) {
            GradientDrawable().apply {
                cornerRadius = 22f * dp
                setColor(tokens.surfaceRaised)
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
        applySelection()
    }

    fun select(tab: Tab, notify: Boolean = true) {
        if (selected == tab) return
        selected = tab
        applySelection()
        if (notify) onTabSelected(tab)
    }

    fun current(): Tab = selected

    fun setAllTabVisible(visible: Boolean) {
        allTab.visibility = if (visible) VISIBLE else GONE
        if (!visible && selected != Tab.NEXUS) select(Tab.NEXUS, notify = false)
    }

    private fun buildTab(label: String): TextView = TextView(context).apply {
        text = label
        NexusTypeScale.bodyStrong.bindTo(this, currentTokens.textPrimary)
        gravity = Gravity.CENTER
        setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
    }

    private fun applySelection() {
        styleTab(nexusTab, selected == Tab.NEXUS)
        styleTab(allTab, selected == Tab.ALL)
    }

    private fun styleTab(tab: TextView, active: Boolean) {
        tab.background = if (active) {
            if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
                com.nexus.launcher.ui.glass.NeumorphicSurfaces.thumb(tab, currentTokens, 18f * dp)
            } else {
                GradientDrawable().apply {
                    cornerRadius = 18f * dp
                    setColor(currentTokens.surface)
                    setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
                }
            }
        } else {
            null
        }
        val ink = if (active) currentTokens.textPrimary else currentTokens.textSecondary
        tab.setTextColor(ink)
        if (tab === nexusTab) {
            com.nexus.launcher.ui.premium.PremiumBadges.decorateOption(tab, com.nexus.launcher.ui.premium.PremiumBadges.isShown(com.nexus.launcher.premium.PremiumFeature.NEXUS_WIDGETS), ink)
        }
    }
}
