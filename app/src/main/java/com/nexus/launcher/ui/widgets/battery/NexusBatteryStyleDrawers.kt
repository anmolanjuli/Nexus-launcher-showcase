package com.nexus.launcher.ui.widgets.battery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.nexus.launcher.R
import com.nexus.launcher.feed.FeedPanelAnim
import com.nexus.launcher.locale.LocaleDigitUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import java.util.Locale

/**
 * Pre-allocated modular drawers for the primary Nexus Battery styles:
 * - Style 0: Horizon Fluid Level (Full-card liquid gauge fill & bold typography)
 * - Style 1: Capsule Slider (Recessed track, slider pill, circular status badge)
 * - Style 2: Radial Gauge (Delegated to [NexusBatteryGaugeDrawers])
 * - Style 3: Tech Matrix (Delegated to [NexusBatteryGaugeDrawers])
 * - Style 4: Power Monitor (Delegated to [NexusBatteryHogsDrawer])
 *
 * All Paint, Path, and RectF instances are class fields (zero per-frame allocations).
 */
object NexusBatteryStyleDrawers {

    const val STYLE_FLUID = 0
    const val STYLE_CAPSULE = 1
    const val STYLE_RADIAL = 2
    const val STYLE_TECH_CELL = 3
    const val STYLE_DASHBOARD = 4

    private val primaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val solidFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wellBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pillHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val drawRect = RectF()
    private val fillRect = RectF()
    private val wellRect = RectF()
    private val badgeRect = RectF()

