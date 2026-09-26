package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.WidgetSize
import com.nexus.launcher.ui.widgets.weather.NexusWeatherRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Main renderer orchestrator for the "La Crosse" Style retro LCD clock panel.
 * Dispatches drawing across sizing breakpoints (TINY, SMALL, MEDIUM, LARGE)
 * and distributes active data columns.
 */
object LaCrosseClockRenderer {

    private val tempPanelBounds = RectF()

    fun drawLaCrosseClock(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        size: WidgetSize,
        palette: NexusNeumorphicDraw.SoftPalette,
        isGlass: Boolean
    ) {
        val cfg = LaCrosseClockConfig.read(context, config.appWidgetId)
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val left = insets.left + 6f * dp
        val top = insets.top + 6f * dp
        val right = w - insets.right - 6f * dp
        val bottom = h - insets.bottom - 6f * dp
        val availW = (right - left).coerceAtLeast(10f)
        val availH = (bottom - top).coerceAtLeast(10f)

        LaCrosseClockTheme.applyTheme(context, config, cfg)

        // Authentic retro LCD panel background fills the full widget area (clipped to rounded corners by caller)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), LaCrosseSegmentDraw.panelBgPaint)

        val cal = Calendar.getInstance()
        // Steady, not blinking. An app widget is redrawn once a minute, so a half-second blink
        // could never be seen: all the phase did was decide, at random, whether this minute's
        // colon was drawn at all. A real blink would mean waking the launcher every second.
        val isColonOn = true

        val availWDp = availW / dp
        val availHDp = availH / dp

        when {
            availWDp < 110f && availHDp >= availWDp * 0.85f -> {
                drawTinyClock(canvas, left, top, availW, availH, cal, cfg)
            }
            availHDp < 55f -> {
                drawCompactClock(context, canvas, left, top, availW, availH, cal, cfg, isColonOn, dp)
            }
            else -> {
                drawLargeClock(context, canvas, left, top, availW, availH, dp, cal, cfg, isColonOn)
            }
        }
    }

    private fun drawTinyClock(
        canvas: Canvas,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        cal: Calendar,
        cfg: LaCrosseClockConfig
    ) {
        val hours = String.format(Locale.US, "%02d", cal.get(Calendar.HOUR_OF_DAY))
        val mins = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val digitH = (h * 0.44f).coerceAtLeast(10f)
        val digitW = digitH * 0.52f
        val gap = digitW * 0.18f

        val row1X = left + (w - (digitW * 2 + gap)) / 2f
        val row1Y = top + h * 0.05f
        LaCrosseSegmentDraw.draw7SegmentDigit(canvas, hours[0], row1X, row1Y, digitW, digitH, cfg.showGhostSegments)
        LaCrosseSegmentDraw.draw7SegmentDigit(canvas, hours[1], row1X + digitW + gap, row1Y, digitW, digitH, cfg.showGhostSegments)

        val row2X = row1X
        val row2Y = top + h * 0.51f
        LaCrosseSegmentDraw.draw7SegmentDigit(canvas, mins[0], row2X, row2Y, digitW, digitH, cfg.showGhostSegments)
        LaCrosseSegmentDraw.draw7SegmentDigit(canvas, mins[1], row2X + digitW + gap, row2Y, digitW, digitH, cfg.showGhostSegments)
    }

