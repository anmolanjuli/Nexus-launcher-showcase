package com.nexus.launcher.ui.widgets.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextUtils
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.util.Locale

/**
 * Modular drawer for Battery Widget Style 4: Power Monitor (Telemetry Dashboard).
 * Re-imagines battery usage stats with squircle icon wells, micro consumption bars,
 * and high typographic hierarchy.
 *
 * All Paint, Rect, and RectF instances are class fields (zero per-frame allocations).
 */
object NexusBatteryHogsDrawer {

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ellipsizeTextPaint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)

    private val iconBounds = Rect()
    private val wellRect = RectF()
    private val barTrackRect = RectF()
    private val barFillRect = RectF()
    private val badgeRect = RectF()

    fun drawPowerMonitor(
        context: Context,
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        dp: Float,
        percent: Int,
        consumers: List<BatteryConsumerApp>,
        hasUsagePermission: Boolean,
        tokens: NexusColorTokens,
        config: NexusWidgetConfig.InstanceConfig,
        locale: Locale,
        isGlass: Boolean,
        isLight: Boolean
    ) {
        val padX = 8f * dp
        val headerH = (height * 0.16f).coerceIn(16f * dp, 22f * dp)
        val headerY = top + headerH * 0.75f

        // 1. Header Title
        primaryTextPaint.apply {
            color = tokens.textPrimary
            textSize = (height * 0.09f).coerceIn(11f * dp, 14f * dp)
            typeface = NexusBatteryStyleDrawers.getTypeface(context, config, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            NexusBatteryStyleDrawers.applyLegibility(this, isGlass, isLight, dp)
        }
        val headerText = context.getString(R.string.battery_hogs_header)
        canvas.drawText(headerText, left + padX, headerY, primaryTextPaint)

        // 2. Right-aligned Battery % Badge
        secondaryTextPaint.apply {
            color = tokens.textPrimary
            textSize = (height * 0.085f).coerceIn(10f * dp, 13f * dp)
            typeface = NexusBatteryStyleDrawers.getTypeface(context, config, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            NexusBatteryStyleDrawers.applyLegibility(this, isGlass, isLight, dp)
        }
        val pctBadge = "${LocaleDigitUtils.formatNumber(percent, locale)}%"
        val pctBadgeW = secondaryTextPaint.measureText(pctBadge)
        val badgePadX = 6f * dp
        val badgePadY = 3f * dp
        val badgeRight = left + width - padX
        badgeRect.set(
            badgeRight - pctBadgeW - badgePadX * 2,
            headerY - secondaryTextPaint.textSize - badgePadY,
            badgeRight,
            headerY + badgePadY
        )
        wellPaint.color = if (isGlass) {
            if (isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
        } else {
            tokens.surfaceRaised
        }
        canvas.drawRoundRect(badgeRect, 6f * dp, 6f * dp, wellPaint)
        canvas.drawText(pctBadge, badgeRight - badgePadX, headerY, secondaryTextPaint)

        // 3. Delicate hairline divider
        val divY = headerY + 8f * dp
        dividerPaint.apply {
            color = if (isGlass) {
                if (isLight) Color.argb(35, 0, 0, 0) else Color.argb(45, 255, 255, 255)
            } else {
                tokens.divider
            }
            strokeWidth = 1f * dp
        }
        canvas.drawLine(left + padX, divY, left + width - padX, divY, dividerPaint)

        val remainingH = (top + height) - divY - 6f * dp

        // 4. Permission Prompt
        if (!hasUsagePermission) {
            drawPermissionPrompt(context, canvas, left, divY, width, remainingH, dp, tokens, config, isGlass, isLight)
            return
        }

        // 5. Empty State
        if (consumers.isEmpty()) {
            secondaryTextPaint.apply {
                color = tokens.textSecondary
                textSize = (height * 0.08f).coerceIn(10f * dp, 12f * dp)
                typeface = NexusBatteryStyleDrawers.getTypeface(context, config, android.graphics.Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                NexusBatteryStyleDrawers.applyLegibility(this, isGlass, isLight, dp)
            }
            val emptyStr = context.getString(R.string.battery_no_hogs_data)
            val pY = divY + remainingH / 2f + secondaryTextPaint.textSize * 0.35f
            canvas.drawText(emptyStr, left + width / 2f, pY, secondaryTextPaint)
            return
        }

        // 6. Consumer Rows (Top 2-3 apps)
        val rows = consumers.take(if (remainingH < 80f * dp) 2 else 3)
        val rowH = remainingH / rows.size.toFloat()

        rows.forEachIndexed { i, app ->
            val rowCenterY = divY + (i + 0.5f) * rowH
            val iconSize = (rowH * 0.52f).coerceIn(16f * dp, 24f * dp)
            val wellSize = iconSize + 4f * dp
            val wellLeft = left + padX
            val wellTop = rowCenterY - wellSize / 2f

            // Squircle well behind app icon
            wellRect.set(wellLeft, wellTop, wellLeft + wellSize, wellTop + wellSize)
            wellPaint.color = if (isGlass) {
                if (isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
            } else {
                tokens.surfaceRaised
            }
            canvas.drawRoundRect(wellRect, 6f * dp, 6f * dp, wellPaint)

            // Draw app icon inside well
            val iconPad = 2f * dp
            val iconL = (wellLeft + iconPad).toInt()
            val iconT = (wellTop + iconPad).toInt()
            val iconR = (wellLeft + wellSize - iconPad).toInt()
            val iconB = (wellTop + wellSize - iconPad).toInt()

            val drawable = app.iconDrawable
            if (drawable != null) {
                iconBounds.set(iconL, iconT, iconR, iconB)
                drawable.bounds = iconBounds
                drawable.draw(canvas)
            } else {
                barFillPaint.color = tokens.textSecondary
                canvas.drawCircle(wellRect.centerX(), wellRect.centerY(), iconSize * 0.35f, barFillPaint)
            }

            // Right-aligned percentage
            val pctStr = com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(app.percentOfDrain, context)
            secondaryTextPaint.apply {
                color = tokens.textPrimary
                textSize = (rowH * 0.32f).coerceIn(10f * dp, 13f * dp)
                typeface = NexusBatteryStyleDrawers.getTypeface(context, config, android.graphics.Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                NexusBatteryStyleDrawers.applyLegibility(this, isGlass, isLight, dp)
            }
            val pctW = secondaryTextPaint.measureText(pctStr)
            val pctBaseline = rowCenterY - (2f * dp)
            canvas.drawText(pctStr, left + width - padX, pctBaseline, secondaryTextPaint)

            // App Label
            val textLeft = wellLeft + wellSize + (8f * dp)
            val maxLabelW = (left + width - padX - pctW - 10f * dp) - textLeft
            primaryTextPaint.apply {
                color = tokens.textPrimary
                textSize = (rowH * 0.32f).coerceIn(10f * dp, 13f * dp)
                typeface = NexusBatteryStyleDrawers.getTypeface(context, config, android.graphics.Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                NexusBatteryStyleDrawers.applyLegibility(this, isGlass, isLight, dp)
            }
            ellipsizeTextPaint.set(primaryTextPaint)
            val label = if (primaryTextPaint.measureText(app.appLabel) > maxLabelW) {
                TextUtils.ellipsize(app.appLabel, ellipsizeTextPaint, maxLabelW, TextUtils.TruncateAt.END).toString()
            } else {
                app.appLabel
            }
            canvas.drawText(label, textLeft, pctBaseline, primaryTextPaint)

            // Sleek Micro Progress Track below the label
            val trackY = pctBaseline + (5f * dp)
            val trackH = 3f * dp
            val trackW = (maxLabelW + pctW).coerceAtLeast(20f * dp)
            barTrackRect.set(textLeft, trackY, textLeft + trackW, trackY + trackH)
            barTrackPaint.color = if (isGlass) {
                if (isLight) Color.argb(30, 0, 0, 0) else Color.argb(40, 255, 255, 255)
            } else {
                tokens.divider
            }
            canvas.drawRoundRect(barTrackRect, trackH / 2f, trackH / 2f, barTrackPaint)

            // Proportional drain fill
            val fillW = (trackW * (app.percentOfDrain.coerceIn(1, 100) / 100f)).coerceAtLeast(trackH)
            barFillRect.set(textLeft, trackY, textLeft + fillW, trackY + trackH)
            barFillPaint.color = tokens.textPrimary
            canvas.drawRoundRect(barFillRect, trackH / 2f, trackH / 2f, barFillPaint)
        }
    }

    private fun drawPermissionPrompt(
        context: Context, canvas: Canvas, left: Float, divY: Float, width: Float,
        remainingH: Float, dp: Float, tokens: NexusColorTokens,
        config: NexusWidgetConfig.InstanceConfig, isGlass: Boolean, isLight: Boolean
    ) {
        val promptText = context.getString(R.string.battery_enable_usage_access)
        secondaryTextPaint.apply {
            color = tokens.textPrimary
            textSize = 11f * dp
            typeface = NexusBatteryStyleDrawers.getTypeface(context, config, android.graphics.Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            NexusBatteryStyleDrawers.applyLegibility(this, isGlass, isLight, dp)
        }
        val textW = secondaryTextPaint.measureText(promptText)
        val btnW = textW + 24f * dp
        val btnH = 28f * dp
        val btnX = left + (width - btnW) / 2f
        val btnY = divY + (remainingH - btnH) / 2f

        wellRect.set(btnX, btnY, btnX + btnW, btnY + btnH)
        wellPaint.color = if (isGlass) {
            if (isLight) Color.argb(25, 0, 0, 0) else Color.argb(35, 255, 255, 255)
        } else {
            tokens.surfaceRaised
        }
        canvas.drawRoundRect(wellRect, btnH / 2f, btnH / 2f, wellPaint)

        dividerPaint.apply {
            color = tokens.textPrimary
            strokeWidth = 1.25f * dp
        }
        canvas.drawRoundRect(wellRect, btnH / 2f, btnH / 2f, dividerPaint)

        val textY = btnY + btnH / 2f + secondaryTextPaint.textSize * 0.35f
        canvas.drawText(promptText, left + width / 2f, textY, secondaryTextPaint)
    }
}
