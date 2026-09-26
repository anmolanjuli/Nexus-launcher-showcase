package com.nexus.launcher.ui.widgets.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.util.Locale

/**
 * Modular drawers for specialized gauge battery styles:
 * - Style 2: Radial Gauge & Device Silhouette
 * - Style 3: Tech Matrix / Segmented Cell
 *
 * All Paint, Path, and RectF instances are class fields (zero per-frame allocations).
 */
object NexusBatteryGaugeDrawers {

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val solidFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val drawRect = RectF()
    private val fillRect = RectF()
    private val wellRect = RectF()
    private val cellRect = RectF()

    // -------------------------------------------------------------------------
    // STYLE 2: Radial Gauge & Device Silhouette
    // -------------------------------------------------------------------------
    /**
     * Three layouts by shape, each built from boxes that cannot overlap:
     * - **Wide**: gauge on the left at full height, reading and status beside it.
     * - **Roomy** (square or tall, with space): gauge top-left, reading bottom-left — the reading
     *   is fitted into the space *below* the gauge. It used to be sized off the widget and placed
     *   at a fixed offset from the bottom, which at around 100dp tall pushed it into the ring;
     *   the ring's stroke then cut the "1" and "100%" read as "!00%".
     * - **Compact**: the gauge fills the widget and the reading sits inside it. This size used to
     *   show the ring alone, with no number at all.
     */
    fun drawRadialGauge(
        context: Context, canvas: Canvas, left: Float, top: Float, width: Float, height: Float,
        dp: Float, percent: Int, isCharging: Boolean, statusText: String, tokens: NexusColorTokens,
        config: NexusWidgetConfig.InstanceConfig, locale: Locale, isGlass: Boolean, isLight: Boolean,
        markColor: Int
    ) {
        val stateColor = if (percent < 20) tokens.danger else markColor
        val pad = (minOf(width, height) * 0.08f).coerceIn(6f * dp, 12f * dp)
        val digits = LocaleDigitUtils.formatNumber(percent, locale)
        primaryTextPaint.apply {
            color = tokens.textPrimary
            typeface = NexusBatteryDrawUtils.getTypeface(context, config, Typeface.BOLD)
            letterSpacing = -0.02f
            NexusBatteryDrawUtils.applyLegibility(this, isGlass, isLight, dp)
        }
        secondaryTextPaint.apply {
            color = tokens.textSecondary
            typeface = NexusBatteryDrawUtils.getTypeface(context, config, Typeface.NORMAL)
            letterSpacing = 0.04f
            textAlign = Paint.Align.LEFT
            NexusBatteryDrawUtils.applyLegibility(this, isGlass, isLight, dp)
        }

        val isWide = width >= height * 1.35f
        val isRoomy = !isWide && minOf(width, height) >= 118f * dp

        when {
            isWide -> {
                val gaugeSize = height - pad * 2f
                val gR = gaugeSize / 2f
                drawGauge(canvas, left + pad + gR, top + pad + gR, gR, dp, percent, isGlass, isLight, tokens, stateColor)
                drawDevice(canvas, left + pad + gR, top + pad + gR, gR, dp, isCharging, tokens, stateColor)

                val textLeft = left + pad * 2.2f + gaugeSize
                val textW = left + width - pad - textLeft
                val showStatus = height >= 76f * dp
                primaryTextPaint.textSize = NexusBatteryText.fitPercent(
                    primaryTextPaint, digits, textW, height * (if (showStatus) 0.40f else 0.56f), 14f * dp, 64f * dp,
                )
                secondaryTextPaint.textSize = NexusBatteryText.fit(secondaryTextPaint, statusText, textW, height * 0.12f, 9f * dp, 13f * dp)
                val inkH = NexusBatteryText.inkHeight(primaryTextPaint, digits)
                val gap = if (showStatus) secondaryTextPaint.textSize * 1.55f else 0f
                val baseline = top + (height - inkH - gap) / 2f + inkH
                NexusBatteryText.drawPercent(canvas, digits, textLeft, baseline, primaryTextPaint, Paint.Align.LEFT)
                if (showStatus) canvas.drawText(statusText, textLeft, baseline + gap, secondaryTextPaint)
            }
            isRoomy -> {
                val gaugeSize = (minOf(width, height) * 0.46f).coerceIn(40f * dp, 112f * dp)
                val gR = gaugeSize / 2f
                val gCx = left + pad + gR
                val gCy = top + pad + gR
                drawGauge(canvas, gCx, gCy, gR, dp, percent, isGlass, isLight, tokens, stateColor)
                drawDevice(canvas, gCx, gCy, gR, dp, isCharging, tokens, stateColor)

                // Everything below the gauge belongs to the reading; nothing reaches back up.
                val boxTop = top + pad + gaugeSize + pad
                val boxH = top + height - pad - boxTop
                val boxW = width - pad * 2f
                val showStatus = boxH >= 44f * dp
                primaryTextPaint.textSize = NexusBatteryText.fitPercent(
                    primaryTextPaint, digits, boxW, boxH * (if (showStatus) 0.66f else 0.9f), 14f * dp, 72f * dp,
                )
                secondaryTextPaint.textSize = NexusBatteryText.fit(secondaryTextPaint, statusText, boxW, boxH * 0.18f, 9f * dp, 13f * dp)
                val gap = if (showStatus) secondaryTextPaint.textSize * 1.5f else 0f
                val baseline = top + height - pad - gap
                NexusBatteryText.drawPercent(canvas, digits, left + pad, baseline, primaryTextPaint, Paint.Align.LEFT)
                if (showStatus) canvas.drawText(statusText, left + pad, baseline + gap, secondaryTextPaint)
            }
            else -> {
                val gR = (minOf(width, height) - pad * 2f) / 2f
                val gCx = left + width / 2f
                val gCy = top + height / 2f
                val strokeW = drawGauge(canvas, gCx, gCy, gR, dp, percent, isGlass, isLight, tokens, stateColor)
                val inner = (gR - strokeW) * 2f
                primaryTextPaint.textSize = NexusBatteryText.fitPercent(
                    primaryTextPaint, digits, inner * 0.70f, inner * 0.34f, 10f * dp, 44f * dp,
                )
                val inkH = NexusBatteryText.inkHeight(primaryTextPaint, digits)
                NexusBatteryText.drawPercent(canvas, digits, gCx, gCy + inkH / 2f, primaryTextPaint, Paint.Align.CENTER)
                if (isCharging) {
                    NexusBatteryDrawUtils.drawLightningBolt(canvas, gCx, gCy - inkH * 1.05f, inkH * 0.7f, stateColor)
                }
            }
        }
    }

