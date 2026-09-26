package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class SpatialCategoryCard(context: Context) : LinearLayout(context) {
    var onColorClick: (() -> Unit)? = null
        set(value) {
            field = value
            title.onColorClick = value
        }
    var onAppClick: ((CategoryApp) -> Unit)? = null
    var onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null
    var colorEditable: Boolean = true

    private val title = SpatialCardTitle(context)
    private val grid: GridLayout = CategoryAppGrid.create(context, columns = 4)
    private val scroll: ScrollView
    private val footer: LinearLayout
    private val quickLabel = TextView(context)
    private val quickRow = LinearLayout(context)
    private val recentLabel = TextView(context)
    private val recentRow = LinearLayout(context)
    private val nestedBlocks = mutableListOf<LinearLayout>()
    private val quickBlock: LinearLayout
    private val recentBlock: LinearLayout

    init {
        orientation = VERTICAL
        clipChildren = true
        val density = resources.displayMetrics.density
        val pad = (10f * density).toInt()
        setPadding(pad, pad, pad, pad)
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        scroll = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10f * density).toInt()
            })
        }
        quickBlock = section(quickLabel, quickRow, density)
        recentBlock = section(recentLabel, recentRow, density)
        footer = LinearLayout(context).apply {
            orientation = VERTICAL
            isBaselineAligned = false
            isClickable = true
            addView(quickBlock)
            addView(recentBlock)
        }
        footer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // Room for the footer and for the last row's label under its icon.
            scroll.setPadding(0, 0, 0, footer.height + (10f * density).toInt())
        }
        footer.elevation = 0f
        val stage = FrameLayout(context)
        stage.addView(scroll, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        stage.addView(
            footer,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM),
        )
        addView(stage, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun bind(category: CategoryGroup, palette: CategoryPalette) {
        val density = resources.displayMetrics.density
        background = CategorySurfaces.spatialCard(palette, density)
        footer.background = if (palette.isGlass) null else CategorySurfaces.footer(palette, density)
        footer.elevation = if (palette.isGlass) 0f else 8f * density
        // Always clip: without it the bottom row of icons drew through the footer.
        scroll.clipToPadding = true
        title.bind(category, palette, colorEditable)
        nestedBlocks.forEach { block ->
            block.background = CategorySurfaces.nested(palette, density, 16f)
        }
        CategoryAppGrid.bind(
            grid,
            category.apps,
            columns = 4,
            iconDp = 50f,
            showLabel = true,
            lightPlate = true,
            onAppClick = onAppClick,
            onAppLongPress = onAppLongPress,
            gapDp = 2f,
        )
        NexusTypeScale.caption.bindTo(quickLabel, palette.textPrimary)
        quickLabel.text = context.getString(R.string.drawer_category_quick_actions)
        val shortcuts = category.quickActions.take(4)
        quickBlock.visibility = if (shortcuts.isEmpty()) GONE else VISIBLE
        CategoryAppGrid.bindRow(quickRow, shortcuts, 36f, showLabel = true, lightPlate = true, onAppClick = onAppClick, onAppLongPress = onAppLongPress)
        NexusTypeScale.caption.bindTo(recentLabel, palette.textPrimary)
        recentLabel.text = context.getString(R.string.drawer_category_recent)
        val recents = category.recents.take(4)
        recentBlock.visibility = if (recents.isEmpty()) GONE else VISIBLE
        CategoryAppGrid.bindRow(recentRow, recents, 32f, showLabel = false, lightPlate = true, onAppClick = onAppClick, onAppLongPress = onAppLongPress)
        val footerVisible = shortcuts.isNotEmpty() || recents.isNotEmpty()
        footer.visibility = if (footerVisible) VISIBLE else GONE
        if (!footerVisible) scroll.setPadding(0, 0, 0, 0)
    }

    private fun section(label: TextView, row: LinearLayout, density: Float): LinearLayout {
        val palette = CategoryPalette(context)
        val block = LinearLayout(context).apply {
            orientation = VERTICAL
            background = CategorySurfaces.nested(palette, density, 16f)
            val pad = (10f * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        nestedBlocks.add(block)
        block.addView(label)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.isBaselineAligned = false
        block.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8f * density).toInt()
        })
        block.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10f * density).toInt()
        }
        return block
    }
}
