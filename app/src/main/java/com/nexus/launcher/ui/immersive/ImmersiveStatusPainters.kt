package com.nexus.launcher.ui.immersive

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.nexus.launcher.locale.LocaleDigitUtils

/**
 * Draws each status-row item in the style the user picked ([ImmersiveStatusStyle]).
 *
 * Split out of [ImmersiveStatusBarView], which owns the row's layout, insets and taps — the
 * variants alone are more drawing code than that file can hold. Every method takes the paints
 * the view already prepared (nothing is allocated while drawing) and returns the edge it drew
 * to, so the view can lay the next item out beside it.
 */
internal class ImmersiveStatusPainters(
    private val context: Context,
    private val density: Float,
    iconSize: Float,
    private val textPaint: Paint,
    private val captionPaint: Paint,
    private val shapePaint: Paint,
    private val strokePaint: Paint,
    private val scratch: RectF,
) {
    var iconSize: Float = iconSize
        set(value) {
            field = value
            battery.iconSize = value
        }
    var insideMeter: Float = iconSize
        set(value) {
            field = value
            battery.insideMeter = value
        }
    var alertColor: Int
        get() = battery.alertColor
        set(value) { battery.alertColor = value }
    var captionColor: Int
        get() = battery.captionColor
        set(value) { battery.captionColor = value }

    private val battery = ImmersiveStatusBatteryDraw(
        context, density, textPaint, captionPaint, shapePaint, strokePaint, scratch,
    )
    private val clipPath = android.graphics.Path()
    private val clusterGap = 6f * density
    private val dateGap = 8f * density

    init {
        battery.iconSize = iconSize
        battery.insideMeter = insideMeter
    }

    /** Right-aligned at [right]; returns its left edge. Time, then date to its left. */
    fun drawClock(canvas: Canvas, right: Float, baseline: Float, style: String): Float {
        var x = right
        val date = dateText(style)
        if (date != null) {
            val w = captionPaint.measureText(date)
            canvas.drawText(date, x - w, baseline, captionPaint)
            x -= w + dateGap
        }
        val time = timeText(style)
        val w = textPaint.measureText(time)
        canvas.drawText(time, x - w, baseline, textPaint)
        return x - w
    }

    fun clockWidth(style: String): Float {
        var w = textPaint.measureText(timeText(style))
        dateText(style)?.let { w += dateGap + captionPaint.measureText(it) }
        return w
    }

    /** Pattern behind the "with date" clock; set from the Date format setting. */
    var datePattern: String = "EEE d MMM"

    fun timeText(style: String): String {
        val now = java.util.Date()
        return when (style) {
            ImmersiveStatusStyle.CLOCK_12H -> format("h:mm a", now)
            ImmersiveStatusStyle.CLOCK_24H -> format("HH:mm", now)
            ImmersiveStatusStyle.CLOCK_SECONDS ->
                if (android.text.format.DateFormat.is24HourFormat(context)) format("HH:mm:ss", now)
                else format("h:mm:ss a", now)
            else -> android.text.format.DateFormat.getTimeFormat(context).format(now)
        }
    }

    fun dateText(style: String): String? {
        if (style != ImmersiveStatusStyle.CLOCK_DATE) return null
        return format(datePattern, java.util.Date())
    }

    private fun format(pattern: String, date: java.util.Date): String {
        val locale = com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context)
        val best = android.text.format.DateFormat.getBestDateTimePattern(locale, pattern)
        return java.text.SimpleDateFormat(best, locale).format(date)
    }

    /** Right-aligned at [right]; returns its left edge. */
    fun drawNotifications(
        canvas: Canvas,
        right: Float,
        cy: Float,
        baseline: Float,
        style: String,
        count: Int,
        bell: Drawable?,
        icons: List<Drawable>,
    ): Float {
        return when (style) {
            ImmersiveStatusStyle.NOTIFICATIONS_ICONS -> drawIconStack(canvas, right, cy, icons)
            ImmersiveStatusStyle.NOTIFICATIONS_DOT -> {
                if (count <= 0) return right
                val r = 3.5f * density
                canvas.drawCircle(right - r, cy, r, shapePaint)
                right - r * 2f
            }
            else -> {
                var x = right
                if (count > 0) {
                    val text = LocaleDigitUtils.formatNumber(
                        count, com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context),
                    )
                    val w = captionPaint.measureText(text)
                    canvas.drawText(text, x - w, baseline, captionPaint)
                    x -= w + clusterGap
                }
                drawIcon(canvas, bell, x - iconSize, cy, 255)
                x - iconSize
            }
        }
    }

    fun notificationsWidth(style: String, count: Int, icons: List<Drawable>): Float {
        return when (style) {
            ImmersiveStatusStyle.NOTIFICATIONS_ICONS -> {
                if (icons.isEmpty()) 0f
                else iconSize + (icons.size - 1) * iconSize * 0.72f
            }
            ImmersiveStatusStyle.NOTIFICATIONS_DOT -> if (count <= 0) 0f else 7f * density
            else -> {
                var w = iconSize
                if (count > 0) {
                    val text = LocaleDigitUtils.formatNumber(
                        count, com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context),
                    )
                    w += clusterGap + captionPaint.measureText(text)
                }
                w
            }
        }
    }

    /** App icons waiting, overlapped and clipped to circles with a hairline. */
    private fun drawIconStack(canvas: Canvas, right: Float, cy: Float, icons: List<Drawable>): Float {
        if (icons.isEmpty()) return right
        val step = iconSize * 0.72f
        val ring = (1f * density).coerceAtLeast(1f)
        var x = right - iconSize
        icons.forEachIndexed { index, icon ->
            val cx = x + iconSize / 2f
            clipPath.reset()
            clipPath.addCircle(cx, cy, iconSize / 2f, android.graphics.Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            icon.alpha = 255
            icon.setBounds(
                x.toInt(), (cy - iconSize / 2f).toInt(),
                (x + iconSize).toInt(), (cy + iconSize / 2f).toInt(),
            )
            icon.draw(canvas)
            canvas.restore()
            canvas.drawCircle(cx, cy, iconSize / 2f - ring / 2f, strokePaint)
            if (index < icons.lastIndex) x -= step
        }
        return x
    }

    /** Left-aligned at [left]; returns its right edge. */
    fun drawWifi(canvas: Canvas, left: Float, cy: Float, style: String, level: Int, icon: Drawable?): Float =
        when (style) {
            ImmersiveStatusStyle.WIFI_BARS -> drawWifiBars(canvas, left, cy, level)
            ImmersiveStatusStyle.WIFI_DOT -> {
                val r = 3.5f * density
                shapePaint.alpha = if (level >= 2) 255 else 110
                canvas.drawCircle(left + r, cy, r, shapePaint)
                shapePaint.alpha = 255
                left + r * 2f
            }
            else -> {
                drawIcon(canvas, icon, left, cy, 90 + 165 * level / 4)
                left + iconSize
            }
        }

    fun wifiGlyphWidth(style: String): Float = when (style) {
        ImmersiveStatusStyle.WIFI_BARS -> iconSize / 6f * 4.2f
        ImmersiveStatusStyle.WIFI_DOT -> 7f * density
        else -> iconSize
    }

    fun drawSignal(canvas: Canvas, left: Float, cy: Float, style: String, level: Int): Float =
        when (style) {
            ImmersiveStatusStyle.SIGNAL_ARC -> drawArc(canvas, left, cy, level)
            ImmersiveStatusStyle.SIGNAL_DOTS -> drawDots(canvas, left, cy, level)
            else -> drawBars(canvas, left, cy, level)
        }

    fun signalGlyphWidth(style: String): Float = when (style) {
        ImmersiveStatusStyle.SIGNAL_ARC -> iconSize * 2f
        ImmersiveStatusStyle.SIGNAL_DOTS -> {
            val r = iconSize / 8f
            r * 2f + 3 * r * 2.6f
        }
        else -> iconSize / 6f * 5.5f
    }

    /** Three rising bars over a dot — a Wi-Fi fan, not the mobile signal's four bars. */
    private fun drawWifiBars(canvas: Canvas, left: Float, cy: Float, level: Int): Float {
        val barW = iconSize / 6f
        val bottom = cy + iconSize / 2f - barW * 1.6f
        for (i in 0 until 3) {
            val barLeft = left + i * barW * 1.6f
            val top = bottom - iconSize * (i + 2) / 5f
            shapePaint.alpha = if (i < level - 1) 255 else 80
            scratch.set(barLeft, top, barLeft + barW, bottom)
            canvas.drawRoundRect(scratch, barW / 3f, barW / 3f, shapePaint)
        }
        shapePaint.alpha = if (level > 0) 255 else 80
        canvas.drawCircle(left + barW * 1.6f, cy + iconSize / 2f - barW / 2f, barW * 0.7f, shapePaint)
        shapePaint.alpha = 255
        return left + barW * 4.2f
    }

    private fun drawBars(canvas: Canvas, left: Float, cy: Float, level: Int): Float {
        val barW = iconSize / 6f
        val bottom = cy + iconSize / 2f
        for (i in 0 until 4) {
            val barLeft = left + i * barW * 1.5f
            val top = bottom - iconSize * (i + 1) / 4f
            shapePaint.alpha = if (i < level) 255 else 80
            scratch.set(barLeft, top, barLeft + barW, bottom)
            canvas.drawRoundRect(scratch, barW / 3f, barW / 3f, shapePaint)
        }
        shapePaint.alpha = 255
        return left + barW * 5.5f
    }

    private fun drawArc(canvas: Canvas, left: Float, cy: Float, level: Int): Float {
        val size = iconSize
        for (i in 0 until 3) {
            val radius = size * (i + 1) / 3f
            strokePaint.alpha = if (i < level - 1) 255 else 80
            scratch.set(left - radius + size, cy + size / 2f - radius, left + radius + size, cy + size / 2f + radius)
            canvas.drawArc(scratch, 225f, 90f, false, strokePaint)
        }
        strokePaint.alpha = 255
        shapePaint.alpha = if (level > 0) 255 else 80
        canvas.drawCircle(left + size, cy + size / 2f, 1.6f * density, shapePaint)
        shapePaint.alpha = 255
        return left + size * 2f
    }

    private fun drawDots(canvas: Canvas, left: Float, cy: Float, level: Int): Float {
        val r = iconSize / 8f
        for (i in 0 until 4) {
            shapePaint.alpha = if (i < level) 255 else 80
            canvas.drawCircle(left + r + i * r * 2.6f, cy, r, shapePaint)
        }
        shapePaint.alpha = 255
        return left + r * 2f + 3 * r * 2.6f
    }

    fun drawBattery(
        canvas: Canvas,
        left: Float,
        cy: Float,
        baseline: Float,
        style: String,
        percentStyle: String,
        percent: Int,
        lowPercent: Int = 0,
        charging: Boolean = false,
        pulse: Float = 1f,
    ): Float = battery.draw(
        canvas, left, cy, baseline, style, percentStyle, percent, lowPercent, charging, pulse,
    )

    fun batteryWidth(style: String, percentStyle: String, percent: Int): Float =
        battery.width(style, percentStyle, percent)

    /** A small word beside a meter; returns its right edge. */
    fun drawWord(canvas: Canvas, text: String, left: Float, baseline: Float): Float {
        if (text.isBlank()) return left
        canvas.drawText(text, left, baseline, captionPaint)
        return left + captionPaint.measureText(text)
    }

    fun wordWidth(text: String): Float =
        if (text.isBlank()) 0f else captionPaint.measureText(text)

    fun drawIcon(canvas: Canvas, icon: Drawable?, left: Float, cy: Float, alpha: Int) {
        icon ?: return
        icon.setTint(textPaint.color)
        icon.alpha = alpha
        icon.setBounds(
            left.toInt(), (cy - iconSize / 2f).toInt(),
            (left + iconSize).toInt(), (cy + iconSize / 2f).toInt(),
        )
        icon.draw(canvas)
    }
}
