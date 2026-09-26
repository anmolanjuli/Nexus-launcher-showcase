package com.nexus.launcher.ui.dock.settings

import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/** Holds views for the Dock Settings Bottom Sheet body. */
internal class DockSettingsBodyViews(
    val root: LinearLayout,
    val sectionHeaderBackground: View,
    val sectionHeaderLabels: View,
    val bgGroup: View,
    val capacitySlider: NexusSliderRow,
    val iconSizeSlider: NexusSliderRow,
    val heightSlider: NexusSliderRow,
    val cornerRadiusSlider: NexusSliderRow,
    val expressiveToggle: NexusToggleRow,
    val frostedScroll: HorizontalScrollView,
    val frostedRow: LinearLayout,
    val opacitySlider: NexusSliderRow,
    val refractionSlider: NexusSliderRow,
    val showLabelsToggle: NexusToggleRow,
    val labelFontSlider: NexusSliderRow,
    val searchToggle: NexusToggleRow
)
