package com.nexus.launcher.ui.widgets

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Agenda-specific settings section builder for NexusWidgetSettingsSheet.
 */
object NexusWidgetSettingsAgendaViews {

    fun buildAgendaGroup(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)

        // 1. Event Range Row (Today / 7 Days / 30 Days)
        val rangeRow = NexusSegmentedRow(context).apply {
            setInline(true)
            val cfg = getConfig()
            val ranges = listOf(
                com.nexus.launcher.ui.widgets.agenda.AgendaRange.TODAY to context.getString(R.string.agenda_range_today),
                com.nexus.launcher.ui.widgets.agenda.AgendaRange.WEEK to context.getString(R.string.agenda_range_week),
                com.nexus.launcher.ui.widgets.agenda.AgendaRange.MONTH to context.getString(R.string.agenda_range_month)
            )
            configure(
                context.getString(R.string.agenda_range_label),
                ranges,
                cfg.agendaRange
            )
            onValueChanged = { value: String ->
                LivingMosaicHaptics.tick(this)
                updateConfig(getConfig().copy(agendaRange = value))
                onChanged()
            }
        }
        group.addChildRow(rangeRow)

        // 2. Countdown Toggle Row
        val countdownRow = NexusToggleRow(context).apply {
            val cfg = getConfig()
            configure(
                context.getString(R.string.agenda_countdown_label),
                cfg.showAgendaCountdown
            )
            onCheckedChanged = { isChecked: Boolean ->
                LivingMosaicHaptics.tick(this)
                updateConfig(getConfig().copy(showAgendaCountdown = isChecked))
                onChanged()
            }
        }
        group.addChildRow(countdownRow)

        val reset: () -> Unit = {
            val cfg = getConfig()
            rangeRow.setSelectedValue(cfg.agendaRange)
            countdownRow.setChecked(cfg.showAgendaCountdown)
        }

        return Pair(group, reset)
    }
}
