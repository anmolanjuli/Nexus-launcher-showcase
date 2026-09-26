package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Builds style settings section for the Performance Widget (Dashboard vs Telemetry).
 */
object NexusWidgetPerformanceSettingsHelper {

    fun buildPerformanceGroup(
        context: Context,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)

        val styleOptions = listOf(
            "0" to context.getString(com.nexus.launcher.R.string.perf_layout_dashboard),
            "1" to context.getString(com.nexus.launcher.R.string.performance_style_telemetry)
        )

        val styleRow = NexusSegmentedRow(context).apply {
            val cfg = getConfig()
            configure(context.getString(com.nexus.launcher.R.string.perf_layout_style), styleOptions, cfg.clockStyle.toString())
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val newStyle = value.toIntOrNull() ?: 0
                updateConfig(getConfig().copy(clockStyle = newStyle))
                onChanged()
            }
        }
        group.addChildRow(styleRow)

        val reset: () -> Unit = {
            val cfg = getConfig()
            styleRow.configure(context.getString(com.nexus.launcher.R.string.perf_layout_style), styleOptions, cfg.clockStyle.toString())
        }

        return Pair(group, reset)
    }
}
