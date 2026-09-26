package com.nexus.launcher.ui.notifications

import android.content.Context
import com.nexus.launcher.data.NotificationRecord
import com.nexus.launcher.service.NexusNotificationService

/**
 * One line in the notification sheet: either a notification still in the shade or one the
 * launcher kept after it was cleared ([NotificationRecord]).
 */
data class NotificationEntry(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val active: Boolean,
)

/** The entries for one app, newest first, active ones above cleared ones. */
data class NotificationGroup(
    val packageName: String,
    val appLabel: String,
    val entries: List<NotificationEntry>,
) {
    val activeCount: Int get() = entries.count { it.active }
}

object NotificationFeed {

    /** Stored history, marked live where the shade still holds the same notification. */
    fun fromRecords(records: List<NotificationRecord>): List<NotificationEntry> =
        records.map { record ->
            NotificationEntry(
                key = record.key,
                packageName = record.packageName,
                title = record.title,
                text = record.text,
                postedAt = record.postedAt,
                active = record.clearedAt == 0L && NexusNotificationService.posted(record.key) != null,
            )
        }

    /**
     * Stored history plus anything live that is not stored yet — a notification can reach the
     * shade a moment before its row reaches the database, and the shade's contents from before
     * history was turned on are only imported once the listener has them. The sheet must never
     * show less than the shade does.
     */
    fun merged(records: List<NotificationRecord>): List<NotificationEntry> {
        val covered = NexusNotificationService.coveredSummaryKeys(
            NexusNotificationService.postedNotifications(),
        )
        val stored = fromRecords(records).filter { it.key !in covered }
        val keys = stored.mapTo(HashSet()) { it.key }
        return stored + live().filter { it.key !in keys }
    }

    /** What the shade holds right now — all the sheet can show with history turned off. */
    fun live(): List<NotificationEntry> {
        val posted = NexusNotificationService.postedNotifications()
        // A group's summary is not a row of its own — see coveredSummaryKeys.
        val covered = NexusNotificationService.coveredSummaryKeys(posted)
        return posted.filter { it.key !in covered }.mapNotNull { sbn ->
            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)
                ?: extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
            if (title.isBlank() && text.isBlank()) return@mapNotNull null
            NotificationEntry(
                key = sbn.key,
                packageName = sbn.packageName,
                title = title,
                text = text,
                postedAt = sbn.postTime,
                active = true,
            )
        }
    }

    /** Groups by app, apps with something live first, then by how recent they are. */
    fun group(context: Context, entries: List<NotificationEntry>): List<NotificationGroup> =
        entries.groupBy { it.packageName }
            .map { (pkg, items) ->
                NotificationGroup(
                    packageName = pkg,
                    appLabel = appLabel(context, pkg),
                    entries = items.sortedWith(
                        compareByDescending<NotificationEntry> { it.active }
                            .thenByDescending { it.postedAt },
                    ),
                )
            }
            .sortedWith(
                compareByDescending<NotificationGroup> { it.activeCount > 0 }
                    .thenByDescending { group -> group.entries.firstOrNull()?.postedAt ?: 0L },
            )

    private fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
