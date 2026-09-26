package com.nexus.launcher.ui.canvas

import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import com.nexus.launcher.data.prefs.NexusDefaults
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object IconLayoutMetrics {
    data class IconLayoutResult(
        val iconRect: Rect,
        val labelY: Float,
        val labelLine2Y: Float,
        val labelMaxWidth: Float,
        val ellipsizedLabel: String?,
        val labelLine2: String?
    )

    fun compute(
        cell: RectF,
        gridRows: Int,
        gapHorizontalPx: Float,
        gapVerticalPx: Float,
        spanX: Int,
        spanY: Int,
        showLabels: Boolean,
        userIconSizeMultiplier: Float,
        density: Float,
        displayWidth: Int,
        displayHeight: Int,
        labelText: String? = null,
        labelPaint: TextPaint? = null,
        caller: String = "other",
        twoLineLabels: Boolean = false
    ): IconLayoutResult {
        val sx = spanX.coerceAtLeast(1)
        val sy = spanY.coerceAtLeast(1)
        val physicalW = cell.width() / sx
        val physicalH = cell.height() / sy

        // Negative gap is treated as 0: it cannot grow the icon past the cell.
        val gapH = max(gapHorizontalPx, 0f)
        val gapV = max(gapVerticalPx, 0f)
        val labelSizePx = labelPaint?.textSize ?: HomeGridAvailableSpace.LABEL_TEXT_SIZE_PX
        val labelGapPx = HomeGridAvailableSpace.LABEL_GAP_DP * density
        val labelBand = IconLabelText.bandHeightPx(
            showLabels, twoLineLabels, labelSizePx, labelGapPx
        )

        val contentW = max(cell.width() - gapH, 1f)
        val contentH = max(cell.height() - gapV - labelBand, 1f)
        val maxSide = min(contentW / sx, contentH / sy)
        val multiplier = userIconSizeMultiplier.coerceIn(
            NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MIN,
            NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MAX
        )
        val requestedPx = maxSide * multiplier
        val baseIconSize = floorToPx(min(requestedPx, maxSide))
        val maxW = floorToPx(contentW)
        val maxH = floorToPx(contentH)
        val iconWidth = min(floorToPx(baseIconSize + (sx - 1) * physicalW), maxW)
        val iconHeight = min(floorToPx(baseIconSize + (sy - 1) * physicalH), maxH)

        var iconLeft: Float
        var iconTop: Float
        if (showLabels) {
            iconLeft = cell.centerX() - iconWidth / 2f
            iconTop = cell.top + gapV / 2f
        } else {
            iconLeft = cell.centerX() - iconWidth / 2f
            iconTop = cell.centerY() - iconHeight / 2f
        }
        val minLeft = cell.left
        val maxLeft = cell.right - iconWidth
        val minTop = cell.top
        val maxTop = cell.bottom - iconHeight - labelBand
        iconLeft = iconLeft.coerceIn(minLeft, max(minLeft, maxLeft))
        iconTop = iconTop.coerceIn(minTop, max(minTop, maxTop))

        val left = floor(iconLeft.toDouble()).toInt()
        val top = floor(iconTop.toDouble()).toInt()
        val iconRect = Rect(left, top, left + iconWidth, top + iconHeight)
        HomeGridOverlapDiag.logIcon(
            caller = caller,
            gridRows = gridRows,
            multiplier = userIconSizeMultiplier,
            cellW = physicalW,
            cellH = physicalH,
            gapHPx = gapHorizontalPx,
            gapVPx = gapVerticalPx,
            requestedPx = requestedPx,
            contentW = contentW,
            contentH = contentH,
            maxSide = maxSide,
            finalW = iconWidth,
            finalH = iconHeight,
            iconLeft = left,
            iconTop = top
        )

        val labelMaxWidth = max(cell.width() - 8f * density * 2, 1f)
        val labelY = iconRect.bottom.toFloat() + labelGapPx + labelSizePx
        val labelLine2Y = labelY + IconLabelText.lineStepPx(labelSizePx)
        val wrapped = if (labelText != null && labelPaint != null) {
            IconLabelText.wrap(labelText, labelPaint, labelMaxWidth, twoLineLabels)
        } else {
            null
        }
        return IconLayoutResult(
            iconRect = iconRect,
            labelY = labelY,
            labelLine2Y = labelLine2Y,
            labelMaxWidth = labelMaxWidth,
            ellipsizedLabel = wrapped?.line1,
            labelLine2 = wrapped?.line2
        )
    }

    private fun floorToPx(value: Float): Int =
        floor(value.toDouble()).toInt().coerceAtLeast(1)
}
