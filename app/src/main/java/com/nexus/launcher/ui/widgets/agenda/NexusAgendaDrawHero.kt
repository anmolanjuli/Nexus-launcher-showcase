package com.nexus.launcher.ui.widgets.agenda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.calendar.CalendarEvent

/**
 * Draws the Agenda Hero layout (2x1) and empty state banner with custom typography and countdown.
 */
class NexusAgendaDrawHero {

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(
        context: Context,
        canvas: Canvas,
        w: Int,
        h: Int,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig,
        palette: NexusNeumorphicDraw.SoftPalette?,
        tokens: NexusColorTokens,
        events: List<CalendarEvent>,
        formatter: AgendaDateFormatter,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, w.toFloat(), h.toFloat(), dp)
        val left = insets.left
        val right = w - insets.right
        val top = insets.top
        val bottom = h - insets.bottom
        val availableW = right - left
        val availableH = bottom - top

        val heroEvent = events.firstOrNull()

        if (heroEvent == null) {
            drawEmptyState(context, canvas, left, top, availableW, availableH, dp, palette, tokens, formatter, regularTf, boldTf)
            return
        }

        val cy = top + availableH / 2f
        val dotRadius = minOf(6f * dp, availableH * 0.12f)
        val dotX = left + dotRadius + 4f * dp
        val dotY = cy

        // Draw Accent Dot
        dotPaint.style = Paint.Style.FILL
        dotPaint.color = if (heroEvent.color != 0) heroEvent.color else tokens.accent
        canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)

        val contentLeft = dotX + dotRadius + 8f * dp

        // Determine Right Badge / Time string
        val isToday = AgendaEventFilter.isToday(heroEvent)
        val rightText = if (isToday) {
            if (config.showAgendaCountdown) formatter.formatRelative(context, heroEvent) else formatter.formatTime(context, heroEvent)
        } else {
            val dayPrefix = formatter.formatEventDayPrefix(context, heroEvent, config.showAgendaCountdown)
            val time = formatter.formatTime(context, heroEvent)
            if (time.isNotEmpty() && !config.showAgendaCountdown) "$dayPrefix • $time" else dayPrefix
        }

        badgeTextPaint.apply {
            color = palette?.textSecondary ?: tokens.textSecondary
            textSize = minOf(13f * dp, availableH * 0.28f)
            typeface = boldTf
            textAlign = Paint.Align.RIGHT
        }
        val rightTextW = if (rightText.isNotEmpty()) badgeTextPaint.measureText(rightText) else 0f
        val rightTextX = right - 4f * dp

        if (rightText.isNotEmpty()) {
            val fontMetrics = badgeTextPaint.fontMetrics
            val textBaseY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(rightText, rightTextX, textBaseY, badgeTextPaint)
        }

        // Draw Title (Ellipsized to fit between dot and badge)
        val maxTitleW = (if (rightTextW > 0f) rightTextX - rightTextW - 12f * dp else rightTextX) - contentLeft
        if (maxTitleW > 20f * dp) {
            titlePaint.apply {
                color = palette?.textPrimary ?: tokens.textPrimary
                textSize = minOf(16f * dp, availableH * 0.35f)
                typeface = boldTf
                textAlign = Paint.Align.LEFT
            }
            val ellipsizedTitle = TextUtils.ellipsize(heroEvent.title, android.text.TextPaint(titlePaint), maxTitleW, TextUtils.TruncateAt.END).toString()
            val titleMetrics = titlePaint.fontMetrics
            val titleY = cy - (titleMetrics.ascent + titleMetrics.descent) / 2f
            canvas.drawText(ellipsizedTitle, contentLeft, titleY, titlePaint)
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
        formatter: AgendaDateFormatter,
        regularTf: Typeface,
        boldTf: Typeface
    ) {
        val cy = top + availableH / 2f
        val cx = left + availableW / 2f

        titlePaint.apply {
            color = palette?.textPrimary ?: tokens.textPrimary
            textSize = minOf(15f * dp, availableH * 0.32f)
            typeface = boldTf
            textAlign = Paint.Align.CENTER
        }
        subPaint.apply {
            color = palette?.textSecondary ?: tokens.textSecondary
            textSize = minOf(12f * dp, availableH * 0.24f)
            typeface = regularTf
            textAlign = Paint.Align.CENTER
        }

        val titleText = context.getString(R.string.agenda_no_events)
        val dateText = formatter.formatCompactDate()

        canvas.drawText(titleText, cx, cy - 2f * dp, titlePaint)
        canvas.drawText(dateText, cx, cy + 16f * dp, subPaint)
    }
}
