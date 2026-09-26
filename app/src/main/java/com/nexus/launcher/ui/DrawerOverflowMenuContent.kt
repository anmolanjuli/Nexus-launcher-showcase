package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/** Row-building content for [DrawerOverflowMenuDialog] — kept in its own file so the dialog
 *  class itself (chrome/positioning/lifecycle) stays under 400 lines. */
object DrawerOverflowMenuContent {

    fun build(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        menuCard: LinearLayout,
        effectiveLayoutMode: String,
        currentListColumns: Int,
        currentGridColumns: Int,
        currentCategoryLayout: String,
        onColumnsSelected: (Int) -> Unit,
        onGridColumnsSelected: (Int) -> Unit,
        onDrawerModeChanged: (String) -> Unit,
        onCategoryLayoutChanged: (String) -> Unit,
        onHiddenApps: () -> Unit,
        onCreateFolder: () -> Unit,
        onDrawerSettings: () -> Unit,
        positionRowLabel: String?,
        positionRowValue: String,
        onPositionSelected: (String) -> Unit
    ) {
        val isCategories = effectiveLayoutMode == "categories"
        val isListMode = effectiveLayoutMode.startsWith("list")
        val isGrid = !isListMode && !isCategories

        // Hidden Apps / Create Folder / Drawer Settings — icon-only, no title above, this row IS
        // the menu's header.
        val iconRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (14 * dp).toInt())
        }
        val iconEntries = listOf(
            Triple(R.drawable.ic_hidden, context.getString(R.string.drawer_overflow_hidden_apps), onHiddenApps),
            Triple(R.drawable.ic_createfolder, context.getString(R.string.drawer_overflow_create_folder), onCreateFolder),
            Triple(R.drawable.ic_settings, context.getString(R.string.drawer_overflow_settings), onDrawerSettings)
        )
        iconEntries.forEachIndexed { index, (iconRes, contentDesc, action) ->
            iconRow.addView(ImageView(context).apply {
                setImageResource(iconRes)
                contentDescription = contentDesc
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                val pad = (11 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(this, tokens, 23 * dp) {
                    GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.surfaceRaised)
                    }
                }
                layoutParams = LinearLayout.LayoutParams((46 * dp).toInt(), (46 * dp).toInt()).apply {
                    if (index < iconEntries.lastIndex) marginEnd = (14 * dp).toInt()
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    action()
                }
            })
        }
        menuCard.addView(iconRow)

        // Grid / List / Categories mode toggle
        menuCard.addView(divider(context, dp, tokens))
        addModeRow(
            context, dp, tokens, menuCard,
            when {
                isCategories -> "categories"
                isListMode -> "list"
                else -> "grid"
            },
            onDrawerModeChanged
        )

        // Grid column density picker
        if (isGrid) {
            menuCard.addView(divider(context, dp, tokens))
            addGridColumnsRow(context, dp, tokens, menuCard, currentGridColumns, onGridColumnsSelected)
        }
        // List column picker — the existing List drawer mode, not Categories-List
        if (isListMode) {
            menuCard.addView(divider(context, dp, tokens))
            addStyleRow(context, dp, tokens, menuCard, currentListColumns, onColumnsSelected)
        }
        if (isCategories) {
            menuCard.addView(divider(context, dp, tokens))
            addCategoryLayoutRow(context, dp, tokens, menuCard, currentCategoryLayout, onCategoryLayoutChanged)
        }

        // Placement of whichever bar is actually on screen — the same preference App Drawer
        // Settings exposes, surfaced here because it is the kind of thing people flip back and
        // forth rather than set once. Omitted entirely when neither bar is showing.
        if (positionRowLabel != null) {
            menuCard.addView(divider(context, dp, tokens))
            addSearchPositionRow(
                context, dp, tokens, menuCard, positionRowLabel, positionRowValue, onPositionSelected
            )
        }
    }

    /** The row label every choice row starts with — one style, so the rows line up. */
    private fun rowLabel(context: Context, tokens: NexusColorTokens, text: String) =
        TextView(context).apply {
            this.text = text
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

    private fun choiceRow(context: Context, label: View, track: View) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label)
            addView(track)
        }

    private fun addSearchPositionRow(
        context: Context, dp: Float, tokens: NexusColorTokens, menuCard: LinearLayout,
        label: String, currentPosition: String, onPositionSelected: (String) -> Unit
    ) {
        val values = listOf("top", "bottom")
        val track = MenuSegmentedTrack.build(
            context = context,
            dp = dp,
            tokens = tokens,
            segments = listOf(
                MenuSegmentedTrack.Segment.Text(context.getString(R.string.drawer_search_bar_position_top)),
                MenuSegmentedTrack.Segment.Text(context.getString(R.string.drawer_search_bar_position_bottom)),
            ),
            selectedIndex = values.indexOf(currentPosition).coerceAtLeast(0),
            segmentWidthDp = 60f,
        ) { index ->
            val value = values[index]
            if (value != currentPosition) onPositionSelected(value)
        }
        menuCard.addView(choiceRow(context, rowLabel(context, tokens, label), track))
    }

    private fun divider(context: Context, dp: Float, tokens: NexusColorTokens): View =
        View(context).apply {
            background = ColorDrawable(tokens.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                topMargin = (2 * dp).toInt(); bottomMargin = (10 * dp).toInt()
            }
        }

    /** List mode's column count, drawn as one list or the same list twice. */
    private fun addStyleRow(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        menuCard: LinearLayout,
        currentListColumns: Int,
        onColumnsSelected: (Int) -> Unit
    ) {
        val track = MenuSegmentedTrack.build(
            context = context,
            dp = dp,
            tokens = tokens,
            segments = listOf(
                MenuSegmentedTrack.Segment.Icon(
                    R.drawable.ic_drawer_layout_list,
                    context.getString(R.string.drawer_overflow_style_one_col),
                ),
                MenuSegmentedTrack.Segment.Icon(
                    R.drawable.ic_drawer_layout_list_two,
                    context.getString(R.string.drawer_overflow_style_two_col),
                ),
            ),
            selectedIndex = if (currentListColumns == 2) 1 else 0,
        ) { index -> onColumnsSelected(index + 1) }
        val label = rowLabel(context, tokens, context.getString(R.string.drawer_overflow_style_label))
        menuCard.addView(choiceRow(context, label, track))
    }

    private fun addModeRow(
        context: Context, dp: Float, tokens: NexusColorTokens, menuCard: LinearLayout,
        currentMode: String, onModeSelected: (String) -> Unit
    ) {
        val modes = listOf("grid", "list", "categories")
        val track = MenuSegmentedTrack.build(
            context = context,
            dp = dp,
            tokens = tokens,
            segments = listOf(
                MenuSegmentedTrack.Segment.Icon(
                    R.drawable.ic_drawer_layout_grid,
                    context.getString(R.string.drawer_overflow_mode_grid),
                ),
                MenuSegmentedTrack.Segment.Icon(
                    R.drawable.ic_drawer_layout_list,
                    context.getString(R.string.drawer_overflow_mode_list),
                ),
                MenuSegmentedTrack.Segment.Icon(
                    R.drawable.ic_category,
                    context.getString(R.string.drawer_overflow_mode_categories),
                ),
            ),
            selectedIndex = modes.indexOf(currentMode).coerceAtLeast(0),
            segmentWidthDp = 40f,
        ) { index -> onModeSelected(modes[index]) }
        val label = rowLabel(context, tokens, context.getString(R.string.drawer_overflow_mode_label))
        menuCard.addView(choiceRow(context, label, track))
    }

    private fun addCategoryLayoutRow(
        context: Context, dp: Float, tokens: NexusColorTokens, menuCard: LinearLayout,
        currentLayout: String, onLayoutSelected: (String) -> Unit
    ) {
        val values = listOf("spatial", "strip", "list")
        val track = MenuSegmentedTrack.build(
            context = context,
            dp = dp,
            tokens = tokens,
            segments = listOf(
                MenuSegmentedTrack.Segment.Text(context.getString(R.string.drawer_category_layout_spatial)),
                MenuSegmentedTrack.Segment.Text(context.getString(R.string.drawer_category_layout_strip)),
                MenuSegmentedTrack.Segment.Text(context.getString(R.string.drawer_category_layout_list)),
            ),
            selectedIndex = values.indexOf(currentLayout).coerceAtLeast(0),
            segmentWidthDp = 56f,
        ) { index -> onLayoutSelected(values[index]) }
        val label = rowLabel(context, tokens, context.getString(R.string.drawer_overflow_style_label))
        menuCard.addView(choiceRow(context, label, track))
    }

    private fun addGridColumnsRow(
        context: Context, dp: Float, tokens: NexusColorTokens, menuCard: LinearLayout,
        currentCols: Int, onColumnsSelected: (Int) -> Unit
    ) {
        var cols = currentCols
        val countText = TextView(context).apply {
            text = cols.toString()
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            minWidth = (28 * dp).toInt()
        }
        fun makeBtn(symbol: String, delta: Int): View = TextView(context).apply {
            text = symbol
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(this, tokens, 17 * dp) {
                GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(tokens.surfaceRaised)
                }
            }
            val sz = (34 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(sz, sz)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                val next = (cols + delta).coerceIn(2, 10)
                if (next != cols) { cols = next; countText.text = cols.toString(); onColumnsSelected(cols) }
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = context.getString(R.string.drawer_overflow_columns_label)
                NexusTypeScale.body.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(makeBtn("−", -1))
            addView(countText.also {
                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = (12 * dp).toInt(); marginEnd = (12 * dp).toInt()
                }
            })
            addView(makeBtn("+", 1))
        }
        menuCard.addView(row)
    }
}
