package com.nexus.launcher.service

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.nexus.launcher.data.NotificationDao
import com.nexus.launcher.data.NotificationRecord
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Keeps what the notification listener sees, so the launcher's own notification sheet can show
 * notifications that have already been cleared as well as the ones still in the shade.
 *
 * Off unless the user turns it on ([enabled]): it stores notification text in the launcher's
 * database, which is theirs to opt into. Turning it off wipes what was stored.
 *
 * A notification's own tap action belongs to the system and dies with it, so only live ones can
 * be reopened where they were ([NexusNotificationService.posted]); a stored one opens its app.
 */
object NotificationHistory {

    @Volatile var enabled: Boolean = false
        private set

    /** How long a cleared notification is kept. */
    @Volatile var retentionHours: Int = DEFAULT_RETENTION_HOURS
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dao: NotificationDao? = null

    private var bound = false

    /**
     * Follows the setting from wherever this is first needed — the listener service runs with
     * no launcher activity around, so it cannot wait for the home screen to push settings in.
     */
    fun bind(context: Context) {
        if (bound) return
        bound = true
        val app = context.applicationContext
        val repository = runCatching {
            EntryPointAccessors.fromApplication(
                app, com.nexus.launcher.ui.dock.settings.DockResetEntryPoint::class.java,
            ).settingsRepository()
        }.getOrNull() ?: return
        scope.launch {
            repository.settingsFlow.collect { settings ->
                setup(app, settings.notificationHistory, settings.notificationRetentionHours)
            }
        }
    }

    fun setup(context: Context, isEnabled: Boolean, hours: Int) {
        val was = enabled
        enabled = isEnabled
        retentionHours = hours.coerceIn(MIN_RETENTION_HOURS, MAX_RETENTION_HOURS)
        val store = dao(context) ?: return
        scope.launch {
            if (!isEnabled) {
                if (was) store.clear()
                return@launch
            }
            store.deleteOlderThan(cutoff())
        }
        // Turning it on — or learning that it is on, which happens after the listener connects
        // because [bind] reads settings asynchronously — must take in what the shade already
        // holds. [onConnected] runs first and sees `enabled == false`, so without this nothing
        // was ever stored until a new notification arrived, and the sheet showed empty.
        if (isEnabled && !was) importActive(context, NexusNotificationService.postedNotifications())
    }

    fun observe(context: Context): Flow<List<NotificationRecord>>? =
        dao(context)?.observeSince(cutoff())

    fun onPosted(context: Context, sbn: StatusBarNotification) {
        if (!enabled) return
        val store = dao(context) ?: return
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        scope.launch {
            // The same notification updated in place (a chat thread) keeps one row, moved to now.
            // Replace the posted row rather than clearing it: clearing left a "cleared" copy of
            // the thread behind on every update, and of every notification on every restart.
            store.deletePosted(sbn.key)
            store.insert(
                NotificationRecord(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = title,
                    text = text,
                    postedAt = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    color = sbn.notification?.color ?: 0,
                    ongoing = sbn.isOngoing,
                )
            )
            store.deleteOlderThan(cutoff())
        }
    }

    fun onRemoved(context: Context, sbn: StatusBarNotification) {
        if (!enabled) return
        val store = dao(context) ?: return
        scope.launch { store.markCleared(sbn.key, System.currentTimeMillis()) }
    }

    /** On connect, the shade's own list is the truth about what is still posted. */
    fun onConnected(context: Context, active: List<StatusBarNotification>) {
        if (!enabled) return
        importActive(context, active)
    }

    private fun importActive(context: Context, active: List<StatusBarNotification>) {
        val store = dao(context) ?: return
        scope.launch {
            val now = System.currentTimeMillis()
            if (active.isEmpty()) store.markAllCleared(now)
            else store.markClearedExcept(active.map { it.key }, now)
            active.forEach { onPosted(context, it) }
            store.deleteOlderThan(cutoff())
        }
    }

    /** Removes one notification from the kept list (the shade's own copy is cancelled apart). */
    fun forget(context: Context, key: String) {
        val store = dao(context) ?: return
        scope.launch { store.deleteByKey(key) }
    }

    fun clear(context: Context) {
        val store = dao(context) ?: return
        scope.launch { store.clear() }
    }

    private fun cutoff(): Long =
        System.currentTimeMillis() - retentionHours.toLong() * 60L * 60L * 1000L

    private fun dao(context: Context): NotificationDao? = dao ?: runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext, DaoEntryPoint::class.java,
        ).notificationDao().also { dao = it }
    }.getOrNull()

    const val DEFAULT_RETENTION_HOURS = 24
    const val MIN_RETENTION_HOURS = 1
    const val MAX_RETENTION_HOURS = 24 * 7
}
