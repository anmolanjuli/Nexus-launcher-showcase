package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * The drawer's standalone category bar — the arrangement used when the category picker lives
 * outside the Split Search Pill (or when the pill is switched off entirely).
 *
 * Renders either a horizontally scrollable chip strip or a single "All Apps ▾" dropdown chip,
 * and optionally hosts the overflow (☰) trigger at its trailing edge when the pill isn't around
 * to carry it.
 *
 * Positioning, insets and layout reserve are owned by [DrawerChromeController]; this class only
 * builds and themes the row.
 */
class DrawerCategoryBar(private val context: Context) {

    private val dp = context.resources.displayMetrics.density

    /** Row height without the container's own vertical padding. */
    val rowHeightPx = (36 * dp).toInt()

    private val chips = mutableListOf<TextView>()
    private var dropdownChip: TextView? = null

    /** The chip the category dropdown opens from, when the bar is in dropdown style. */
    val dropdownAnchor: View? get() = dropdownChip
    private var scroll: HorizontalScrollView? = null

    /** Set by the controller so a chip tap can filter, and the dropdown chip can open its list. */
    var onCategorySelected: ((String) -> Unit)? = null
    var onDropdownRequested: ((View) -> Unit)? = null

    lateinit var row: LinearLayout
        private set

    /**
     * @param isStrip chips strip when true, a single dropdown chip when false.
     * @param showCategories false when the bar exists only to host the overflow trigger.
     */
    fun build(
        tokens: NexusColorTokens,
        categories: List<String>,
        selectedCategory: String,
        isStrip: Boolean,
        showCategories: Boolean
    ): LinearLayout {
        chips.clear()
        dropdownChip = null
        scroll = null

        row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (showCategories && isStrip) {
            val strip = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            categories.forEach { category ->
                val chip = buildChip(tokens, category, category == selectedCategory) {
                    onCategorySelected?.invoke(category)
                }
                chips.add(chip)
                strip.addView(chip)
            }
            val scrollView = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                // Chips are a filter rail, not a page — clipping them mid-glyph at the edge reads
                // as broken, so let the last one run to the container's padding instead.
                clipToPadding = false
                addView(strip)
                layoutParams = LinearLayout.LayoutParams(0, rowHeightPx, 1f)
            }
            scroll = scrollView
            row.addView(scrollView)
        } else if (showCategories) {
            val chip = buildChip(tokens, category = selectedCategory, isSelected = true, withChevron = true) {}
            chip.setOnClickListener { anchor ->
                anchor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onDropdownRequested?.invoke(anchor)
            }
            dropdownChip = chip
            row.addView(chip)
            // Pushes the overflow trigger to the far edge.
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        } else {
            row.addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }

        return row
    }

    /** Re-marks the active chip / relabels the dropdown chip without rebuilding the row. */
    fun setSelectedCategory(tokens: NexusColorTokens, selected: String) {
        dropdownChip?.let { chip ->
            chip.text = DrawerSearchPillBuilder.categoryLabelText(context, selected)
            return
        }
        chips.forEach { chip ->
            val isSelected = chip.tag == selected
            applyChipColors(chip, tokens, isSelected)
        }
        scrollSelectedIntoView()
    }

    fun applyTokens(tokens: NexusColorTokens, selected: String) {
        dropdownChip?.let { applyChipColors(it, tokens, true) }
        chips.forEach { applyChipColors(it, tokens, it.tag == selected) }
    }

    /** A chip scrolled off-screen gives no clue which filter is live — pull it back into view. */
    private fun scrollSelectedIntoView() {
        val target = chips.firstOrNull { it.isSelected } ?: return
        val sv = scroll ?: return
        sv.post {
            sv.smoothScrollTo((target.left - (16 * dp).toInt()).coerceAtLeast(0), 0)
        }
    }

    private fun buildChip(
        tokens: NexusColorTokens,
        category: String,
        isSelected: Boolean,
        withChevron: Boolean = false,
        onClick: () -> Unit
    ): TextView = TextView(context).apply {
        tag = category
        text = DrawerSearchPillBuilder.categoryLabelText(context, category) + if (withChevron) "  ⌄" else ""
        gravity = Gravity.CENTER
        setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, rowHeightPx
        ).apply { marginEnd = (8 * dp).toInt() }
        setCompoundDrawablesRelative(
            androidx.core.content.ContextCompat.getDrawable(context, DrawerCategories.iconFor(context, category))?.mutate()?.apply {
                val size = (14 * dp).toInt()
                setBounds(0, 0, size, size)
            },
            null, null, null,
        )
        compoundDrawablePadding = (6 * dp).toInt()
        applyChipColors(this, tokens, isSelected)
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
    }

    private fun applyChipColors(chip: TextView, tokens: NexusColorTokens, isSelected: Boolean) {
        chip.isSelected = isSelected
        NexusTypeScale.caption.bindTo(
            chip, if (isSelected) tokens.textPrimary else tokens.textSecondary
        )
        chip.compoundDrawableTintList = ColorStateList.valueOf(if (isSelected) tokens.textPrimary else tokens.textSecondary)
        // Neumorphism: the live filter is pressed in. Unselected chips stay on the plain opaque
        // ground rather than raised — they sit in a horizontal scroller exactly their own height,
        // which would cut a raised shadow off top and bottom; a sunken well draws inside its bounds.
        if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
            // Inset from the rail's top and bottom: flush, the well's rim read as touching the
            // edge of the bar. Both states share the inset so the chips keep one height.
            val gap = (3 * dp).toInt()
            val corner = rowHeightPx / 2f - gap
            val face = if (isSelected) {
                com.nexus.launcher.ui.glass.NeumorphicSurfaces.field(chip, tokens, corner)
            } else {
                GradientDrawable().apply {
                    cornerRadius = corner
                    setColor(com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(tokens).surfaceLight)
                }
            }
            chip.background = android.graphics.drawable.InsetDrawable(face, 0, gap, 0, gap)
            return
        }
        chip.background = GradientDrawable().apply {
            cornerRadius = rowHeightPx / 2f
            // Opaque like the pill: drawer icons scroll underneath this bar too.
            setColor(
                if (isSelected) tokens.surfaceRaised or 0xFF000000.toInt()
                else (tokens.surface and 0x00FFFFFF) or (0xD9 shl 24)
            )
            setStroke(
                (1 * dp).toInt().coerceAtLeast(1),
                if (isSelected) tokens.divider else android.graphics.Color.TRANSPARENT
            )
        }
    }

    /** Adds the overflow trigger at the bar's trailing edge, for when there is no pill to host it. */
    fun attachOverflow(icon: ImageView, tokens: NexusColorTokens) {
        icon.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        icon.background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(icon, tokens, rowHeightPx / 2f) {
            GradientDrawable().apply {
                cornerRadius = rowHeightPx / 2f
                setColor(tokens.surfaceRaised or 0xFF000000.toInt())
            }
        }
        val pad = (8 * dp).toInt()
        icon.setPadding(pad, pad, pad, pad)
        icon.layoutParams = LinearLayout.LayoutParams(rowHeightPx, rowHeightPx).apply {
            marginStart = (8 * dp).toInt()
        }
        row.addView(icon)
    }

    companion object {
        /** Icon used by the overflow trigger, wherever it currently lives. */
        val OVERFLOW_ICON_RES = R.drawable.ic_menu
    }
}
