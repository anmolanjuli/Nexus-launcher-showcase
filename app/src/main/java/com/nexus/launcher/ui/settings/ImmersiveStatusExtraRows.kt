package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.immersive.ImmersiveStatusStyle
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * The rest of each status-row module's settings, beneath its style: where the clock sits and how
 * its date reads, how many app icons the notification stack shows and which notifications count,
 * when the battery calls itself low, and the words beside the signal and Wi-Fi meters.
 *
 * Kept apart from [ImmersiveStatusStyleRows] so both stay well inside the file-size limit; the
 * section below interleaves them under each module's switch.
 */
class ImmersiveStatusExtraRows(private val context: Context) {

    private val dp = context.resources.displayMetrics.density

    private val clockPosition = styleRow()
    private val dateFormat = styleRow()
    private val maxIcons = slider()
    private val showSilent = toggle()
    private val groupByApp = toggle()
    private val lowBattery = slider()
    private val chargingAnimation = toggle()
    private val showDataType = toggle()
    private val showCarrier = toggle()
    private val showSsid = toggle()
    private val showBand = toggle()

    fun rowsFor(item: String): List<View> = when (item) {
        ImmersiveStatusStyleRows.CLOCK -> listOf(clockPosition, dateFormat)
        ImmersiveStatusStyleRows.NOTIFICATIONS -> listOf(maxIcons, showSilent, groupByApp)
        ImmersiveStatusStyleRows.BATTERY -> listOf(lowBattery, chargingAnimation)
        ImmersiveStatusStyleRows.SIGNAL -> listOf(showDataType, showCarrier)
        ImmersiveStatusStyleRows.WIFI -> listOf(showSsid, showBand)
        else -> emptyList()
    }

    /** The rows that only make sense with a particular style chosen above them. */
    fun applyConditions(s: NexusSettingsData) {
        val style = ImmersiveStatusStyle.of(s)
        dateFormat.visibility = shown(style.clock == ImmersiveStatusStyle.CLOCK_DATE)
        maxIcons.visibility = shown(style.notifications == ImmersiveStatusStyle.NOTIFICATIONS_ICONS)
    }

    fun bind(s: NexusSettingsData) {
        val style = ImmersiveStatusStyle.of(s)
        clockPosition.configure(
            context.getString(R.string.status_clock_position),
            listOf(
                ImmersiveStatusStyle.POSITION_LEFT to context.getString(R.string.status_opt_left),
                ImmersiveStatusStyle.POSITION_CENTER to context.getString(R.string.status_opt_center),
                ImmersiveStatusStyle.POSITION_RIGHT to context.getString(R.string.status_opt_right),
            ),
            style.clockPosition, inline = true,
        )
        dateFormat.configure(
            context.getString(R.string.status_date_format),
            listOf(
                ImmersiveStatusStyle.DATE_SHORT to context.getString(R.string.status_opt_date_short),
                ImmersiveStatusStyle.DATE_LONG to context.getString(R.string.status_opt_date_long),
                ImmersiveStatusStyle.DATE_WEEKDAY to context.getString(R.string.status_opt_date_weekday),
            ),
            style.dateFormat, inline = true,
        )
        // The date only reads as a date when the clock is showing one.
        dateFormat.visibility = shown(style.clock == ImmersiveStatusStyle.CLOCK_DATE)
        maxIcons.configure(
            label = context.getString(R.string.status_max_icons),
            min = 1, max = 10, value = style.maxIcons, stepSize = 1f,
        )
        maxIcons.visibility = shown(style.notifications == ImmersiveStatusStyle.NOTIFICATIONS_ICONS)
        showSilent.configure(context.getString(R.string.status_show_silent), style.showSilent)
        groupByApp.configure(context.getString(R.string.status_group_by_app), style.groupByApp)
        lowBattery.configure(
            label = context.getString(R.string.status_low_threshold),
            min = 5, max = 30, value = style.lowBatteryPercent, stepSize = 5f,
            formatValue = { "$it%" },
        )
        chargingAnimation.configure(
            context.getString(R.string.status_charging_animation), style.chargingAnimation,
        )
        val dataTypeAvailable = com.nexus.launcher.ui.immersive.StatusNetworkNames.canReadDataType(context)
        showDataType.configure(
            context.getString(R.string.status_show_data_type),
            style.showDataType && dataTypeAvailable,
            subtitle = if (dataTypeAvailable) null
            else context.getString(R.string.status_needs_phone_permission),
        )
        // Without the permission it can only ever be off, so it says why rather than doing
        // nothing when switched on.
        StatusRowDimming.apply(showDataType, dataTypeAvailable)
        showCarrier.configure(context.getString(R.string.status_show_carrier), style.showCarrier)
        val ssidAvailable = com.nexus.launcher.ui.immersive.StatusNetworkNames.canReadSsid(context)
        showSsid.configure(
            context.getString(R.string.status_show_ssid),
            style.showSsid && ssidAvailable,
            subtitle = if (ssidAvailable) null
            else context.getString(R.string.status_needs_location_permission),
        )
        StatusRowDimming.apply(showSsid, ssidAvailable)
        showBand.configure(context.getString(R.string.status_show_band), style.showBand)
    }

    fun setListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        isIgnoreCallbacks: () -> Boolean,
    ) {
        clockPosition.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusClockPosition = v) }
        }
        dateFormat.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusDateFormat = v) }
        }
        maxIcons.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusMaxIcons = v) }
        }
        showSilent.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusShowSilent = checked) }
        }
        groupByApp.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusGroupByApp = checked) }
        }
        lowBattery.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusLowBatteryPercent = v) }
        }
        chargingAnimation.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusChargingAnimation = checked) }
        }
        showDataType.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusShowDataType = checked) }
        }
        showCarrier.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusShowCarrier = checked) }
        }
        showSsid.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusShowSsid = checked) }
        }
        showBand.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusShowBand = checked) }
        }
    }

    private fun shown(value: Boolean) = if (value) View.VISIBLE else View.GONE

    /** Same indent and quieter label as a style row: these belong to the switch above them. */
    private fun styleRow() = NexusSegmentedRow(context).apply {
        setContentPadding((32 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
        setLabelSubdued(true)
    }

    private fun slider() = NexusSliderRow(context).apply {
        indent(this)
        setLabelSubdued(true)
    }

    private fun toggle() = NexusToggleRow(context).apply {
        indent(this)
        setLabelSubdued(true)
    }

    private fun indent(view: View) {
        view.setPadding((32 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
    }
}
