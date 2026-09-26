package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

class IslandSettingsBinder(private val context: Context) {
    val enable = NexusToggleRow(context)
    val calibrationRow = NexusNavRow(context)
    val animSpeed = NexusSliderRow(context)
    val haptics = NexusToggleRow(context)

    val liveActivity = NexusToggleRow(context)
    val music = NexusToggleRow(context)
    val timer = NexusToggleRow(context)
    val stopwatch = NexusToggleRow(context)
    val batteryLow = NexusToggleRow(context)
    val charging = NexusToggleRow(context)
    val dnd = NexusToggleRow(context)
    val bluetooth = NexusToggleRow(context)
    val calendar = NexusToggleRow(context)

    val mainSection: NexusSection
    val triggerSection: NexusSection
    private var lastData: NexusSettingsData? = null

    init {
        val mainGroup = SettingsSectionGroupView(context).apply {
            addChildRow(enable)
            addChildRow(calibrationRow)
            addChildRow(animSpeed)
            addChildRow(haptics)
        }
        mainSection = NexusSection(context).apply {
            isTransparentCard = true
            addRow(mainGroup)
        }
        val triggerGroup = SettingsSectionGroupView(context).apply {
            addChildRow(liveActivity)
            addChildRow(music)
            addChildRow(charging)
            addChildRow(batteryLow)
            addChildRow(bluetooth)
            addChildRow(timer)
            addChildRow(stopwatch)
            addChildRow(dnd)
            addChildRow(calendar)
        }
        triggerSection = NexusSection(context).apply {
            isTransparentCard = true
            setTitle(context.getString(R.string.island_mini_events_title))
            addRow(triggerGroup)
        }
    }

    fun bind(pending: NexusSettingsData) {
        lastData = pending
        enable.configure(
            context.getString(R.string.island_enable),
            pending.islandEnabled,
            subtitle = context.getString(R.string.island_enable_subtitle),
        )
        calibrationRow.apply {
            setTitle(context.getString(R.string.island_size_position_title))
            setSubtitle(context.getString(R.string.island_calibration_size_summary, pending.islandWidthDp, pending.islandHeightDp))
            setChevronVisible(true)
        }
        animSpeed.configure(
            context.getString(R.string.island_anim_speed),
            50,
            150,
            (pending.islandAnimSpeed * 100f).toInt(),
            subtitle = context.getString(R.string.island_anim_speed_subtitle),
            formatValue = { "${it}%" },
        )
        haptics.configure(
            context.getString(R.string.island_haptics),
            pending.islandHaptics,
            subtitle = context.getString(R.string.island_haptics_subtitle),
        )

        liveActivity.configure(
            context.getString(R.string.island_trigger_activity),
            pending.islandTriggerActivity,
            subtitle = context.getString(R.string.island_trigger_activity_subtitle),
        )
        music.configure(context.getString(R.string.island_trigger_music), pending.islandTriggerMusic)
        charging.configure(context.getString(R.string.island_trigger_charging), pending.islandTriggerCharging)
        batteryLow.configure(context.getString(R.string.island_trigger_battery_low), pending.islandTriggerBatteryLow)
        bluetooth.configure(context.getString(R.string.island_trigger_bluetooth), pending.islandTriggerBluetooth)
        timer.configure(context.getString(R.string.island_trigger_timer), pending.islandTriggerTimer)
        stopwatch.configure(context.getString(R.string.island_trigger_stopwatch), pending.islandTriggerStopwatch)
        dnd.configure(context.getString(R.string.island_trigger_dnd), pending.islandTriggerDnd)
        calendar.configure(context.getString(R.string.island_trigger_calendar), pending.islandTriggerCalendar)

        val enabled = pending.islandEnabled
        listOf(
            calibrationRow, animSpeed, haptics,
            liveActivity, music, charging, batteryLow, bluetooth, timer, stopwatch, dnd, calendar,
        ).forEach { it.isEnabled = enabled }
    }

    fun setupListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        ignoring: () -> Boolean,
    ) {
        enable.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandEnabled = checked) }
        }
        calibrationRow.setOnClickListener {
            val current = lastData ?: return@setOnClickListener
            IslandCalibrationSheet.show(context, current) { updated ->
                onPatch { updated }
            }
        }
        animSpeed.onValueChanged = { value ->
            if (!ignoring()) onPatch { it.copy(islandAnimSpeed = value / 100f) }
        }
        haptics.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandHaptics = checked) }
        }
        liveActivity.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerActivity = checked) }
        }
        music.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerMusic = checked) }
        }
        charging.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerCharging = checked) }
        }
        batteryLow.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerBatteryLow = checked) }
        }
        bluetooth.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerBluetooth = checked) }
        }
        timer.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerTimer = checked) }
        }
        stopwatch.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerStopwatch = checked) }
        }
        dnd.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerDnd = checked) }
        }
        calendar.onCheckedChanged = { checked ->
            if (!ignoring()) onPatch { it.copy(islandTriggerCalendar = checked) }
        }
    }
}
