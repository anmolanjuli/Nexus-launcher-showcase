package com.nexus.launcher.ui.widgets.liveapp

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Shape preset selector for Live App Widget: Grid / Horizontal / Vertical / Compact.
 */
class LiveAppShapeSelectorRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val dp = resources.displayMetrics.density
    private var selectedPreset = LiveAppConfig.PRESET_GRID

    private val gridPill: TextView
    private val horizontalPill: TextView
    private val verticalPill: TextView
    private val compactPill: TextView

    var onPresetSelected: ((preset: String, cols: Int, rows: Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val hPad = (16 * dp).toInt()
        val vPad = (8 * dp).toInt()
        setPadding(hPad, vPad, hPad, vPad)

        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        gridPill = createPill(context.getString(R.string.live_apps_preset_grid)) {
            select(LiveAppConfig.PRESET_GRID, 3, 3, tokens)
        }
        horizontalPill = createPill(context.getString(R.string.live_apps_preset_horizontal)) {
            select(LiveAppConfig.PRESET_HORIZONTAL, 4, 1, tokens)
        }
        verticalPill = createPill(context.getString(R.string.live_apps_preset_vertical)) {
            select(LiveAppConfig.PRESET_VERTICAL, 1, 4, tokens)
        }
        compactPill = createPill(context.getString(R.string.live_apps_preset_compact)) {
            select(LiveAppConfig.PRESET_COMPACT, 2, 2, tokens)
        }

        addView(gridPill)
        addView(horizontalPill)
        addView(verticalPill)
        addView(compactPill)

        updatePills(tokens)
    }

    private fun select(preset: String, cols: Int, rows: Int, tokens: NexusColorTokens) {
        if (selectedPreset == preset) return
        LivingMosaicHaptics.tick(this)
        selectedPreset = preset
        updatePills(tokens)
        onPresetSelected?.invoke(preset, cols, rows)
    }

    fun configure(preset: String) {
        selectedPreset = preset
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        updatePills(tokens)
    }

    private fun createPill(label: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val hp = (10 * dp).toInt()
            val vp = (6 * dp).toInt()
            setPadding(hp, vp, hp, vp)
            isClickable = true
            isFocusable = true
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (4 * dp).toInt()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updatePills(tokens: NexusColorTokens) {
        stylePill(gridPill, selectedPreset == LiveAppConfig.PRESET_GRID, tokens)
        stylePill(horizontalPill, selectedPreset == LiveAppConfig.PRESET_HORIZONTAL, tokens)
        stylePill(verticalPill, selectedPreset == LiveAppConfig.PRESET_VERTICAL, tokens)
        stylePill(compactPill, selectedPreset == LiveAppConfig.PRESET_COMPACT, tokens)
    }

    private fun stylePill(pill: TextView, isSelected: Boolean, tokens: NexusColorTokens) {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * dp
            if (isSelected) {
                setColor(tokens.accent)
                setStroke(0, 0)
            } else {
                setColor(ColorUtils.setAlphaComponent(tokens.surfaceRaised, 0x80))
                setStroke((1 * dp).toInt(), ColorUtils.setAlphaComponent(tokens.divider, 0x60))
            }
        }
        val textDark = android.graphics.Color.parseColor(com.nexus.launcher.ui.NexusDesignSystem.COLOR_TEXT_DARK)
        pill.setTextColor(if (isSelected) textDark else tokens.textPrimary)
    }
}
