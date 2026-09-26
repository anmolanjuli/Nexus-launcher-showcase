package com.nexus.launcher.ui.widgets

import android.graphics.RectF

/**
 * Where the slots of the three icon boxes — App Box, Live App and Shortcut Box — sit: the one
 * definition that drawing, tapping, long-pressing and dropping onto a slot all read.
 *
 * Until 2026-09-24 each renderer had two copies (draw and hit-test) and the boxes disagreed: App
 * Box and Live App padded by 6dp, which is exactly the 6dp inset of the Neumorphic/Default card,
 * so their icons touched the card's top and bottom edges. All three now use the Shortcut Box's
 * padding, and the gap between icons is the box's Icon spacing setting (Tight also packs them).
 *
 * Mutable and reused so a draw does not allocate. Keep one instance for drawing and another for
 * hit-testing, so a touch never reads a half-updated layout.
 */
internal class BoxSlotGrid {

    var cols = 1
        private set
    var rows = 1
        private set
    var slotW = 0f
        private set
    var slotH = 0f
        private set
    private var gap = 0f
    private var originX = 0f
    private var originY = 0f
    private var disc = DISC
    private var discWithLabels = DISC_WITH_LABELS

    fun layout(width: Float, height: Float, cols: Int, rows: Int, spacing: Int, dp: Float): BoxSlotGrid {
        this.cols = cols
        this.rows = rows
        val padding = PADDING_DP * dp
        gap = gapDp(spacing) * dp
        slotW = ((width - padding * 2f - (cols - 1) * gap).coerceAtLeast(1f)) / cols
        slotH = ((height - padding * 2f - (rows - 1) * gap).coerceAtLeast(1f)) / rows
        originX = padding
        originY = padding
        if (spacing == SPACING_TIGHT) {
            // Tight packs square cells together and centres the group. Spreading the slots
            // across a wide box left the spare width between icons whatever the gap was.
            val cell = minOf(slotW, slotH)
            slotW = cell
            slotH = cell
            originX = (width - cols * cell - (cols - 1) * gap) / 2f
            originY = (height - rows * cell - (rows - 1) * gap) / 2f
            disc = DISC_TIGHT
            discWithLabels = DISC_WITH_LABELS_TIGHT
        } else {
            disc = DISC
            discWithLabels = DISC_WITH_LABELS
        }
        return this
    }

    fun slotRect(col: Int, row: Int, out: RectF) {
        val left = originX + col * (slotW + gap)
        val top = originY + row * (slotH + gap)
        out.set(left, top, left + slotW, top + slotH)
    }

    /** The app's circle inside a slot; smaller when a label has to fit underneath. */
    fun discDiameter(withLabels: Boolean): Float =
        minOf(slotW, slotH) * (if (withLabels) discWithLabels else disc)

    /** The slot index under a point, or null when it falls in the padding or a gap. */
    fun slotAt(x: Float, y: Float): Int? {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = originX + c * (slotW + gap)
                val top = originY + r * (slotH + gap)
                if (x >= left && x <= left + slotW && y >= top && y <= top + slotH) return r * cols + c
            }
        }
        return null
    }

    companion object {
        const val SPACING_TIGHT = 0
        const val SPACING_NORMAL = 1
        const val SPACING_WIDE = 2

        private const val PADDING_DP = 10f
        private const val DISC = 0.88f
        private const val DISC_WITH_LABELS = 0.76f
        private const val DISC_TIGHT = 0.98f
        private const val DISC_WITH_LABELS_TIGHT = 0.84f

        /** A stored spacing, from JSON that may predate the setting or come from a newer build. */
        fun parseSpacing(raw: Int): Int = when (raw) {
            SPACING_TIGHT, SPACING_NORMAL, SPACING_WIDE -> raw
            else -> SPACING_NORMAL
        }

        private fun gapDp(spacing: Int): Float = when (spacing) {
            SPACING_TIGHT -> 2f
            SPACING_NORMAL -> 8f
            SPACING_WIDE -> 16f
            else -> error("Unknown box icon spacing $spacing — parse it with parseSpacing()")
        }
    }
}
