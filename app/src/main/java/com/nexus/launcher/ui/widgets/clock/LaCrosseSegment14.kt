package com.nexus.launcher.ui.widgets.clock

import android.graphics.Canvas
import android.graphics.Path

/**
 * Pre-built 14-segment starburst geometry and character mappings for alphanumeric text
 * (e.g. Day "MON"-"SUN", Month "JAN"-"DEC") on retro LCD panels.
 */
object LaCrosseSegment14 {

    const val UNIT_W = 100f
    const val UNIT_H = 180f

    // 0: A, 1: B, 2: C, 3: D, 4: E, 5: F, 6: G1, 7: G2, 8: H, 9: I, 10: J, 11: K, 12: L, 13: M
    private val seg14Paths = Array(14) { Path() }

    init {
        build14SegmentPaths()
    }

    private fun build14SegmentPaths() {
        val t = 14f
        val g = 2.5f
        val midX = UNIT_W / 2f
        val midY = UNIT_H / 2f

        // 0: A (Top)
        seg14Paths[0].apply {
            reset()
            moveTo(g + t / 2f, g)
            lineTo(UNIT_W - g - t / 2f, g)
            lineTo(UNIT_W - g - t, g + t)
            lineTo(g + t, g + t)
            close()
        }
        // 1: B (Top-Right)
        seg14Paths[1].apply {
            reset()
            moveTo(UNIT_W - g, g + t / 2f)
            lineTo(UNIT_W - g, midY - g)
            lineTo(UNIT_W - g - t, midY - g - t / 2f)
            lineTo(UNIT_W - g - t, g + t)
            close()
        }
        // 2: C (Bottom-Right)
        seg14Paths[2].apply {
            reset()
            moveTo(UNIT_W - g, midY + g)
            lineTo(UNIT_W - g, UNIT_H - g - t / 2f)
            lineTo(UNIT_W - g - t, UNIT_H - g - t)
            lineTo(UNIT_W - g - t, midY + g + t / 2f)
            close()
        }
        // 3: D (Bottom)
        seg14Paths[3].apply {
            reset()
            moveTo(g + t, UNIT_H - g - t)
            lineTo(UNIT_W - g - t, UNIT_H - g - t)
            lineTo(UNIT_W - g - t / 2f, UNIT_H - g)
            lineTo(g + t / 2f, UNIT_H - g)
            close()
        }
        // 4: E (Bottom-Left)
        seg14Paths[4].apply {
            reset()
            moveTo(g, midY + g)
            lineTo(g + t, midY + g + t / 2f)
            lineTo(g + t, UNIT_H - g - t)
            lineTo(g, UNIT_H - g - t / 2f)
            close()
        }
        // 5: F (Top-Left)
        seg14Paths[5].apply {
            reset()
            moveTo(g, g + t / 2f)
            lineTo(g + t, g + t)
            lineTo(g + t, midY - g - t / 2f)
            lineTo(g, midY - g)
            close()
        }
        // 6: G1 (Middle-Left)
        seg14Paths[6].apply {
            reset()
            moveTo(g + t / 2f, midY)
            lineTo(g + t, midY - t / 2f)
            lineTo(midX - g, midY - t / 2f)
            lineTo(midX - g, midY + t / 2f)
            lineTo(g + t, midY + t / 2f)
            close()
        }
        // 7: G2 (Middle-Right)
        seg14Paths[7].apply {
            reset()
            moveTo(midX + g, midY - t / 2f)
            lineTo(UNIT_W - g - t, midY - t / 2f)
            lineTo(UNIT_W - g - t / 2f, midY)
            lineTo(UNIT_W - g - t, midY + t / 2f)
            lineTo(midX + g, midY + t / 2f)
            close()
        }
        // 8: H (Top-Left diagonal to center)
        seg14Paths[8].apply {
            reset()
            moveTo(g + t * 1.2f, g + t * 1.2f)
            lineTo(g + t * 2.2f, g + t * 1.2f)
            lineTo(midX - g, midY - t)
            lineTo(midX - g - t, midY - t)
            close()
        }
        // 9: I (Top-Center vertical)
        seg14Paths[9].apply {
            reset()
            moveTo(midX - t / 2f, g + t + g)
            lineTo(midX + t / 2f, g + t + g)
            lineTo(midX + t / 2f, midY - t / 2f - g)
            lineTo(midX - t / 2f, midY - t / 2f - g)
            close()
        }
        // 10: J (Top-Right diagonal to center)
        seg14Paths[10].apply {
            reset()
            moveTo(UNIT_W - g - t * 2.2f, g + t * 1.2f)
            lineTo(UNIT_W - g - t * 1.2f, g + t * 1.2f)
            lineTo(midX + g + t, midY - t)
            lineTo(midX + g, midY - t)
            close()
        }
        // 11: K (Center to Bottom-Right diagonal)
        seg14Paths[11].apply {
            reset()
            moveTo(midX + g, midY + t)
            lineTo(midX + g + t, midY + t)
            lineTo(UNIT_W - g - t * 1.2f, UNIT_H - g - t * 1.2f)
            lineTo(UNIT_W - g - t * 2.2f, UNIT_H - g - t * 1.2f)
            close()
        }
        // 12: L (Bottom-Center vertical)
        seg14Paths[12].apply {
            reset()
            moveTo(midX - t / 2f, midY + t / 2f + g)
            lineTo(midX + t / 2f, midY + t / 2f + g)
            lineTo(midX + t / 2f, UNIT_H - g - t - g)
            lineTo(midX - t / 2f, UNIT_H - g - t - g)
            close()
        }
        // 13: M (Center to Bottom-Left diagonal)
        seg14Paths[13].apply {
            reset()
            moveTo(midX - g - t, midY + t)
            lineTo(midX - g, midY + t)
            lineTo(g + t * 2.2f, UNIT_H - g - t * 1.2f)
            lineTo(g + t * 1.2f, UNIT_H - g - t * 1.2f)
            close()
        }
    }

