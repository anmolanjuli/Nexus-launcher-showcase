package com.nexus.launcher.feed

import android.content.Context
import com.nexus.launcher.R

/** "5m ago"-style article age, in the app language. Shared by every feed card. */
object NexusFeedTimeAgo {

    fun format(context: Context, epochMs: Long): String {
        if (epochMs <= 0L) return ""
        val diff = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1 -> context.getString(R.string.feed_time_just_now)
            minutes < 60 -> context.getString(R.string.feed_time_minutes, minutes.toInt())
            hours < 24 -> context.getString(R.string.feed_time_hours, hours.toInt())
            days < 7 -> context.getString(R.string.feed_time_days, days.toInt())
            else -> context.getString(R.string.feed_time_weeks, (days / 7).toInt())
        }
    }
}
