package com.nexus.launcher.ui.widgets.progress

import android.content.Context
import com.nexus.launcher.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/**
 * Computes live progress values, date ranges, and formatted display strings
 * for Progress Bar presets and custom deadline slots.
 */
object ProgressPresets {

    data class CalculatedTrack(
        val label: String,
        val progressFraction: Float, // 0.0 .. 1.0
        val badgeText: String,       // e.g. "125d left" or "34%"
        val secondaryText: String,   // e.g. "Jan 1 – Dec 31" or "240 of 365 days"
        val colorHex: String?
    )

    fun calculate(context: Context, track: ProgressTrack, nowMillis: Long = System.currentTimeMillis()): CalculatedTrack {
        val (startMillis, endMillis) = resolveTimeWindow(track, nowMillis)
        val totalMillis = (endMillis - startMillis).coerceAtLeast(1000L)
        val elapsedMillis = (nowMillis - startMillis).coerceIn(0L, totalMillis)
        val remainingMillis = (endMillis - nowMillis).coerceAtLeast(0L)

        val fraction = (elapsedMillis.toDouble() / totalMillis.toDouble()).toFloat().coerceIn(0f, 1f)

        val oneDayMillis = 86400000.0
        val totalDays = ceil(totalMillis / oneDayMillis).toInt().coerceAtLeast(1)
        val remainingDays = ceil(remainingMillis / oneDayMillis).toInt().coerceAtLeast(0)
        val elapsedDays = (elapsedMillis / 86400000L).toInt().coerceAtLeast(0)

        val percentRemaining = ((1f - fraction) * 100f).toInt().coerceIn(0, 100)
        val percentElapsed = (fraction * 100f).toInt().coerceIn(0, 100)

        val badgeText = when (track.displayMode) {
            ProgressTrack.MODE_REMAINING_PERCENT -> context.getString(R.string.progress_percent_left, percentRemaining)
            ProgressTrack.MODE_ELAPSED_DAYS -> context.getString(R.string.progress_days_elapsed, elapsedDays)
            ProgressTrack.MODE_ELAPSED_PERCENT -> context.getString(R.string.progress_percent_elapsed, percentElapsed)
            else -> context.getString(R.string.progress_days_left, remainingDays)
        }

        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val secondaryText = context.getString(
            R.string.progress_date_range,
            df.format(Date(startMillis)),
            df.format(Date(endMillis))
        )

        return CalculatedTrack(
            label = track.label,
            progressFraction = fraction,
            badgeText = badgeText,
            secondaryText = secondaryText,
            colorHex = track.colorHex
        )
    }

    fun resolveTimeWindow(track: ProgressTrack, nowMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis

        return when (track.presetType) {
            ProgressTrack.PRESET_YEAR -> {
                val year = cal.get(Calendar.YEAR)
                cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            ProgressTrack.PRESET_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                cal.set(Calendar.DAY_OF_MONTH, maxDay)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            ProgressTrack.PRESET_WEEK -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                Pair(start, end)
            }
            else -> {
                val start = if (track.startDateMillis > 0L) track.startDateMillis else nowMillis
                val end = if (track.targetDateMillis > start) track.targetDateMillis else (start + 86400000L * 30L)
                Pair(start, end)
            }
        }
    }

    fun createDefaultTracks(context: Context): List<ProgressTrack> {
        return listOf(
            ProgressTrack(
                label = context.getString(R.string.progress_preset_year),
                presetType = ProgressTrack.PRESET_YEAR,
                displayMode = ProgressTrack.MODE_REMAINING_DAYS
            )
        )
    }
}
