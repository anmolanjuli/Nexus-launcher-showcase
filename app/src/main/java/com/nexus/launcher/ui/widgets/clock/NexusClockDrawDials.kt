package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import kotlin.math.cos
import kotlin.math.sin

/** Helper for drawing analog clock dials, hands, tick marks, and LCD digital wells. */
object NexusClockDrawDials {

    fun drawAnalogDial(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        hours: Float,
        minutes: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        dp: Float,
        showNumerals: Boolean = true,
        isNeumorphic: Boolean = false,
        isGlass: Boolean = false
    ) {
        val r = radius.coerceAtLeast(10f)
        val isLight = palette.isLight

        // Outer well / dial surface
        if (isNeumorphic) {
            val dialRect = RectF(cx - r, cy - r, cx + r, cy + r)
            NexusNeumorphicDraw.drawDebossedWell(canvas, dialRect, r, palette, dp)
        } else {
            val wellColor = if (isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wellColor }
            canvas.drawCircle(cx, cy, r, bgPaint)
        }

        val secondaryColor = if (isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        // Numerals 12, 3, 6, 9
        if (showNumerals && r >= 28f * dp) {
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryColor
                textSize = minOf(r * 0.28f, 13f * dp)
                typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            val numInset = r * 0.72f
            canvas.drawText("12", cx, cy - numInset + numPaint.textSize * 0.35f, numPaint)
            canvas.drawText("6", cx, cy + numInset + numPaint.textSize * 0.35f, numPaint)
            canvas.drawText("3", cx + numInset, cy + numPaint.textSize * 0.35f, numPaint)
            canvas.drawText("9", cx - numInset, cy + numPaint.textSize * 0.35f, numPaint)
        }

        // Hour Ticks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLight) Color.argb(120, 0, 0, 0) else if (isGlass) Color.argb(160, 255, 255, 255) else palette.textSecondary
            strokeCap = Paint.Cap.ROUND
            strokeWidth = maxOf(1.5f * dp, r * 0.035f)
        }
        val tickOuter = r * 0.88f
        val tickInner = r * 0.78f
        for (i in 0 until 12) {
            if (showNumerals && (i == 0 || i == 3 || i == 6 || i == 9)) continue
            val angle = (Math.PI * 2 * (i / 12.0) - Math.PI / 2).toFloat()
            val x1 = cx + cos(angle) * tickInner
            val y1 = cy + sin(angle) * tickInner
            val x2 = cx + cos(angle) * tickOuter
            val y2 = cy + sin(angle) * tickOuter
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Hour Hand
        val hourAngle = (Math.PI * 2 * ((hours + minutes / 60f) / 12.0) - Math.PI / 2).toFloat()
        val hourLen = r * 0.52f
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            strokeCap = Paint.Cap.ROUND
            strokeWidth = maxOf(3.5f * dp, r * 0.075f)
            if (isGlass) {
                val shadow = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val hx = cx + cos(hourAngle) * hourLen
        val hy = cy + sin(hourAngle) * hourLen
        canvas.drawLine(cx, cy, hx, hy, hourPaint)

        // Minute Hand
        val minAngle = (Math.PI * 2 * (minutes / 60.0) - Math.PI / 2).toFloat()
        val minLen = r * 0.76f
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            strokeCap = Paint.Cap.ROUND
            strokeWidth = maxOf(2.2f * dp, r * 0.05f)
            if (isGlass) {
                val shadow = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        val mx = cx + cos(minAngle) * minLen
        val my = cy + sin(minAngle) * minLen
        canvas.drawLine(cx, cy, mx, my, minPaint)

        // Center Pin (monochromatic theme-friendly)
        val centerPinRadius = maxOf(3.5f * dp, r * 0.08f)
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
        }
        canvas.drawCircle(cx, cy, centerPinRadius, pinPaint)
        val pinCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surfaceLight
        }
        canvas.drawCircle(cx, cy, centerPinRadius * 0.45f, pinCenterPaint)
    }

    fun drawRetroLcdDisplay(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        timeStr: String,
        palette: NexusNeumorphicDraw.SoftPalette,
        dp: Float,
        customTypeface: Typeface? = null,
        isNeumorphic: Boolean = false,
        isGlass: Boolean = false
    ) {
        val box = RectF(left, top, left + width, top + height)
        val cornerRadius = minOf(14f * dp, height * 0.30f)

        if (isNeumorphic) {
            NexusNeumorphicDraw.drawDebossedWell(canvas, box, cornerRadius, palette, dp)
        } else {
            val wellColor = if (palette.isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wellColor }
            canvas.drawRoundRect(box, cornerRadius, cornerRadius, bgPaint)
        }

        val maxTextW = (width - 12f * dp).coerceAtLeast(10f)
        val targetSize = minOf(height * 0.52f, 36f * dp, maxTextW / 3.4f).coerceAtLeast(10f * dp)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            textSize = targetSize
            typeface = customTypeface ?: Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            letterSpacing = 0.06f
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }

        val cy = box.centerY() + targetSize * 0.35f
        canvas.drawText(timeStr, box.centerX(), cy, textPaint)
    }

