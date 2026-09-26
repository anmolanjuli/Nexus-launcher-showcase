package com.nexus.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.widgets.weather.NexusWeatherPermission

private const val PREFS = "nexus_prefs"
private const val KEY_ASKED = "location_permission_asked"
private const val KEY_LAST_GRANTED = "location_permission_last_granted"

internal fun MainActivity.consumeLocationPermissionIntent(intent: Intent?): Boolean {
    if (intent?.action != NexusWeatherPermission.ACTION_REQUEST) return false
    intent.action = null
    launchLocationPermissionFlow()
    return true
}

internal fun MainActivity.refreshWeatherWidgetsIfPermissionGained() {
    val granted = hasLocationPermission()
    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val wasGranted = prefs.getBoolean(KEY_LAST_GRANTED, false)
    if (granted != wasGranted) {
        prefs.edit().putBoolean(KEY_LAST_GRANTED, granted).apply()
        if (granted) {
            NexusWeatherPermission.refreshAll(this)
            reloadNexusWidgetsAfterPermission()
        }
    }
}

internal fun MainActivity.onLocationPermissionResult(requestCode: Int, grantResults: IntArray) {
    if (requestCode != NexusWeatherPermission.REQUEST_CODE) return
    val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_LAST_GRANTED, granted).apply()
    if (granted) {
        NexusWeatherPermission.refreshAll(this)
        reloadNexusWidgetsAfterPermission()
    }
}

private fun MainActivity.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
}

private fun MainActivity.launchLocationPermissionFlow() {
    if (hasLocationPermission()) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LAST_GRANTED, true).apply()
        NexusWeatherPermission.refreshAll(this)
        reloadNexusWidgetsAfterPermission()
        return
    }
    val asked = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)
    val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (!asked || showRationale) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
        requestPermissions(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            NexusWeatherPermission.REQUEST_CODE
        )
        return
    }
    openLocationAppSettings()
}

private fun MainActivity.openLocationAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    )
}
