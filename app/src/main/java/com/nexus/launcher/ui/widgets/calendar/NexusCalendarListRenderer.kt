package com.nexus.launcher.ui.widgets.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset

/**
 * Draws medium (2-event) and large (5-event) calendar agenda lists with high contrast
 * across Light, Dark, Calm, and Glass modes.
 */
internal class NexusCalendarListRenderer(private val events: List<CalendarEvent>?) {

    private fun applyTextShadow(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadow = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
        }
    }

    fun drawMedium(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        dateFormatter: NexusCalendarDateFormatter,
        palette: NexusNeumorphicDraw.SoftPalette,
        getTypeface: (Context, Int) -> Typeface
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val rightMargin = insets.right
        val topMargin = insets.top + 10f * dp

        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)

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
            typeface = getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }
        val dateStr = dateFormatter.formatHeaderDate(System.currentTimeMillis())
        canvas.drawText(dateStr, leftMargin, topMargin, headerPaint)

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

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 14f * dp
            typeface = getTypeface(context, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 12f * dp
            typeface = getTypeface(context, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
        }

        var currentY = divY + 24f * dp
        events?.take(2)?.forEach { event ->
            canvas.drawCircle(leftMargin + 4f * dp, currentY - 4f * dp, 3f * dp, dotPaint)

            val timeStr = if (event.isAllDay) context.getString(com.nexus.launcher.R.string.calendar_all_day) else dateFormatter.formatCompactTime(event.startMs)
            canvas.drawText(timeStr, w - rightMargin, currentY, timePaint)

            var t = event.title
            if (t.length > 20) t = t.take(18) + "..."
            canvas.drawText(t, leftMargin + 14f * dp, currentY, titlePaint)

            currentY += 24f * dp
        }
    }

    fun drawLarge(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        dateFormatter: NexusCalendarDateFormatter,
        palette: NexusNeumorphicDraw.SoftPalette,
        getTypeface: (Context, Int) -> Typeface
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val leftMargin = insets.left
        val rightMargin = insets.right
        val topMargin = insets.top + 18f * dp

        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)

        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 18f * dp
            typeface = getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }
        val dateStr = dateFormatter.formatHeaderDate(System.currentTimeMillis())
        canvas.drawText(dateStr, leftMargin, topMargin, headerPaint)

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
        val divY = topMargin + 16f * dp
        canvas.drawLine(leftMargin, divY, w - rightMargin, divY, divPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 16f * dp
            typeface = getTypeface(context, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 14f * dp
            typeface = getTypeface(context, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
        }

        var currentY = divY + 32f * dp
        events?.take(5)?.forEach { event ->
            canvas.drawCircle(leftMargin + 6f * dp, currentY - 5f * dp, 4f * dp, dotPaint)

            val timeStr = if (event.isAllDay) context.getString(com.nexus.launcher.R.string.calendar_all_day) else dateFormatter.formatCompactTime(event.startMs)
            canvas.drawText(timeStr, w - rightMargin, currentY, timePaint)

            var t = event.title
            if (t.length > 25) t = t.take(23) + "..."
            canvas.drawText(t, leftMargin + 20f * dp, currentY, titlePaint)

            currentY += 32f * dp
        }
    }
}
