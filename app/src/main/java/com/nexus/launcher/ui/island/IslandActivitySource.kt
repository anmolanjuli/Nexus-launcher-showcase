package com.nexus.launcher.ui.island

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.nexus.launcher.service.NexusNotificationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Whatever an app is doing right now, read from its own ongoing notification: a download, a
 * route, a ride on its way, a recording, an upload.
 *
 * This is the island's general case. Android already asks apps to describe live work in a
 * notification — a progress bar, a running clock, a few buttons — so rather than a class per
 * app, anything that describes itself that way becomes an island activity. Calls, media and the
 * clock app keep their own sources, which read them better, and are skipped here.
 *
 * Nothing is polled: the notification service says when its list changed.
 */
class IslandActivitySource(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private var job: Job? = null

    fun start() {
        NexusNotificationService.reloadPosted()
        job?.cancel()
        job = scope.launch {
            NexusNotificationService.postedRevision.collect { emit() }
        }
        emit()
    }

    fun stop() {
        job?.cancel()
        job = null
        IslandTriggerBus.publish(IslandKind.ACTIVITY, null)
    }

    private fun emit() {
        val best = NexusNotificationService.postedNotifications()
            .filter { isLiveActivity(it) }
            .maxByOrNull { it.postTime }
        IslandTriggerBus.publish(IslandKind.ACTIVITY, best?.let { toPayload(it) })
    }

    /**
     * Ongoing, and describing something in flight — a progress bar or a running clock. Media
     * and calls are left to the sources that understand them; a plain ongoing notification with
     * nothing moving (a VPN, a keyboard) is not an activity and would only sit there.
     */
    private fun isLiveActivity(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification ?: return false
        if (sbn.packageName == context.packageName) return false
        val flags = n.flags
        val ongoing = (flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (!ongoing) return false
        val extras = n.extras ?: return false
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return false
        // A timer is a chronometer too. It belongs to the clock source, which reads the time
        // off it; taken as a generic activity it outranked that and showed a bare "Timer".
        if (IslandClockSource.isClockNotification(sbn)) return false
        if (n.category == Notification.CATEGORY_CALL ||
            n.category == Notification.CATEGORY_TRANSPORT
        ) return false
        val hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val hasChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        return hasProgress || hasChronometer
    }

    private fun toPayload(sbn: StatusBarNotification): IslandPayload {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        val chrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        return IslandPayload(
            kind = IslandKind.ACTIVITY,
            title = title.ifBlank { appLabel(sbn.packageName) },
            subtitle = text,
            packageName = sbn.packageName,
            // One identity per app, so an app updating its notification stays the same activity
            // rather than looking like a new one every few seconds.
            identity = "activity:${sbn.packageName}",
            progress = if (!indeterminate && max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else -1f,
            busy = indeterminate,
            chronoBaseMs = if (chrono) n.`when` else 0L,
            countDown = chrono && isCountDown(extras),
            actions = IslandNotificationParts.actions(n),
            icon = IslandNotificationParts.smallIcon(context, sbn),
            contentIntent = n.contentIntent,
        )
    }

    private fun isCountDown(extras: android.os.Bundle): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N &&
            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