    /**
     * Renders a 14-segment character (A-Z, 0-9, space) at (x, y) with bounding width w and height h.
     */
    fun draw(canvas: Canvas, char: Char, x: Float, y: Float, w: Float, h: Float, showGhost: Boolean) {
        val mask = get14SegmentMask(char.uppercaseChar())

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(w / UNIT_W, h / UNIT_H)

        for (i in 0 until 14) {
            val isActive = (mask and (1 shl i)) != 0
            if (isActive) {
                canvas.drawPath(seg14Paths[i], LaCrosseSegmentDraw.activePaint)
            } else if (showGhost) {
                canvas.drawPath(seg14Paths[i], LaCrosseSegmentDraw.ghostPaint)
            }
        }
        canvas.restore()
    }

    private fun get14SegmentMask(c: Char): Int {
        return when (c) {
            'A' -> 0b00000001110111 // A, B, C, E, F, G1, G2
            'B' -> 0b00100100001111 // A, B, C, D, I, L, G2
            'C' -> 0b00000000111001 // A, D, E, F
            'D' -> 0b00100100001111 // A, B, C, D, I, L
            'E' -> 0b00000001011001 // A, D, E, F, G1
            'F' -> 0b00000001010001 // A, E, F, G1
            'G' -> 0b00000010111001 // A, C, D, E, F, G2
            'H' -> 0b00000011110110 // B, C, E, F, G1, G2
            'I' -> 0b00100100001001 // A, D, I, L
            'J' -> 0b00000000011110 // B, C, D, E
            'K' -> 0b00001001010000 or (1 shl 10) or (1 shl 11) // E, F, G1, J, K
            'L' -> 0b00000000111000 // D, E, F
            'M' -> 0b00000101010110 or (1 shl 8) or (1 shl 10) // B, C, E, F, H, J
            'N' -> 0b00000000110110 or (1 shl 8) or (1 shl 11) // B, C, E, F, H, K
            'O' -> 0b00000000111111 // A, B, C, D, E, F
            'P' -> 0b00000011010011 // A, B, E, F, G1, G2
            'Q' -> 0b00000000111111 or (1 shl 11) // A, B, C, D, E, F, K
            'R' -> 0b00000011010011 or (1 shl 11) // A, B, E, F, G1, G2, K
            'S' -> 0b00000011101101 // A, C, D, F, G1, G2
            'T' -> 0b00100100000001 // A, I, L
            'U' -> 0b00000000111110 // B, C, D, E, F
            'V' -> 0b00000000110000 or (1 shl 10) or (1 shl 13) // E, F, J, M
            'W' -> 0b00000000110110 or (1 shl 11) or (1 shl 13) // B, C, E, F, M, K
            'X' -> (1 shl 8) or (1 shl 10) or (1 shl 11) or (1 shl 13) // H, J, K, M
            'Y' -> (1 shl 8) or (1 shl 10) or (1 shl 12) // H, J, L
            'Z' -> 0b00000000001001 or (1 shl 10) or (1 shl 13) // A, D, J, M
            '0' -> 0b00000000111111 or (1 shl 10) or (1 shl 13)
            '1' -> 0b00000000000110
            '2' -> 0b00000011011011
            '3' -> 0b00000011001111
            '4' -> 0b00000011000110
            '5' -> 0b00000011101101
            '6' -> 0b00000011111101
            '7' -> 0b00000000000111
            '8' -> 0b00000011111111
            '9' -> 0b00000011001111
            else -> 0
        }
    }
}
