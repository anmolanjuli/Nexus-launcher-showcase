package com.nexus.launcher.ui.widgets.notes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Draws adaptive size layouts for the Notes widget:
 * 2x1, 2x2, 4x2, and empty states.
 * Urgency bar is replaced by Green completion fill as progress increases.
 * Urgency and Status chips are displayed in two distinct rows.
 */
class NexusNotesDrawCard {

    private val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripeProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val chipRect = RectF()
    private val statusRect = RectF()
    private val stripeRect = RectF()
    private val stripeFillRect = RectF()

    /** Follows the device's 12/24-hour setting and the locale's own time pattern. */
    private fun timeFormat(context: Context): SimpleDateFormat =
        android.text.format.DateFormat.getTimeFormat(context) as? SimpleDateFormat
            ?: SimpleDateFormat("h:mm a", Locale.getDefault())
    private val renderer4x2 = NexusNotes4x2Renderer()

    fun draw(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: NexusNeumorphicDraw.SoftPalette?,
        tokens: NexusColorTokens,
        note: NoteData,
        regularTf: Typeface,
        boldTf: Typeface,
        isExpanded: Boolean // 4x2 density
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val left = insets.left
        val right = w - insets.right
        val top = insets.top
        val bottom = h - insets.bottom
        val availableW = right - left
        val availableH = bottom - top

        if (note.isEmpty()) {
            drawEmptyState(context, canvas, left, top, availableW, availableH, dp, palette, tokens, boldTf)
            return
        }

        val hDp = availableH / dp
        when {
            hDp < 75f -> draw2x1(context, canvas, left, top, right, bottom, dp, tokens, note, regularTf)
            isExpanded -> renderer4x2.draw(
                context, canvas, left, top, right, bottom, dp, config, palette, tokens, note, regularTf, boldTf, timeFormat(context),
                ::drawTwoColorProgressStripe
            )
            else -> draw2x2(context, canvas, left, top, right, bottom, dp, config, palette, tokens, note, regularTf, boldTf)
        }
    }

    private fun drawTwoColorProgressStripe(
        canvas: Canvas,
        sLeft: Float,
        sTop: Float,
        sRight: Float,
        sBottom: Float,
        dp: Float,
        urgencyColor: Int,
        note: NoteData
    ) {
        // 1. Base Urgency Bar (Solid)
        stripeRect.set(sLeft, sTop, sRight, sBottom)
        stripePaint.color = urgencyColor
        canvas.drawRoundRect(stripeRect, 2f * dp, 2f * dp, stripePaint)

        // 2. Green Completion Bar filling upwards from bottom
        val fillRatio = if (note.isDone()) 1f else (note.progress / 100f).coerceIn(0f, 1f)
        if (fillRatio > 0f) {
            val totalH = sBottom - sTop
            val fillTop = sBottom - totalH * fillRatio
            stripeFillRect.set(sLeft, fillTop, sRight, sBottom)
            stripeProgressPaint.color = NoteData.COLOR_DONE
            canvas.drawRoundRect(stripeFillRect, 2f * dp, 2f * dp, stripeProgressPaint)
        }
    }

    private fun draw2x1(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        dp: Float,
        tokens: NexusColorTokens,
        note: NoteData,
        regularTf: Typeface
    ) {
        val urgencyColor = note.getUrgencyColor(tokens)

        // 1. Left Two-Color Progress Stripe
        drawTwoColorProgressStripe(canvas, left, top + 2f * dp, left + 4f * dp, bottom - 2f * dp, dp, urgencyColor, note)

        val contentLeft = left + 12f * dp
        val cy = top + (bottom - top) / 2f

        // 2. Body Text
        bodyPaint.apply {
            color = tokens.textPrimary
            textSize = 13.5f * dp
            typeface = regularTf
            textAlign = Paint.Align.LEFT
        }

        val maxW = right - 4f * dp - contentLeft
        val displayStr = if (note.title.isNotBlank()) "${note.title} • ${note.body}" else note.body
        val lines = NotesTextLayout.layoutText(displayStr, bodyPaint, maxW, 2)

        if (lines.size == 1) {
            val metrics = bodyPaint.fontMetrics
            val textY = cy - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(lines[0], contentLeft, textY, bodyPaint)
        } else if (lines.size >= 2) {
            val lineHeight = 16f * dp
            val startY = cy - lineHeight / 2f + 4f * dp
            canvas.drawText(lines[0], contentLeft, startY, bodyPaint)
            canvas.drawText(lines[1], contentLeft, startY + lineHeight, bodyPaint)
        }
    }

