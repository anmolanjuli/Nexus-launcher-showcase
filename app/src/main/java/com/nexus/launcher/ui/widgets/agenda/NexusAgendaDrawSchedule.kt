package com.nexus.launcher.ui.widgets.agenda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.calendar.CalendarEvent

/**
 * The Agenda's schedule layout for every size above the one-line hero, after Google Calendar's
 * schedule widget: today's date on top, a heading per later day ("Tomorrow", "Sun, Oct 4") and each
 * day's events as pills in their calendar's colour, as many as the widget's height holds, then
 * "N more". Too short for the date line, today's events get a "Today" heading instead.
 *
 * Replaced (2026-09-24) a list that showed one "today" card and at most two to four upcoming
 * rows whatever the widget's size, so a tall Agenda showed less than it had room for.
 */
class NexusAgendaDrawSchedule {

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val morePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pill = RectF()

    fun draw(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: NexusNeumorphicDraw.SoftPalette,
        tokens: NexusColorTokens,
        events: List<CalendarEvent>,
        formatter: AgendaDateFormatter,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val left = insets.left
        val right = w - insets.right
        val bottom = h - insets.bottom
        var y = insets.top

        // Today's date across the top, as Google's widget does, when the widget is tall enough to
        // spare the line; today's events then sit directly under it, without a "Today" heading.
        val dateHeader = bottom - y >= 150f * dp
        if (dateHeader) {
            headerPaint.style(palette.textPrimary, 16f * dp, boldTf, Paint.Align.LEFT)
            canvas.drawText(formatter.formatCompactDate(), left + 2f * dp, y + 16f * dp, headerPaint)
            y += 26f * dp
        }

        if (events.isEmpty()) {
            drawEmpty(context, canvas, (left + right) / 2f, (y + bottom) / 2f, dp, palette, formatter, regularTf, boldTf)
            return
        }

        val dayH = 22f * dp
        val eventH = 28f * dp
        val moreH = 18f * dp
        val lines = AgendaScheduleLayout.build(events).let { all ->
            val first = all.firstOrNull()
            if (dateHeader && first is AgendaScheduleLayout.Line.Day &&
                first.dayStartMs == AgendaScheduleLayout.startOfDay(System.currentTimeMillis())
            ) all.drop(1) else all
        }
        val shown = AgendaScheduleLayout.fit(lines, bottom - y, dayH, eventH, moreH)

        dayPaint.style(palette.textPrimary, 13.5f * dp, boldTf, Paint.Align.LEFT)
        for (i in 0 until shown) {
            when (val line = lines[i]) {
                is AgendaScheduleLayout.Line.Day -> {
                    canvas.drawText(formatter.formatDayHeading(context, line.dayStartMs), left + 2f * dp, y + 15f * dp, dayPaint)
                    y += dayH
                }
                is AgendaScheduleLayout.Line.Event -> {
                    drawPill(context, canvas, line.event, left, y, right, dp, config, tokens, formatter, regularTf, boldTf)
                    y += eventH
                }
            }
        }

        val hidden = AgendaScheduleLayout.eventCount(lines) - AgendaScheduleLayout.eventCount(lines, shown)
        if (hidden > 0) {
            morePaint.style(palette.textSecondary, 11.5f * dp, regularTf, Paint.Align.LEFT)
            val text = context.resources.getQuantityString(R.plurals.agenda_more_events, hidden, hidden)
            canvas.drawText(text, left + 2f * dp, y + 13f * dp, morePaint)
        }
    }

    private fun drawPill(
        context: Context,
        canvas: Canvas,
        event: CalendarEvent,
        left: Float,
        top: Float,
        right: Float,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        tokens: NexusColorTokens,
        formatter: AgendaDateFormatter,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        // Calendar colours come without a dependable alpha; the pill is always solid.
        val fill = ColorUtils.setAlphaComponent(if (event.color != 0) event.color else tokens.accent, 255)
        val onFill = if (ColorUtils.calculateLuminance(fill) > 0.55) {
            ColorUtils.setAlphaComponent(Color.BLACK, 222)
        } else {
            Color.WHITE
        }
        pill.set(left, top, right, top + 24f * dp)
        pillPaint.color = fill
        canvas.drawRoundRect(pill, 7f * dp, 7f * dp, pillPaint)

        val baseline = pill.centerY() + 4.3f * dp
        val pad = 9f * dp
        // Timed events show their start (or a countdown today, if the widget is set to).
        val time = if (config.showAgendaCountdown && AgendaEventFilter.isToday(event)) {
            formatter.formatRelative(context, event).ifEmpty { formatter.formatTime(context, event) }
        } else {
            formatter.formatTime(context, event)
        }
        var timeW = 0f
        if (time.isNotEmpty()) {
            timePaint.style(ColorUtils.setAlphaComponent(onFill, 200), 11f * dp, regularTf, Paint.Align.RIGHT)
            canvas.drawText(time, pill.right - pad, baseline, timePaint)
            timeW = timePaint.measureText(time) + 8f * dp
        }
        titlePaint.style(onFill, 12.5f * dp, boldTf, Paint.Align.LEFT)
        val maxW = pill.width() - pad * 2 - timeW
        if (maxW > 12f * dp) {
            val title = TextUtils.ellipsize(event.title, titlePaint, maxW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(title, pill.left + pad, baseline, titlePaint)
        }
    }

    private fun drawEmpty(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        dp: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        formatter: AgendaDateFormatter,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        emptyPaint.style(palette.textPrimary, 14.5f * dp, boldTf, Paint.Align.CENTER)
        canvas.drawText(context.getString(R.string.agenda_no_events), cx, cy - 2f * dp, emptyPaint)
        emptyPaint.style(palette.textSecondary, 12f * dp, regularTf, Paint.Align.CENTER)
        canvas.drawText(formatter.formatCompactDate(), cx, cy + 16f * dp, emptyPaint)
    }

    private fun Paint.style(color: Int, size: Float, tf: Typeface, align: Paint.Align) {
        this.color = color
        textSize = size
        typeface = tf
        textAlign = align
    }
}
