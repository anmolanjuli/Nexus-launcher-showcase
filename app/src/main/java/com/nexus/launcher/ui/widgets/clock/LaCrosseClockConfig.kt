package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Persisted configuration for the "La Crosse" Style LCD Clock Widget.
 * Stores individual field toggles, theme modes, time/month formats, and display options.
 */
data class LaCrosseClockConfig(
    val themeMode: ThemeMode = ThemeMode.CLASSIC,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val monthFormat: MonthFormat = MonthFormat.ABBREV,
    val showTime: Boolean = true,
    val showAmPm: Boolean = true,
    val showMonth: Boolean = true,
    val showDate: Boolean = true,
    val showDay: Boolean = true,
    val showTemp: Boolean = true,
    val showGhostSegments: Boolean = true,
    val showDividerLines: Boolean = true
) {
    enum class ThemeMode { CLASSIC, THEMED, ICE }
    enum class TimeFormat { SYSTEM, H12, H24 }
    enum class MonthFormat { ABBREV, NUMERIC }

    companion object {
        private const val KEY_THEME = "lacrosse_theme_"
        private const val KEY_TIME_FORMAT = "lacrosse_time_fmt_"
        private const val KEY_MONTH_FORMAT = "lacrosse_month_fmt_"
        private const val KEY_SHOW_TIME = "lacrosse_show_time_"
        private const val KEY_SHOW_AMPM = "lacrosse_show_ampm_"
        private const val KEY_SHOW_MONTH = "lacrosse_show_month_"
        private const val KEY_SHOW_DATE = "lacrosse_show_date_"
        private const val KEY_SHOW_DAY = "lacrosse_show_day_"
        private const val KEY_SHOW_TEMP = "lacrosse_show_temp_"
        private const val KEY_GHOST_SEGMENTS = "lacrosse_ghost_seg_"
        private const val KEY_DIVIDER_LINES = "lacrosse_div_lines_"

        fun read(context: Context, appWidgetId: Int): LaCrosseClockConfig {
            val prefs = context.getSharedPreferences(NexusWidgetConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val themeStr = prefs.getString("$KEY_THEME$appWidgetId", ThemeMode.CLASSIC.name)
            val timeFmtStr = prefs.getString("$KEY_TIME_FORMAT$appWidgetId", TimeFormat.SYSTEM.name)
            val monthFmtStr = prefs.getString("$KEY_MONTH_FORMAT$appWidgetId", MonthFormat.ABBREV.name)

            val theme = try { ThemeMode.valueOf(themeStr ?: ThemeMode.CLASSIC.name) } catch (_: Exception) { ThemeMode.CLASSIC }
            val timeFmt = try { TimeFormat.valueOf(timeFmtStr ?: TimeFormat.SYSTEM.name) } catch (_: Exception) { TimeFormat.SYSTEM }
            val monthFmt = try { MonthFormat.valueOf(monthFmtStr ?: MonthFormat.ABBREV.name) } catch (_: Exception) { MonthFormat.ABBREV }

            return LaCrosseClockConfig(
                themeMode = theme,
                timeFormat = timeFmt,
                monthFormat = monthFmt,
                showTime = prefs.getBoolean("$KEY_SHOW_TIME$appWidgetId", true),
                showAmPm = prefs.getBoolean("$KEY_SHOW_AMPM$appWidgetId", true),
                showMonth = prefs.getBoolean("$KEY_SHOW_MONTH$appWidgetId", true),
                showDate = prefs.getBoolean("$KEY_SHOW_DATE$appWidgetId", true),
                showDay = prefs.getBoolean("$KEY_SHOW_DAY$appWidgetId", true),
                showTemp = prefs.getBoolean("$KEY_SHOW_TEMP$appWidgetId", true),
                showGhostSegments = prefs.getBoolean("$KEY_GHOST_SEGMENTS$appWidgetId", true),
                showDividerLines = prefs.getBoolean("$KEY_DIVIDER_LINES$appWidgetId", true)
            )
        }

        fun write(context: Context, appWidgetId: Int, config: LaCrosseClockConfig) {
            val prefs = context.getSharedPreferences(NexusWidgetConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("$KEY_THEME$appWidgetId", config.themeMode.name)
                .putString("$KEY_TIME_FORMAT$appWidgetId", config.timeFormat.name)
                .putString("$KEY_MONTH_FORMAT$appWidgetId", config.monthFormat.name)
                .putBoolean("$KEY_SHOW_TIME$appWidgetId", config.showTime)
                .putBoolean("$KEY_SHOW_AMPM$appWidgetId", config.showAmPm)
                .putBoolean("$KEY_SHOW_MONTH$appWidgetId", config.showMonth)
                .putBoolean("$KEY_SHOW_DATE$appWidgetId", config.showDate)
                .putBoolean("$KEY_SHOW_DAY$appWidgetId", config.showDay)
                .putBoolean("$KEY_SHOW_TEMP$appWidgetId", config.showTemp)
                .putBoolean("$KEY_GHOST_SEGMENTS$appWidgetId", config.showGhostSegments)
                .putBoolean("$KEY_DIVIDER_LINES$appWidgetId", config.showDividerLines)
                .apply()
        }

        fun delete(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(NexusWidgetConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove("$KEY_THEME$appWidgetId")
                .remove("$KEY_TIME_FORMAT$appWidgetId")
                .remove("$KEY_MONTH_FORMAT$appWidgetId")
                .remove("$KEY_SHOW_TIME$appWidgetId")
                .remove("$KEY_SHOW_AMPM$appWidgetId")
                .remove("$KEY_SHOW_MONTH$appWidgetId")
                .remove("$KEY_SHOW_DATE$appWidgetId")
                .remove("$KEY_SHOW_DAY$appWidgetId")
                .remove("$KEY_SHOW_TEMP$appWidgetId")
                .remove("$KEY_GHOST_SEGMENTS$appWidgetId")
                .remove("$KEY_DIVIDER_LINES$appWidgetId")
                .apply()
        }
    }
}