    private fun draw2x2(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: NexusNeumorphicDraw.SoftPalette?,
        tokens: NexusColorTokens,
        note: NoteData,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        val urgencyColor = note.getUrgencyColor(tokens)
        val statusColor = note.getStatusColor(tokens)

        // 1. Left Two-Color Progress Stripe
        drawTwoColorProgressStripe(canvas, left, top + 2f * dp, left + 4f * dp, bottom - 2f * dp, dp, urgencyColor, note)

        var currentY = top + 4f * dp
        val contentLeft = left + 12f * dp
        val headerRight = right - 4f * dp

        // 2. Top-Right Chips in TWO Rows: Urgency (Row 1), Status (Row 2 below)
        val showUrgency = config.showNoteUrgencyLabel && note.urgency != NoteData.URGENCY_LATER
        var chipMaxW = 0f

        // Row 1: Urgency Chip
        if (showUrgency) {
            val urgencyText = note.getUrgencyLabel(context)
            chipTextPaint.apply {
                color = urgencyColor
                textSize = 10f * dp
                typeface = boldTf
                textAlign = Paint.Align.CENTER
            }
            val textW = chipTextPaint.measureText(urgencyText)
            val chipW = textW + 10f * dp
            val chipH = 16f * dp
            val chipLeft = headerRight - chipW
            val chipTop = currentY

            chipBgPaint.color = (urgencyColor and 0x00FFFFFF) or 0x22000000
            chipRect.set(chipLeft, chipTop, headerRight, chipTop + chipH)
            canvas.drawRoundRect(chipRect, 8f * dp, 8f * dp, chipBgPaint)

            val textY = chipTop + chipH / 2f + 3.2f * dp
            canvas.drawText(urgencyText, chipLeft + chipW / 2f, textY, chipTextPaint)

            chipMaxW = maxOf(chipMaxW, chipW)
        }

        // Row 2: Status Chip
        val statusText = note.getStatusLabel(context).uppercase()
        val statusTop = if (showUrgency) currentY + 19f * dp else currentY
        statusTextPaint.apply {
            color = statusColor
            textSize = 9.5f * dp
            typeface = boldTf
            textAlign = Paint.Align.CENTER
        }
        val sTextW = statusTextPaint.measureText(statusText)
        val sChipW = sTextW + 10f * dp
        val sChipH = 16f * dp
        val sChipLeft = headerRight - sChipW

        statusBgPaint.color = (statusColor and 0x00FFFFFF) or 0x22000000
        statusRect.set(sChipLeft, statusTop, headerRight, statusTop + sChipH)
        canvas.drawRoundRect(statusRect, 8f * dp, 8f * dp, statusBgPaint)

        val sTextY = statusTop + sChipH / 2f + 3.2f * dp
        canvas.drawText(statusText, sChipLeft + sChipW / 2f, sTextY, statusTextPaint)

        chipMaxW = maxOf(chipMaxW, sChipW)

        // Draw Title (if present and enabled)
        val hasTitle = config.showNoteTitle && note.title.isNotBlank()
        val titleRight = headerRight - chipMaxW - 6f * dp
        val availableTitleW = titleRight - contentLeft

        if (hasTitle && availableTitleW > 20f * dp) {
            titlePaint.apply {
                color = palette?.textPrimary ?: tokens.textPrimary
                textSize = 15f * dp
                typeface = boldTf
                textAlign = Paint.Align.LEFT
            }
            val titleText = TextUtils.ellipsize(note.title, android.text.TextPaint(titlePaint), availableTitleW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(titleText, contentLeft, currentY + 13f * dp, titlePaint)
            currentY += if (showUrgency) 38f * dp else 22f * dp
        } else {
            currentY += if (showUrgency) 38f * dp else 20f * dp
        }

        // 3. Body Text (Multiline wrapped)
        val availableW = right - contentLeft - 4f * dp
        val remainingH = bottom - currentY - 4f * dp
        val lineHeight = 16.5f * dp
        val maxLines = (remainingH / lineHeight).toInt().coerceIn(1, 6)

        bodyPaint.apply {
            color = palette?.textPrimary ?: tokens.textPrimary
            textSize = 13f * dp
            typeface = regularTf
            textAlign = Paint.Align.LEFT
        }

        val lines = NotesTextLayout.layoutText(note.body, bodyPaint, availableW, maxLines)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, contentLeft, currentY + (i + 1) * lineHeight - 3f * dp, bodyPaint)
        }
    }

    private fun drawEmptyState(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        availableW: Float,
        availableH: Float,
        dp: Float,
        palette: NexusNeumorphicDraw.SoftPalette?,
        tokens: NexusColorTokens,
        boldTf: Typeface
    ) {
        val cx = left + availableW / 2f
        val cy = top + availableH / 2f

        emptyPaint.apply {
            color = palette?.textSecondary ?: tokens.textSecondary
            textSize = 14f * dp
            typeface = boldTf
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText(context.getString(R.string.notes_empty_tap_to_add), cx, cy + 5f * dp, emptyPaint)
    }
}
