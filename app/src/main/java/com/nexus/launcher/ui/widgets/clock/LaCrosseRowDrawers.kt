package com.nexus.launcher.ui.widgets.clock

import android.content.Context
import android.graphics.Canvas
import com.nexus.launcher.R
import java.util.Calendar

/**
 * Handles rendering of the top row (Time, AM/PM, status icons) and bottom column fields
 * (silkscreen labels, 7-segment digits, 14-segment letters) for the La Crosse LCD clock.
 */
object LaCrosseRowDrawers {

    fun drawTopRow(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        dp: Float,
        cal: Calendar,
        cfg: LaCrosseClockConfig,
        isColonOn: Boolean
    ) {
        val details = LaCrosseClockTheme.formatTimeDetails(context, cal, cfg.timeFormat)
        val hasAmPm = details.is12h && cfg.showAmPm
        val hasStatus = true

        val maxTimeW = when {
            hasAmPm && hasStatus -> w * 0.58f
            hasAmPm || hasStatus -> w * 0.72f
            else -> w * 0.88f
        }
        val maxDigitHByW = maxTimeW / 2.41f
        val digitH = minOf(h * 0.76f, maxDigitHByW).coerceAtLeast(14f * dp)
        val digitW = digitH * 0.50f
        val colonW = digitW * 0.28f
        val gap = digitW * 0.14f

        val timeW = digitW * 4 + colonW + gap * 4
        val timeX = x + (w - timeW) / 2f
        val timeY = y + (h - digitH) / 2f

        // Draw AM/PM on the left if in 12-hour mode and enabled
        if (hasAmPm) {
            val amLabel = context.getString(R.string.lacrosse_label_am)
            val pmLabel = context.getString(R.string.lacrosse_label_pm)
            val labelText = if (details.isAm) amLabel else pmLabel
            val amPmSize = (digitH * 0.22f).coerceAtLeast(8f * dp)
            LaCrosseSegmentDraw.silkscreenPaint.textSize = amPmSize
            val amX = (x + timeX) / 2f
            val amY = timeY + digitH * 0.40f
            canvas.drawText(labelText, amX, amY, LaCrosseSegmentDraw.silkscreenPaint)
        }

        // Draw Time Digits if enabled
        if (cfg.showTime) {
            var curX = timeX
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.hours[0], curX, timeY, digitW, digitH, cfg.showGhostSegments)
            curX += digitW + gap
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.hours[1], curX, timeY, digitW, digitH, cfg.showGhostSegments)
            curX += digitW + gap
            LaCrosseSegmentDraw.drawColon(canvas, curX, timeY, colonW, digitH, isColonOn, cfg.showGhostSegments)
            curX += colonW + gap
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.minutes[0], curX, timeY, digitW, digitH, cfg.showGhostSegments)
            curX += digitW + gap
            LaCrosseSegmentDraw.draw7SegmentDigit(canvas, details.minutes[1], curX, timeY, digitW, digitH, cfg.showGhostSegments)
        }

        // Draw Status Icons on the right if enabled
        if (hasStatus) {
            val rightSpace = (x + w) - (timeX + timeW)
            val iconSize = minOf(digitH * 0.26f, rightSpace * 0.40f).coerceAtLeast(8f * dp)
            val iconTotalW = iconSize * 2.1f
            val iconX = (timeX + timeW) + (rightSpace - iconTotalW) / 2f
            val iconY = timeY + digitH * 0.18f
            LaCrosseSegmentDraw.drawStatusIcons(canvas, iconX, iconY, iconSize)
        }
    }

    fun drawColumnField(
        canvas: Canvas,
        label: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        isAlphanumeric: Boolean,
        text: String,
        showGhost: Boolean,
        dp: Float
    ) {
        // Silkscreen label at top with width containment
        val maxLabelSize = (w * 0.88f) / (label.length.coerceAtLeast(1) * 0.65f)
        val labelSize = minOf(h * 0.22f, maxLabelSize).coerceIn(7f * dp, 12f * dp)
        LaCrosseSegmentDraw.silkscreenPaint.textSize = labelSize
        canvas.drawText(label, x + w / 2f, y + labelSize, LaCrosseSegmentDraw.silkscreenPaint)

        // Value digits below label with character width auto-scaling
        val valTop = y + labelSize + 2f * dp
        val valH = (h - (labelSize + 4f * dp)).coerceAtLeast(8f * dp)
        val cleanText = text.trim()
        val charCount = cleanText.length.coerceAtLeast(1)
        val maxCharW = (w * 0.88f) / (charCount + (charCount - 1) * 0.12f)
        val charW = minOf(valH * 0.50f, maxCharW).coerceAtLeast(4f * dp)
        val gap = charW * 0.12f

        val totalCharsW = cleanText.length * charW + (cleanText.length - 1).coerceAtLeast(0) * gap
        var curX = x + (w - totalCharsW) / 2f

        for (c in cleanText) {
            if (c == '°') {
                LaCrosseSegmentDraw.drawDegreeSymbol(canvas, curX, valTop, charW * 0.6f, valH * 0.6f)
                curX += charW * 0.6f + gap
            } else if (isAlphanumeric) {
                LaCrosseSegmentDraw.draw14SegmentChar(canvas, c, curX, valTop, charW, valH, showGhost)
                curX += charW + gap
            } else {
                LaCrosseSegmentDraw.draw7SegmentDigit(canvas, c, curX, valTop, charW, valH, showGhost)
                curX += charW + gap
            }
        }
    }
}
