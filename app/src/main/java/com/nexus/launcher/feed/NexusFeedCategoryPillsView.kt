package com.nexus.launcher.feed

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import java.util.Locale

class NexusFeedCategoryPillsView(
    context: Context,
    private val onCategorySelected: (String) -> Unit
) : HorizontalScrollView(context) {

    private val dp = resources.displayMetrics.density
    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), (8 * dp).toInt())
    }
    var selectedCategory = "All"
        private set
    private var currentCategories = listOf("All")
    private var categoryCounts = mapOf<String, Int>()
    private var tokens: NexusColorTokens = ThemeObserver.currentTokens(context)

    init {
        overScrollMode = View.OVER_SCROLL_NEVER
        isHorizontalScrollBarEnabled = false
        visibility = View.VISIBLE
        addView(container)
        renderPills()
    }

    fun applyTokens(newTokens: NexusColorTokens) {
        tokens = newTokens
        renderPills()
    }

    fun updateCategories(sourceCategories: List<String>) {
        val unique = sourceCategories.filter { it.isNotBlank() }
            .map { it.uppercase(Locale.ROOT) }
            .distinct()
            .sorted()
        val newCats = listOf("All") + unique
        if (newCats != currentCategories) {
            currentCategories = newCats
            if (!currentCategories.contains(selectedCategory.uppercase(Locale.ROOT)) && selectedCategory != "All") {
                selectedCategory = "All"
            }
            renderPills()
        }
    }

    fun updateCounts(counts: Map<String, Int>) {
        categoryCounts = counts
        renderPills()
    }

    fun renderPills() {
        container.removeAllViews()
        for (cat in currentCategories) {
            val isSelected = cat.equals(selectedCategory, ignoreCase = true)
            val displayLabel = when (cat.uppercase(Locale.ROOT)) {
                "ALL" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_all)
                "WORLD" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_world)
                "TECHNOLOGY" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_tech)
                "FINANCE" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_finance)
                "SPORTS" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_sports)
                "ENTERTAINMENT" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_entertainment)
                "SCIENCE" -> context.getString(com.nexus.launcher.R.string.nexus_feed_category_science)
                else -> cat.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
            }

            val count = if (cat.equals("All", ignoreCase = true)) {
                categoryCounts["ALL"] ?: categoryCounts["All"] ?: 0
            } else {
                categoryCounts[cat.uppercase(Locale.ROOT)] ?: categoryCounts[cat] ?: 0
            }

            val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
            val isDark = NexusFeedEInkCoordinator.isEInkDark(context)
            val currentTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else tokens

            val pillLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    if (isEInk) {
                        cornerRadius = 0f // Sharp edges for E-Ink
                        if (isSelected) {
                            setColor(currentTokens.textPrimary)
                        } else {
                            setColor(currentTokens.bg)
                            setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
                        }
                    } else {
                        cornerRadius = 9999f
                        if (isSelected) {
                            setColor(currentTokens.accent)
                        } else {
                            val alpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255).toInt()
                            setColor((alpha shl 24) or (currentTokens.surface and 0x00FFFFFF))
                            setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
                        }
                    }
                }
                setPadding((14 * dp).toInt(), (6 * dp).toInt(), (14 * dp).toInt(), (6 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }

                addView(TextView(context).apply {
                    text = displayLabel
                    if (isEInk) {
                        typeface = if (isSelected) NexusFeedEInkStyler.serifBold else NexusFeedEInkStyler.serifRegular
                        setTextColor(if (isSelected) currentTokens.bg else currentTokens.textSecondary)
                    } else {
                        NexusTypeScale.bodyStrong.bindTo(
                            this,
                            if (isSelected) Color.BLACK else currentTokens.textSecondary
                        )
                    }
                })

                if (count > 0) {
                    val badge = TextView(context).apply {
                        text = "$count"
                        if (isEInk) {
                            typeface = NexusFeedEInkStyler.monospaceFont
                            setTextColor(if (isSelected) currentTokens.bg else currentTokens.textPrimary)
                            background = GradientDrawable().apply {
                                setColor(if (isSelected) (0x33 shl 24) or (currentTokens.bg and 0x00FFFFFF) else currentTokens.surfaceRaised)
                                cornerRadius = 0f
                            }
                        } else {
                            NexusTypeScale.labelSmall.bindTo(this, if (isSelected) Color.BLACK else currentTokens.textPrimary)
                            gravity = Gravity.CENTER
                            background = GradientDrawable().apply {
                                setColor(if (isSelected) 0x33000000 else currentTokens.surfaceRaised)
                                cornerRadius = 9999f
                            }
                        }
                        gravity = Gravity.CENTER
                        setPadding((6 * dp).toInt(), (1 * dp).toInt(), (6 * dp).toInt(), (1 * dp).toInt())
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginStart = (6 * dp).toInt()
                        }
                    }
                    addView(badge)
                }

                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    selectedCategory = cat
                    renderPills()
                    onCategorySelected(cat)
                }
            }
            container.addView(pillLayout)
        }
    }
}
