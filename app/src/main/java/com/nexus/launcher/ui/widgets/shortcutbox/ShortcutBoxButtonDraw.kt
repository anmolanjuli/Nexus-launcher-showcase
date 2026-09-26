package com.nexus.launcher.ui.widgets.shortcutbox

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * The button behind a built-in Nexus shortcut (app drawer, Wi-Fi, Do Not Disturb, ...).
 *
 * ## Why built-ins need a plate and app shortcuts do not
 *
 * An app shortcut draws a full-colour launcher icon that carries its own background, so it reads
 * on any surface. A built-in is a flat single-colour glyph. Outside Neumorphism — which has always
 * drawn a raised surface here — nothing was painted behind it, and the glyph was tinted with
 * `textSecondary`, the deliberately dimmed grey. On a frosted card over a busy wallpaper that left
 * idle Nexus shortcuts as the faintest thing in the box: weaker than an *empty* slot, which at
 * least gets an outlined well. Only an active shortcut read clearly, because its accent glow was
 * the one thing drawing a plate.
 *
 * Idle built-ins now get the plate the active state was already implying, in the slot's own shape,
 * and a full-strength glyph ([glyphTint]). The active glow draws on top of that plate, so on/off
 * becomes a change of colour on a button that is always visibly there.
 */
internal class ShortcutBoxButtonDraw(private val dp: Float) {

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * dp
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The idle button. Glass: a raised frosted fill dense enough to hold the glyph against any
     * wallpaper, with the canonical glass border. Flat styles: the theme's raised surface.
     */
    fun drawPlate(
        canvas: Canvas,
        shape: Path,
        isGlass: Boolean,
        flushBorder: Boolean,
        tokens: NexusColorTokens,
        frosted: FrostedGlassEngine.FrostedTokens,
    ) {
        if (isGlass) {
            platePaint.color = ColorUtils.setAlphaComponent(frosted.surfaceRaised, PLATE_ALPHA_GLASS)
            borderPaint.color = frosted.border
        } else {
            platePaint.color = tokens.surfaceRaised
            borderPaint.color = tokens.divider
        }
        canvas.drawPath(shape, platePaint)
        if (!flushBorder) canvas.drawPath(shape, borderPaint)
    }

    /** The "on" state: an accent wash and rim over whatever plate is already there. */
    fun drawActiveGlow(canvas: Canvas, shape: Path, accent: Int) {
        glowPaint.style = Paint.Style.FILL
        glowPaint.color = ColorUtils.setAlphaComponent(accent, 0x2E)
        canvas.drawPath(shape, glowPaint)

        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = 1.5f * dp
        glowPaint.color = ColorUtils.setAlphaComponent(accent, 0x88)
        canvas.drawPath(shape, glowPaint)
    }

    /**
     * Glyph colour for a built-in. Active takes its accent; otherwise full-strength text, now that
     * it sits on a plate — `textSecondary` was only ever legible on Neumorphism's opaque surface,
     * which keeps its own palette colour.
     */
    fun glyphTint(activeAccent: Int?, neumorphicSecondary: Int?, tokens: NexusColorTokens): Int =
        activeAccent ?: neumorphicSecondary ?: tokens.textPrimary

    private companion object {
        /**
         * Denser than an empty well (0x4D) so a filled button and an empty slot read as different
         * things, but still translucent so the frost shows through.
         */
        const val PLATE_ALPHA_GLASS = 0xB8
    }
}
