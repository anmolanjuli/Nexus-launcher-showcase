package com.nexus.launcher.ui.widgets

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Notes-specific settings section builder for NexusWidgetSettingsSheet.
 */
object NexusWidgetSettingsNotesViews {

    fun buildNotesGroup(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)

        // 1. Show Title Row
        val titleRow = NexusToggleRow(context).apply {
            val cfg = getConfig()
            configure(
                context.getString(R.string.notes_setting_show_title),
                cfg.showNoteTitle
            )
            onCheckedChanged = { isChecked: Boolean ->
                LivingMosaicHaptics.tick(this)
                updateConfig(getConfig().copy(showNoteTitle = isChecked))
                onChanged()
            }
        }
        group.addChildRow(titleRow)

        // 2. Show Urgency Label Row
        val urgencyRow = NexusToggleRow(context).apply {
            val cfg = getConfig()
            configure(
                context.getString(R.string.notes_setting_show_urgency_label),
                cfg.showNoteUrgencyLabel
            )
            onCheckedChanged = { isChecked: Boolean ->
                LivingMosaicHaptics.tick(this)
                updateConfig(getConfig().copy(showNoteUrgencyLabel = isChecked))
                onChanged()
            }
        }
        group.addChildRow(urgencyRow)

        val reset: () -> Unit = {
            val cfg = getConfig()
            titleRow.setChecked(cfg.showNoteTitle)
            urgencyRow.setChecked(cfg.showNoteUrgencyLabel)
        }

        return Pair(group, reset)
    }
}
