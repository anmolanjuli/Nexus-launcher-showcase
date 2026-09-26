package com.nexus.launcher.ui.widgets.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/** Draws the compact next-event calendar layout. */
class NexusCalendarSmallRenderer(private val events: List<CalendarEvent>) {

    internal fun draw(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        formatter: NexusCalendarDateFormatter,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        val event = events.firstOrNull() ?: return
        val insets = com.nexus.launcher.ui.widgets.NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val topMargin = h / 2f - 4f * dp
        val isGlass = config.backgroundMode == com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS

        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 16f * dp
            typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 13f * dp
            typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }

        val title = if (event.title.length > 20) event.title.take(18) + "..." else event.title
        canvas.drawText(title, leftMargin, topMargin, titlePaint)
        val time = if (event.isAllDay) context.getString(com.nexus.launcher.R.string.calendar_all_day) else formatter.formatFullTime(event.startMs)
        canvas.drawText(time, leftMargin, topMargin + 18f * dp, subPaint)
    }
}

