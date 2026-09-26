package com.nexus.launcher.ui

import android.content.Context

object UsageStatsHelper {

    fun isUsageStatsPermissionGranted(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
            if (appOps != null) {
                val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                }
                mode == android.app.AppOpsManager.MODE_ALLOWED
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    // Requires PACKAGE_USAGE_STATS permission
    // User must grant via Settings → Apps → Special app access → Usage access
    fun getRecentAppsFromUsageStats(context: Context): List<String> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return emptyList()
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 1000L * 60 * 60 * 24 * 7
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            if (stats.isNullOrEmpty()) return emptyList()
            stats
                .filter { it.lastTimeUsed > 0 || it.totalTimeInForeground > 0 }
                .sortedByDescending { it.lastTimeUsed }
                .map { it.packageName }
                .distinct()
                .filter { pkg -> pkg != context.packageName && context.packageManager.getLaunchIntentForPackage(pkg) != null }
                .take(12)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
