package com.nexus.launcher.ui.widgets.mosaic

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Count-driven mosaic rects (normalized 0–1 inside the content inset).
 * Near-zero gaps so tiles sit close and fill the mosaic box.
 */
object LivingMosaicLayouts {

    data class NormRect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val opacity: Float = 1f,
        val scale: Float = 1f
    )

    /** A visible gap creating collage borders (exposes glass background). */
    private const val GAP = 0.008f

    fun packed(count: Int): List<NormRect> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf(NormRect(0f, 0f, 1f, 1f))
        count == 2 -> {
            val w = (1f - GAP) / 2f
            listOf(
                NormRect(0f, 0f, w, 1f),
                NormRect(w + GAP, 0f, w, 1f)
            )
        }
        count == 3 -> {
            val topH = 0.56f
            val botH = 1f - topH - GAP
            val botW = (1f - GAP) / 2f
            listOf(
                NormRect(0f, 0f, 1f, topH),
                NormRect(0f, topH + GAP, botW, botH),
                NormRect(botW + GAP, topH + GAP, botW, botH)
            )
        }
        count == 4 -> {
            val w = (1f - GAP) / 2f
            val h = (1f - GAP) / 2f
            listOf(
                NormRect(0f, 0f, w, h),
                NormRect(w + GAP, 0f, w, h),
                NormRect(0f, h + GAP, w, h),
                NormRect(w + GAP, h + GAP, w, h)
            )
        }
        else -> equalGrid(count)
    }

    private fun equalGrid(count: Int): List<NormRect> {
        val cols = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(2)
        val rows = ceil(count.toDouble() / cols).toInt().coerceAtLeast(1)
        val cellW = (1f - GAP * (cols - 1)) / cols
        val cellH = (1f - GAP * (rows - 1)) / rows
        return List(count) { i ->
            val c = i % cols
            val r = i / cols
            NormRect(
                left = c * (cellW + GAP),
                top = r * (cellH + GAP),
                width = cellW,
                height = cellH
            )
        }
    }

    fun singleFocus(count: Int, focusIndex: Int): List<NormRect> {
        if (count <= 0) return emptyList()
        val focus = focusIndex.coerceIn(0, count - 1)
        // Fixed-size carousel: every card is full cell; only X offset / opacity change.
        // Avoids mid-swipe reflow that made widgets look like they were resizing.
        val gap = 0.06f
        return List(count) { i ->
            val rel = i - focus
            when (rel) {
                0 -> NormRect(0f, 0f, 1f, 1f, 1f, 1f)
                -1 -> NormRect(-(1f + gap), 0f, 1f, 1f, 0.35f, 1f)
                1 -> NormRect(1f + gap, 0f, 1f, 1f, 0.35f, 1f)
                else -> {
                    val left = if (rel < 0) -(2f + gap * 2) else (2f + gap * 2)
                    NormRect(left, 0f, 1f, 1f, 0f, 1f)
                }
            }
        }
    }

    fun template(templateId: String?): List<NormRect>? = when (templateId) {
        "hero_left_2_stacked" -> {
            val wL = 0.6f - GAP / 2f
            val wR = 0.4f - GAP / 2f
            val h2 = 0.5f - GAP / 2f
            listOf(
                NormRect(0f, 0f, wL, 1f),
                NormRect(wL + GAP, 0f, wR, h2),
                NormRect(wL + GAP, h2 + GAP, wR, h2)
            )
        }
        "even_2x2" -> equalGrid(4)
        "hero_top_3_row" -> {
            val hT = 0.6f - GAP / 2f
            val hB = 0.4f - GAP / 2f
            val w3 = (1f - 2 * GAP) / 3f
            listOf(
                NormRect(0f, 0f, 1f, hT),
                NormRect(0f, hT + GAP, w3, hB),
                NormRect(w3 + GAP, hT + GAP, w3, hB),
                NormRect(w3 * 2 + GAP * 2, hT + GAP, w3, hB)
            )
        }
        "3_even_columns" -> {
            val w3 = (1f - 2 * GAP) / 3f
            listOf(
                NormRect(0f, 0f, w3, 1f),
                NormRect(w3 + GAP, 0f, w3, 1f),
                NormRect(w3 * 2 + GAP * 2, 0f, w3, 1f)
            )
        }
        "hero_left_2_cols_stacked" -> {
            val wL = 0.5f - GAP
            val wS = 0.25f - GAP / 2f
            val h2 = 0.5f - GAP / 2f
            listOf(
                NormRect(0f, 0f, wL, 1f),
                NormRect(wL + GAP, 0f, wS, h2),
                NormRect(wL + GAP, h2 + GAP, wS, h2),
                NormRect(wL + wS + GAP * 2, 0f, wS, h2),
                NormRect(wL + wS + GAP * 2, h2 + GAP, wS, h2)
            )
        }
        "3_even_rows" -> {
            val h3 = (1f - 2 * GAP) / 3f
            listOf(
                NormRect(0f, 0f, 1f, h3),
                NormRect(0f, h3 + GAP, 1f, h3),
                NormRect(0f, h3 * 2 + GAP * 2, 1f, h3)
            )
        }
        "even_3x3" -> equalGrid(9)
        else -> null
    }
}
