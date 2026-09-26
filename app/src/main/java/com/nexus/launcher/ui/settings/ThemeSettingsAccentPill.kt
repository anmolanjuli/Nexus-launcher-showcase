package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.settings.views.AccentColorPicker
import com.nexus.launcher.ui.settings.views.NexusNavRow

/**
 * The Accent Color pillow of the Theme section: its header, the swatch row, and the custom colour.
 *
 * The last of [ThemeSettingsModeSection]'s four pillows, and the one that owns the most behaviour —
 * preset swatches, a custom swatch that opens a picker, and a header whose subtitle names whichever
 * colour is chosen. Kept apart so the section stays under the 400-line limit.
 */
internal class ThemeSettingsAccentPill(
    context: Context,
    tokens: NexusColorTokens,
    dp: Float,
    childPadH: Int,
    radiusInner: Float,
    radiusOuter: Float,
    onAccentColorSelected: (String) -> Unit,
) {
    private val background = GradientDrawable().apply {
        // Bottom pillow: square top corners where it meets the pillow above, round at the foot.
        cornerRadii = floatArrayOf(
            radiusInner, radiusInner,
            radiusInner, radiusInner,
            radiusOuter, radiusOuter,
            radiusOuter, radiusOuter,
        )
        setColor(tokens.surface)
    }

    val header: NexusNavRow = NexusNavRow(
        context,
        title = context.getString(R.string.settings_section_accent_color),
        subtitle = ThemeSettingsFormatters.getAccentLabel(context, AccentColorPicker.DEFAULT_ACCENT),
        iconRes = R.drawable.outline_colors_24,
        showChevron = false,
    ).apply {
        setContentPadding(childPadH, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
    }

    val picker: AccentColorPicker = AccentColorPicker(context).apply {
        val pad = (8 * dp).toInt()
        setPadding(childPadH + pad, 0, pad, (10 * dp).toInt())
        onColorSelected = { hex ->
            onAccentColorSelected(hex)
            header.setSubtitle(ThemeSettingsFormatters.getAccentLabel(context, hex))
        }
        onCustomRequested = { currentHex ->
            SolidColorPickerDialog.show(
                context = context,
                initialHex = currentHex,
                accentColorHex = currentHex,
            ) { picked ->
                setSelectedColor(picked)
                onAccentColorSelected(picked)
                header.setSubtitle(ThemeSettingsFormatters.getAccentLabel(context, picked))
            }
        }
    }

    val pill: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        background = this@ThemeSettingsAccentPill.background
        clipToOutline = true
        addView(header)
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(picker)
        })
    }

    fun applyTokens(tokens: NexusColorTokens) {
        background.setColor(tokens.surface)
        header.applyTokens(tokens)
    }
}
