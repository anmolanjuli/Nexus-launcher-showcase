package com.nexus.launcher.ui.widgets.agenda

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.ui.widgets.calendar.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Configuration-sensitive and locale-aware date/time and relative countdown formatter for Agenda.
 */
class AgendaDateFormatter {

    private var cachedLocale: Locale? = null
    private var cachedIs24Hour: Boolean? = null

    private lateinit var timeFormatCompact: SimpleDateFormat
    private lateinit var compactDateFormat: SimpleDateFormat
    private lateinit var dayNameFormat: SimpleDateFormat
    private lateinit var dayNumberFormat: SimpleDateFormat

    fun ensure(context: Context) {
        val currentLocale = LocaleObserver.getEffectiveLocale(context)
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)

        if (currentLocale != cachedLocale || is24Hour != cachedIs24Hour) {
            cachedLocale = currentLocale
            cachedIs24Hour = is24Hour

            timeFormatCompact = SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm a", currentLocale)
            compactDateFormat = SimpleDateFormat("EEE, MMM d", currentLocale)
            dayNameFormat = SimpleDateFormat("EEE", currentLocale)
            dayNumberFormat = SimpleDateFormat("d", currentLocale)
        }
    }

    private fun locale(): Locale = cachedLocale ?: Locale.getDefault()

    fun formatTime(context: Context, event: CalendarEvent): String {
        if (event.isAllDay) {
            return "" // No "All day" label
        }
        return LocaleDigitUtils.localizeDigits(timeFormatCompact.format(event.startMs), locale())
    }

    fun formatRelative(
        context: Context,
        event: CalendarEvent,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val loc = locale()
        val effStart = AgendaEventFilter.getEffectiveStartMs(event)
        val effEnd = AgendaEventFilter.getEffectiveEndMs(event)

        if (event.isAllDay) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = cal.timeInMillis
            val startOfTomorrow = startOfToday + 86_400_000L
            val startOfAfterTomorrow = startOfTomorrow + 86_400_000L

            return when {
                effStart in startOfToday until startOfTomorrow -> context.getString(R.string.agenda_today_header)
                effStart in startOfTomorrow until startOfAfterTomorrow -> context.getString(R.string.agenda_tomorrow)
                else -> {
                    val diffDays = ((effStart - startOfToday) / 86_400_000L).toInt()
                    if (diffDays in 1..7) {
                        val formatted = String.format(loc, context.getString(R.string.agenda_in_days), diffDays)
                        LocaleDigitUtils.localizeDigits(formatted, loc)
                    } else {
                        val day = dayNameFormat.format(effStart)
                        val num = LocaleDigitUtils.localizeDigits(dayNumberFormat.format(effStart), loc)
                        "$day $num"
                    }
                }
            }
        }

        if (nowMs >= effStart && nowMs < effEnd) {
            return context.getString(R.string.agenda_now)
        }

        if (effStart > nowMs) {
            val diffMs = effStart - nowMs
            val diffMins = (diffMs / 60_000L).toInt()

            if (diffMins < 60) {
                val formatted = String.format(loc, context.getString(R.string.agenda_in_minutes), maxOf(1, diffMins))
                return LocaleDigitUtils.localizeDigits(formatted, loc)
            }

            val calToday = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }
            val endOfTodayMs = calToday.timeInMillis

            if (effStart <= endOfTodayMs) {
                val diffHours = diffMins / 60
                val formatted = String.format(loc, context.getString(R.string.agenda_in_hours), maxOf(1, diffHours))
                return LocaleDigitUtils.localizeDigits(formatted, loc)
            }

            val endOfTomorrowMs = endOfTodayMs + 86_400_000L
            if (effStart <= endOfTomorrowMs) {
                return context.getString(R.string.agenda_tomorrow)
            }

            val diffDays = (diffMs / 86_400_000L).toInt() + 1
            if (diffDays <= 7) {
                val formatted = String.format(loc, context.getString(R.string.agenda_in_days), diffDays)
                return LocaleDigitUtils.localizeDigits(formatted, loc)
            }

            val day = dayNameFormat.format(effStart)
            val num = LocaleDigitUtils.localizeDigits(dayNumberFormat.format(effStart), loc)
            return "$day $num"
        }

        return formatTime(context, event)
    }

    fun formatEventDayPrefix(
        context: Context,
        event: CalendarEvent,
        showCountdown: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): String {
        if (showCountdown) {
            val rel = formatRelative(context, event, nowMs)
            if (rel.isNotEmpty()) return rel
        }

        val effStart = AgendaEventFilter.getEffectiveStartMs(event)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val startOfTomorrow = startOfToday + 86_400_000L
        val startOfAfterTomorrow = startOfTomorrow + 86_400_000L

        return when {
            effStart in startOfToday until startOfTomorrow -> context.getString(R.string.agenda_today_header)
            effStart in startOfTomorrow until startOfAfterTomorrow -> context.getString(R.string.agenda_tomorrow)
            else -> {
                val day = dayNameFormat.format(effStart)
                val num = LocaleDigitUtils.localizeDigits(dayNumberFormat.format(effStart), locale())
                "$day $num"
            }
        }
    }

    /** A schedule day heading: "Today", "Tomorrow", then "Sun, Oct 4". [dayStartMs] is local midnight. */
    fun formatDayHeading(context: Context, dayStartMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        val today = AgendaScheduleLayout.startOfDay(nowMs)
        val tomorrow = AgendaScheduleLayout.startOfDay(today + 36 * 3_600_000L) // DST-safe next midnight
        return when (dayStartMs) {
            today -> context.getString(R.string.agenda_today_header)
            tomorrow -> context.getString(R.string.agenda_tomorrow)
            else -> formatCompactDate(dayStartMs)
        }
    }

    fun formatCompactDate(timeMs: Long = System.currentTimeMillis()): String {
        return LocaleDigitUtils.localizeDigits(compactDateFormat.format(timeMs), locale())
    }
}
