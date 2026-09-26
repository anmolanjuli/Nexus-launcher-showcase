package com.nexus.launcher.ui.widgets.liveapp

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.nexus.launcher.ui.UsageStatsHelper
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The apps shown by the Recent apps widget (the Live App box; its code and stored kind keep the old
 * name so existing widgets and backups still load).
 *
 * Most recently used first, from usage access. Until 2026-09-24 it only showed apps used in the
 * last 45 minutes or running a foreground service, so the widget emptied itself whenever the
 * phone sat idle; now it is simply the last apps used, seeded from the past [SEED_WINDOW_MS] of
 * usage stats and kept current from usage events. A foreground service still earns the dot.
 */
object LiveAppRepository {

    private const val TAG = "LiveAppDiag"
    private const val SEED_WINDOW_MS = 1000L * 60 * 60 * 24 * 3
    private const val EVENTS_BASELINE_MS = 1000L * 60 * 60 * 2

    private val _liveAppsFlow = MutableStateFlow<List<LiveAppEntry>>(emptyList())
    val liveAppsFlow: StateFlow<List<LiveAppEntry>> = _liveAppsFlow.asStateFlow()

    private val _isUsageAccessGranted = MutableStateFlow(false)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    private var activeWidgetCount = 0
    private var refreshJob: Job? = null
    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private class PackageState(
        val packageName: String,
        var lastForegroundTime: Long = 0L,
        var hasForegroundService: Boolean = false
    )

    private val packageStates = mutableMapOf<String, PackageState>()
    private var lastQueryTimestamp: Long = 0L
    private val unlaunchable = mutableSetOf<String>()

    private val iconCache = mutableMapOf<String, Drawable>()
    private val labelCache = mutableMapOf<String, String>()

    fun registerWidget(context: Context) {
        activeWidgetCount++
        refresh(context)
    }

    fun unregisterWidget() {
        activeWidgetCount = (activeWidgetCount - 1).coerceAtLeast(0)
    }

    fun hasActiveWidgets(): Boolean = activeWidgetCount > 0

    fun refresh(context: Context, @Suppress("UNUSED_PARAMETER") scope: CoroutineScope? = null) {
        if (activeWidgetCount <= 0) return
        val appContext = context.applicationContext
        refreshJob?.cancel()
        refreshJob = repoScope.launch {
            executeRefresh(appContext)
        }
    }

    // Synchronized: a refresh has no suspension point, so cancelling the previous job does not
    // stop it, and two could otherwise update packageStates at once.
    @Synchronized
    private fun executeRefresh(context: Context) {
        val hasPermission = UsageStatsHelper.isUsageStatsPermissionGranted(context)
        _isUsageAccessGranted.value = hasPermission
        if (!hasPermission) {
            if (_liveAppsFlow.value.isNotEmpty()) {
                _liveAppsFlow.value = emptyList()
            }
            return
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
        val pm = context.packageManager
        val selfPkg = context.packageName
        val now = System.currentTimeMillis()

        // First refresh: seed from the usage stats of the last few days, so the widget is full
        // even when nothing was opened in the last couple of hours.
        if (lastQueryTimestamp == 0L) {
            try {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - SEED_WINDOW_MS, now)?.forEach { stats ->
                    val pkg = stats.packageName ?: return@forEach
                    if (pkg == selfPkg || stats.lastTimeUsed <= 0L || stats.totalTimeInForeground <= 0L) return@forEach
                    val state = packageStates.getOrPut(pkg) { PackageState(pkg) }
                    state.lastForegroundTime = maxOf(state.lastForegroundTime, stats.lastTimeUsed)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed querying usage stats", e)
            }
        }

        // Then events: since the last query (10s overlap), or the last two hours the first time.
        val eventsStartTime = if (lastQueryTimestamp > 0L) {
            (lastQueryTimestamp - 10_000L).coerceAtLeast(now - EVENTS_BASELINE_MS)
        } else {
            now - EVENTS_BASELINE_MS
        }
        lastQueryTimestamp = now

        try {
            val events = usm.queryEvents(eventsStartTime, now)
            val event = UsageEvents.Event()
            while (events != null && events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg.isEmpty() || pkg == selfPkg) continue
                val state = packageStates.getOrPut(pkg) { PackageState(pkg) }
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED ->
                        state.lastForegroundTime = maxOf(state.lastForegroundTime, event.timeStamp)
                    19 -> state.hasForegroundService = true // FOREGROUND_SERVICE_START (API 29)
                    20 -> state.hasForegroundService = false // FOREGROUND_SERVICE_STOP (API 29)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying UsageEvents", e)
        }

        // Most recently used first. Apps with no launcher entry (system UI, keyboards) are skipped
        // below, before they can take a slot, so the list is walked rather than cut to size here.
        val sorted = packageStates.values
            .filter { it.lastForegroundTime > 0L && it.packageName !in unlaunchable }
            .sortedByDescending { it.lastForegroundTime }

        val resolver = try {
            EntryPointAccessors.fromApplication(
                context,
                com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
            ).iconResolver()
        } catch (_: Exception) {
            null
        }

        val appEntries = mutableListOf<LiveAppEntry>()
        for (state in sorted) {
            if (appEntries.size >= LiveAppConfig.MAX_ICONS) break
            val pkg = state.packageName
            // Checked every time, cached icon or not: with no time cutoff an uninstalled app
            // would otherwise stay in the list. At most MAX_ICONS lookups, off the main thread.
            val launchIntent = try { pm.getLaunchIntentForPackage(pkg) } catch (_: Exception) { null }
            if (launchIntent == null) {
                unlaunchable.add(pkg)
                iconCache.remove(pkg)
                labelCache.remove(pkg)
                continue
            }
            try {
                val label = labelCache[pkg] ?: pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                val icon = iconCache[pkg]
                    ?: resolver?.getIcon(packageName = pkg, className = launchIntent.component?.className)
                    ?: pm.getApplicationIcon(pkg)
                iconCache[pkg] = icon
                labelCache[pkg] = label
                appEntries.add(
                    LiveAppEntry(
                        packageName = pkg,
                        label = label,
                        icon = icon,
                        lastTimeUsed = state.lastForegroundTime,
                        isForegroundService = state.hasForegroundService
                    )
                )
            } catch (_: Exception) {
                // Removed between the two lookups; the next refresh drops it.
            }
        }

        if (_liveAppsFlow.value != appEntries) {
            _liveAppsFlow.value = appEntries
        }
    }
}
