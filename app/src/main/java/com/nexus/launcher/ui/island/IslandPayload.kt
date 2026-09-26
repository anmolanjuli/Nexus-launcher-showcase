package com.nexus.launcher.ui.island

import android.app.PendingIntent
import android.graphics.Bitmap

/** One button an app offered on its notification. */
data class IslandActionButton(
    val title: String,
    val intent: PendingIntent,
)

data class IslandPayload(
    val kind: IslandKind,
    val title: String,
    val subtitle: String = "",
    val packageName: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val seconds: Long = 0L,
    val percent: Int = 0,
    val eventId: Long = 0L,
    val identity: String = kind.name,
    val answerIntent: PendingIntent? = null,
    val declineIntent: PendingIntent? = null,
    /** 0..1 for a determinate job, -1 when there is nothing to show. */
    val progress: Float = -1f,
    /** Indeterminate work: a bar that moves without meaning a fraction. */
    val busy: Boolean = false,
    /** A clock the app is running: the moment it counts from, in elapsed-time terms. */
    val chronoBaseMs: Long = 0L,
    val countDown: Boolean = false,
    /** The app's own buttons, in the order it offered them. */
    val actions: List<IslandActionButton> = emptyList(),
    /** The app's icon, for activities the launcher has no glyph of its own for. */
    val icon: Bitmap? = null,
    /** What the notification is opened by. */
    val contentIntent: PendingIntent? = null,
) {
    /**
     * Drawn as a live activity: the app's own title, clock and buttons.
     *
     * A timer or a stopwatch qualifies once it brings buttons with it — they are Pause and
     * Reset, and the launcher has no business guessing at them. Without this the card drew its
     * own single button, which could only open the clock app.
     */
    val usesActivityCard: Boolean
        get() = kind == IslandKind.ACTIVITY ||
            ((kind == IslandKind.TIMER || kind == IslandKind.STOPWATCH) && actions.isNotEmpty())
}
