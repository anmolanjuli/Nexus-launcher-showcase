package com.nexus.launcher.ui.immersive

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager

/**
 * The words beside the meters: the carrier's name, the mobile data type (5G, LTE, ...), the
 * Wi-Fi network's name and its band.
 *
 * Each one is optional and off by default, because Android hands some of them over only with a
 * permission the launcher does not ask for: without it the network name comes back as a
 * placeholder and the data type is unknown. Anything unavailable is left out rather than shown
 * as "unknown" — see each reader.
 */
object StatusNetworkNames {

    /**
     * The carrier's own name, or empty. Needs no permission. The SIM's own operator stands in
     * where the network has not named itself — and no SIM state is demanded, because a dual-SIM
     * phone reports the first slot, which may not be the one in use.
     */
    fun carrier(context: Context): String = runCatching {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return ""
        tm.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: tm.simOperatorName?.takeIf { it.isNotBlank() }
            ?: ""
    }.getOrDefault("")

    /** Whether Android will tell us the data type at all; it needs READ_PHONE_STATE. */
    fun canReadDataType(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    /** "5G", "LTE", "3G"... — empty without READ_PHONE_STATE, which is not requested. */
    fun dataType(context: Context): String {
        if (!canReadDataType(context)) return ""
        return runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return ""
            when (tm.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP -> "H+"
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> ""
            }
        }.getOrDefault("")
    }

    /**
     * The network's name needs a location permission at runtime — Android hands back a
     * placeholder without one, and the launcher does not ask for it.
     */
    fun canReadSsid(context: Context): Boolean =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The network's name, or empty when Android withholds it. */
    fun ssid(context: Context): String = runCatching {
        if (!canReadSsid(context)) return ""
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return ""
        @Suppress("DEPRECATION")
        val raw = wifi.connectionInfo?.ssid.orEmpty().trim('"')
        if (raw.isBlank() || raw.contains("unknown", ignoreCase = true) || raw == "0x") "" else raw
    }.getOrDefault("")

    /** The Wi-Fi band from the connection's frequency — spelled out, so "5 GHz" is not read
     *  as the mobile network's 5G. */
    fun band(context: Context): String = runCatching {
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return ""
        @Suppress("DEPRECATION")
        when (wifi.connectionInfo?.frequency ?: 0) {
            in 2400..2500 -> "2.4GHz"
            in 4900..5900 -> "5GHz"
            in 5925..7125 -> "6GHz"
            else -> ""
        }
    }.getOrDefault("")
}
