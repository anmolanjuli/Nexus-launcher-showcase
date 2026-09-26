package com.nexus.launcher.ui.widgets.agenda

import com.nexus.launcher.ui.widgets.calendar.CalendarEvent
import java.util.Calendar
import java.util.TimeZone

/**
 * Filters and sorts raw calendar events for the Agenda widget.
 * Normalizes all-day UTC timestamps to local midnight to prevent timezone day-shift bugs.
 */
object AgendaEventFilter {

    fun getEffectiveStartMs(event: CalendarEvent): Long {
        if (!event.isAllDay) return event.startMs
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = event.startMs
        }
        val localCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }

    fun getEffectiveEndMs(event: CalendarEvent): Long {
        if (!event.isAllDay) {
            return if (event.endMs > event.startMs) event.endMs else event.startMs + 3_600_000L
        }
        val startLocal = getEffectiveStartMs(event)
        val utcCalStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = event.startMs }
        val utcCalEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = event.endMs }
        val diffDays = maxOf(1, ((utcCalEnd.timeInMillis - utcCalStart.timeInMillis) / 86_400_000L).toInt())
        return startLocal + (diffDays * 86_400_000L)
    }

    fun filterAndSort(
        rawEvents: List<CalendarEvent>?,
        range: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<CalendarEvent> {
        if (rawEvents.isNullOrEmpty()) return emptyList()

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfTodayMs = cal.timeInMillis
        val maxWindowMs = startOfTodayMs + AgendaRange.days(range) * 86_400_000L

        return rawEvents.filter { event ->
            val effStart = getEffectiveStartMs(event)
            val effEnd = getEffectiveEndMs(event)

            if (event.isAllDay) {
                effEnd > startOfTodayMs && effStart < maxWindowMs
            } else {
                effEnd > nowMs && effStart < maxWindowMs
            }
        }.sortedWith(
            compareBy<CalendarEvent> { getEffectiveStartMs(it) }
                .thenByDescending { it.isAllDay }
                .thenBy { it.title }
        )
    }

    fun isToday(event: CalendarEvent, nowMs: Long = System.currentTimeMillis()): Boolean {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endOfTodayMs = cal.timeInMillis
        val effStart = getEffectiveStartMs(event)
        val effEnd = getEffectiveEndMs(event)
        return effStart <= endOfTodayMs && effEnd > (endOfTodayMs - 86_400_000L)
    }
}