    // -------------------------------------------------------------------------
    // STYLE 0: Horizon Fluid Level
    // -------------------------------------------------------------------------
    /**
     * The widget filled to its charge level like a vessel ([NexusBatteryLiquid]), with the reading
     * fitted over it — a pillar layout for tall widgets, a centred reading otherwise.
     *
     * The liquid is the theme's own ink, so it is light on dark themes and dark on light ones. The
     * reading is drawn twice to stay legible across it: once in ink, then again clipped to the
     * liquid in the inverse colour — so where the surface crosses a digit, the digit changes colour
     * exactly at the line.
     */
    fun drawFluidLevel(
        context: Context, canvas: Canvas, left: Float, top: Float, width: Float, height: Float,
        dp: Float, percent: Int, isCharging: Boolean, statusText: String, tokens: NexusColorTokens,
        config: NexusWidgetConfig.InstanceConfig, locale: Locale, isGlass: Boolean, isLight: Boolean,
        markColor: Int
    ) {
        val fillColor = if (percent < 20) tokens.danger else markColor
        val isTall = height / width > 1.35f
        val isWide = width / height > 1.45f
        val pct = percent.coerceIn(0, 100) / 100f
        val digits = LocaleDigitUtils.formatNumber(percent, locale)
        val cx = left + width / 2f

        NexusBatteryLiquid.draw(canvas, dp, pct, isWide, fillColor, isGlass, isLight)

        primaryTextPaint.apply {
            typeface = getTypeface(context, config, Typeface.BOLD)
            letterSpacing = -0.02f
        }
        secondaryTextPaint.textAlign = Paint.Align.CENTER

        // Lay out once; the same positions are painted twice below.
        val label = context.getString(R.string.battery_label_uppercase)
        var pctBaseline: Float
        var labelBaseline = 0f
        var boltCx = 0f
        var boltCy = 0f
        var boltSize = 0f
        var readingX = cx
        var readingAlign = Paint.Align.CENTER
        var statusBaseline = 0f
        val showStatus: Boolean
        if (isTall) {
            primaryTextPaint.textSize = NexusBatteryText.fitPercent(
                primaryTextPaint, digits, width * 0.88f, height * 0.20f, 16f * dp, 52f * dp,
            )
            pctBaseline = top + height - height * 0.06f
            val pctTop = pctBaseline - NexusBatteryText.inkHeight(primaryTextPaint, digits)
            secondaryTextPaint.apply {
                typeface = getTypeface(context, config, Typeface.BOLD)
                letterSpacing = 0.18f
                textSize = NexusBatteryText.fit(this, label, width * 0.9f, height * 0.05f, 8f * dp, 12f * dp)
            }
            labelBaseline = pctTop - height * 0.05f
            val boltRoomTop = top + height * 0.08f
            val boltRoomBottom = labelBaseline - secondaryTextPaint.textSize - height * 0.06f
            boltSize = minOf(width * 0.46f, (boltRoomBottom - boltRoomTop) * 0.9f)
            boltCx = cx
            boltCy = (boltRoomTop + boltRoomBottom) / 2f
            showStatus = false
        } else {
            showStatus = height >= 70f * dp
            val readingBoxH = height * (if (showStatus) 0.40f else 0.56f)
            val boltShare = if (isCharging) 0.30f else 0f
            primaryTextPaint.textSize = NexusBatteryText.fitPercent(
                primaryTextPaint, digits, width * (0.84f - boltShare * 0.5f), readingBoxH, 16f * dp, 64f * dp,
            )
            val inkH = NexusBatteryText.inkHeight(primaryTextPaint, digits)
            secondaryTextPaint.apply {
                typeface = getTypeface(context, config, Typeface.NORMAL)
                letterSpacing = 0.04f
                textSize = NexusBatteryText.fit(this, statusText, width * 0.86f, height * 0.12f, 9f * dp, 13f * dp)
            }
            val statusGap = if (showStatus) secondaryTextPaint.textSize * 1.6f else 0f
            pctBaseline = top + (height - inkH - statusGap) / 2f + inkH
            statusBaseline = pctBaseline + statusGap
            if (isCharging) {
                boltSize = inkH * 0.95f
                val gap = inkH * 0.18f
                val readingW = NexusBatteryText.percentWidth(primaryTextPaint, digits)
                val startX = cx - (boltSize + gap + readingW) / 2f
                boltCx = startX + boltSize / 2f
                boltCy = pctBaseline - inkH / 2f
                readingX = startX + boltSize + gap
                readingAlign = Paint.Align.LEFT
            }
        }

        fun paintReading(primary: Int, secondary: Int, legible: Boolean) {
            primaryTextPaint.color = primary
            secondaryTextPaint.color = secondary
            if (legible) {
                applyLegibility(primaryTextPaint, isGlass, isLight, dp)
                applyLegibility(secondaryTextPaint, isGlass, isLight, dp)
            } else {
                // A shadow behind inverted text reads as a smudge on the liquid.
                primaryTextPaint.clearShadowLayer()
                secondaryTextPaint.clearShadowLayer()
            }
            NexusBatteryText.drawPercent(canvas, digits, readingX, pctBaseline, primaryTextPaint, readingAlign)
            if (isTall) canvas.drawText(label, cx, labelBaseline, secondaryTextPaint)
            if (!isTall && showStatus) canvas.drawText(statusText, cx, statusBaseline, secondaryTextPaint)
            if (boltSize > 12f * dp && (isTall || isCharging)) {
                drawLightningBolt(canvas, boltCx, boltCy, boltSize, primary)
            }
        }

        paintReading(tokens.textPrimary, tokens.textSecondary, legible = true)

        val inverse = if (androidx.core.graphics.ColorUtils.calculateLuminance(fillColor) > 0.45) {
            Color.rgb(18, 20, 24)
        } else {
            Color.WHITE
        }
        canvas.save()
        if (NexusBatteryLiquid.invertsReading(isGlass) && NexusBatteryLiquid.clipToLiquid(canvas)) {
            paintReading(inverse, androidx.core.graphics.ColorUtils.setAlphaComponent(inverse, 190), legible = false)
        }
        canvas.restore()
    }

