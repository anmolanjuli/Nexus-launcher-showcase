package com.nexus.launcher.ui.widgets

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow

/** The Icon spacing row shared by the App Box, Live App and Shortcut Box settings sheets. */
internal object BoxIconSpacingRow {

    fun create(context: Context, accent: Int, initial: Int, onChanged: (Int) -> Unit): NexusSegmentedRow =
        NexusSegmentedRow(context).apply {
            val options = listOf(
                BoxSlotGrid.SPACING_TIGHT.toString() to context.getString(R.string.box_icon_spacing_tight),
                BoxSlotGrid.SPACING_NORMAL.toString() to context.getString(R.string.box_icon_spacing_normal),
                BoxSlotGrid.SPACING_WIDE.toString() to context.getString(R.string.box_icon_spacing_wide),
            )
            configure(context.getString(R.string.box_icon_spacing), options, initial.toString(), inline = true)
            applyAccentColor(accent)
            onValueChanged = { value -> onChanged(BoxSlotGrid.parseSpacing(value.toInt())) }
        }
}
