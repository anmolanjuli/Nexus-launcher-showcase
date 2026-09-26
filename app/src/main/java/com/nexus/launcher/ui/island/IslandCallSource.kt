package com.nexus.launcher.ui.island

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.service.notification.StatusBarNotification
import com.nexus.launcher.R
import com.nexus.launcher.service.NexusNotificationService
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Incoming and ongoing calls, read from the call notification.
 *
 * The service already tells this class when a notification arrives or leaves; the one-second
 * scan it also ran was a second way of learning the same thing, so it now re-reads the list
 * when the list changes and never on a timer.
 */
class IslandCallSource(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private var activeCallKey: String? = null
    private var job: kotlinx.coroutines.Job? = null

    fun start() {
        instance = this
        NexusNotificationService.reloadPosted()
        job?.cancel()
        job = scope.launch {
            NexusNotificationService.postedRevision.collect { scanActiveCalls() }
        }
        scanActiveCalls()
    }

    fun stop() {
        if (instance === this) instance = null
        job?.cancel()
        job = null
        activeCallKey = null
        IslandTriggerBus.publish(IslandKind.CALL, null)
    }

    companion object {
        @Volatile
        var instance: IslandCallSource? = null

        fun onPosted(sbn: StatusBarNotification) {
            instance?.onNotificationPosted(sbn)
        }

        fun onRemoved(sbn: StatusBarNotification) {
            instance?.onNotificationRemoved(sbn)
        }
    }

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val payload = extractCallPayload(sbn)
        if (payload != null) {
            activeCallKey = sbn.key
            IslandTriggerBus.publish(IslandKind.CALL, payload)
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == activeCallKey) {
            activeCallKey = null
            IslandTriggerBus.publish(IslandKind.CALL, null)
        }
    }

    private fun scanActiveCalls() {
        val posted = NexusNotificationService.postedNotifications()
        val callSbn = posted.firstOrNull { extractCallPayload(it) != null }
        if (callSbn != null) {
            val payload = extractCallPayload(callSbn)
            activeCallKey = callSbn.key
            IslandTriggerBus.publish(IslandKind.CALL, payload)
        } else if (activeCallKey != null) {
            activeCallKey = null
            IslandTriggerBus.publish(IslandKind.CALL, null)
        }
    }

    private fun extractCallPayload(sbn: StatusBarNotification): IslandPayload? {
        val notification = sbn.notification ?: return null
        val category = notification.category
        val isCallCategory = category == Notification.CATEGORY_CALL
        val actions = notification.actions.orEmpty()

        var answerIntent: PendingIntent? = null
        var declineIntent: PendingIntent? = null

        for (action in actions) {
            val label = action.title?.toString()?.lowercase(Locale.ROOT) ?: ""
            if (label.contains("answer") || label.contains("accept") || label.contains("receive")) {
                answerIntent = action.actionIntent
            } else if (label.contains("decline") || label.contains("reject") || label.contains("dismiss") || label.contains("hang up")) {
                declineIntent = action.actionIntent
            }
        }

        val hasCallActions = answerIntent != null || declineIntent != null
        if (!isCallCategory && !hasCallActions) {
            return null
        }

        val extras = notification.extras
        val callerName = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras?.getCharSequence("android.title")?.toString()
            ?: extras?.getCharSequence(Notification.EXTRA_CALL_PERSON)?.toString()
            ?: context.getString(R.string.island_incoming_call)

        val subText = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: context.getString(R.string.island_incoming_call)

        return IslandPayload(
            kind = IslandKind.CALL,
            title = callerName,
            subtitle = subText,
            packageName = sbn.packageName,
            identity = "CALL:${sbn.key}",
            answerIntent = answerIntent,
            declineIntent = declineIntent,
        )
    }
}
