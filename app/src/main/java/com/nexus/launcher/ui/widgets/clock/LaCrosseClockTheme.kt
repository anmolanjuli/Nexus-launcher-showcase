package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import android.graphics.Color
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.util.Calendar
import java.util.Locale

/**
 * Resolves theme colors, palette constants, and formatted time details for the La Crosse LCD clock.
 */
object LaCrosseClockTheme {

    const val CLASSIC_LCD_BG = "#D4D4C8"
    const val CLASSIC_LCD_INK = "#2A2A2A"

    fun applyTheme(
        context: Context,
        config: NexusWidgetConfig.InstanceConfig,
        cfg: LaCrosseClockConfig
    ) {
        val ink = Color.parseColor(CLASSIC_LCD_INK)
        val panel = Color.parseColor(CLASSIC_LCD_BG)
        val ghost = Color.argb(20, Color.red(ink), Color.green(ink), Color.blue(ink))
        val div = Color.argb(55, Color.red(ink), Color.green(ink), Color.blue(ink))
        val silk = Color.argb(160, Color.red(ink), Color.green(ink), Color.blue(ink))

        LaCrosseSegmentDraw.panelBgPaint.color = panel
        LaCrosseSegmentDraw.activePaint.color = ink
        LaCrosseSegmentDraw.ghostPaint.color = if (cfg.showGhostSegments) ghost else Color.TRANSPARENT
        LaCrosseSegmentDraw.dividerPaint.color = div
        LaCrosseSegmentDraw.silkscreenPaint.color = silk
    }

    fun formatTimeDetails(context: Context, cal: Calendar, timeFormat: LaCrosseClockConfig.TimeFormat): TimeDetails {
        val is12h = when (timeFormat) {
            LaCrosseClockConfig.TimeFormat.H12 -> true
            LaCrosseClockConfig.TimeFormat.H24 -> false
            LaCrosseClockConfig.TimeFormat.SYSTEM -> !android.text.format.DateFormat.is24HourFormat(context)
        }

        val hStr = if (is12h) {
            val h = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            if (h < 10) " $h" else "$h"
        } else {
            String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY))
        }

        val mStr = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val isAm = cal.get(Calendar.AM_PM) == Calendar.AM

        return TimeDetails(hStr, mStr, is12h, isAm)
    }

    data class TimeDetails(val hours: String, val minutes: String, val is12h: Boolean, val isAm: Boolean)
    data class BottomColumnSpec(val label: String, val value: String, val isAlphanumeric: Boolean)
    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
