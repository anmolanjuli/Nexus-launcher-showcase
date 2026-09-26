package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.immersive.ImmersiveStatusStyle
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/**
 * The look of each immersive status-row item: clock format, how notifications are shown, the
 * battery's shape and where its percentage goes, and the signal and Wi-Fi meters.
 *
 * Its own file so [ImmersiveStatusSettingsSection] keeps to the five on/off rows; both are bound
 * from the same settings draft. Each row appears only while its item is switched on.
 */
class ImmersiveStatusStyleRows(private val context: Context) {

    companion object {
        const val CLOCK = "clock"
        const val NOTIFICATIONS = "notifications"
        const val BATTERY = "battery"
        const val SIGNAL = "signal"
        const val WIFI = "wifi"
    }

    private val clock = styleRow()
    private val notifications = styleRow()
    private val battery = styleRow()
    private val batteryPercent = styleRow()
    private val signal = styleRow()
    private val wifi = styleRow()

    /**
     * A style row belongs to the switch above it, so it is stepped in from the left and its
     * label is the quieter one — it reads as that switch's options rather than a setting of
     * its own.
     */
    private fun styleRow(): NexusSegmentedRow {
        val dp = context.resources.displayMetrics.density
        return NexusSegmentedRow(context).apply {
            setContentPadding((32 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setLabelSubdued(true)
        }
    }

    /** The style rows for one item, to sit directly under that item's switch. */
    fun rowsFor(item: String): List<NexusSegmentedRow> = when (item) {
        CLOCK -> listOf(clock)
        NOTIFICATIONS -> listOf(notifications)
        BATTERY -> listOf(battery, batteryPercent)
        SIGNAL -> listOf(signal)
        WIFI -> listOf(wifi)
        else -> emptyList()
    }

    fun bind(s: NexusSettingsData) {
        val style = ImmersiveStatusStyle.of(s)
        lastBatteryStyle = style.battery
        clock.configure(
            context.getString(R.string.status_style_clock),
            options(ImmersiveStatusStyle.CLOCK_OPTIONS), style.clock, inline = true,
        )
        notifications.configure(
            context.getString(R.string.status_style_notifications),
            options(ImmersiveStatusStyle.NOTIFICATION_OPTIONS), style.notifications, inline = true,
        )
        battery.configure(
            context.getString(R.string.status_style_battery),
            options(ImmersiveStatusStyle.BATTERY_OPTIONS), style.battery, inline = true,
        )
        batteryPercent.configure(
            context.getString(R.string.status_style_battery_percent),
            options(ImmersiveStatusStyle.PERCENT_OPTIONS), style.batteryPercent, inline = true,
        )
        signal.configure(
            context.getString(R.string.status_style_signal),
            options(ImmersiveStatusStyle.SIGNAL_OPTIONS), style.signal, inline = true,
        )
        wifi.configure(
            context.getString(R.string.status_style_wifi),
            options(ImmersiveStatusStyle.WIFI_OPTIONS), style.wifi, inline = true,
        )
        // A module that is off dims its rows rather than hiding them (StatusRowDimming), so
        // only the one row that would mean nothing is taken away.
        batteryPercent.visibility = visibility(style.battery != ImmersiveStatusStyle.BATTERY_TEXT)
    }

    fun setListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        isIgnoreCallbacks: () -> Boolean,
    ) {
        clock.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusClockStyle = v) }
        }
        notifications.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusNotificationStyle = v) }
        }
        battery.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) {
                lastBatteryStyle = v
                batteryPercent.visibility = visibility(v != ImmersiveStatusStyle.BATTERY_TEXT)
                onPatch { s -> s.copy(statusBatteryStyle = v) }
            }
        }
        batteryPercent.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusBatteryPercent = v) }
        }
        signal.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusSignalStyle = v) }
        }
        wifi.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusWifiStyle = v) }
        }
    }

    private var lastBatteryStyle = ImmersiveStatusStyle.BATTERY_BAR

    private fun visibility(shown: Boolean) = if (shown) View.VISIBLE else View.GONE

    private fun options(values: List<String>): List<Pair<String, String>> =
        values.map { it to context.getString(labelOf(it)) }

    private fun labelOf(value: String): Int = when (value) {
        ImmersiveStatusStyle.CLOCK_SYSTEM -> R.string.status_opt_default
        ImmersiveStatusStyle.CLOCK_12H -> R.string.status_opt_12h
        ImmersiveStatusStyle.CLOCK_24H -> R.string.status_opt_24h
        ImmersiveStatusStyle.CLOCK_SECONDS -> R.string.status_opt_seconds
        ImmersiveStatusStyle.CLOCK_DATE -> R.string.status_opt_date
        ImmersiveStatusStyle.NOTIFICATIONS_COUNT -> R.string.status_opt_count
        ImmersiveStatusStyle.NOTIFICATIONS_ICONS -> R.string.status_opt_app_icons
        ImmersiveStatusStyle.BATTERY_BAR -> R.string.status_opt_bar
        ImmersiveStatusStyle.BATTERY_RING -> R.string.status_opt_ring
        ImmersiveStatusStyle.BATTERY_TEXT -> R.string.status_opt_number
        ImmersiveStatusStyle.PERCENT_BESIDE -> R.string.status_opt_beside
        ImmersiveStatusStyle.PERCENT_INSIDE -> R.string.status_opt_inside
        ImmersiveStatusStyle.PERCENT_OFF -> R.string.status_opt_off
        ImmersiveStatusStyle.SIGNAL_ARC -> R.string.status_opt_arc
        ImmersiveStatusStyle.SIGNAL_DOTS -> R.string.status_opt_dots
        ImmersiveStatusStyle.WIFI_ARCS -> R.string.status_opt_arcs
        // "bars" and "dot" are shared by more than one item.
        ImmersiveStatusStyle.SIGNAL_BARS -> R.string.status_opt_bars
        else -> R.string.status_opt_dot
    }
}
