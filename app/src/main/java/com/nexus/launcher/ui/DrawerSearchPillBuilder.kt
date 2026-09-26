package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.glass.FrostedPanelLayout

/**
 * View construction and theming for the drawer's Split Search Pill — the category dropdown on the
 * left, a hairline divider, and the search input on the right. Split out of
 * [DrawerChromeController] purely to keep both files inside the 400-line limit.
 *
 * Also builds the standalone dropdown chip that sits beside the pill in the same row when the
 * user puts the category picker outside the pill but keeps it as a dropdown.
 */
object DrawerSearchPillBuilder {

    /** Every child view the bar needs to re-theme or re-bind after construction. */
    class Views(
        val pill: FrostedPanelLayout,
        val categorySegment: LinearLayout,
        val categoryLabel: TextView,
        val categoryChevron: ImageView,
        val divider: View,
        val searchIcon: ImageView,
        val editText: EditText
    ) {
        /** Present only when the category picker is a chip beside the pill. */
        var dropdownChip: FrostedPanelLayout? = null
        var dropdownLabel: TextView? = null
        var dropdownChevron: ImageView? = null
    }

    fun build(context: Context, dp: Float, tokens: NexusColorTokens): Views {
        val categoryLabel = TextView(context).apply {
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (110 * dp).toInt()
        }
        val categoryChevron = chevron(context, dp)
        val categorySegment = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), 0, (10 * dp).toInt(), 0)
            addView(categoryLabel)
            addView(categoryChevron)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (1 * dp).toInt().coerceAtLeast(1), (22 * dp).toInt()
            ).apply { gravity = Gravity.CENTER_VERTICAL }
        }

        val searchIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply {
                marginStart = (12 * dp).toInt()
                marginEnd = (10 * dp).toInt()
            }
        }

        val editText = EditText(context).apply {
            hint = context.getString(R.string.drawer_minimal_search_hint)
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        val pill = FrostedPanelLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Neumorphism: a search field is pressed into the surface, as in the search widget.
            relief = FrostedPanelLayout.Relief.SUNKEN
            setCornerRadiusPx(24 * dp)
            setPadding(0, 0, (4 * dp).toInt(), 0)
            addView(categorySegment)
            addView(divider)
            addView(searchIcon)
            addView(editText)
        }

        return Views(pill, categorySegment, categoryLabel, categoryChevron, divider, searchIcon, editText)
            .also { applyTokens(it, tokens, dp) }
    }

    /**
     * Builds the category chip that shares the pill's row, to its left. Uses the same frosted
     * surface as the pill so the two read as one control split in three, rather than a glass pill
     * with a flat tab bolted to it.
     */
    fun buildDropdownChip(context: Context, dp: Float, tokens: NexusColorTokens, views: Views) {
        val label = TextView(context).apply {
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = (96 * dp).toInt()
        }
        val chev = chevron(context, dp)
        val chip = FrostedPanelLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // A button beside the field: raised, where the field is sunken.
            relief = FrostedPanelLayout.Relief.RAISED
            setCornerRadiusPx(24 * dp)
            setPadding((14 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            addView(label)
            addView(chev)
        }
        views.dropdownChip = chip
        views.dropdownLabel = label
        views.dropdownChevron = chev
        applyTokens(views, tokens, dp)
    }

    private fun chevron(context: Context, dp: Float) = ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_down)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        layoutParams = LinearLayout.LayoutParams((16 * dp).toInt(), (16 * dp).toInt()).apply {
            marginStart = (4 * dp).toInt()
        }
    }

    /**
     * Repaints every surface from live theme tokens. The pill stays a real frosted-glass surface
     * only while the Frosted Glass UI Style is active; in Default it renders flat on the theme's
     * own `surface` color with no stroke, per the drawer's minimal spec, and in Neumorphism the pill
     * is a sunken well and the chip beside it raised.
     */
    fun applyTokens(views: Views, tokens: NexusColorTokens, dp: Float) {
        // Dense, not sheet: drawer icons scroll *underneath* these surfaces, and at sheet opacity
        // they read straight through. They still sample their own blurred wallpaper, so they stay
        // glass — just opaque enough to be a surface rather than a window onto the grid.
        listOfNotNull(views.pill, views.dropdownChip).forEach { surface ->
            surface.applyStyle(tokens, FrostedPanelLayout.Density.DENSE)
        }

        views.categoryLabel.setTextColor(tokens.textPrimary)
        views.dropdownLabel?.setTextColor(tokens.textPrimary)
        listOfNotNull(views.categoryChevron, views.dropdownChevron).forEach {
            it.imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        }
        views.divider.setBackgroundColor(
            (tokens.textSecondary and 0x00FFFFFF) or (0x3D shl 24)
        )
        views.searchIcon.imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        views.editText.setHintTextColor(tokens.textSecondary)
        views.editText.setTextColor(tokens.textPrimary)
    }

    /** Display label for a drawer category: the user's name for it, else the translation. */
    fun categoryLabelText(context: Context, category: String): String =
        DrawerCategories.customName(context, category) ?: defaultCategoryLabel(context, category)

    /** A built-in category's translated name, ignoring any rename. */
    fun defaultCategoryLabel(context: Context, category: String): String = when (category.lowercase()) {
        "all" -> context.getString(R.string.category_all)
        "social" -> context.getString(R.string.category_social)
        "media" -> context.getString(R.string.category_media)
        "games" -> context.getString(R.string.category_games)
        "productivity" -> context.getString(R.string.category_productivity)
        "finance" -> context.getString(R.string.category_finance)
        "utilities" -> context.getString(R.string.category_utilities)
        "system" -> context.getString(R.string.category_system)
        "shopping" -> context.getString(R.string.category_shopping)
        "travel" -> context.getString(R.string.category_travel)
        "health" -> context.getString(R.string.category_health)
        "news" -> context.getString(R.string.category_news)
        "education" -> context.getString(R.string.category_education)
        "photography" -> context.getString(R.string.category_photography)
        "music" -> context.getString(R.string.category_music)
        "video" -> context.getString(R.string.category_video)
        "food" -> context.getString(R.string.category_food)
        "weather" -> context.getString(R.string.category_weather)
        "personalization" -> context.getString(R.string.category_personalization)
        else -> category
    }

    /** "All" reads as a filter state, not a place — spell it out in the dropdown segment. */
    fun categoryLabelText(category: String): String =
        if (category == "All") "All Apps" else category
}
