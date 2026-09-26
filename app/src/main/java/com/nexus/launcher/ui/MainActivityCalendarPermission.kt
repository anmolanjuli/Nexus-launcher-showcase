package com.nexus.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarPermission

private const val PREFS = "nexus_prefs"
private const val KEY_ASKED = "calendar_permission_asked"
private const val KEY_LAST_GRANTED = "calendar_permission_last_granted"

internal fun MainActivity.consumeCalendarPermissionIntent(intent: Intent?): Boolean {
    if (intent?.action != NexusCalendarPermission.ACTION_REQUEST) return false
    intent.action = null
    launchCalendarPermissionFlow()
    return true
}

internal fun MainActivity.refreshCalendarWidgetsIfPermissionGained() {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val wasGranted = prefs.getBoolean(KEY_LAST_GRANTED, false)
    if (granted != wasGranted) {
        prefs.edit().putBoolean(KEY_LAST_GRANTED, granted).apply()
        if (granted) {
            NexusCalendarPermission.refreshAll(this)
            reloadNexusWidgetsAfterPermission()
        }
    }
}

internal fun MainActivity.onCalendarPermissionResult(requestCode: Int, grantResults: IntArray) {
    if (requestCode != NexusCalendarPermission.REQUEST_CODE) return
    val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
    getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_LAST_GRANTED, granted).apply()
    if (granted) {
        NexusCalendarPermission.refreshAll(this)
        reloadNexusWidgetsAfterPermission()
    }
}

private fun MainActivity.launchCalendarPermissionFlow() {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LAST_GRANTED, true).apply()
        NexusCalendarPermission.refreshAll(this)
        reloadNexusWidgetsAfterPermission()
        return
    }
    val asked = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)
    val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
        this, Manifest.permission.READ_CALENDAR
    )
    if (!asked || showRationale) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
        requestPermissions(
            arrayOf(Manifest.permission.READ_CALENDAR),
            NexusCalendarPermission.REQUEST_CODE
        )
        return
    }
    openCalendarAppSettings()
}

private fun MainActivity.openCalendarAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
    )
}