    fun drawBauhausDial(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        hours: Float,
        minutes: Float,
        palette: NexusNeumorphicDraw.SoftPalette,
        dp: Float,
        isNeumorphic: Boolean = false,
        isGlass: Boolean = false
    ) {
        val r = radius.coerceAtLeast(10f)
        if (isNeumorphic) {
            val dialRect = RectF(cx - r, cy - r, cx + r, cy + r)
            NexusNeumorphicDraw.drawNeumorphicRoundButton(canvas, cx, cy, r, palette, dp)
        } else {
            val wellColor = if (palette.isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = wellColor }
            canvas.drawCircle(cx, cy, r, bgPaint)
        }

        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        // Minimalist Hour Markers (Bold bars at 12, 3, 6, 9)
        val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            strokeCap = Paint.Cap.SQUARE
            strokeWidth = 2.5f * dp
        }
        val minorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = secondaryColor
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 1.2f * dp
        }

        for (i in 0 until 12) {
            val isMajor = i % 3 == 0
            val angle = (Math.PI * 2 * (i / 12.0) - Math.PI / 2).toFloat()
            val len = if (isMajor) r * 0.16f else r * 0.08f
            val x1 = cx + cos(angle) * (r * 0.84f - len)
            val y1 = cy + sin(angle) * (r * 0.84f - len)
            val x2 = cx + cos(angle) * (r * 0.84f)
            val y2 = cy + sin(angle) * (r * 0.84f)
            canvas.drawLine(x1, y1, x2, y2, if (isMajor) majorPaint else minorPaint)
        }

        // Slender Bauhaus Baton Hands
        val hourAngle = (Math.PI * 2 * ((hours + minutes / 60f) / 12.0) - Math.PI / 2).toFloat()
        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            strokeCap = Paint.Cap.BUTT
            strokeWidth = 3f * dp
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        canvas.drawLine(cx, cy, cx + cos(hourAngle) * (r * 0.50f), cy + sin(hourAngle) * (r * 0.50f), hourPaint)

        val minAngle = (Math.PI * 2 * (minutes / 60.0) - Math.PI / 2).toFloat()
        val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.textPrimary
            strokeCap = Paint.Cap.BUTT
            strokeWidth = 2f * dp
            if (isGlass) {
                val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
            }
        }
        canvas.drawLine(cx, cy, cx + cos(minAngle) * (r * 0.72f), cy + sin(minAngle) * (r * 0.72f), minPaint)

        val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.textPrimary }
        canvas.drawCircle(cx, cy, 3f * dp, centerDot)
    }

    fun drawBoldTypoDigital(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        hoursStr: String,
        minutesStr: String,
        palette: NexusNeumorphicDraw.SoftPalette,
        dp: Float,
        customTypeface: Typeface? = null,
        availHeight: Float = 90f * dp,
        availWidth: Float = 90f * dp,
        isGlass: Boolean = false
    ) {
        val tf = customTypeface ?: com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        if (availHeight < 56f * dp) {
            // Horizontal inline format for shallow heights
            val targetSize = minOf(availHeight * 0.65f, availWidth * 0.22f, 28f * dp).coerceAtLeast(11f * dp)
            val inlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = targetSize
                typeface = tf
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            val text = "$hoursStr : $minutesStr"
            val textY = cy + targetSize * 0.35f
            canvas.drawText(text, cx, textY, inlinePaint)
        } else {
            // Stacked digital format
            val targetSize = minOf(availHeight * 0.44f, availWidth * 0.65f, 38f * dp).coerceAtLeast(12f * dp)
            val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.textPrimary
                textSize = targetSize
                typeface = tf
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }
            val mPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = secondaryColor
                textSize = targetSize
                typeface = tf
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadow = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadow)
                }
            }

            canvas.drawText(hoursStr, cx, cy - targetSize * 0.08f, hPaint)
            canvas.drawText(minutesStr, cx, cy + targetSize * 0.88f, mPaint)
        }
    }
}