    // -------------------------------------------------------------------------
    // STYLE 1: Capsule Slider (Recessed Well & Status Cluster) (Concept 1)
    // -------------------------------------------------------------------------
    fun drawCapsuleSlider(
        context: Context, canvas: Canvas, left: Float, top: Float, width: Float, height: Float,
        dp: Float, percent: Int, isCharging: Boolean, statusText: String, tokens: NexusColorTokens,
        config: NexusWidgetConfig.InstanceConfig, locale: Locale, isGlass: Boolean, isLight: Boolean,
        markColor: Int, animPhase: Float = 0f
    ) {
        val stateColor = if (percent < 20) tokens.danger else markColor
        val pad = 10f * dp
        val isWide = width / height > 1.35f
        val pctClamped = percent.coerceIn(0, 100) / 100f
        val pctStr = "${LocaleDigitUtils.formatNumber(percent, locale)}%"

        if (!isWide) {
            val trackW = (width * 0.32f).coerceIn(20f * dp, 40f * dp)
            val trackH = height - pad * 2f
            val trackX = left + pad
            val trackY = top + pad

            // Recessed Well (Darker frosted cutout with subtle rim)
            wellRect.set(trackX, trackY, trackX + trackW, trackY + trackH)
            wellPaint.color = if (isGlass) {
                if (isLight) Color.argb(35, 0, 0, 0) else Color.argb(70, 0, 0, 0)
            } else {
                tokens.surfaceRaised
            }
            canvas.drawRoundRect(wellRect, trackW / 2f, trackW / 2f, wellPaint)

            wellBorderPaint.apply {
                color = if (isGlass) {
                    if (isLight) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255)
                } else {
                    tokens.divider
                }
                strokeWidth = 1f * dp
            }
            canvas.drawRoundRect(wellRect, trackW / 2f, trackW / 2f, wellBorderPaint)

            // Tactile Slider Pill Fill
            val fillH = (trackH * pctClamped).coerceAtLeast(trackW)
            fillRect.set(trackX, (trackY + trackH) - fillH, trackX + trackW, trackY + trackH)
            solidFillPaint.color = stateColor
            canvas.drawRoundRect(fillRect, trackW / 2f, trackW / 2f, solidFillPaint)

            // Specular top highlight for frosted glass tactile depth
            val highlightH = minOf(fillH * 0.45f, trackW * 1.2f)
            drawRect.set(trackX, fillRect.top, trackX + trackW, fillRect.top + highlightH)
            canvas.save()
            canvas.clipRect(fillRect)
            pillHighlightPaint.color = Color.argb(55, 255, 255, 255)
            canvas.drawRoundRect(drawRect, trackW / 2f, trackW / 2f, pillHighlightPaint)
            canvas.restore()

            // Charging Shimmer
            if (isCharging && FeedPanelAnim.systemAnimatorScale(context) > 0f) {
                NexusBatteryDrawUtils.drawVerticalShimmer(canvas, fillRect, trackW, stateColor, animPhase)
            }

            // Right Cluster: Status Badge & Typography
            val rightLeft = trackX + trackW + (12f * dp)
            val rightW = (left + width - pad) - rightLeft

            // Circular Charging Badge (top right)
            val badgeSize = (minOf(rightW, height * 0.30f)).coerceIn(24f * dp, 36f * dp)
            val badgeCx = rightLeft + rightW - badgeSize / 2f
            val badgeCy = top + pad + badgeSize / 2f
            badgeRect.set(badgeCx - badgeSize / 2f, badgeCy - badgeSize / 2f, badgeCx + badgeSize / 2f, badgeCy + badgeSize / 2f)
            canvas.drawRoundRect(badgeRect, badgeSize / 2f, badgeSize / 2f, wellPaint)
            canvas.drawRoundRect(badgeRect, badgeSize / 2f, badgeSize / 2f, wellBorderPaint)

            val boltColor = if (percent < 20) tokens.danger else if (isCharging) markColor else tokens.textSecondary
            drawLightningBolt(canvas, badgeCx, badgeCy, badgeSize * 0.50f, boltColor)

            // Reading and status share the space under the badge, fitted to the column.
            val digits = LocaleDigitUtils.formatNumber(percent, locale)
            val boxTop = badgeRect.bottom + pad * 0.6f
            val boxH = (top + height - pad) - boxTop
            val showStatus = boxH >= 40f * dp
            primaryTextPaint.apply {
                color = tokens.textPrimary
                typeface = getTypeface(context, config, Typeface.BOLD)
                letterSpacing = -0.02f
                applyLegibility(this, isGlass, isLight, dp)
                textSize = NexusBatteryText.fitPercent(this, digits, rightW, boxH * (if (showStatus) 0.62f else 0.9f), 14f * dp, 56f * dp)
            }
            secondaryTextPaint.apply {
                color = tokens.textSecondary
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.LEFT
                letterSpacing = 0.04f
                applyLegibility(this, isGlass, isLight, dp)
                textSize = NexusBatteryText.fit(this, statusText, rightW, boxH * 0.2f, 9f * dp, 13f * dp)
            }
            val gap = if (showStatus) secondaryTextPaint.textSize * 1.5f else 0f
            val baseline = top + height - pad - gap
            NexusBatteryText.drawPercent(canvas, digits, rightLeft, baseline, primaryTextPaint, Paint.Align.LEFT)
            if (showStatus) canvas.drawText(statusText, rightLeft, baseline + gap, secondaryTextPaint)
        } else {
            // Wide Strip Layout
            val trackH = (height * 0.24f).coerceIn(10f * dp, 20f * dp)
            val trackY = (top + height) - pad - trackH
            val trackW = width - pad * 2f

            wellRect.set(left + pad, trackY, left + pad + trackW, trackY + trackH)
            wellPaint.color = if (isGlass) {
                if (isLight) Color.argb(35, 0, 0, 0) else Color.argb(70, 0, 0, 0)
            } else {
                tokens.surfaceRaised
            }
            canvas.drawRoundRect(wellRect, trackH / 2f, trackH / 2f, wellPaint)

            wellBorderPaint.apply {
                color = if (isGlass) {
                    if (isLight) Color.argb(40, 0, 0, 0) else Color.argb(45, 255, 255, 255)
                } else {
                    tokens.divider
                }
                strokeWidth = 1f * dp
            }
            canvas.drawRoundRect(wellRect, trackH / 2f, trackH / 2f, wellBorderPaint)

            val fillW = (trackW * pctClamped).coerceAtLeast(trackH)
            fillRect.set(left + pad, trackY, left + pad + fillW, trackY + trackH)
            solidFillPaint.color = stateColor
            canvas.drawRoundRect(fillRect, trackH / 2f, trackH / 2f, solidFillPaint)

            // Upper cluster: bolt and reading on the left, status on the right only in the room
            // the reading leaves. Both used to share a baseline with nothing keeping them apart,
            // so on a narrower strip the status ran into the number.
            val digits = LocaleDigitUtils.formatNumber(percent, locale)
            val contentH = trackY - top - pad
            primaryTextPaint.apply {
                color = tokens.textPrimary
                typeface = getTypeface(context, config, Typeface.BOLD)
                letterSpacing = -0.02f
                applyLegibility(this, isGlass, isLight, dp)
                textSize = NexusBatteryText.fitPercent(this, digits, trackW * 0.55f, contentH * 0.62f, 14f * dp, 44f * dp)
            }
            val inkH = NexusBatteryText.inkHeight(primaryTextPaint, digits)
            val mainY = top + pad + (contentH + inkH) / 2f
            val boltSize = inkH * 0.9f
            drawLightningBolt(canvas, left + pad + boltSize / 2f, mainY - inkH / 2f, boltSize, stateColor)
            val readingX = left + pad + boltSize + 8f * dp
            NexusBatteryText.drawPercent(canvas, digits, readingX, mainY, primaryTextPaint, Paint.Align.LEFT)

            val readingEnd = readingX + NexusBatteryText.percentWidth(primaryTextPaint, digits)
            val statusRoom = (left + width - pad) - readingEnd - 12f * dp
            secondaryTextPaint.apply {
                color = tokens.textSecondary
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
                letterSpacing = 0.04f
                applyLegibility(this, isGlass, isLight, dp)
                textSize = NexusBatteryText.fit(this, statusText, statusRoom, contentH * 0.3f, 9f * dp, 13f * dp)
            }
            if (statusRoom > 0f && NexusBatteryText.fits(secondaryTextPaint, statusText, statusRoom, contentH)) {
                canvas.drawText(statusText, left + width - pad, mainY, secondaryTextPaint)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared Helpers Delegated to NexusBatteryDrawUtils
    // -------------------------------------------------------------------------
    fun drawLightningBolt(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        NexusBatteryDrawUtils.drawLightningBolt(canvas, cx, cy, size, color)
    }

    fun applyLegibility(paint: Paint, isGlass: Boolean, isLight: Boolean, dp: Float) {
        NexusBatteryDrawUtils.applyLegibility(paint, isGlass, isLight, dp)
    }

    fun getTypeface(context: Context, config: NexusWidgetConfig.InstanceConfig, style: Int): Typeface {
        return NexusBatteryDrawUtils.getTypeface(context, config, style)
    }
}
