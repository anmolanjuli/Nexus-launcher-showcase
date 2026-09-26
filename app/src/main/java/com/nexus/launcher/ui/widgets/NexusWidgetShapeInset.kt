package com.nexus.launcher.ui.widgets

/**
 * Single source of truth for safe content insets across widget shapes (Squircle, Square, Pill, Circle).
 * Guarantees content never clips against circular arcs or pill end-caps.
 */
object NexusWidgetShapeInset {

    data class Inset(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    fun getInsets(shapeStyle: Int, w: Float, h: Float, dp: Float): Inset {
        return when (shapeStyle) {
            0 -> {
                // Circle: the square inscribed in the centred circle (side = d / sqrt 2), less a
                // little so content does not touch the rim. Measured from the circle, not the
                // widget — on a wide widget the old 18%-of-each-side inset put content outside it.
                val d = minOf(w, h)
                val side = d * 0.66f
                val ix = ((w - side) / 2f).coerceAtLeast(10f * dp)
                val iy = ((h - side) / 2f).coerceAtLeast(10f * dp)
                Inset(ix, iy, ix, iy)
            }
            11 -> {
                // Pill / Capsule: semicircular caps on ends
                if (w >= h) {
                    // Horizontal Pill: semicircular caps on left & right
                    val ix = (h * 0.28f).coerceAtLeast(18f * dp)
                    val iy = 14f * dp
                    Inset(ix, iy, ix, iy)
                } else {
                    // Vertical Pill: semicircular caps on top & bottom
                    val ix = 16f * dp
                    val iy = (w * 0.28f).coerceAtLeast(16f * dp)
                    Inset(ix, iy, ix, iy)
                }
            }
            else -> {
                // Squircle (1), Square (2), or default
                val baseH = 16f * dp
                val baseV = 14f * dp
                Inset(baseH, baseV, baseH, baseV)
            }
        }
    }
}