    /** The ring: track and charge arc. Returns the stroke width so callers can find the inside. */
    private fun drawGauge(
        canvas: Canvas, cx: Float, cy: Float, outerR: Float, dp: Float, percent: Int,
        isGlass: Boolean, isLight: Boolean, tokens: NexusColorTokens, stateColor: Int,
    ): Float {
        val strokeW = (outerR * 0.18f).coerceIn(3.5f * dp, 9f * dp)
        val r = outerR - strokeW / 2f
        drawRect.set(cx - r, cy - r, cx + r, cy + r)
        trackPaint.apply {
            color = if (isGlass) {
                if (isLight) Color.argb(35, 0, 0, 0) else Color.argb(45, 255, 255, 255)
            } else {
                tokens.divider
            }
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(drawRect, 0f, 360f, false, trackPaint)
        fillPaint.apply {
            color = stateColor
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(drawRect, -90f, percent.coerceIn(0, 100) / 100f * 360f, false, fillPaint)
        return strokeW
    }

    /** The phone silhouette inside the gauge; skipped when the gauge is too small to hold one. */
    private fun drawDevice(
        canvas: Canvas, cx: Float, cy: Float, outerR: Float, dp: Float, isCharging: Boolean,
        tokens: NexusColorTokens, stateColor: Int,
    ) {
        if (outerR < 16f * dp) return
        val devW = outerR * 0.56f
        val devH = outerR * 0.96f
        wellRect.set(cx - devW / 2f, cy - devH / 2f, cx + devW / 2f, cy + devH / 2f)
        borderPaint.apply {
            color = if (isCharging) stateColor else tokens.textSecondary
            strokeWidth = (outerR * 0.05f).coerceIn(1.2f * dp, 2f * dp)
            style = Paint.Style.STROKE
        }
        val corner = devW * 0.22f
        canvas.drawRoundRect(wellRect, corner, corner, borderPaint)
        val speakerW = devW * 0.32f
        val speakerY = wellRect.top + devH * 0.09f
        canvas.drawLine(cx - speakerW / 2f, speakerY, cx + speakerW / 2f, speakerY, borderPaint)
        if (isCharging) {
            NexusBatteryDrawUtils.drawLightningBolt(canvas, cx, cy, devW * 0.52f, stateColor)
        }
    }

    // -------------------------------------------------------------------------
    // STYLE 3: Tech Matrix / Segmented Cell
    // -------------------------------------------------------------------------
    /**
     * Readout beside a segmented cell — or, when the widget is too narrow for the reading to sit
     * beside the cell at a readable size, the cell on top and the reading centred below it.
     *
     * The side-by-side layout used to be the only one. On a narrow widget its text column shrank to
     * a sliver and the readout ran into the cell. The readout itself is a stack — header, reading,
     * status — and when the widget is too short for all three the header goes first, then the
     * status, rather than any of them overlapping.
     */
    fun drawTechCell(
        context: Context, canvas: Canvas, left: Float, top: Float, width: Float, height: Float,
        dp: Float, percent: Int, isCharging: Boolean, statusText: String, tokens: NexusColorTokens,
        config: NexusWidgetConfig.InstanceConfig, locale: Locale, isGlass: Boolean, isLight: Boolean,
        markColor: Int
    ) {
        val stateColor = if (percent < 20) tokens.danger else markColor
        val pad = (minOf(width, height) * 0.08f).coerceIn(6f * dp, 12f * dp)
        val digits = LocaleDigitUtils.formatNumber(percent, locale)
        val header = context.getString(R.string.battery_label_uppercase)
        val subText = if (isCharging) context.getString(R.string.battery_status_charging) else statusText

        primaryTextPaint.apply {
            color = tokens.textPrimary
            typeface = NexusBatteryDrawUtils.getTypeface(context, config, Typeface.BOLD)
            letterSpacing = -0.01f
            NexusBatteryDrawUtils.applyLegibility(this, isGlass, isLight, dp)
            textSize = 18f * dp
        }
        val readableW = NexusBatteryText.percentWidth(primaryTextPaint, digits)

        val sideCellH = (height - pad * 2f - 3f * dp).coerceAtLeast(24f * dp)
        // Not coerceIn(16dp, width * 0.30f): on a narrow widget that range is empty and throws,
        // and this runs in the launcher's own process. The width cap wins; 16dp is only a floor
        // when there is room for it.
        val sideCellW = minOf((sideCellH * 0.46f).coerceAtLeast(16f * dp), width * 0.30f)
        val sideColW = width - pad * 2f - sideCellW - pad * 1.2f
        val stacked = sideColW < readableW && height > width * 1.1f

        if (!stacked) {
            val cellX = left + width - pad - sideCellW
            val cellY = top + (height - sideCellH) / 2f + 1.5f * dp
            drawSegmentedCell(canvas, cellX, cellY, sideCellW, sideCellH, dp, percent, tokens, isGlass, isLight, stateColor)
            drawReadout(
                canvas, left + pad, top, sideColW, height, pad, dp, digits, header, subText,
                context, config, tokens, isGlass, isLight, Paint.Align.LEFT,
            )
        } else {
            val cellH = (height * 0.54f).coerceAtLeast(24f * dp)
            val cellW = (cellH * 0.46f).coerceAtMost(width * 0.5f)
            val cellX = left + (width - cellW) / 2f
            val cellY = top + pad + 3f * dp
            drawSegmentedCell(canvas, cellX, cellY, cellW, cellH, dp, percent, tokens, isGlass, isLight, stateColor)
            val boxTop = cellY + cellH + pad * 0.8f
            drawReadout(
                canvas, left + pad, boxTop, width - pad * 2f, top + height - boxTop, pad * 0.5f, dp,
                digits, null, subText, context, config, tokens, isGlass, isLight, Paint.Align.CENTER,
            )
        }
    }

    /** The cell: terminal tip, shell, and five segments filled to the nearest one. */
    private fun drawSegmentedCell(
        canvas: Canvas, cellX: Float, cellY: Float, cellW: Float, cellH: Float, dp: Float, percent: Int,
        tokens: NexusColorTokens, isGlass: Boolean, isLight: Boolean, stateColor: Int,
    ) {
        val tipW = cellW * 0.40f
        val tipH = (cellH * 0.035f).coerceIn(2f * dp, 4f * dp)
        drawRect.set(cellX + (cellW - tipW) / 2f, cellY - tipH, cellX + (cellW + tipW) / 2f, cellY)
        solidFillPaint.color = tokens.textSecondary
        canvas.drawRoundRect(drawRect, tipH / 2f, tipH / 2f, solidFillPaint)

        cellRect.set(cellX, cellY, cellX + cellW, cellY + cellH)
        borderPaint.apply {
            color = tokens.textSecondary
            strokeWidth = (cellW * 0.05f).coerceIn(1.2f * dp, 2f * dp)
            style = Paint.Style.STROKE
        }
        val cellCorner = cellW * 0.22f
        canvas.drawRoundRect(cellRect, cellCorner, cellCorner, borderPaint)

        val segCount = 5
        val segPad = (cellW * 0.12f).coerceIn(2.5f * dp, 4f * dp)
        val segGap = (cellH * 0.025f).coerceIn(2f * dp, 3.5f * dp)
        val segH = (cellH - segPad * 2f - segGap * (segCount - 1)) / segCount
        val segW = cellW - segPad * 2f
        // Nearest segment: 19% used to floor to none at all, while 81% must not read as full.
        val filledSegs = kotlin.math.round(percent.coerceIn(0, 100) / 100f * segCount).toInt()
        for (i in 0 until segCount) {
            val segY = (cellY + cellH - segPad) - (i + 1) * segH - i * segGap
            fillRect.set(cellX + segPad, segY, cellX + segPad + segW, segY + segH)
            solidFillPaint.color = if (i < filledSegs) stateColor else {
                if (isGlass) (if (isLight) Color.argb(20, 0, 0, 0) else Color.argb(30, 255, 255, 255)) else tokens.divider
            }
            val segCorner = minOf(segH, segW) * 0.28f
            canvas.drawRoundRect(fillRect, segCorner, segCorner, solidFillPaint)
        }
    }

    /**
     * Header (optional), reading and status, fitted to [colW] × [boxH] and centred vertically in
     * the box. Drops the header, then the status, when the box is too short for all of them.
     */
    private fun drawReadout(
        canvas: Canvas, colX: Float, boxTop: Float, colW: Float, boxH: Float, pad: Float, dp: Float,
        digits: String, header: String?, subText: String, context: Context,
        config: NexusWidgetConfig.InstanceConfig, tokens: NexusColorTokens,
        isGlass: Boolean, isLight: Boolean, align: Paint.Align,
    ) {
        val availH = boxH - pad * 2f
        val x = if (align == Paint.Align.CENTER) colX + colW / 2f else colX
        primaryTextPaint.textSize = NexusBatteryText.fitPercent(primaryTextPaint, digits, colW, availH * 0.46f, 14f * dp, 60f * dp)
        secondaryTextPaint.apply {
            color = tokens.textSecondary
            typeface = NexusBatteryDrawUtils.getTypeface(context, config, Typeface.BOLD)
            textAlign = align
            NexusBatteryDrawUtils.applyLegibility(this, isGlass, isLight, dp)
            textSize = NexusBatteryText.fit(this, header ?: subText, colW, availH * 0.10f, 8f * dp, 12f * dp)
        }
        val pctInk = NexusBatteryText.inkHeight(primaryTextPaint, digits)
        val lineH = secondaryTextPaint.textSize * 1.45f
        var showHeader = header != null
        var showStatus = true
        fun stackH() = pctInk + (if (showHeader) lineH else 0f) + (if (showStatus) lineH else 0f)
        if (stackH() > availH) showHeader = false
        if (stackH() > availH) showStatus = false

        var y = boxTop + (boxH - stackH()) / 2f
        if (showHeader && header != null) {
            secondaryTextPaint.letterSpacing = 0.18f
            y += secondaryTextPaint.textSize
            canvas.drawText(header, x, y, secondaryTextPaint)
            y += lineH - secondaryTextPaint.textSize
        }
        y += pctInk
        NexusBatteryText.drawPercent(canvas, digits, x, y, primaryTextPaint, align)
        if (showStatus) {
            secondaryTextPaint.letterSpacing = 0.04f
            secondaryTextPaint.typeface = NexusBatteryDrawUtils.getTypeface(context, config, Typeface.NORMAL)
            secondaryTextPaint.textSize = NexusBatteryText.fit(secondaryTextPaint, subText, colW, availH * 0.10f, 8f * dp, 12f * dp)
            canvas.drawText(subText, x, y + lineH, secondaryTextPaint)
        }
    }
}
