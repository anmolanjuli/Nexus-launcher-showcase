package com.nexus.launcher.ui.dock.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.dock.DockFrostedGradients

/**
 * Unified horizontal swatch strip for dock settings containing:
 * - "None" transparent tile
 * - All frosted gradient preset tiles
 * - Custom solid color picker tile
 */
internal class DockFrostedGradientSwatchBinder(
    private val context: Context,
    private val row: LinearLayout,
    private val scroll: HorizontalScrollView?,
    private val onNoneSelected: () -> Unit,
    private val onGradientSelected: (Int) -> Unit,
    private val onSolidColorClicked: () -> Unit
) {
    companion object {
        const val SELECTION_NONE = -1
        const val SELECTION_SOLID = -2
    }

    private var noneView: View? = null
    private val gradientViews = mutableListOf<View>()
    private var solidView: View? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var selectedType: Int = 0 // SELECTION_NONE, SELECTION_SOLID, or gradient index >= 0
    private var solidColorArgb: Int = Color.WHITE

    fun bind(initialSelection: Int, solidArgb: Int, tokens: NexusColorTokens) {
        this.currentTokens = tokens
        this.selectedType = initialSelection
        this.solidColorArgb = solidArgb
        if (row.childCount == 0) {
            buildAllSwatches()
        }
        updateSelection()
    }

    fun setSelection(selection: Int, solidArgb: Int = solidColorArgb) {
        this.selectedType = selection
        this.solidColorArgb = solidArgb
        updateSelection()
    }

    fun applyTokens(tokens: NexusColorTokens) {
        this.currentTokens = tokens
        updateSelection()
    }

    private fun buildAllSwatches() {
        row.removeAllViews()
        gradientViews.clear()
        val dp = context.resources.displayMetrics.density
        val swatchW = (44 * dp).toInt()
        val swatchH = (32 * dp).toInt()
        val gap = (8 * dp).toInt()

        // 1. None Tile
        noneView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(swatchW, swatchH).apply {
                marginEnd = gap
            }
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(currentTokens.textSecondary)
            val pad = (8 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = context.getString(com.nexus.launcher.R.string.dock_bg_mode_none)
            setOnClickListener {
                selectedType = SELECTION_NONE
                updateSelection()
                onNoneSelected()
            }
        }
        row.addView(noneView)

        // 2. Gradient Presets
        DockFrostedGradients.presets.forEachIndexed { index, preset ->
            val swatch = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(swatchW, swatchH).apply {
                    marginEnd = gap
                }
                contentDescription = preset.name
                setOnClickListener {
                    selectedType = index
                    updateSelection()
                    onGradientSelected(index)
                }
            }
            gradientViews.add(swatch)
            row.addView(swatch)
        }

        // 3. Custom Solid Color Tile
        solidView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(swatchW, swatchH)
            setImageResource(R.drawable.ic_search) // subtle color indicator icon
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            val pad = (7 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            contentDescription = context.getString(com.nexus.launcher.R.string.dock_bg_solid_color)
            setOnClickListener {
                selectedType = SELECTION_SOLID
                updateSelection()
                onSolidColorClicked()
            }
        }
        row.addView(solidView)

        row.gravity = Gravity.CENTER_VERTICAL
        scroll?.isHorizontalScrollBarEnabled = false
        updateSelection()
    }

    private fun updateSelection() {
        val dp = context.resources.displayMetrics.density
        val corner = 8f * dp
        val strokeW = (2 * dp).toInt().coerceAtLeast(2)
        val normalStrokeW = (1 * dp).toInt().coerceAtLeast(1)

        // None tile
        noneView?.background = GradientDrawable().apply {
            cornerRadius = corner
            setColor(if (selectedType == SELECTION_NONE) currentTokens.surfaceRaised else Color.TRANSPARENT)
            setStroke(if (selectedType == SELECTION_NONE) strokeW else normalStrokeW,
                if (selectedType == SELECTION_NONE) currentTokens.textPrimary else currentTokens.divider)
        }

        // Gradient tiles
        gradientViews.forEachIndexed { index, view ->
            val preset = DockFrostedGradients.presets[index]
            val isSelected = selectedType == index
            view.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(preset.startRgb, preset.endRgb)
            ).apply {
                cornerRadius = corner
                setStroke(if (isSelected) strokeW else normalStrokeW,
                    if (isSelected) currentTokens.textPrimary else currentTokens.divider)
            }
        }

        // Solid tile
        val isSolid = selectedType == SELECTION_SOLID
        solidView?.background = GradientDrawable().apply {
            cornerRadius = corner
            setColor(solidColorArgb)
            setStroke(if (isSolid) strokeW else normalStrokeW,
                if (isSolid) currentTokens.textPrimary else currentTokens.divider)
        }
    }
}
