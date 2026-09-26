package com.nexus.launcher.ui.immersive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.service.NexusNotificationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Live values for the immersive status row: battery, time, Wi-Fi and mobile signal levels, and
 * the number of active notifications. [start] registers everything, [stop] releases it; every
 * change calls [onChanged] on the main thread.
 *
 * No new permissions: battery and time are broadcasts, Wi-Fi comes from ConnectivityManager
 * (ACCESS_NETWORK_STATE), the signal level from TelephonyManager.getSignalStrength (API 28+,
 * polled), and notifications from the badge counts NexusNotificationService already keeps
 * (0 when notification access is off).
 */
class StatusSources(private val context: Context, private val onChanged: () -> Unit) {

    var batteryPercent: Int = -1
        private set
    var isCharging: Boolean = false
        private set
    /** 0..4, or -1 when not on Wi-Fi. */
    var wifiLevel: Int = -1
        private set
    /** 0..4, or -1 when unavailable (no SIM, API < 28). */
    var signalLevel: Int = -1
        private set
    var notificationCount: Int = 0
        private set

    /** Icons of the apps with something waiting, for the stacked notification style. */
    var notificationIcons: List<android.graphics.drawable.Drawable> = emptyList()
        private set

    /** Words beside the meters, empty unless their setting is on and Android will hand them over. */
    var carrier: String = ""
        private set
    var dataType: String = ""
        private set
    var ssid: String = ""
        private set
    var band: String = ""
        private set

    /** Which of those to read; set from the status row's style. */
    var wantsNames: Names = Names()
        set(value) {
            if (field == value) return
            field = value
            readNames()
            onChanged()
        }

    data class Names(
        val carrier: Boolean = false,
        val dataType: Boolean = false,
        val ssid: Boolean = false,
        val band: Boolean = false,
    )

    /** Which count the row wants: silent ones in or out, per notification or per app. */
    var countShape: CountShape = CountShape()
        set(value) {
            if (field == value) return
            field = value
            applyCounts(NexusNotificationService.counts.value)
            onChanged()
        }

    data class CountShape(val includeSilent: Boolean = true, val byApp: Boolean = false)

    /** Loading icons is only worth it for the style that draws them. */
    var wantsAppIcons: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            // The style is set after the collector has already delivered the current count, so
            // without this the icons only appeared at the next notification.
            if (value) {
                readIcons()
                onChanged()
            }
        }

    /** The minute tick is enough for every clock style but the one showing seconds. */
    var wantsSeconds: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            main.removeCallbacks(secondTick)
            if (value && started) main.post(secondTick)
        }

    private val main = Handler(Looper.getMainLooper())
    private var started = false
    private var notificationJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) readBattery(intent)
            onChanged()
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val level = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                // signalStrength is API 29+; below that the level is unknown, shown as full.
                wifiLevelFor(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) caps.signalStrength
                    else Int.MIN_VALUE
                )
            } else -1
            main.post {
                readNames()
                if (level != wifiLevel) wifiLevel = level
                onChanged()
            }
        }

        override fun onLost(network: Network) {
            main.post { if (wifiLevel != -1) { wifiLevel = -1; onChanged() } }
        }
    }

    private val secondTick = object : Runnable {
        override fun run() {
            onChanged()
            main.postDelayed(this, 1_000L)
        }
    }

    private val signalPoll = object : Runnable {
        override fun run() {
            val level = readSignalLevel()
            val before = carrier + dataType + ssid + band
            readNames()
            if (level != signalLevel || before != carrier + dataType + ssid + band) {
                signalLevel = level
                onChanged()
            }
            main.postDelayed(this, SIGNAL_POLL_MS)
        }
    }

    fun start(owner: LifecycleOwner) {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)?.let { readBattery(it) }
        try {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.registerDefaultNetworkCallback(networkCallback)
        } catch (_: Exception) { }
        main.post(signalPoll)
        if (wantsSeconds) main.post(secondTick)
        notificationJob = owner.lifecycleScope.launch {
            // The shade's own count, not the sum of icon badges: badges leave out ongoing
            // notifications and count a group as one, so the row read lower than the tray.
            NexusNotificationService.counts.collect { counts ->
                applyCounts(counts)
                if (wantsAppIcons) readIcons()
                onChanged()
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
        try {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)
                ?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { }
        main.removeCallbacks(signalPoll)
        main.removeCallbacks(secondTick)
        notificationJob?.cancel()
        notificationJob = null
    }

    private fun applyCounts(counts: NexusNotificationService.Counts) {
        notificationCount = when {
            countShape.byApp && countShape.includeSilent -> counts.apps
            countShape.byApp -> counts.loudApps
            countShape.includeSilent -> counts.all
            else -> counts.loud
        }
        notificationPackages = counts.packages
    }

    private var notificationPackages: List<String> = emptyList()

    /** At most [maxIcons], newest apps first; a missing icon is simply left out. */
    private fun readIcons() {
        notificationIcons = notificationPackages.take(maxIcons).mapNotNull { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
        }
    }

    /** How many app icons the stack may show (Settings → Status info → Max icons). */
    var maxIcons: Int = 3
        set(value) {
            val next = value.coerceIn(1, 10)
            if (field == next) return
            field = next
            if (wantsAppIcons) { readIcons(); onChanged() }
        }

    private fun readNames() {
        carrier = if (wantsNames.carrier) StatusNetworkNames.carrier(context) else ""
        dataType = if (wantsNames.dataType) StatusNetworkNames.dataType(context) else ""
        ssid = if (wantsNames.ssid) StatusNetworkNames.ssid(context) else ""
        band = if (wantsNames.band) StatusNetworkNames.band(context) else ""
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun readSignalLevel(): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return -1
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm == null || tm.simState != TelephonyManager.SIM_STATE_READY) -1
            else tm.signalStrength?.level ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    /** RSSI (dBm) to 0..4 bars, the thresholds Android's own status bar uses. */
    private fun wifiLevelFor(rssi: Int): Int = when {
        rssi == Int.MIN_VALUE -> 4 // SIGNAL_STRENGTH_UNSPECIFIED, or unknown below API 29
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }

    private companion object {
        const val SIGNAL_POLL_MS = 15_000L
    }
}
