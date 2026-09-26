package com.nexus.launcher.ui.widgets.mosaic

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.dock.DockFrostedGradients

/**
 * Glass + frosted gradient swatches for Mosaic Settings.
 * Reuses [DockFrostedGradients] with dynamic design tokens.
 */
class MosaicBackgroundPickerBinder(
    private val host: LinearLayout,
    private val onChanged: (mode: String, frostedIndex: Int) -> Unit
) {
    private val dp = host.resources.displayMetrics.density
    private val swatchViews = mutableListOf<View>()
    private var selectedMode = MosaicConfig.BG_GLASS
    private var selectedIndex = 0

    private var headerView: TextView? = null
    private var scrollView: HorizontalScrollView? = null
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(host.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    fun bind(initialMode: String, initialIndex: Int) {
        selectedMode = if (initialMode == MosaicConfig.BG_FROSTED) {
            MosaicConfig.BG_FROSTED
        } else {
            MosaicConfig.BG_GLASS
        }
        selectedIndex = DockFrostedGradients.clampIndex(initialIndex)
        if (swatchViews.isEmpty()) buildRow()
        updateSelection()
    }

    fun setVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        headerView?.visibility = v
        scrollView?.visibility = v
    }

    private fun buildRow() {
        val header = TextView(host.context).apply {
            text = host.context.getString(com.nexus.launcher.R.string.folder_background_style)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
        }
        headerView = header
        host.addView(header)

        val row = LinearLayout(host.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(host.context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        scrollView = scroll
        host.addView(scroll)

        val swatchW = (48 * dp).toInt()
        val swatchH = (32 * dp).toInt()
        val gap = (8 * dp).toInt()
        val corner = 8f * dp

        // Glass (default)
        val glass = View(host.context).apply {
            layoutParams = LinearLayout.LayoutParams(swatchW, swatchH)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = corner
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setOnClickListener {
                if (selectedMode != MosaicConfig.BG_GLASS) {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    selectedMode = MosaicConfig.BG_GLASS
                    updateSelection()
                    onChanged(selectedMode, selectedIndex)
                }
            }
        }
        swatchViews.add(glass)
        row.addView(glass)

        DockFrostedGradients.presets.forEachIndexed { index, preset ->
            val swatch = View(host.context).apply {
                layoutParams = LinearLayout.LayoutParams(swatchW, swatchH).apply {
                    marginStart = gap
                }
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(preset.startRgb, preset.endRgb)
                ).apply { cornerRadius = corner }
                setOnClickListener {
                    if (selectedMode != MosaicConfig.BG_FROSTED || selectedIndex != index) {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        selectedMode = MosaicConfig.BG_FROSTED
                        selectedIndex = index
                        updateSelection()
                        onChanged(selectedMode, selectedIndex)
                    }
                }
            }
            swatchViews.add(swatch)
            row.addView(swatch)
        }
    }

    private fun updateSelection() {
        val strokeW = (2 * dp).toInt()
        val accent = tokens.textPrimary
        val corner = 8f * dp
        swatchViews.forEachIndexed { i, view ->
            val gd = (view.background as? GradientDrawable) ?: return@forEachIndexed
            if (i == 0) {
                gd.setColor(tokens.surfaceRaised)
                if (selectedMode == MosaicConfig.BG_GLASS) gd.setStroke(strokeW, accent)
                else gd.setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            } else {
                val preset = DockFrostedGradients.presets[i - 1]
                gd.colors = intArrayOf(preset.startRgb, preset.endRgb)
                gd.cornerRadius = corner
                if (selectedMode == MosaicConfig.BG_FROSTED && selectedIndex == i - 1) {
                    gd.setStroke(strokeW, accent)
                } else {
                    gd.setStroke(0, Color.TRANSPARENT)
                }
            }
        }
    }
}
