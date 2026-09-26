package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Builds the settings options group for the "La Crosse" Style LCD Clock Widget.
 * Configures theme mode, time/month formats, individual field toggles, and display lines/ghosts.
 */
object NexusWidgetSettingsLaCrosseViews {

    fun buildLaCrosseGroup(
        context: Context,
        appWidgetId: Int,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)
        var cfg = LaCrosseClockConfig.read(context, appWidgetId)

        // 1. Time Format
        val timeFmtRow = NexusSegmentedRow(context).apply {
            val fmtOptions = listOf(
                LaCrosseClockConfig.TimeFormat.SYSTEM.name to context.getString(R.string.widget_lacrosse_time_format_system),
                LaCrosseClockConfig.TimeFormat.H12.name to context.getString(R.string.widget_lacrosse_time_format_12h),
                LaCrosseClockConfig.TimeFormat.H24.name to context.getString(R.string.widget_lacrosse_time_format_24h)
            )
            configure(context.getString(R.string.widget_lacrosse_time_format), fmtOptions, cfg.timeFormat.name)
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val newFmt = try { LaCrosseClockConfig.TimeFormat.valueOf(value) } catch (_: Exception) { LaCrosseClockConfig.TimeFormat.SYSTEM }
                cfg = cfg.copy(timeFormat = newFmt)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(timeFmtRow)

        // 3. Month Format
        val monthFmtRow = NexusSegmentedRow(context).apply {
            val monthOptions = listOf(
                LaCrosseClockConfig.MonthFormat.ABBREV.name to context.getString(R.string.widget_lacrosse_month_format_abbrev),
                LaCrosseClockConfig.MonthFormat.NUMERIC.name to context.getString(R.string.widget_lacrosse_month_format_numeric)
            )
            configure(context.getString(R.string.widget_lacrosse_month_format), monthOptions, cfg.monthFormat.name)
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val newMonthFmt = try { LaCrosseClockConfig.MonthFormat.valueOf(value) } catch (_: Exception) { LaCrosseClockConfig.MonthFormat.ABBREV }
                cfg = cfg.copy(monthFormat = newMonthFmt)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(monthFmtRow)

        // 4. Field Toggles
        val timeToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_field_time), cfg.showTime)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showTime = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(timeToggle)

        val amPmToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_field_ampm), cfg.showAmPm)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showAmPm = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(amPmToggle)

        val monthToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_field_month), cfg.showMonth)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showMonth = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(monthToggle)

        val dateToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_field_date), cfg.showDate)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showDate = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(dateToggle)

        val dayToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_field_day), cfg.showDay)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showDay = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(dayToggle)

        val tempToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_field_temp), cfg.showTemp)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showTemp = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(tempToggle)

        // 5. Display Toggles
        val ghostToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_ghost_segments), cfg.showGhostSegments)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showGhostSegments = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(ghostToggle)

        val dividerToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_lacrosse_divider_lines), cfg.showDividerLines)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showDividerLines = checked)
                LaCrosseClockConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(dividerToggle)

        val resetViews = {
            val defaults = LaCrosseClockConfig()
            LaCrosseClockConfig.write(context, appWidgetId, defaults)
            cfg = defaults

            timeFmtRow.configure(context.getString(R.string.widget_lacrosse_time_format), listOf(
                LaCrosseClockConfig.TimeFormat.SYSTEM.name to context.getString(R.string.widget_lacrosse_time_format_system),
                LaCrosseClockConfig.TimeFormat.H12.name to context.getString(R.string.widget_lacrosse_time_format_12h),
                LaCrosseClockConfig.TimeFormat.H24.name to context.getString(R.string.widget_lacrosse_time_format_24h)
            ), defaults.timeFormat.name)

            monthFmtRow.configure(context.getString(R.string.widget_lacrosse_month_format), listOf(
                LaCrosseClockConfig.MonthFormat.ABBREV.name to context.getString(R.string.widget_lacrosse_month_format_abbrev),
                LaCrosseClockConfig.MonthFormat.NUMERIC.name to context.getString(R.string.widget_lacrosse_month_format_numeric)
            ), defaults.monthFormat.name)

            timeToggle.configure(context.getString(R.string.widget_lacrosse_field_time), defaults.showTime)
            amPmToggle.configure(context.getString(R.string.widget_lacrosse_field_ampm), defaults.showAmPm)
            monthToggle.configure(context.getString(R.string.widget_lacrosse_field_month), defaults.showMonth)
            dateToggle.configure(context.getString(R.string.widget_lacrosse_field_date), defaults.showDate)
            dayToggle.configure(context.getString(R.string.widget_lacrosse_field_day), defaults.showDay)
            tempToggle.configure(context.getString(R.string.widget_lacrosse_field_temp), defaults.showTemp)
            ghostToggle.configure(context.getString(R.string.widget_lacrosse_ghost_segments), defaults.showGhostSegments)
            dividerToggle.configure(context.getString(R.string.widget_lacrosse_divider_lines), defaults.showDividerLines)
        }

        return Pair(group, resetViews)
    }
}
