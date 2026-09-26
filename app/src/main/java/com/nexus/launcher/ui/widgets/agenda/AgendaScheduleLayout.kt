package com.nexus.launcher.ui.widgets.agenda

import com.nexus.launcher.ui.widgets.calendar.CalendarEvent
import java.util.Calendar

/**
 * The Agenda's schedule, as lines: a day heading, then that day's events — the layout of Google
 * Calendar's schedule widget. Also decides how many lines fit a height, so a day heading is never
 * left at the bottom without an event under it.
 *
 * An event that began before today and is still running (a multi-day all-day event, say) is
 * listed under today, where it is happening, not under the day it started.
 */
internal object AgendaScheduleLayout {

    sealed class Line {
        /** [dayStartMs] is local midnight of the day. */
        class Day(val dayStartMs: Long) : Line()
        class Event(val event: CalendarEvent) : Line()
    }

    fun build(events: List<CalendarEvent>, nowMs: Long = System.currentTimeMillis()): List<Line> {
        val startOfToday = startOfDay(nowMs)
        val lines = ArrayList<Line>(events.size * 2)
        var currentDay = Long.MIN_VALUE
        for (event in events) {
            val day = startOfDay(maxOf(AgendaEventFilter.getEffectiveStartMs(event), startOfToday))
            if (day != currentDay) {
                lines += Line.Day(day)
                currentDay = day
            }
            lines += Line.Event(event)
        }
        return lines
    }

    /**
     * How many of [lines] fit in [height], given each line's height, never ending on a day
     * heading. When not every event fits, room for a "N more" line of [moreH] is kept.
     */
    fun fit(lines: List<Line>, height: Float, dayH: Float, eventH: Float, moreH: Float): Int {
        val all = fitIn(lines, height, dayH, eventH)
        return if (all == lines.size) all else fitIn(lines, height - moreH, dayH, eventH)
    }

    fun eventCount(lines: List<Line>, upTo: Int = lines.size): Int =
        (0 until upTo).count { lines[it] is Line.Event }

    private fun fitIn(lines: List<Line>, height: Float, dayH: Float, eventH: Float): Int {
        var used = 0f
        var count = 0
        for ((i, line) in lines.withIndex()) {
            // A day heading only goes in with room for its first event under it.
            val need = if (line is Line.Day) dayH + eventH else eventH
            if (used + need > height) break
            used += if (line is Line.Day) dayH else eventH
            count = i + 1
        }
        // The loop can stop right after a heading whose event then didn't fit; drop the heading.
        if (count > 0 && lines[count - 1] is Line.Day) count--
        return count
    }

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
