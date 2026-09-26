package com.nexus.launcher.ui.widgets.agenda

/**
 * The Agenda's Event range setting: how many days ahead, from the start of today, it reads and
 * shows. One definition for the calendar query and the widget's own filter — they each had a copy
 * ("1 else 8") before 2026-09-24, when the 30-day range was added and made the default, so the
 * schedule layout has more than a week to fill (Google's schedule widget reads weeks ahead).
 */
object AgendaRange {
    const val TODAY = "TODAY"
    const val WEEK = "WEEK"
    const val MONTH = "MONTH"
    const val DEFAULT = MONTH

    /** Days ahead for a stored range; a value this build doesn't know reads as [DEFAULT]. */
    fun days(range: String): Int = when (range) {
        TODAY -> 1
        WEEK -> 8 // today plus the next seven
        MONTH -> 31 // today plus the next thirty
        else -> days(DEFAULT)
    }
}
