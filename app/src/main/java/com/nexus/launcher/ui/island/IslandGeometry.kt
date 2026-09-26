package com.nexus.launcher.ui.island

import android.graphics.RectF
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max

/**
 * Single source of truth for Nexus Island pill size and placement.
 * Draw, hit-test, spring targets and bounce all call here.
 */
object IslandGeometry {
    const val REST_W_DP = 52f
    const val REST_H_DP = 26f
    const val MULTI_GAP_DP = 8f
    const val EDGE_PAD_DP = 16f
    const val STROKE_DP = 1f
    const val SURFACE_ALPHA = 0.95f
    private const val BAND_FALLBACK_DP = 24f

    /**
     * Width of the open card. Its height is not set here — it comes from what the page draws
     * ([IslandContentMeasure]), so a widget row is not squeezed into a call card's height.
     */
    fun cardWidthDp(size: String): Float = when (size) {
        "small" -> 300f
        "large" -> 360f
        else -> 330f
    }

    /** Tallest the open card may grow, whatever its contents ask for. */
    const val CARD_MAX_H_DP = 200f

    data class StatusBand(
        val heightPx: Float,
        val centerX: Float,
        val centerY: Float,
        val cutoutWidthPx: Float,
        val cutoutHeightPx: Float,
    )

    /**
     * The status-bar band the island lives in. Uses insets ignoring visibility so the band
     * stays put when Immersive Mode hides the system bars. The camera hole, when present,
     * is the cutout nearest the horizontal center.
     */
    fun readBand(host: View): StatusBand {
        val density = host.resources.displayMetrics.density
        val screenW = host.width.takeIf { it > 0 }?.toFloat()
            ?: host.resources.displayMetrics.widthPixels.toFloat()
        val fallback = BAND_FALLBACK_DP * density
        val insets = ViewCompat.getRootWindowInsets(host)
        if (insets == null) return StatusBand(fallback, screenW / 2f, fallback / 2f, 0f, 0f)
        val status = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top.toFloat()
        val cutoutTop = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout()).top.toFloat()
        val hole = insets.displayCutout?.boundingRects
            ?.filter { it.height() > 0 && it.top <= max(status, cutoutTop).coerceAtLeast(1f) + 8f }
            ?.minByOrNull { abs(it.exactCenterX() - screenW / 2f) }
        val band = max(max(status, cutoutTop), hole?.height()?.toFloat() ?: 0f).coerceAtLeast(fallback)
        val centerX = hole?.exactCenterX() ?: (screenW / 2f)
        val centerY = hole?.exactCenterY() ?: (status / 2f).takeIf { it > 0f } ?: (band / 2f)
        val cutoutW = hole?.width()?.toFloat() ?: 0f
        val cutoutH = hole?.height()?.toFloat() ?: 0f
        return StatusBand(band, centerX, centerY, cutoutW, cutoutH)
    }

    /** Vertical center of the camera hole, or the status band when there is no hole. */
    fun pillTop(centerY: Float, pillHeight: Float): Float {
        if (centerY <= 0f) return 0f
        return (centerY - pillHeight / 2f).coerceAtLeast(0f)
    }

    /** The capsule follows the calibrated size; the open card takes [openHeightDp] as measured. */
    fun targetPx(
        shape: IslandShape,
        size: String,
        viewWidth: Float,
        density: Float,
        calibratedWidthDp: Int = 110,
        calibratedHeightDp: Int = 28,
        openHeightDp: Float = 116f,
    ): Pair<Float, Float> {
        val (wDp, hDp) = if (shape == IslandShape.EXPANDED) {
            cardWidthDp(size) to openHeightDp.coerceIn(72f, CARD_MAX_H_DP)
        } else {
            calibratedWidthDp.toFloat() to calibratedHeightDp.toFloat()
        }
        val screenW = viewWidth.takeIf { it > 0f } ?: (density * 360f)
        val edgeCap = (screenW - 2f * EDGE_PAD_DP * density).coerceAtLeast(1f)
        val width = (wDp * density).let { if (it <= edgeCap) it else it.coerceAtMost(screenW) }
        return width to (hDp * density)
    }

    /** Room for the capsule plus the tallest card it could open into. */
    fun viewHeightPx(centerY: Float, density: Float): Int {
        val capH = REST_H_DP * density
        val capTop = pillTop(centerY, capH)
        val cardH = CARD_MAX_H_DP * density
        return (capTop + 48f * density + cardH + 20f * density).toInt().coerceAtLeast(1)
    }

    fun pillLeft(
        viewWidth: Float,
        pillWidth: Float,
        position: String,
        density: Float,
    ): Float {
        val (minLeft, maxLeft) = horizontalRange(viewWidth, pillWidth, density)
        return when (position) {
            "left" -> minLeft
            "right" -> maxLeft
            else -> ((viewWidth - pillWidth) / 2f).coerceIn(minLeft, maxLeft)
        }
    }

    /**
     * Anchors the capsule over the camera cutout.
     * When expanded, opens just below the cutout hugging it so contents are completely clear of the hole.
     */
    fun layoutPills(
        cap: RectF,
        outPrimary: RectF,
        outSecondary: RectF,
        viewWidth: Float,
        animW: Float,
        animH: Float,
        shape: IslandShape,
        position: String = "center",
        density: Float,
        centerY: Float,
        cutoutCenterX: Float,
        calibratedWidthDp: Int = 110,
        calibratedHeightDp: Int = 28,
        xOffsetDp: Int = 0,
        yOffsetDp: Int = 0,
    ) {
        val baseW = calibratedWidthDp * density
        val baseH = calibratedHeightDp * density
        val offsetX = xOffsetDp * density
        val offsetY = yOffsetDp * density
        val anchorCenterX = if (cutoutCenterX > 0f && (position == "center" || position.isBlank())) {
            cutoutCenterX + offsetX
        } else {
            anchoredLeft(viewWidth, baseW, position, density, cutoutCenterX) + baseW / 2f + offsetX
        }
        val anchorCenterY = centerY + offsetY
        val capTop = (anchorCenterY - baseH / 2f).coerceAtLeast(0f)
        val capLeft = (anchorCenterX - baseW / 2f).coerceAtLeast(0f)
        cap.set(capLeft, capTop, capLeft + baseW, capTop + baseH)

        if (shape == IslandShape.DORMANT || animW < 1f || animH < 1f) {
            outPrimary.setEmpty()
            outSecondary.setEmpty()
            return
        }

        if (shape == IslandShape.EXPANDED) {
            val left = (anchorCenterX - animW / 2f).coerceAtLeast(EDGE_PAD_DP * density)
            val right = (anchorCenterX + animW / 2f).coerceAtMost(viewWidth - EDGE_PAD_DP * density)
            val top = cap.bottom + 4f * density
            val bottom = top + animH
            outPrimary.set(left, top, right, bottom)
            outSecondary.setEmpty()
        } else {
            // COMPACT: sits directly on the camera cutout
            val left = (anchorCenterX - animW / 2f).coerceAtLeast(0f)
            val right = left + animW
            val top = (anchorCenterY - animH / 2f).coerceAtLeast(0f)
            val bottom = top + animH
            outPrimary.set(left, top, right, bottom)
            outSecondary.setEmpty()
        }
    }

    private fun anchoredLeft(
        viewWidth: Float,
        pillWidth: Float,
        position: String,
        density: Float,
        cutoutCenterX: Float,
    ): Float {
        val (minLeft, maxLeft) = horizontalRange(viewWidth, pillWidth, density)
        if (position == "center" && cutoutCenterX > 0f) {
            return (cutoutCenterX - pillWidth / 2f).coerceIn(minLeft, maxLeft)
        }
        return pillLeft(viewWidth, pillWidth, position, density)
    }

    /** Keeps the pill on screen. Side padding applies only when the pill still fits inside it. */
    private fun horizontalRange(viewWidth: Float, pillWidth: Float, density: Float): Pair<Float, Float> {
        val span = (viewWidth - pillWidth).coerceAtLeast(0f)
        val pad = EDGE_PAD_DP * density
        return if (span >= pad * 2f) pad to (span - pad) else 0f to span
    }
}
