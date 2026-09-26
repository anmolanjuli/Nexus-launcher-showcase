package com.nexus.launcher.ui.widgets.battery

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.nexus.launcher.ui.UsageStatsHelper
import com.nexus.launcher.ui.icons.IconResolverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Queries [UsageStatsManager] to discover the top foreground battery consumers in the last hour.
 * Employs a 60-second in-memory cache to prevent repetitive IPC calls during render cycles.
 */
object NexusBatteryUsageHelper {

    private var cachedConsumers: List<BatteryConsumerApp> = emptyList()
    private var lastCacheTime: Long = 0L
    private const val CACHE_TTL_MS = 60_000L

    fun hasUsagePermission(context: Context): Boolean {
        return UsageStatsHelper.isUsageStatsPermissionGranted(context)
    }

    fun getTopConsumers(context: Context, maxCount: Int = 3): List<BatteryConsumerApp> {
        val now = System.currentTimeMillis()
        if (now - lastCacheTime < CACHE_TTL_MS && cachedConsumers.isNotEmpty()) {
            return cachedConsumers
        }

        if (!hasUsagePermission(context)) {
            return emptyList()
        }

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()
            val startTime = now - (3600 * 1000L) // Last 1 hour
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, now)
            if (stats.isNullOrEmpty()) return emptyList()

            val pm = context.packageManager
            val ownPkg = context.packageName

            // Aggregate foreground time per package (INTERVAL_BEST may return multiple interval buckets)
            val aggregated = stats
                .filter { it.totalTimeInForeground > 0L && it.packageName != ownPkg }
                .groupBy { it.packageName }
                .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
                .filter { (pkg, time) -> time > 0L && pm.getLaunchIntentForPackage(pkg) != null }

            val sorted = aggregated.toList().sortedByDescending { it.second }.take(maxCount)
            if (sorted.isEmpty()) return emptyList()

            val totalTime = sorted.sumOf { it.second }.coerceAtLeast(1L)

            val resolver = runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    IconResolverEntryPoint::class.java
                ).iconResolver()
            }.getOrNull()

            val results = sorted.map { (pkg, time) ->
                val percent = ((time * 100L) / totalTime).toInt().coerceIn(1, 100)
                val label = runCatching {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                }.getOrDefault(pkg)

                val icon = runCatching {
                    resolver?.getIcon(pkg) ?: pm.getApplicationIcon(pkg)
                }.getOrNull()

                BatteryConsumerApp(
                    packageName = pkg,
                    appLabel = label,
                    percentOfDrain = percent,
                    iconDrawable = icon
                )
            }

            cachedConsumers = results
            lastCacheTime = now
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Mock top consumers for settings preview carousel when permission is absent or previewing. */
    fun createMockConsumers(context: Context): List<BatteryConsumerApp> {
        val pm = context.packageManager
        val ownIcon = runCatching { pm.getApplicationIcon(context.packageName) }.getOrNull()
        return listOf(
            BatteryConsumerApp("com.android.chrome", context.getString(com.nexus.launcher.R.string.preview_app_browser), 46, ownIcon),
            BatteryConsumerApp("com.google.android.youtube", context.getString(com.nexus.launcher.R.string.category_media), 32, ownIcon),
            BatteryConsumerApp("com.nexus.launcher", "Nexus", 22, ownIcon)
        )
    }
}
