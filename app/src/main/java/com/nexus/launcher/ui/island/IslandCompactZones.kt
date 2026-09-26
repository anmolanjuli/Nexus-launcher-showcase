package com.nexus.launcher.ui.island

import android.graphics.RectF

/**
 * Where a closed capsule can actually draw, given the camera sitting in the middle of it.
 *
 * A capsule calibrated to cover the cutout is the look people want, but everything drawn inside
 * it then landed under the lens: the glyph, the timer, the track name. When the camera overlaps
 * the capsule the content is split around it instead — a glyph on the left of the lens, one
 * short line on the right — and each side is only used if there is room for it.
 */
object IslandCompactZones {

    /**
     * @param split true when the camera is inside the capsule and content must go around it.
     * @param leftEnd right edge of the usable space left of the camera.
     * @param rightStart left edge of the usable space right of it.
     */
    data class Zones(
        val split: Boolean = false,
        val leftStart: Float = 0f,
        val leftEnd: Float = 0f,
        val rightStart: Float = 0f,
        val rightEnd: Float = 0f,
    ) {
        val leftWidth: Float get() = (leftEnd - leftStart).coerceAtLeast(0f)
        val rightWidth: Float get() = (rightEnd - rightStart).coerceAtLeast(0f)
    }

    private val NONE = Zones()

    fun of(pill: RectF, cutoutCenterX: Float, cutoutWidth: Float, density: Float): Zones {
        if (cutoutCenterX <= 0f || cutoutWidth <= 0f) return NONE
        val pad = 8f * density
        val clear = 5f * density
        val holeLeft = cutoutCenterX - cutoutWidth / 2f - clear
        val holeRight = cutoutCenterX + cutoutWidth / 2f + clear
        // Only a hole that is actually inside the capsule is worth working around.
        if (holeRight <= pill.left + pad || holeLeft >= pill.right - pad) return NONE
        return Zones(
            split = true,
            leftStart = pill.left + pad,
            leftEnd = holeLeft,
            rightStart = holeRight,
            rightEnd = pill.right - pad,
        )
    }
}
