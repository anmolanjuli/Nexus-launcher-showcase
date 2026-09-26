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
import java.text.SimpleDateFormat

/** Renders the expanded 4x2 layout for the Notes widget card. */
internal class NexusNotes4x2Renderer {

    private val paperWellRect = RectF()
    private val chipRect = RectF()
    private val statusRect = RectF()
    private val chipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val statusTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
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
        boldTf: Typeface,
        timeFormat: SimpleDateFormat,
        drawStripe: (Canvas, Float, Float, Float, Float, Float, Int, NoteData) -> Unit
    ) {
        val urgencyColor = note.getUrgencyColor(tokens)
        val statusColor = note.getStatusColor(tokens)

        // 1. Soft UI Paper Well (if Neumorphic)
        if (palette != null) {
            paperWellRect.set(left - 2f * dp, top - 2f * dp, right + 2f * dp, bottom + 2f * dp)
            NexusNeumorphicDraw.drawDebossedWell(canvas, paperWellRect, 12f * dp, palette, dp)
        }

        // 2. Left Two-Color Progress Stripe
        drawStripe(canvas, left + 2f * dp, top + 4f * dp, left + 6f * dp, bottom - 4f * dp, dp, urgencyColor, note)

        var currentY = top + 8f * dp
        val contentLeft = left + 16f * dp
        val headerRight = right - 8f * dp

        // 3. Top Header: Urgency Chip (Row 1) & Status Chip (Row 2) on Top Right
        val showUrgency = config.showNoteUrgencyLabel
        var chipMaxW = 0f

        if (showUrgency) {
            val urgencyText = note.getUrgencyLabel(context)
            chipTextPaint.apply {
                color = urgencyColor
                textSize = 10.5f * dp
                typeface = boldTf
                textAlign = Paint.Align.CENTER
            }
            val textW = chipTextPaint.measureText(urgencyText)
            val chipW = textW + 12f * dp
            val chipH = 18f * dp
            val chipLeft = headerRight - chipW
            val chipTop = currentY

            chipBgPaint.color = (urgencyColor and 0x00FFFFFF) or 0x22000000
            chipRect.set(chipLeft, chipTop, headerRight, chipTop + chipH)
            canvas.drawRoundRect(chipRect, 9f * dp, 9f * dp, chipBgPaint)

            val textY = chipTop + chipH / 2f + 3.5f * dp
            canvas.drawText(urgencyText, chipLeft + chipW / 2f, textY, chipTextPaint)

            chipMaxW = maxOf(chipMaxW, chipW)
        }

        val statusText = note.getStatusLabel(context).uppercase()
        val statusTop = if (showUrgency) currentY + 22f * dp else currentY
        statusTextPaint.apply {
            color = statusColor
            textSize = 10f * dp
            typeface = boldTf
            textAlign = Paint.Align.CENTER
        }
        val sTextW = statusTextPaint.measureText(statusText)
        val sChipW = sTextW + 12f * dp
        val sChipH = 18f * dp
        val sChipLeft = headerRight - sChipW

        statusBgPaint.color = (statusColor and 0x00FFFFFF) or 0x22000000
        statusRect.set(sChipLeft, statusTop, headerRight, statusTop + sChipH)
        canvas.drawRoundRect(statusRect, 9f * dp, 9f * dp, statusBgPaint)

        val sTextY = statusTop + sChipH / 2f + 3.5f * dp
        canvas.drawText(statusText, sChipLeft + sChipW / 2f, sTextY, statusTextPaint)

        chipMaxW = maxOf(chipMaxW, sChipW)

        // Draw Title
        val hasTitle = config.showNoteTitle && note.title.isNotBlank()
        val titleRight = headerRight - chipMaxW - 8f * dp
        val availableTitleW = titleRight - contentLeft

        if (hasTitle && availableTitleW > 20f * dp) {
            titlePaint.apply {
                color = palette?.textPrimary ?: tokens.textPrimary
                textSize = 16f * dp
                typeface = boldTf
                textAlign = Paint.Align.LEFT
            }
            val titleText = TextUtils.ellipsize(note.title, android.text.TextPaint(titlePaint), availableTitleW, TextUtils.TruncateAt.END).toString()
            canvas.drawText(titleText, contentLeft, currentY + 14f * dp, titlePaint)
            currentY += if (showUrgency) 44f * dp else 24f * dp
        } else {
            currentY += if (showUrgency) 44f * dp else 22f * dp
        }

        // 4. Edited Timestamp on Bottom Right
        var contentBottom = bottom - 6f * dp
        if (note.updatedAt > 0L) {
            val timeStr = timeFormat.format(note.updatedAt)
            val editedStr = String.format(context.getString(R.string.notes_edited_time), timeStr)
            timePaint.apply {
                color = palette?.textSecondary ?: tokens.textSecondary
                textSize = 10.5f * dp
                typeface = regularTf
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(editedStr, right - 8f * dp, bottom - 4f * dp, timePaint)
            contentBottom = bottom - 18f * dp
        }

        // 5. Multiline Body Text
        val availableW = right - contentLeft - 8f * dp
        val remainingH = contentBottom - currentY
        val lineHeight = 18f * dp
        val maxLines = (remainingH / lineHeight).toInt().coerceIn(1, 8)

        bodyPaint.apply {
            color = palette?.textPrimary ?: tokens.textPrimary
            textSize = 13.5f * dp
            typeface = regularTf
            textAlign = Paint.Align.LEFT
        }

        val lines = NotesTextLayout.layoutText(note.body, bodyPaint, availableW, maxLines)
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, contentLeft, currentY + (i + 1) * lineHeight - 3f * dp, bodyPaint)
        }
    }
}
