package com.nexus.launcher.ui.dock.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/** Builds the grouped card sections for Dock Settings Bottom Sheet. */
internal object DockSettingsBodyBuilder {

    fun build(context: Context, tokens: NexusColorTokens, dp: Float): DockSettingsBodyViews {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (16 * dp).toInt()
            setPadding(padH, 0, padH, (8 * dp).toInt())
        }

        fun sectionHeader(titleRes: Int): TextView {
            return TextView(context).apply {
                text = context.getString(titleRes).uppercase()
                NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
                setPaddingRelative(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
            }
        }

        // Section 1: Layout & Sizing
        root.addView(sectionHeader(R.string.dock_settings_section_layout))
        val layoutGroup = SettingsSectionGroupView(context)
        val capacitySlider = NexusSliderRow(context)
        val iconSizeSlider = NexusSliderRow(context)
        val heightSlider = NexusSliderRow(context)
        val cornerRadiusSlider = NexusSliderRow(context)
        layoutGroup.addChildRow(capacitySlider)
        layoutGroup.addChildRow(iconSizeSlider)
        layoutGroup.addChildRow(heightSlider)
        layoutGroup.addChildRow(cornerRadiusSlider)
        root.addView(layoutGroup)

        // Section 2: Dock UI & Background
        val sectionHeaderBackground = sectionHeader(R.string.dock_settings_section_background)
        root.addView(sectionHeaderBackground)
        val bgGroup = SettingsSectionGroupView(context)
        val expressiveToggle = NexusToggleRow(context)

        val frostedScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            visibility = View.GONE
        }
        val frostedRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        frostedScroll.addView(frostedRow)

        val opacitySlider = NexusSliderRow(context).apply {
            visibility = View.GONE
        }
        val refractionSlider = NexusSliderRow(context).apply {
            visibility = View.GONE
        }

        bgGroup.addChildRow(expressiveToggle)
        bgGroup.addChildRow(frostedScroll)
        bgGroup.addChildRow(opacitySlider)
        bgGroup.addChildRow(refractionSlider)
        root.addView(bgGroup)

        // Section 3: Labels & Search
        val sectionHeaderLabels = sectionHeader(R.string.dock_settings_section_labels)
        root.addView(sectionHeaderLabels)
        val labelsGroup = SettingsSectionGroupView(context)
        val showLabelsToggle = NexusToggleRow(context)
        val labelFontSlider = NexusSliderRow(context).apply {
            visibility = View.GONE
        }
        val searchToggle = NexusToggleRow(context)
        labelsGroup.addChildRow(showLabelsToggle)
        labelsGroup.addChildRow(labelFontSlider)
        labelsGroup.addChildRow(searchToggle)
        root.addView(labelsGroup)

        return DockSettingsBodyViews(
            root = root,
            sectionHeaderBackground = sectionHeaderBackground,
            sectionHeaderLabels = sectionHeaderLabels,
            bgGroup = bgGroup,
            capacitySlider = capacitySlider,
            iconSizeSlider = iconSizeSlider,
            heightSlider = heightSlider,
            cornerRadiusSlider = cornerRadiusSlider,
            expressiveToggle = expressiveToggle,
            frostedScroll = frostedScroll,
            frostedRow = frostedRow,
            opacitySlider = opacitySlider,
            refractionSlider = refractionSlider,
            showLabelsToggle = showLabelsToggle,
            labelFontSlider = labelFontSlider,
            searchToggle = searchToggle
        )
    }
}
