package com.nexus.launcher.ui.folder

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.settings.SolidColorPickerDialog

class FolderBackgroundPickerBinder(
    private val host: LinearLayout,
    private val onModeChanged: (mode: String, frostedIndex: Int, solidHex: String?) -> Unit
) {
    private val dp = host.resources.displayMetrics.density
    private val swatchViews = mutableListOf<View>()
    private var selectedMode = "TRANSPARENT"
    private var selectedIndex = 0
    private var selectedSolidHex: String? = null

    private var headerView: TextView? = null
    private var scrollView: HorizontalScrollView? = null
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(host.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    fun bind(initialMode: String, initialIndex: Int, initialSolidHex: String?) {
        selectedMode = initialMode.uppercase()
        selectedIndex = DockFrostedGradients.clampIndex(initialIndex)
        selectedSolidHex = initialSolidHex
        if (swatchViews.isEmpty()) buildRow()
        updateSelection()
    }

    fun setVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        headerView?.visibility = v
        scrollView?.visibility = v
    }

    private fun buildRow() {
        val context = host.context
        val header = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.folder_background_style)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPadding(0, (12 * dp).toInt(), 0, (8 * dp).toInt())
        }
        headerView = header
        host.addView(header)

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        scrollView = scroll

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val swatchW = (48 * dp).toInt()
        val swatchH = (32 * dp).toInt()
        val gap = (8 * dp).toInt()

        // 1. None/Transparent Option
        val noneView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(swatchW, swatchH)
            setImageResource(R.drawable.ic_close)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                selectedMode = "TRANSPARENT"
                updateSelection()
                onModeChanged(selectedMode, selectedIndex, selectedSolidHex)
            }
        }
        swatchViews.add(noneView)
        row.addView(noneView)

        // 2. Frosted Swatches
        DockFrostedGradients.presets.forEachIndexed { index, preset ->
            val swatch = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(swatchW, swatchH).apply {
                    marginStart = gap
                }
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    selectedMode = "FROSTED"
                    selectedIndex = index
                    updateSelection()
                    onModeChanged(selectedMode, selectedIndex, selectedSolidHex)
                }
            }
            swatchViews.add(swatch)
            row.addView(swatch)
        }

        // 3. Thin Divider
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams((1 * dp).toInt().coerceAtLeast(1), swatchH).apply {
                marginStart = gap
                marginEnd = gap
            }
            setBackgroundColor(tokens.divider)
        }
        row.addView(divider)

        // 4. Solid Color Picker Option
        val solidView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(swatchW, swatchH)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                SolidColorPickerDialog.show(
                    context,
                    selectedSolidHex ?: "#131822",
                    FolderAuroraTheme.ACCENT
                ) { hex ->
                    selectedMode = "SOLID"
                    selectedSolidHex = hex
                    updateSelection()
                    onModeChanged(selectedMode, selectedIndex, selectedSolidHex)
                }
            }
        }
        swatchViews.add(solidView)
        row.addView(solidView)

        scroll.addView(row)
        host.addView(scroll)
    }

    private fun updateSelection() {
        val strokeW = (2 * dp).toInt()
        val accent = tokens.textPrimary
        val corner = 8f * dp

        if (swatchViews.isEmpty()) return

        val noneView = swatchViews[0]
        noneView.background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = corner
            if (selectedMode == "TRANSPARENT") setStroke(strokeW, accent)
            else setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }

        DockFrostedGradients.presets.forEachIndexed { index, preset ->
            val view = swatchViews.getOrNull(index + 1) ?: return@forEachIndexed
            view.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(preset.startRgb, preset.endRgb)
            ).apply {
                cornerRadius = corner
                if (selectedMode == "FROSTED" && index == selectedIndex) {
                    setStroke(strokeW, accent)
                } else {
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
        }

        val solidView = swatchViews.lastOrNull() as? ImageView ?: return
        if (selectedMode == "SOLID") {
            solidView.setImageResource(R.drawable.ic_palette)
            val solidColor = try {
                Color.parseColor(selectedSolidHex)
            } catch (_: Exception) {
                tokens.surfaceRaised
            }
            solidView.background = GradientDrawable().apply {
                setColor(solidColor)
                cornerRadius = corner
                setStroke(strokeW, accent)
            }
        } else {
            solidView.setImageResource(R.drawable.ic_palette)
            solidView.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#444444"), Color.parseColor("#888888"))
            ).apply {
                cornerRadius = corner
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }
}
