package com.nexus.launcher.ui.island

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The running time on a clock notification, read from the notification's own view.
 *
 * Some clock apps say what they are counting in a way that can be read from the extras — a
 * chronometer flag, or the time written into the title. Others (the stopwatch in Google's clock
 * among them) draw their own layout, and then the extras hold the word "Stopwatch" and nothing
 * else. The shade still shows a running time, because the layout contains a [Chronometer]; this
 * inflates that layout once per update and reads the chronometer's base off it, so the island
 * can count from the same instant the shade does instead of guessing.
 *
 * Inflating a RemoteViews is not free, so each notification is read once per post and cached.
 */
object IslandClockFace {

    /** @param wallBaseMs the instant being counted from, as wall-clock time; 0 when unknown. */
    data class Face(
        val wallBaseMs: Long = 0L,
        val countDown: Boolean = false,
        val text: String = "",
    ) {
        val isEmpty: Boolean get() = wallBaseMs <= 0L && text.isEmpty()
    }

    private val EMPTY = Face()
    private val CLOCK = Regex("""\d{1,2}:\d{2}""")

    private val cache = HashMap<String, Pair<Long, Face>>()

    fun read(context: Context, sbn: StatusBarNotification): Face {
        val stamp = sbn.postTime
        cache[sbn.key]?.let { (cachedStamp, face) -> if (cachedStamp == stamp) return face }
        val face = runCatching { inflateAndRead(context, sbn) }.getOrNull() ?: EMPTY
        if (cache.size > 8) cache.clear()
        cache[sbn.key] = stamp to face
        return face
    }

    private fun inflateAndRead(context: Context, sbn: StatusBarNotification): Face? {
        val n = sbn.notification
        val views = n.bigContentView ?: n.contentView
            ?: runCatching {
                Notification.Builder.recoverBuilder(context, n).createContentView()
            }.getOrNull()
            ?: return null
        val host = FrameLayout(context)
        val root = views.apply(context, host) ?: return null
        return chronometerIn(root) ?: textIn(root)
    }

    private fun chronometerIn(view: View): Face? {
        if (view is Chronometer && view.visibility == View.VISIBLE) {
            val countDown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && view.isCountDown
            // Chronometer counts in elapsed-real time; the island counts in wall time.
            val wall = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - view.base)
            return Face(wallBaseMs = wall, countDown = countDown)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                chronometerIn(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun textIn(view: View): Face? {
        if (view is TextView && view.visibility == View.VISIBLE) {
            val text = view.text?.toString().orEmpty()
            if (CLOCK.containsMatchIn(text)) return Face(text = text.trim())
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                textIn(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
