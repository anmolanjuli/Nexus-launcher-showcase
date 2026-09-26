package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The cell-marker field that appears under a home-screen drag.
 *
 * A dot sits at every cell centre, and each dot's size and opacity fall off with distance from
 * the finger. That falloff is the whole point: the grid answers continuously as the hand moves
 * rather than switching between discrete states, which is what reads as "alive" in launchers
 * that do this well. It is also what keeps the effect informative instead of decorative — every
 * dot is telling you where things can land, and the brightest ones are telling you where this
 * one will.
 *
 * Cost is bounded and independent of how many icons are on the page: dots are bucketed into
 * [TIERS] intensity bands and each band goes out as a single [Canvas.drawPoints] call, so the
 * whole field is four draw calls over a fixed-size grid.
 */
internal class DragGridField(private val density: Float) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** 0 = absent, 1 = fully present. Eased toward the target so the field fades in and out. */
    private var presence = 0f
    private var coreColor = Color.WHITE
    private var haloColor = Color.BLACK

    private var tierPoints: Array<FloatArray> = Array(TIERS) { FloatArray(0) }
    private val tierCounts = IntArray(TIERS)
    private var capacityCells = -1

    /**
     * Each dot is a core plus a contrasting halo, taken from the theme's foreground and
     * background respectively — which is always a light/dark pair in every token set.
     *
     * A single flat colour cannot work here. In [com.nexus.launcher.theme.BackgroundLayerMode]
     * `WALLPAPER` the field sits directly over the user's wallpaper, so there is no known
     * backdrop to contrast against; a light dot vanishes on a light photo. Pairing the two means
     * one half of every dot separates from whatever happens to be behind it.
     */
    fun setTheme(tokens: com.nexus.launcher.theme.NexusColorTokens) {
        coreColor = tokens.textPrimary
        haloColor = tokens.bg
    }

    /**
     * Advances the fade toward present/absent. Returns true while still moving, so the caller
     * knows to schedule another frame — the field must be able to fade out after the finger
     * has already lifted and stopped generating touch-driven invalidations.
     */
    fun step(active: Boolean): Boolean {
        val target = if (active) 1f else 0f
        if (abs(target - presence) < SETTLED) {
            presence = target
            return false
        }
        presence += (target - presence) * FADE_RATE
        return true
    }

    val isVisible: Boolean get() = presence > 0.01f

    fun draw(
        canvas: Canvas,
        view: LauncherCanvasView,
        fingerX: Float,
        fingerY: Float,
        contentBox: android.graphics.RectF? = null
    ) {
        if (!isVisible) return
        val cells = view.homeGridCells
        if (cells.isEmpty()) return
        ensureCapacity(cells.size)
        java.util.Arrays.fill(tierCounts, 0)

        val falloff = cells[0].width() * FALLOFF_CELLS
        if (falloff <= 0f) return

        for (cell in cells) {
            val cx = cell.centerX()
            val cy = cell.centerY()
            val dist = if (contentBox != null) {
                val dx = maxOf(0f, maxOf(contentBox.left - cx, cx - contentBox.right))
                val dy = maxOf(0f, maxOf(contentBox.top - cy, cy - contentBox.bottom))
                if (dx == 0f && dy == 0f) 0f else hypot(dx, dy)
            } else {
                hypot(cx - fingerX, cy - fingerY)
            }
            val reach = (1f - dist / falloff).coerceIn(0f, 1f)
            // Squared so the bright core stays tight around the content instead of washing out
            // across half the page.
            val intensity = reach * reach
            val tier = (intensity * TIERS).toInt().coerceIn(0, TIERS - 1)
            val slot = tierCounts[tier]
            tierPoints[tier][slot * 2] = cx
            tierPoints[tier][slot * 2 + 1] = cy
            tierCounts[tier] = slot + 1
        }

        // Every halo first, then every core, so a neighbouring tier's halo can never land on top
        // of an already-drawn core.
        drawPass(canvas, halo = true)
        drawPass(canvas, halo = false)
    }

    private fun drawPass(canvas: Canvas, halo: Boolean) {
        for (tier in 0 until TIERS) {
            val count = tierCounts[tier]
            if (count == 0) continue
            val band = tier.toFloat() / (TIERS - 1).coerceAtLeast(1)
            val coreDp = MIN_DOT_DP + (MAX_DOT_DP - MIN_DOT_DP) * band
            dotPaint.strokeWidth = (if (halo) coreDp + HALO_DP else coreDp) * density
            val band0 = if (halo) HALO_MIN_ALPHA else MIN_ALPHA
            val band1 = if (halo) HALO_MAX_ALPHA else MAX_ALPHA
            val alpha = (band0 + (band1 - band0) * band) * presence
            dotPaint.color = ((if (halo) haloColor else coreColor) and 0x00FFFFFF) or
                ((alpha * 255f).toInt().coerceIn(0, 255) shl 24)
            canvas.drawPoints(tierPoints[tier], 0, count * 2, dotPaint)
        }
    }

    private fun ensureCapacity(cellCount: Int) {
        if (capacityCells == cellCount) return
        capacityCells = cellCount
        tierPoints = Array(TIERS) { FloatArray(cellCount * 2) }
    }

    private companion object {
        const val TIERS = 5
        /** Falloff radius, in cell widths. Larger spreads the glow further from the content. */
        const val FALLOFF_CELLS = 2.4f
        /** Core dot diameter, dp, from the dimmest band to the one under the content. */
        const val MIN_DOT_DP = 4.5f
        const val MAX_DOT_DP = 11.5f
        /** How far the contrasting halo extends past the core, dp. */
        const val HALO_DP = 3.5f
        const val MIN_ALPHA = 0.15f
        const val MAX_ALPHA = 1.0f
        const val HALO_MIN_ALPHA = 0.10f
        const val HALO_MAX_ALPHA = 0.85f
        const val FADE_RATE = 0.18f
        const val SETTLED = 0.004f
    }
}
