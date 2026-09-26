package com.nexus.launcher.ui.widgets.calendar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.util.Calendar

/** Draws the monthly grid calendar layout. */
class NexusCalendarMonthGridRenderer(private val events: List<CalendarEvent>?) {

    internal fun draw(
        context: android.content.Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        formatter: NexusCalendarDateFormatter,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        val insets = com.nexus.launcher.ui.widgets.NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val rightMargin = insets.right
        val topMargin = insets.top + 6f * dp
        val bottomMargin = insets.bottom

        val isGlass = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)

        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 14f * dp
            typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val monthYearStr = formatter.formatMonthYear(System.currentTimeMillis())
        canvas.drawText(monthYearStr, leftMargin, topMargin, headerPaint)

        if (isNeumorphic) {
            val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textSecondary
                textSize = 9f * dp
                typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(context.getString(com.nexus.launcher.R.string.widget_calendar_label), w - rightMargin, topMargin, subHeaderPaint)
        }

        val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) {
                if (palette.isLight) Color.argb(40, 0, 0, 0) else Color.argb(65, 255, 255, 255)
            } else if (isNeumorphic) {
                Color.argb(40, 100, 100, 100)
            } else {
                Color.argb(40, 128, 128, 128)
            }
            strokeWidth = 1f * dp
        }
        val divY = topMargin + 12f * dp
        canvas.drawLine(leftMargin, divY, w - rightMargin, divY, divPaint)

        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)

        cal.set(Calendar.DAY_OF_MONTH, 1)
        // The week starts where the phone's locale says (Sunday or Monday), like the weekly strip.
        // Until 2026-09-24 the header started at the weekday of the 1st and the dates assumed
        // Sunday, so both were wrong in any month not starting on a Sunday.
        val weekStart = cal.firstDayOfWeek // Calendar.SUNDAY = 1 .. SATURDAY = 7
        val firstOfMonthColumn = (cal.get(Calendar.DAY_OF_WEEK) - weekStart + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val gridTop = divY + 24f * dp
        val gridWidth = w - leftMargin - rightMargin
        val colWidth = gridWidth / 7f
        val rowHeight = (h - bottomMargin - gridTop) / 7f

        if (isNeumorphic) {
            val trackRect = android.graphics.RectF(leftMargin - 4f * dp, gridTop - 12f * dp, w - rightMargin + 4f * dp, gridTop + 5f * dp)
            com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawDebossedWell(canvas, trackRect, 6f * dp, palette, dp)
        }

        val dayHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 10f * dp
            typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }

        for (i in 0..6) {
            val cx = leftMargin + i * colWidth + colWidth / 2f
            val calDay = (weekStart - 1 + i) % 7 + 1
            canvas.drawText(formatter.getWeekdayInitial(calDay), cx, gridTop, dayHeaderPaint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 12f * dp
            typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }

        val todayBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
        }

        val todayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.todayText
            textSize = 12f * dp
            typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
        }


        val eventDays = mutableSetOf<Int>()
        events?.forEach {
            val eventCal = Calendar.getInstance()
            eventCal.timeInMillis = it.startMs
            if (eventCal.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH) &&
                eventCal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                eventDays.add(eventCal.get(Calendar.DAY_OF_MONTH))
            }
        }

        var currentDay = 1
        var row = 1
        var col = firstOfMonthColumn // 0-indexed column
        val badgeRect = android.graphics.RectF()

        while (currentDay <= daysInMonth) {
            val cx = leftMargin + col * colWidth + colWidth / 2f
            val cy = gridTop + row * rowHeight
            val dayStr = formatter.getDayNumberString(currentDay)

            if (currentDay == today) {
                if (isNeumorphic) {
                    val halfW = 10f * dp
                    val halfH = 10f * dp
                    badgeRect.set(cx - halfW, cy - halfH - 4f * dp, cx + halfW, cy + halfH - 4f * dp)
                    com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawPillBadge(canvas, badgeRect, 6f * dp, palette!!.textPrimary, dp)
                } else {
                    canvas.drawCircle(cx, cy - 4f * dp, 10f * dp, todayBgPaint)
                }
                canvas.drawText(dayStr, cx, cy, todayTextPaint)
            } else {
                canvas.drawText(dayStr, cx, cy, textPaint)
            }

            if (eventDays.contains(currentDay)) {
                canvas.drawCircle(cx, cy + 8f * dp, 2f * dp, dotPaint)
            }

            currentDay++
            col++
            if (col > 6) {
                col = 0
                row++
            }
        }
    }
}
