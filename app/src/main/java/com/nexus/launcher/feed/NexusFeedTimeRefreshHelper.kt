package com.nexus.launcher.feed

import android.content.SharedPreferences
import java.util.Locale

object NexusFeedTimeRefreshHelper {
    const val PREFS_NAME = "nexus_feed_prefs"
    const val KEY_REFRESH_INTERVAL = "feed_refresh_interval"

    /**
     * Set by a backup restore, consumed once by [com.nexus.launcher.feed.NexusFeedRefreshController].
     * Forces one fetch regardless of the interval or the last-refresh stamp: article bodies are
     * never part of a backup, so after a restore the feed has sources it has never fetched for.
     */
    const val KEY_PENDING_RESTORE_REFRESH = "pending_restore_refresh"
    const val DEFAULT_REFRESH_INTERVAL = "1h"

    fun shouldRefresh(prefs: SharedPreferences): Boolean {
        val intervalStr = prefs.getString(
            KEY_REFRESH_INTERVAL,
            DEFAULT_REFRESH_INTERVAL
        ) ?: "1h"
        if (intervalStr.equals("off", ignoreCase = true)) return false

        val intervalMs = when (intervalStr.lowercase(Locale.ROOT)) {
            "15m" -> 15 * 60 * 1000L
            "30m" -> 30 * 60 * 1000L
            "1h" -> 60 * 60 * 1000L
            "6h" -> 6 * 3600 * 1000L
            "12h" -> 12 * 3600 * 1000L
            "24h" -> 24 * 3600 * 1000L
            else -> 60 * 60 * 1000L
        }
        val lastRefresh = prefs.getLong("last_refresh_time", 0L)
        val now = System.currentTimeMillis()
        return (now - lastRefresh >= intervalMs)
    }
}
