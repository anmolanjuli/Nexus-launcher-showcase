package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Renders all Nexus Clock widget styles with full adaptive auto-scaling.
 * Ensures the primary time elements are 100% visible at all resize bounds.
 */
class NexusClockRenderer : NexusWidgetRenderer() {

    companion object {
        const val STYLE_GREETING_ANALOG = 0
        const val STYLE_RETRO_LCD = 1
        const val STYLE_MINIMAL_BAUHAUS = 2
        const val STYLE_BOLD_TYPOGRAPHY = 3
        const val STYLE_LACROSSE_LCD = 4
    }

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)

        val cal = Calendar.getInstance()
        val hours = (cal.get(Calendar.HOUR) % 12).toFloat()
        val minutes = cal.get(Calendar.MINUTE).toFloat()

        when (config.clockStyle) {
            STYLE_GREETING_ANALOG -> drawGreetingAnalog(context, canvas, width, height, dp, config, hours, minutes, cal, palette, isNeumorphic, isGlass)
            STYLE_RETRO_LCD -> drawRetroLcd(context, canvas, width, height, dp, config, cal, palette, isNeumorphic, isGlass)
            STYLE_MINIMAL_BAUHAUS -> drawMinimalBauhaus(context, canvas, width, height, dp, config, hours, minutes, cal, palette, isNeumorphic, isGlass)
            STYLE_BOLD_TYPOGRAPHY -> drawBoldTypography(context, canvas, width, height, dp, config, cal, palette, isGlass)
            STYLE_LACROSSE_LCD -> LaCrosseClockRenderer.drawLaCrosseClock(context, canvas, width, height, dp, config, size, palette, isGlass)
            else -> drawGreetingAnalog(context, canvas, width, height, dp, config, hours, minutes, cal, palette, isNeumorphic, isGlass)
        }
    }

    private fun drawGreetingAnalog(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        hours: Float,
        minutes: Float,
        cal: Calendar,
        palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean,
        isGlass: Boolean
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val left = insets.left
        val right = w - insets.right
        val availW = right - left
        val availH = h - insets.top - insets.bottom

        val hasSpaceForText = availW >= 170f * dp && availH >= 80f * dp
        if (!hasSpaceForText) {
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(availW, availH) * 0.44f
            NexusClockDrawDials.drawAnalogDial(context, canvas, cx, cy, radius, hours, minutes, palette, dp, showNumerals = radius >= 22f * dp, isNeumorphic = isNeumorphic, isGlass = isGlass)
            return
        }

        // Dual Card layout: Left = Dial, Right = Greeting Text
        val dialRadius = minOf(availH * 0.44f, availW * 0.25f).coerceAtLeast(18f * dp)
        val cx = left + dialRadius + 4f * dp
        val cy = h / 2f

        NexusClockDrawDials.drawAnalogDial(context, canvas, cx, cy, dialRadius, hours, minutes, palette, dp, showNumerals = dialRadius >= 22f * dp, isNeumorphic = isNeumorphic, isGlass = isGlass)

        // Right side greeting text
        val textLeft = cx + dialRadius + 14f * dp
        val textRight = right
        val textWidth = (textRight - textLeft).coerceAtLeast(10f)

        val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
        val secondaryColor = if (palette.isLight) Color.parseColor("#222A35") else if (isGlass) Color.argb(225, 245, 245, 245) else palette.textSecondary

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = minOf(20f * dp, availH * 0.22f)
            typeface = getTypeface(context, config, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = minOf(13f * dp, availH * 0.16f)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }

        val greetingTitle = context.getString(R.string.clock_greeting_prefix, monthName)
        val greetingQuote = config.customText.takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.clock_greeting_quote)

        val titleEllipsized = TextUtils.ellipsize(greetingTitle, titlePaint, textWidth, TextUtils.TruncateAt.END).toString()
        val quoteEllipsized = TextUtils.ellipsize(greetingQuote, subPaint, textWidth, TextUtils.TruncateAt.END).toString()

        val titleY = cy - 4f * dp
        val quoteY = cy + 18f * dp

        canvas.drawText(titleEllipsized, textLeft, titleY, titlePaint)
        canvas.drawText(quoteEllipsized, textLeft, quoteY, subPaint)
    }

    private fun drawRetroLcd(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        cal: Calendar,
        palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean,
        isGlass: Boolean
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val left = insets.left
        val right = w - insets.right
        val availW = right - left
        val availH = h - insets.top - insets.bottom

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
        val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        val dateStr = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMdd"), Locale.getDefault()).format(cal.time)

        val tf = getTypeface(context, config, Typeface.BOLD)
        val hasSpaceForText = availW >= 170f * dp && availH >= 70f * dp

        if (!hasSpaceForText) {
            val wellW = (availW * 0.92f).coerceAtLeast(10f)
            val wellH = (availH * 0.76f).coerceAtLeast(10f)
            val wellLeft = left + (availW - wellW) / 2f
            val wellTop = insets.top + (availH - wellH) / 2f
            NexusClockDrawDials.drawRetroLcdDisplay(canvas, wellLeft, wellTop, wellW, wellH, timeStr, palette, dp, tf, isNeumorphic, isGlass)
            return
        }

        // Horizontal split format: Left = LCD screen, Right = Day & Date
        val wellW = (availW * 0.52f).coerceIn(70f * dp, 170f * dp)
        val wellH = (availH * 0.72f).coerceIn(32f * dp, 76f * dp)
        val wellLeft = left + 4f * dp
        val wellTop = (h - wellH) / 2f

        NexusClockDrawDials.drawRetroLcdDisplay(canvas, wellLeft, wellTop, wellW, wellH, timeStr, palette, dp, tf, isNeumorphic, isGlass)

        val textLeft = wellLeft + wellW + 14f * dp
        val textRight = right
        val textWidth = (textRight - textLeft).coerceAtLeast(10f)

        val secondaryColor = if (palette.isLight) Color.parseColor("#222A35") else if (isGlass) Color.argb(225, 245, 245, 245) else palette.textSecondary

        val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = minOf(18f * dp, availH * 0.24f)
            typeface = tf
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }

        val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            textSize = minOf(12f * dp, availH * 0.16f)
            typeface = getTypeface(context, config, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
            applyTextShadow(this, isGlass, palette.isLight, dp)
        }

        val dayEllipsized = TextUtils.ellipsize(dayOfWeek, dayPaint, textWidth, TextUtils.TruncateAt.END).toString()
        val dateEllipsized = TextUtils.ellipsize(dateStr, datePaint, textWidth, TextUtils.TruncateAt.END).toString()

        val cy = h / 2f
        canvas.drawText(dayEllipsized, textLeft, cy - 2f * dp, dayPaint)
        canvas.drawText(dateEllipsized, textLeft, cy + 18f * dp, datePaint)
    }

    private fun drawMinimalBauhaus(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        hours: Float,
        minutes: Float,
        cal: Calendar,
        palette: NexusNeumorphicDraw.SoftPalette,
        isNeumorphic: Boolean,
        isGlass: Boolean
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val availW = w - insets.left - insets.right
        val availH = h - insets.top - insets.bottom

        val hasSpaceForText = availW >= 170f * dp && availH >= 80f * dp
        if (hasSpaceForText) {
            val dialRadius = minOf(availH * 0.44f, availW * 0.25f).coerceAtLeast(18f * dp)
            val cx = insets.left + dialRadius + 4f * dp
            val cy = h / 2f
            NexusClockDrawDials.drawBauhausDial(context, canvas, cx, cy, dialRadius, hours, minutes, palette, dp, isNeumorphic, isGlass)

            val textLeft = cx + dialRadius + 14f * dp
            val textWidth = (w - insets.right - textLeft).coerceAtLeast(10f)

            val secondaryColor = if (palette.isLight) Color.parseColor("#222A35") else if (isGlass) Color.argb(225, 245, 245, 245) else palette.textSecondary

            val dayPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = minOf(20f * dp, availH * 0.24f)
                typeface = getTypeface(context, config, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                applyTextShadow(this, isGlass, palette.isLight, dp)
            }
            val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryColor
                textSize = minOf(13f * dp, availH * 0.16f)
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                applyTextShadow(this, isGlass, palette.isLight, dp)
            }
            val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
            val dateStr = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMMd"), Locale.getDefault()).format(cal.time)

            val dayEllipsized = TextUtils.ellipsize(dayOfWeek, dayPaint, textWidth, TextUtils.TruncateAt.END).toString()
            val dateEllipsized = TextUtils.ellipsize(dateStr, datePaint, textWidth, TextUtils.TruncateAt.END).toString()

            canvas.drawText(dayEllipsized, textLeft, cy - 3f * dp, dayPaint)
            canvas.drawText(dateEllipsized, textLeft, cy + 18f * dp, datePaint)
        } else {
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(availW, availH) * 0.44f
            NexusClockDrawDials.drawBauhausDial(context, canvas, cx, cy, radius, hours, minutes, palette, dp, isNeumorphic, isGlass)
        }
    }

    private fun drawBoldTypography(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        cal: Calendar,
        palette: NexusNeumorphicDraw.SoftPalette,
        isGlass: Boolean
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val availW = w - insets.left - insets.right
        val availH = h - insets.top - insets.bottom

        val hoursStr = SimpleDateFormat("HH", Locale.getDefault()).format(cal.time)
        val minsStr = SimpleDateFormat("mm", Locale.getDefault()).format(cal.time)
        val dayDateStr = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"), Locale.getDefault()).format(cal.time)

        val tf = getTypeface(context, config, Typeface.BOLD)
        val hasSpaceForText = availW >= 170f * dp && availH >= 80f * dp

        val secondaryColor = if (palette.isLight) Color.parseColor("#222A35") else if (isGlass) Color.argb(225, 245, 245, 245) else palette.textSecondary

        if (hasSpaceForText) {
            val cxLeft = insets.left + availW * 0.26f
            val cy = h / 2f
            NexusClockDrawDials.drawBoldTypoDigital(context, canvas, cxLeft, cy, hoursStr, minsStr, palette, dp, tf, availH, availW * 0.40f, isGlass)

            val textLeft = cxLeft + minOf(availW * 0.22f, 38f * dp)
            val textWidth = (w - insets.right - textLeft).coerceAtLeast(10f)

            val infoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = minOf(18f * dp, availH * 0.22f)
                typeface = tf
                textAlign = Paint.Align.LEFT
                applyTextShadow(this, isGlass, palette.isLight, dp)
            }
            val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryColor
                textSize = minOf(12f * dp, availH * 0.15f)
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                applyTextShadow(this, isGlass, palette.isLight, dp)
            }
            val quoteStr = config.customText.takeUnless { it.isNullOrBlank() } ?: context.getString(R.string.clock_greeting_quote)
            val dateEllipsized = TextUtils.ellipsize(dayDateStr, infoPaint, textWidth, TextUtils.TruncateAt.END).toString()
            val quoteEllipsized = TextUtils.ellipsize(quoteStr, quotePaint, textWidth, TextUtils.TruncateAt.END).toString()

            canvas.drawText(dateEllipsized, textLeft, cy - 2f * dp, infoPaint)
            canvas.drawText(quoteEllipsized, textLeft, cy + 18f * dp, quotePaint)
        } else {
            val cx = w / 2f
            val cy = h / 2f
            NexusClockDrawDials.drawBoldTypoDigital(context, canvas, cx, cy, hoursStr, minsStr, palette, dp, tf, availH, availW, isGlass)
        }
    }

    private fun applyTextShadow(paint: TextPaint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        if (isGlass) {
            val shadow = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
            paint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
        }
    }

    override fun isRetroStyle(config: NexusWidgetConfig.InstanceConfig): Boolean =
        config.clockStyle == STYLE_LACROSSE_LCD
}
