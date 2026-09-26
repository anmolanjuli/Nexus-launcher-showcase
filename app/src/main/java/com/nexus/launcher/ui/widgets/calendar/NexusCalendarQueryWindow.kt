package com.nexus.launcher.ui.widgets.calendar

import com.nexus.launcher.ui.widgets.WidgetSize
import java.util.Calendar

/**
 * Query windows for [CalendarContract.Instances], aligned with spec 5.3
 * (month view, week view, agenda next-5) and the current renderer.
 */
internal object NexusCalendarQueryWindow {
    data class Bounds(val startMs: Long, val endMs: Long, val kind: String)

    fun forWidget(size: WidgetSize, viewMode: String): Bounds {
        val cal = Calendar.getInstance()
        return if (viewMode == "MONTHLY") {
            when (size) {
                WidgetSize.TINY, WidgetSize.SMALL -> weekStripBounds(cal)
                WidgetSize.MEDIUM, WidgetSize.LARGE -> monthGridBounds(cal)
            }
        } else {
            agendaBounds()
        }
    }

    /** Mutates [cal] to local midnight of the week that contains today. */
    fun alignToWeekStart(cal: Calendar) {
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val first = cal.firstDayOfWeek
        var diff = dayOfWeek - first
        if (diff < 0) diff += 7
        cal.add(Calendar.DAY_OF_MONTH, -diff)
        startOfDay(cal)
    }

    /** Agenda (DAILY): upcoming occurrences from now, far enough to fill "next 5". */
    private fun agendaBounds(): Bounds {
        val now = System.currentTimeMillis()
        return Bounds(now, now + 90L * 24 * 60 * 60 * 1000L, "agenda")
    }

    /** Month grid: all occurrences in the current local month (dots on past + future days). */
    private fun monthGridBounds(cal: Calendar): Bounds {
        cal.set(Calendar.DAY_OF_MONTH, 1)
        startOfDay(cal)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return Bounds(start, cal.timeInMillis, "month")
    }

    /**
     * Weekly strip starts at [Calendar.firstDayOfWeek] (same math as
     * [NexusCalendarWeeklyStripRenderer]) and can show up to ~6 rows.
     */
    private fun weekStripBounds(cal: Calendar): Bounds {
        alignToWeekStart(cal)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 7 * 6)
        return Bounds(start, cal.timeInMillis, "week")
    }

    private fun startOfDay(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
}
