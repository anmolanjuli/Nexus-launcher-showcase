package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class SpatialColorPickerOverlay(context: Context) : FrameLayout(context) {
    var onColorPicked: ((Int) -> Unit)? = null

    private val palette = CategoryPalette(context)
    private val sheet = LinearLayout(context)
    private val swatches = mutableListOf<View>()
    private var selectedColor = palette.categoryPresets.first()

    init {
        visibility = GONE
        isClickable = true
        setBackgroundColor(palette.scrim())
        setOnClickListener { hide() }
        val density = resources.displayMetrics.density
        sheet.orientation = LinearLayout.VERTICAL
        sheet.background = CategorySurfaces.card(palette, density, 24f)
        val pad = (20f * density).toInt()
        sheet.setPadding(pad, pad, pad, pad)
        sheet.setOnClickListener { }
        val title = TextView(context)
        title.text = context.getString(R.string.drawer_category_color_title)
        NexusTypeScale.bodyStrong.bindTo(title, palette.textPrimary)
        sheet.addView(title)
        val grid = GridLayout(context).apply {
            columnCount = 4
            val top = (16f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = top }
        }
        palette.categoryPresets.forEachIndexed { index, color ->
            val swatch = View(context)
            swatch.background = CategorySurfaces.swatch(color, density)
            swatch.contentDescription = context.getString(R.string.drawer_category_color_change)
            swatch.setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                onColorPicked?.invoke(color)
            }
            val size = (44f * density).toInt()
            val cellPad = (8f * density).toInt()
            grid.addView(swatch, GridLayout.LayoutParams().apply {
                width = size
                height = size
                columnSpec = GridLayout.spec(index % 4)
                rowSpec = GridLayout.spec(index / 4)
                setMargins(cellPad, cellPad, cellPad, cellPad)
            })
            swatches.add(swatch)
        }
        sheet.addView(grid)
        addView(
            sheet,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
                val inset = (16f * density).toInt()
                leftMargin = inset
                rightMargin = inset
                bottomMargin = (24f * density).toInt()
            },
        )
    }

    fun show(current: Int) {
        selectedColor = current
        visibility = VISIBLE
        bringToFront()
        refreshSelection()
    }

    fun hide() {
        visibility = GONE
    }

    private fun refreshSelection() {
        val density = resources.displayMetrics.density
        swatches.forEachIndexed { index, view ->
            val color = palette.categoryPresets[index]
            view.background = CategorySurfaces.colorSwatch(color, density, color == selectedColor)
        }
    }
}