    private fun drawCompactClock(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        cal: Calendar,
        cfg: LaCrosseClockConfig,
        isColonOn: Boolean,
        dp: Float
    ) {
        val details = LaCrosseClockTheme.formatTimeDetails(context, cal, cfg.timeFormat)
        val digitH = (h * 0.72f).coerceAtLeast(14f * dp)
        val digitW = digitH * 0.52f
        val colonW = digitW * 0.32f
        val gap = digitW * 0.14f

        val totalW = digitW * 4 + colonW + gap * 4
        var curX = left + (w - totalW) / 2f
        val curY = top + (h - digitH) / 2f

        if (details.is12h && cfg.showAmPm) {
            val labelText = if (details.isAm) context.getString(R.string.lacrosse_label_am) else context.getString(R.string.lacrosse_label_pm)
            LaCrosseSegmentDraw.silkscreenPaint.textSize = (digitH * 0.24f).coerceAtLeast(7f * dp)
            val amX = left + (curX - left) / 2f
            val amY = curY + digitH * 0.45f
            canvas.drawText(labelText, amX, amY, LaCrosseSegmentDraw.silkscreenPaint)
        }

        if (cfg.showTime) {
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.hours[0], curX, curY, digitW, digitH, cfg.showGhostSegments)
            curX += digitW + gap
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.hours[1], curX, curY, digitW, digitH, cfg.showGhostSegments)
            curX += digitW + gap
            LaCrosseSegmentDraw.drawColon(canvas, curX, curY, colonW, digitH, isColonOn, cfg.showGhostSegments)
            curX += colonW + gap
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.minutes[0], curX, curY, digitW, digitH, cfg.showGhostSegments)
            curX += digitW + gap
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.minutes[1], curX, curY, digitW, digitH, cfg.showGhostSegments)
        }
    }

    private fun drawLargeClock(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        dp: Float,
        cal: Calendar,
        cfg: LaCrosseClockConfig,
        isColonOn: Boolean
    ) {
        val cachedTemp = if (cfg.showTemp) {
            NexusWeatherRepository.getCachedTemperature(context) ?: run {
                val isFahr = NexusWeatherRepository.getTemperatureUnit(context) == "fahrenheit"
                Pair(if (isFahr) 72 else 22, if (isFahr) "°F" else "°C")
            }
        } else null

        val columns = mutableListOf<LaCrosseClockTheme.BottomColumnSpec>()
        if (cfg.showMonth) {
            val mVal = if (cfg.monthFormat == LaCrosseClockConfig.MonthFormat.NUMERIC) {
                String.format(Locale.US, "%02d", cal.get(Calendar.MONTH) + 1)
            } else {
                SimpleDateFormat("MMM", Locale.US).format(cal.time).uppercase()
            }
            columns.add(LaCrosseClockTheme.BottomColumnSpec(context.getString(R.string.lacrosse_label_month), mVal, cfg.monthFormat != LaCrosseClockConfig.MonthFormat.NUMERIC))
        }
        if (cfg.showDate) {
            val dVal = String.format(Locale.US, "%02d", cal.get(Calendar.DAY_OF_MONTH))
            columns.add(LaCrosseClockTheme.BottomColumnSpec(context.getString(R.string.lacrosse_label_date), dVal, false))
        }
        if (cfg.showDay) {
            val dayVal = SimpleDateFormat("EEE", Locale.US).format(cal.time).uppercase()
            columns.add(LaCrosseClockTheme.BottomColumnSpec(context.getString(R.string.lacrosse_label_day), dayVal, true))
        }
        if (cachedTemp != null) {
            val tempVal = "${cachedTemp.first}°"
            columns.add(LaCrosseClockTheme.BottomColumnSpec(context.getString(R.string.lacrosse_label_temp), tempVal, false))
        }

        val hasBottom = columns.isNotEmpty()
        val hasTop = cfg.showTime

        val topH = when {
            !hasBottom -> h
            !hasTop -> 0f
            else -> h * 0.58f
        }
        val botH = when {
            !hasTop -> h
            !hasBottom -> 0f
            else -> h * 0.38f
        }
        val divY = top + topH

        if (hasTop && topH > 0f) {
            LaCrosseRowDrawers.drawTopRow(context, canvas, left, top, w, topH, dp, cal, cfg, isColonOn)
        }

        if (cfg.showDividerLines) {
            val lineWidth = ((h / dp) * 0.008f * dp).coerceIn(0.5f * dp, 1.35f * dp)
            LaCrosseSegmentDraw.dividerPaint.strokeWidth = lineWidth
        }

        if (hasTop && hasBottom && cfg.showDividerLines) {
            canvas.drawLine(left, divY, left + w, divY, LaCrosseSegmentDraw.dividerPaint)
        }

        if (hasBottom && botH > 0f) {
            val colY = if (hasTop) divY + 3f * dp else top
            val colH = if (hasTop) (h - topH - 5f * dp).coerceAtLeast(10f) else h
            val colW = w / columns.size
            columns.forEachIndexed { index, col ->
                val colX = left + index * colW
                if (index > 0 && cfg.showDividerLines) {
                    val lineTop = if (hasTop) divY else top
                    val lineBot = top + h
                    canvas.drawLine(colX, lineTop, colX, lineBot, LaCrosseSegmentDraw.dividerPaint)
                }
                LaCrosseRowDrawers.drawColumnField(
                    canvas, col.label, colX, colY, colW, colH, col.isAlphanumeric, col.value, cfg.showGhostSegments, dp
                )
            }
        }
    }
}
