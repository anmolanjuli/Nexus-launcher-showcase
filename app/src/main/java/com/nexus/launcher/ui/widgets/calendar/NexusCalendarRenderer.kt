package com.nexus.launcher.ui.widgets.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.WidgetSize
import java.util.Calendar

class NexusCalendarRenderer(private val events: List<CalendarEvent>?) : NexusWidgetRenderer() {

    private val dateFormatter = NexusCalendarDateFormatter()
    private val weeklyStripRenderer = NexusCalendarWeeklyStripRenderer(events)
    private val monthGridRenderer = NexusCalendarMonthGridRenderer(events)
    private val smallRenderer = NexusCalendarSmallRenderer(events.orEmpty())
    private val listRenderer = NexusCalendarListRenderer(events)

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val locale = context.resources.configuration.locales[0]
        NexusCalendarDebug.d(
            "drawContent locale=$locale events=${if (events == null) "null" else events.size.toString()} " +
                "size=$size showCount=${config.showCountWhenSmall} view=${config.calendarViewMode}"
        )
        try {
            dateFormatter.ensure(context)
            NexusCalendarDebug.d("ensure() ok locale=$locale")
        } catch (t: Throwable) {
            NexusCalendarDebug.e("ensure() caught error locale=$locale", t)
        }

        val tokens = com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val palette = com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(tokens)

        if (config.calendarViewMode == "MONTHLY") {
            drawMonthlyMode(context, canvas, width, height, dp, config, palette)
            return
        }

        if (events == null || events.isEmpty()) {
            NexusCalendarDebug.d("EMPTY STATE branch drawing calendar_no_upcoming_events")
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 14f * dp
                typeface = getTypeface(context, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                if (config.backgroundMode == com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            canvas.drawText(context.getString(com.nexus.launcher.R.string.calendar_no_upcoming_events), width / 2f, height / 2f, textPaint)
            return
        }

        when (size) {
            WidgetSize.TINY -> drawTiny(context, canvas, width, height, dp, config, palette)
            WidgetSize.SMALL -> drawSmall(context, canvas, width, height, dp, config, palette)
            WidgetSize.MEDIUM -> drawMedium(context, canvas, width, height, dp, config, palette)
            WidgetSize.LARGE -> drawLarge(context, canvas, width, height, dp, config, palette)
        }
    }

    private fun drawMonthlyMode(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        val wDp = w / dp
        val hDp = h / dp
        when {
            wDp < 95f || hDp < 80f -> drawDayOnly(context, canvas, w, h, dp, config, palette)
            wDp < 155f || hDp < 115f -> drawTinyMonthly(context, canvas, w, h, dp, config, palette)
            wDp < 225f || hDp < 175f -> drawWeeklyStrip(context, canvas, w, h, dp, config, palette)
            else -> drawMonthlyGrid(context, canvas, w, h, dp, config, palette)
        }
    }

    private fun drawDayOnly(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        val cal = Calendar.getInstance()
        val dateNumStr = dateFormatter.getDayNumberString(cal.get(Calendar.DAY_OF_MONTH))
        val isGlass = config.backgroundMode == com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = minOf(h * 0.5f, 38f * dp)
            typeface = getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val y = h / 2f + numPaint.textSize * 0.35f
        canvas.drawText(dateNumStr, w / 2f, y, numPaint)
    }

    private fun drawTinyMonthly(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        val cal = Calendar.getInstance()
        val monthStr = dateFormatter.formatMonth(cal.timeInMillis)
        val dayStr = dateFormatter.formatDay(cal.timeInMillis)
        val dateNumStr = dateFormatter.getDayNumberString(cal.get(Calendar.DAY_OF_MONTH))
        val yearStr = cal.get(Calendar.YEAR).toString()
        val isGlass = config.backgroundMode == com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS

        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 11f * dp
            typeface = getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = 30f * dp
            typeface = getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val botPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = 12f * dp
            typeface = getTypeface(context, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }

        val cy = h / 2f
        canvas.drawText("$monthStr $yearStr".uppercase(), w / 2f, cy - 18f * dp, topPaint)
        canvas.drawText(dateNumStr, w / 2f, cy + 12f * dp, midPaint)
        canvas.drawText(dayStr.uppercase(), w / 2f, cy + 28f * dp, botPaint)
    }

    private fun drawTiny(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        val event = events?.firstOrNull() ?: return
        val isGlass = config.backgroundMode == com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS
        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        if (config.showCountWhenSmall) {
            val countStr = dateFormatter.getDayNumberString(events.size)
            val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 28f * dp
                typeface = getTypeface(context, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryColor
                textSize = 12f * dp
                typeface = getTypeface(context, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            val cy = h / 2f
            canvas.drawText(countStr, w / 2f, cy + 4f * dp, countPaint)
            val label = try {
                val s = context.resources.getQuantityString(
                    com.nexus.launcher.R.plurals.calendar_events_label, events.size
                )
                NexusCalendarDebug.d("getQuantityString ok count=${events.size} label='$s'")
                s
            } catch (t: Throwable) {
                NexusCalendarDebug.e("getQuantityString caught fallback count=${events.size} locale=${context.resources.configuration.locales[0]}", t)
                if (events.size == 1) "event" else "events"
            }
            canvas.drawText(label, w / 2f, cy + 20f * dp, subPaint)
            return
        }

        if (event.isAllDay) {
            val dateStr = dateFormatter.formatAllDay(event.startMs)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 20f * dp
                typeface = getTypeface(context, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            canvas.drawText(dateStr, w / 2f, h / 2f + 8f * dp, textPaint)
        } else {
            val timeStr = dateFormatter.formatCompactTime(event.startMs)
            val amPmStr = dateFormatter.formatAmPm(event.startMs)

            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = 28f * dp
                typeface = getTypeface(context, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryColor
                textSize = 12f * dp
                typeface = getTypeface(context, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }

            val cy = h / 2f
            canvas.drawText(timeStr, w / 2f, cy + 4f * dp, timePaint)
            canvas.drawText(amPmStr, w / 2f, cy + 20f * dp, subPaint)
        }
    }

    private fun drawSmall(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        smallRenderer.draw(context, canvas, w, h, dp, config, dateFormatter, palette)
    }

    private fun drawMedium(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        listRenderer.drawMedium(context, canvas, w, h, dp, config, dateFormatter, palette) { ctx, wt ->
            getTypeface(ctx, wt)
        }
    }

    private fun drawLarge(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        listRenderer.drawLarge(context, canvas, w, h, dp, config, dateFormatter, palette) { ctx, wt ->
            getTypeface(ctx, wt)
        }
    }

    private fun drawMonthlyGrid(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        monthGridRenderer.draw(context, canvas, w, h, dp, config, dateFormatter, palette)
    }

    private fun drawWeeklyStrip(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.SoftPalette
    ) {
        weeklyStripRenderer.draw(context, canvas, w, h, dp, config, dateFormatter, palette)
    }
}
