package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.canvas.GridMetrics
import com.nexus.launcher.ui.canvas.HomeGridAvailableSpace
import com.nexus.launcher.ui.canvas.HomeGridBounds
import com.nexus.launcher.ui.canvas.HomeGridNetSnapshot
import com.nexus.launcher.ui.canvas.HomeGridOverlapDiag
import com.nexus.launcher.ui.canvas.IconLabelText
import com.nexus.launcher.ui.canvas.IconLayoutMetrics
class HomeScreenPreviewView(context: Context) : View(context) {

    var pendingSettings: PendingHomeSettings? = null
        set(value) {
            val changed = field != value
            field = value?.copy()
            if (changed) {
                requestLayout()
                invalidate()
            }
        }

    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val iconPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val layoutLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = HomeGridAvailableSpace.LABEL_TEXT_SIZE_PX
        isFakeBoldText = true
    }

    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val containerRect = RectF()
    private val destRect = Rect()
    private val indicatorRect = RectF()

    /** First resolved status-bar height wins so measure/draw cannot flip by 1px. */
    private var lockedStatusBarTopPx = 0

    init {
        try {
            currentTokens = ThemeObserver.currentTokens(context)
        } catch (_: Exception) {}
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        invalidate()
    }



    private fun computeScaledLayout(viewW: Float): Triple<GridMetrics.Layout, Float, Float> {
        val settings = pendingSettings
        val density = resources.displayMetrics.density
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels

        val cols = HomeGridBounds.columns(
            settings?.homeColumns ?: NexusDefaults.HOME_COLUMNS
        )
        val rows = HomeGridBounds.rows(
            settings?.homeRows ?: NexusDefaults.HOME_ROWS
        )
        val statusBarTop = previewStatusBarTop()
        val net = HomeGridNetSnapshot.resolve(
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            statusBarPx = statusBarTop,
            density = density
        )
        val gridAreaTop = net.topInsetPx.toFloat()
        HomeGridOverlapDiag.logDock(
            caller = HomeGridOverlapDiag.CALLER_PREVIEW,
            source = net.dockSource,
            reservePx = net.dockReservePx
        )

        val layout = GridMetrics.compute(
            availableWidthPx = net.availableWidthPx,
            availableHeightPx = net.availableHeightPx,
            columns = cols,
            rows = rows,
            paddingLeftRightDp = settings?.homePaddingLeftRightDp ?: 0f,
            paddingTopBottomDp = settings?.homePaddingTopBottomDp ?: 0f,
            gapHorizontalDp = settings?.homeGapHorizontalDp ?: 0f,
            gapVerticalDp = settings?.homeGapVerticalDp ?: 0f,
            density = density,
            caller = HomeGridOverlapDiag.CALLER_PREVIEW,
            statusBarPx = net.statusBarPx,
            topInsetPx = net.topInsetPx,
            dockSource = net.dockSource,
            dockReservePx = net.dockReservePx
        )

        val padH = 14f * density
        val padV = 10f * density
        val availableW = (viewW - padH * 2f).coerceAtLeast(1f)
        val scale = availableW / net.gridWidthPx.toFloat().coerceAtLeast(1f)

        val cellRow0 = layout.getCellBounds(0, 0, 1, 1, 0f, gridAreaTop)
        val cellRow1 = layout.getCellBounds(0, 1, 1, 1, 0f, gridAreaTop)
        val scaledRow0Top = cellRow0.top * scale
        val scaledRow1Bottom = cellRow1.bottom * scale
        val scaledGridH = (scaledRow1Bottom - scaledRow0Top).coerceAtLeast(1f)

        val showIndicator = settings?.homeShowIndicator ?: true
        val indicatorExtra = if (showIndicator) 18f * density else 6f * density
        val desiredHeight = scaledGridH + indicatorExtra + (padV * 2f)

        return Triple(layout, scale, desiredHeight)
    }

    private fun previewStatusBarTop(): Int {
        if (lockedStatusBarTopPx > 0) return lockedStatusBarTopPx
        val resolved = HomeGridAvailableSpace.statusBarTop(this)
        if (resolved > 0) lockedStatusBarTopPx = resolved
        return resolved
    }

    private fun previewGridAreaTop(): Float {
        val density = resources.displayMetrics.density
        val net = HomeGridNetSnapshot.resolve(
            displayWidth = resources.displayMetrics.widthPixels,
            displayHeight = resources.displayMetrics.heightPixels,
            statusBarPx = previewStatusBarTop(),
            density = density
        )
        return net.topInsetPx.toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val (_, _, desiredHeight) = computeScaledLayout(width.toFloat())
        setMeasuredDimension(width, desiredHeight.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val settings = pendingSettings ?: return
        val density = resources.displayMetrics.density
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val radius = 20 * density
        val strokeW = 1f * density

        val isLight = currentTokens.bg == NexusColorTokens.Light.bg ||
                ColorUtils.calculateLuminance(currentTokens.bg) > 0.5

        bgPaint.color = currentTokens.surface
        borderPaint.color = currentTokens.divider
        borderPaint.strokeWidth = strokeW

        containerRect.set(0f, 0f, viewW, viewH)
        canvas.drawRoundRect(containerRect, radius, radius, bgPaint)
        canvas.drawRoundRect(containerRect, radius, radius, borderPaint)

        val (layout, scale, _) = computeScaledLayout(viewW)

        val padH = 14f * density
        val padV = 10f * density
        val cols = HomeGridBounds.columns(settings.homeColumns)
        val rows = HomeGridBounds.rows(settings.homeRows)
        val numRows = 2
        val gridAreaTop = previewGridAreaTop()

        val cellRow0 = layout.getCellBounds(0, 0, 1, 1, 0f, gridAreaTop)
        val cellRow1 = layout.getCellBounds(0, 1, 1, 1, 0f, gridAreaTop)
        val scaledRow0Top = cellRow0.top * scale
        val scaledRow1Bottom = cellRow1.bottom * scale

        val offsetX = padH
        val offsetY = padV - scaledRow0Top

        val showLabels = settings.homeShowLabels
        val showIndicator = settings.homeShowIndicator

        labelPaint.color = currentTokens.textSecondary
        labelPaint.textSize = HomeGridAvailableSpace.LABEL_TEXT_SIZE_PX * scale

        for (r in 0 until numRows) {
            for (c in 0 until cols) {
                val index = (r * cols + c)
                val bmp = SettingsPreviewIconRenderer.getBitmap(
                    context, index, settings.iconShape, isLight, currentTokens
                )
                val label = SettingsPreviewIconRenderer.getLabel(context, index)

                val cell = layout.getCellBounds(c, r, 1, 1, 0f, gridAreaTop)
                val iconResult = IconLayoutMetrics.compute(
                    cell = cell,
                    gridRows = rows,
                    gapHorizontalPx = layout.gapHorizontalPx,
                    gapVerticalPx = layout.gapVerticalPx,
                    spanX = 1,
                    spanY = 1,
                    showLabels = showLabels,
                    userIconSizeMultiplier = settings.homeIconSizeMultiplier,
                    density = density,
                    displayWidth = displayWidth,
                    displayHeight = displayHeight,
                    labelText = label,
                    labelPaint = layoutLabelPaint,
                    caller = HomeGridOverlapDiag.CALLER_PREVIEW,
                    twoLineLabels = settings.homeTwoLineLabels
                )

                val left = (iconResult.iconRect.left * scale + offsetX).toInt()
                val top = (iconResult.iconRect.top * scale + offsetY).toInt()
                val right = (iconResult.iconRect.right * scale + offsetX).toInt()
                val bottom = (iconResult.iconRect.bottom * scale + offsetY).toInt()

                destRect.set(left, top, right, bottom)
                canvas.drawBitmap(bmp, null, destRect, iconPaint)

                if (showLabels) {
                    val labelX = iconResult.iconRect.exactCenterX() * scale + offsetX
                    val labelY = iconResult.labelY * scale + offsetY
                    val step = (iconResult.labelLine2Y - iconResult.labelY) * scale
                    IconLabelText.drawCentered(
                        canvas, labelPaint, labelX, labelY,
                        iconResult.ellipsizedLabel ?: label,
                        iconResult.labelLine2,
                        step
                    )
                }
            }
        }

        if (showIndicator) {
            val indicatorY = (scaledRow1Bottom + offsetY) + (8f * density)
            val centerX = viewW / 2f
            val dotRadius = 2.5f * density
            val activePillW = 12f * density
            val dotSpacing = 12f * density

            val offColor = (currentTokens.textSecondary and 0x00FFFFFF) or (0x40 shl 24)
            indicatorPaint.color = offColor

            // Dot 0 (inactive)
            canvas.drawCircle(centerX - dotSpacing - (activePillW / 4f), indicatorY, dotRadius, indicatorPaint)

            // Dot 1 (active pill)
            indicatorPaint.color = currentTokens.textPrimary
            indicatorRect.set(centerX - (activePillW / 2f), indicatorY - dotRadius, centerX + (activePillW / 2f), indicatorY + dotRadius)
            canvas.drawRoundRect(indicatorRect, dotRadius, dotRadius, indicatorPaint)

            // Dot 2 (inactive)
            indicatorPaint.color = offColor
            canvas.drawCircle(centerX + dotSpacing + (activePillW / 4f), indicatorY, dotRadius, indicatorPaint)
        }
    }
}
