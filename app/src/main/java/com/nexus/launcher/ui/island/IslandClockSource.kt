package com.nexus.launcher.ui.island

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import com.nexus.launcher.R
import com.nexus.launcher.service.NexusNotificationService
import kotlinx.coroutines.launch

/**
 * Timers and stopwatches, read from the notifications the clock app posts.
 *
 * It used to scan and classify every posted notification twice a second for as long as the
 * island was switched on. Now it reads the list when the list changes
 * ([NexusNotificationService.postedRevision]) and keeps a one-second tick only while a timer or
 * stopwatch is actually running, because only then does a second hand need moving.
 */
class IslandClockSource(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var job: kotlinx.coroutines.Job? = null
    private var counting = false

    private val tick = object : Runnable {
        override fun run() {
            emit()
            if (counting) handler.postDelayed(this, 1000L)
        }
    }

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
        counting = false
        handler.removeCallbacks(tick)
        IslandTriggerBus.publish(IslandKind.TIMER, null)
        IslandTriggerBus.publish(IslandKind.STOPWATCH, null)
    }

    private fun emit() {
        val posted = NexusNotificationService.postedNotifications()
        val timer = posted.firstOrNull { classify(it) == IslandKind.TIMER }
        val watch = posted.firstOrNull { classify(it) == IslandKind.STOPWATCH }
        publishClock(IslandKind.TIMER, timer)
        publishClock(IslandKind.STOPWATCH, watch)
        setCounting(timer != null || watch != null)
    }

    /** The per-second tick exists only while there is a running clock to keep up with. */
    private fun setCounting(now: Boolean) {
        if (counting == now) return
        counting = now
        handler.removeCallbacks(tick)
        if (now) handler.postDelayed(tick, 1000L)
    }

    private fun publishClock(kind: IslandKind, sbn: StatusBarNotification?) {
        if (sbn == null) {
            IslandTriggerBus.publish(kind, null)
            return
        }
        val extras = sbn.notification.extras
        val chronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        // What the app says in its extras, and failing that what its own notification view is
        // showing — the stopwatch in Google's clock only says it there.
        val fromView = if (chronometer) IslandClockFace.Face() else IslandClockFace.read(context, sbn)
        val base = if (chronometer) sbn.notification.`when` else fromView.wallBaseMs
        val counting = base > 0L
        // Only a face with a number in it is worth showing; "Timer" beside the word Timer is not.
        // While the card counts for itself, the title stays the bare label.
        val raw = when {
            counting -> ""
            fromView.text.isNotEmpty() -> fromView.text
            else -> clockFace(sbn).takeIf { face -> face.any { it.isDigit() } }.orEmpty()
        }
        val res = if (kind == IslandKind.TIMER) R.string.island_timer else R.string.island_stopwatch
        val title = context.getString(res, raw).trim()
        IslandTriggerBus.publish(
            kind,
            IslandPayload(
                kind = kind,
                title = title,
                packageName = sbn.packageName,
                identity = "${kind.name}:${sbn.key}",
                // Pause and Reset belong to the clock app; the island only passes them on.
                actions = IslandNotificationParts.actions(sbn.notification),
                icon = IslandNotificationParts.smallIcon(context, sbn),
                contentIntent = sbn.notification.contentIntent,
                chronoBaseMs = base,
                countDown = if (chronometer) isCountDown(extras) else fromView.countDown,
            ),
        )
    }

    private fun isCountDown(extras: android.os.Bundle?): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) == true

    private fun classify(sbn: StatusBarNotification): IslandKind? {
        if (!isClockNotification(sbn)) return null
        val n = sbn.notification
        val extras = n.extras ?: return null
        val blob = describe(sbn)
        val countdown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val stopwatch = blob.contains("stopwatch") || blob.contains("stop watch") ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && n.category == Notification.CATEGORY_STOPWATCH)
        val timerWord = blob.contains("timer") || countdown
        val chronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val ongoing = n.flags and (
            Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_NO_CLEAR
            ) != 0
        return when {
            stopwatch -> IslandKind.STOPWATCH
            timerWord || n.category == Notification.CATEGORY_ALARM -> IslandKind.TIMER
            chronometer -> IslandKind.STOPWATCH
            ongoing -> IslandKind.TIMER
            else -> null
        }
    }

    /**
     * The face to show: the running time if the notification carries one, otherwise whichever
     * of its own lines already reads as a clock.
     *
     * Clock apps differ. Some set a chronometer and let the system count; some write the
     * remaining time into the title, others into the text, with the word "Timer" in the other
     * line. Taking the title blindly is how the island came to show "Timer" and no number.
     */
    private fun clockFace(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val chronometer = extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        val countdown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) == true
        val base = sbn.notification.`when`
        if ((chronometer || countdown) && base > 0L) {
            val delta = if (countdown) base - System.currentTimeMillis() else System.currentTimeMillis() - base
            return formatClock(delta)
        }
        val lines = listOfNotNull(
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            sbn.notification.tickerText?.toString(),
        ).filter { it.isNotBlank() }
        lines.firstOrNull { CLOCK_FACE.containsMatchIn(it) }?.let { return it }
        lines.firstOrNull { DURATION.containsMatchIn(it) }?.let { return it }
        // Counting from the notification's post time was tried and is wrong: apps repost as they
        // go, so the island restarted from zero and disagreed with the clock on screen. Better
        // to show the label alone, with the app's own buttons under it, than a number that lies.
        return lines.firstOrNull().orEmpty()
    }

    /** "12:34" or "1:02:03" — a clock face. */
    private val CLOCK_FACE = Regex("""\d{1,2}:\d{2}""")

    /** "5 min", "30s", "2 h 10 m" — the same thing spelled out. */
    private val DURATION = Regex("""\d+\s*[a-z]{1,3}\b""", RegexOption.IGNORE_CASE)

    private fun formatClock(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        /**
         * Does this notification belong to a timer or a stopwatch? The activity source asks
         * too: a countdown is a chronometer like any other, and without this it claimed the
         * clock's notification and drew it as a nameless live activity.
         */
        fun isClockNotification(sbn: StatusBarNotification): Boolean {
            val pkg = sbn.packageName.lowercase()
            if (pkg.contains("deskclock") || pkg.contains("clock") || pkg.contains("alarm")) return true
            val blob = describe(sbn)
            return blob.contains("stopwatch") || blob.contains("timer")
        }

        fun describe(sbn: StatusBarNotification): String {
            val extras = sbn.notification.extras
            val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sbn.notification.channelId.orEmpty()
            } else {
                ""
            }
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            return "$channel $title $text ${sbn.notification.category} ${sbn.tag}".lowercase()
        }
    }
}
