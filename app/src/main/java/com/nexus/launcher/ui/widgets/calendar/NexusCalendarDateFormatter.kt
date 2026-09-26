package com.nexus.launcher.ui.widgets.calendar

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.nexus.launcher.locale.LocaleDigitUtils
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Locale-aware, configuration-sensitive Date and Time formatter cache for Calendar widgets.
 * Automatically invalidates and rebuilds SimpleDateFormat instances and precomputed day strings
 * whenever the active Context configuration changes (locale or 12/24-hour setting).
 */
internal class NexusCalendarDateFormatter {

    private var cachedLocale: Locale? = null
    private var cachedIs24Hour: Boolean? = null

    private lateinit var timeFormatCompact: SimpleDateFormat
    private lateinit var timeFormatFull: SimpleDateFormat
    private lateinit var amPmFormat: SimpleDateFormat
    private lateinit var headerDateFormat: SimpleDateFormat
    private lateinit var allDayFormat: SimpleDateFormat
    private lateinit var monthFormat: SimpleDateFormat
    private lateinit var dayFormat: SimpleDateFormat
    private lateinit var monthYearFormat: SimpleDateFormat
    private lateinit var weekdayInitials: Array<String>
    private lateinit var localizedDayNumbers: Array<String>

    fun ensure(context: Context) {
        val currentLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        NexusCalendarDebug.d("ensure() locale=$currentLocale is24Hour=$is24Hour cachedLocale=$cachedLocale")

        if (currentLocale != cachedLocale || is24Hour != cachedIs24Hour) {
            cachedLocale = currentLocale
            cachedIs24Hour = is24Hour

            timeFormatCompact = SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm", currentLocale)
            timeFormatFull = SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm a", currentLocale)
            amPmFormat = SimpleDateFormat("a", currentLocale)
            headerDateFormat = SimpleDateFormat("EEEE, MMMM d", currentLocale)
            allDayFormat = SimpleDateFormat("MMM d", currentLocale)
            monthFormat = SimpleDateFormat("MMM", currentLocale)
            dayFormat = SimpleDateFormat("EEE", currentLocale)
            monthYearFormat = SimpleDateFormat("MMMM yyyy", currentLocale)

            val symbols = DateFormatSymbols.getInstance(currentLocale)
            val shortWeekdays = symbols.shortWeekdays // 1=Sunday, 2=Monday, ...
            weekdayInitials = Array(7) { i ->
                val calDay = i + 1
                val shortName = shortWeekdays.getOrNull(calDay)
                if (!shortName.isNullOrEmpty()) shortName.take(1).uppercase(currentLocale)
                else arrayOf("S", "M", "T", "W", "T", "F", "S")[i]
            }

            // Precompute day number strings 0..31 to avoid allocations in per-cell draw loops
            localizedDayNumbers = Array(32) { i ->
                LocaleDigitUtils.formatNumber(i, currentLocale)
            }

            NexusCalendarDebug.d("ensure() rebuilt formats weekdayInitials=${weekdayInitials.contentToString()}")
        }
    }

    private fun locale(): Locale = cachedLocale ?: Locale.getDefault()

    fun formatCompactTime(timeMs: Long): String = LocaleDigitUtils.localizeDigits(timeFormatCompact.format(timeMs), locale())
    fun formatFullTime(timeMs: Long): String = LocaleDigitUtils.localizeDigits(timeFormatFull.format(timeMs), locale())
    fun formatAmPm(timeMs: Long): String = amPmFormat.format(timeMs)
    fun formatHeaderDate(timeMs: Long): String = LocaleDigitUtils.localizeDigits(headerDateFormat.format(timeMs), locale())
    fun formatAllDay(timeMs: Long): String = LocaleDigitUtils.localizeDigits(allDayFormat.format(timeMs), locale())
    fun formatMonth(timeMs: Long): String = monthFormat.format(timeMs).uppercase(locale())
    fun formatDay(timeMs: Long): String = dayFormat.format(timeMs)
    fun formatMonthYear(timeMs: Long): String = LocaleDigitUtils.localizeDigits(monthYearFormat.format(timeMs), locale())
    fun getWeekdayInitial(calDayOfWeek: Int): String = weekdayInitials.getOrElse((calDayOfWeek - 1).coerceIn(0, 6)) { "S" }
    fun getDayNumberString(dayOfMonth: Int): String = localizedDayNumbers.getOrElse(dayOfMonth) { dayOfMonth.toString() }
}
