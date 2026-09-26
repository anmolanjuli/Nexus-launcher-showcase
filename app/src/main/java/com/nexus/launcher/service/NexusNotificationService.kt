package com.nexus.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NexusNotificationService : NotificationListenerService() {

    /** What the shade is showing, in the shapes the status row can ask for. */
    data class Counts(
        val all: Int = 0,
        val loud: Int = 0,
        val apps: Int = 0,
        val loudApps: Int = 0,
        val packages: List<String> = emptyList(),
    )

    companion object {
        private val _badgeCounts =
            MutableStateFlow<Map<String, Int>>(emptyMap())
        val badgeCounts: StateFlow<Map<String, Int>> =
            _badgeCounts.asStateFlow()

        fun getBadgeCount(packageName: String): Int =
            _badgeCounts.value[packageName] ?: 0

        private val _postedRevision = MutableStateFlow(0)

        /**
         * Ticks whenever a notification is posted, updated or removed. Anything that used to
         * poll `postedNotifications()` to notice a change can follow this instead.
         */
        val postedRevision: StateFlow<Int> = _postedRevision.asStateFlow()

        private val _counts = MutableStateFlow(Counts())
        val counts: StateFlow<Counts> = _counts.asStateFlow()

        private val _activeCount = MutableStateFlow(0)

        /**
         * How many notifications the shade is listing right now — what the immersive status row
         * shows. Unlike [badgeCounts] this counts ongoing ones (the shade lists them) and drops
         * a group's summary when its children are counted, so it matches the tray instead of
         * the icon badges, which deliberately leave ongoing notifications out.
         */
        val activeCount: StateFlow<Int> = _activeCount.asStateFlow()

        private val postedByKey = java.util.concurrent.ConcurrentHashMap<String, StatusBarNotification>()

        /**
         * The notifications still in the shade, by key. Their own tap actions live here and
         * nowhere else: a pending intent cannot be stored, so the notification sheet can only
         * reopen what is still posted.
         */
        fun posted(key: String): StatusBarNotification? = postedByKey[key]

        /**
         * Group summaries whose group has children among [notifications]. The shade shows the
         * children, not these — and cancelling a summary cancels its whole group, so a summary
         * listed as a row in the sheet ("4 new messages") cleared all four when dismissed.
         * The one test for this: the status-row count and the sheet both use it.
         */
        fun coveredSummaryKeys(notifications: Collection<StatusBarNotification>): Set<String> {
            val groupsWithChildren = notifications.mapNotNullTo(HashSet()) { sbn ->
                // isGroup/groupKey include the system's auto-grouping override, which a bare
                // notification.group misses (its children have no group of their own).
                if (sbn.isSummary() || !sbn.isGroup) null else sbn.groupKey
            }
            return notifications.mapNotNullTo(HashSet()) { sbn ->
                sbn.key.takeIf { sbn.isSummary() && sbn.groupKey in groupsWithChildren }
            }
        }

        private fun StatusBarNotification.isSummary(): Boolean =
            (notification?.flags ?: 0) and android.app.Notification.FLAG_GROUP_SUMMARY != 0

        /** Everything still in the shade, for the sheet's live list. */
        fun postedNotifications(): List<StatusBarNotification> = postedByKey.values.toList()

        /** Re-read the shade so a timer posted before the listener attached is visible. */
        fun reloadPosted() {
            val service = instance ?: return
            val active = runCatching { service.activeNotifications }.getOrNull() ?: return
            postedByKey.clear()
            active.forEach { postedByKey[it.key] = it }
        }

        /** The sheet's swipe-away, for a notification that is still posted. */
        fun dismiss(key: String) {
            val service = instance ?: return
            runCatching { service.cancelNotification(key) }
        }

        /** "Clear" in the sheet: the shade's own clear-all, for everything that can be cleared. */
        fun dismissAll() {
            val service = instance ?: return
            runCatching { service.cancelAllNotifications() }
        }

        @Volatile
        private var instance: NexusNotificationService? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        postedByKey[sbn.key] = sbn
        NotificationHistory.onPosted(applicationContext, sbn)
        updateCounts()
        com.nexus.launcher.ui.island.IslandCallSource.onPosted(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        postedByKey.remove(sbn.key)
        NotificationHistory.onRemoved(applicationContext, sbn)
        updateCounts()
        com.nexus.launcher.ui.island.IslandCallSource.onRemoved(sbn)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        NotificationHistory.bind(applicationContext)
        val active = runCatching { activeNotifications }.getOrNull()?.toList().orEmpty()
        postedByKey.clear()
        active.forEach { postedByKey[it.key] = it }
        NotificationHistory.onConnected(applicationContext, active)
        updateCounts()
    }

    override fun onListenerDisconnected() {
        instance = null
        postedByKey.clear()
        super.onListenerDisconnected()
    }

    /**
     * Every row the shade lists: each notification counts once, a group's summary is dropped
     * when its children are there (they are what the shade shows), and ongoing and silent ones
     * count too — they are on screen, so leaving them out read as too few.
     */
    private fun shadeCount(notifications: Array<android.service.notification.StatusBarNotification>): Int {
        val covered = coveredSummaryKeys(notifications.asList())
        return notifications.count { sbn ->
            if (sbn.key in covered) return@count false
            // Same test as the sheet: something with neither title nor text draws no row there,
            // so counting it made the row read one higher than the list underneath it.
            hasContent(sbn)
        }
    }

    private fun hasContent(sbn: android.service.notification.StatusBarNotification): Boolean {
        val extras = sbn.notification?.extras ?: return false
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(android.app.Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        return title.isNotBlank() || text.isNotBlank()
    }

    /** The same rows [shadeCount] counts, split by silent-or-not and by app. */
    private fun countsOf(
        notifications: Array<android.service.notification.StatusBarNotification>,
    ): Counts {
        val covered = coveredSummaryKeys(notifications.asList())
        val rows = notifications.filter { sbn -> sbn.key !in covered && hasContent(sbn) }
        val loud = rows.filter { it.notification.priority > android.app.Notification.PRIORITY_MIN }
        return Counts(
            all = rows.size,
            loud = loud.size,
            apps = rows.map { it.packageName }.distinct().size,
            loudApps = loud.map { it.packageName }.distinct().size,
            // Newest first: the icon stack shows the most recent apps.
            packages = rows.sortedByDescending { it.postTime }.map { it.packageName }.distinct(),
        )
    }

    private fun updateCounts() {
        try {
            val notifications = activeNotifications ?: return
            val counts = mutableMapOf<String, Int>()
            for (sbn in notifications) {
                if (sbn.isOngoing) continue
                val pkg = sbn.packageName
                // For group summaries, only count if no children exist for this package
                val isGroupSummary = sbn.notification.flags and
                        android.app.Notification.FLAG_GROUP_SUMMARY != 0
                if (isGroupSummary) {
                    // Only add if we haven't already counted children for this package
                    if (!counts.containsKey(pkg)) {
                        counts[pkg] = sbn.notification.number
                            .coerceAtLeast(1)
                    }
                } else {
                    counts[pkg] = (counts[pkg] ?: 0) + 1
                }
            }
            _badgeCounts.value = counts
            _activeCount.value = shadeCount(notifications)
            _counts.value = countsOf(notifications)
            _postedRevision.value = _postedRevision.value + 1
        } catch (e: Exception) {
            // Service not connected yet
        }
    }
}
