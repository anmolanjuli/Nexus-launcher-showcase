package com.nexus.launcher.ui.widgets.calendar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.util.Calendar

/** Draws the multi-week compact calendar layout. */
class NexusCalendarWeeklyStripRenderer(private val events: List<CalendarEvent>?) {

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
        val headerHeight = 20f * dp
        val rowHeight = 36f * dp
        val minPadding = insets.top
        val availableHeight = h - minPadding * 2 - headerHeight
        val numWeeks = (availableHeight / rowHeight).toInt().coerceAtLeast(1)
        val totalGridHeight = headerHeight + numWeeks * rowHeight
        val topMargin = (h - totalGridHeight) / 2f

        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val currentMonth = cal.get(Calendar.MONTH)
        val currentYear = cal.get(Calendar.YEAR)
        val firstDayOfWeek = cal.firstDayOfWeek
        NexusCalendarQueryWindow.alignToWeekStart(cal)

        val isGlass = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)

        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        val gridWidth = w - leftMargin - rightMargin
        val colWidth = gridWidth / 7f

        if (isNeumorphic) {
            val trackRect = android.graphics.RectF(leftMargin - 4f * dp, topMargin, w - rightMargin + 4f * dp, topMargin + 18f * dp)
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
        val eventDates = mutableSetOf<String>()
        events?.forEach {
            val eventCal = Calendar.getInstance().apply { timeInMillis = it.startMs }
            eventDates += "${eventCal.get(Calendar.YEAR)}-${eventCal.get(Calendar.MONTH)}-${eventCal.get(Calendar.DAY_OF_MONTH)}"
        }

        val badgeRect = android.graphics.RectF()

        for (i in 0..6) {
            val cx = leftMargin + i * colWidth + colWidth / 2f
            val calDay = (firstDayOfWeek - 1 + i) % 7 + 1
            canvas.drawText(formatter.getWeekdayInitial(calDay), cx, topMargin + 12f * dp, dayHeaderPaint)
        }
        for (row in 0 until numWeeks) {
            for (i in 0..6) {
                val cx = leftMargin + i * colWidth + colWidth / 2f
                val year = cal.get(Calendar.YEAR)
                val month = cal.get(Calendar.MONTH)
                val day = cal.get(Calendar.DAY_OF_MONTH)
                val cy = topMargin + headerHeight + row * rowHeight + 16f * dp
                val isToday = year == currentYear && month == currentMonth && day == today
                val dayStr = formatter.getDayNumberString(day)
                if (isToday) {
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
                if (eventDates.contains("$year-$month-$day")) {
                    canvas.drawCircle(cx, cy + 8f * dp, 2f * dp, dotPaint)
                }
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

}
