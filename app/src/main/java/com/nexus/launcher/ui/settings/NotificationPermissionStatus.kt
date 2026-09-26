package com.nexus.launcher.ui.settings

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexus.launcher.service.NexusAccessibilityService

/** Centralized status and permission checks for notification-related settings. */
object NotificationPermissionStatus {

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasStoragePermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasAppUsagePermission(context: Context): Boolean {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isAccessibilityEnabled(): Boolean =
        NexusAccessibilityService.instance != null

    fun getGrantedCount(context: Context): Int {
        var count = 0
        if (hasNotificationAccess(context)) count++
        if (hasStoragePermission(context)) count++
        if (isAccessibilityEnabled()) count++
        if (hasAppUsagePermission(context)) count++
        if (hasLocationPermission(context)) count++
        return count
    }

    fun getStatusLabel(context: Context, granted: Boolean, grantedText: String? = null): String =
        if (granted) (grantedText ?: context.getString(com.nexus.launcher.R.string.permission_status_granted)) else context.getString(com.nexus.launcher.R.string.permission_status_tap_to_enable)

    fun getStatusLabel(granted: Boolean, grantedText: String = "Granted"): String =
        if (granted) grantedText else "Tap to enable"
}
