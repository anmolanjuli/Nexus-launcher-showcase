package com.nexus.launcher.ui.island

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap

/**
 * The pieces of a notification the island draws: the app's own buttons, and its icon.
 *
 * Shared, because a timer and a delivery are the same thing from here: something an app is
 * doing, with buttons it wants pressed. The island sends those buttons back to the app instead
 * of guessing — pressing Pause on a stopwatch used to open the clock app rather than pause it.
 */
object IslandNotificationParts {

    /** The app's own buttons; three is as many as the card has room for. */
    fun actions(n: Notification): List<IslandActionButton> {
        val actions = n.actions ?: return emptyList()
        return actions.mapNotNull { action ->
            val intent = action.actionIntent ?: return@mapNotNull null
            val label = action.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            IslandActionButton(label, intent)
        }.take(3)
    }

    fun smallIcon(context: Context, sbn: StatusBarNotification): Bitmap? = runCatching {
        val size = (20f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        sbn.notification.smallIcon?.loadDrawable(context)?.toBitmap(size, size)
    }.getOrNull()
}
