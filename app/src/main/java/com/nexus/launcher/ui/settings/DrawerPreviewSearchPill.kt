package com.nexus.launcher.ui.settings

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Draws the App Drawer preview's mock chrome — the Split Search Pill and the standalone category
 * bar, either of which the user can hide or dock to either edge.
 *
 * Split out of [DrawerPreviewView] to keep that file inside the 400-line limit. Everything is
 * vector-drawn rather than inflated so the preview stays a single cheap [android.view.View].
 */
object DrawerPreviewSearchPill {

    /** Height the pill occupies in the preview's own (real-display-scaled) coordinate space. */
    fun heightPx(density: Float): Float = 34f * density

    /** Height of the standalone category bar in that same space. */
    fun categoryBarHeightPx(density: Float): Float = 26f * density

    fun draw(
        context: android.content.Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        left: Float,
        top: Float,
        right: Float,
        showCategorySegment: Boolean,
        showChipBeside: Boolean,
        showOverflow: Boolean,
        pillPaint: Paint,
        borderPaint: Paint,
        textPaint: TextPaint,
        rect: RectF
    ) {
        val h = heightPx(density)
        val overflowSlot = if (showOverflow) 26f * density else 0f
        val radius = h / 2f

        val oldAlignInit = textPaint.textAlign
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12f * density
        // The chip leads this row, so the pill starts after it and gives up that width.
        val chipLabel = context.getString(com.nexus.launcher.R.string.preview_cat_all_apps)
        val chipWidth = if (showChipBeside) textPaint.measureText(chipLabel) + 34f * density else 0f
        val chipGap = if (showChipBeside) 8f * density else 0f
        val pillLeft = left + chipWidth + chipGap
        val pillRight = right - overflowSlot
        textPaint.textAlign = oldAlignInit

        pillPaint.color = tokens.surfaceRaised
        if (showChipBeside) {
            rect.set(left, top, left + chipWidth, top + h)
            canvas.drawRoundRect(rect, radius, radius, pillPaint)
            val oldAlign2 = textPaint.textAlign
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = 12f * density
            textPaint.color = tokens.textPrimary
            canvas.drawText(chipLabel, left + 11f * density, top + h / 2f + 4f * density, textPaint)
            drawChevron(
                canvas, borderPaint, tokens, density,
                left + 11f * density + textPaint.measureText(chipLabel) + 5f * density,
                top + h / 2f
            )
            textPaint.textAlign = oldAlign2
        }

        rect.set(pillLeft, top, pillRight, top + h)
        canvas.drawRoundRect(rect, radius, radius, pillPaint)

        val oldAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 12f * density
        val baseline = top + h / 2f + 4f * density

        var x = pillLeft + 12f * density
        if (showCategorySegment) {
            textPaint.color = tokens.textPrimary
            val label = context.getString(com.nexus.launcher.R.string.preview_cat_all_apps)
            canvas.drawText(label, x, baseline, textPaint)
            x += textPaint.measureText(label) + 6f * density
            x = drawChevron(canvas, borderPaint, tokens, density, x, top + h / 2f) + 10f * density

            borderPaint.color = (tokens.textSecondary and 0x00FFFFFF) or (0x3D shl 24)
            canvas.drawLine(x, top + 8f * density, x, top + h - 8f * density, borderPaint)
            x += 10f * density
        }

        textPaint.color = tokens.textSecondary
        canvas.drawText(context.getString(com.nexus.launcher.R.string.picker_search_apps), x, baseline, textPaint)

        if (showOverflow) {
            drawOverflowGlyph(
                canvas, borderPaint, tokens, density, right - overflowSlot + 8f * density, top + h / 2f
            )
        }
        textPaint.textAlign = oldAlign
    }

    /**
     * The category bar as it renders outside the pill: a row of chips when [isStrip], otherwise a
     * single dropdown chip, with the overflow glyph trailing it when no pill is there to host it.
     */
    fun drawCategoryBar(
        context: android.content.Context,
        canvas: Canvas,
        tokens: NexusColorTokens,
        density: Float,
        left: Float,
        top: Float,
        right: Float,
        isStrip: Boolean,
        showOverflow: Boolean,
        pillPaint: Paint,
        borderPaint: Paint,
        textPaint: TextPaint,
        rect: RectF
    ) {
        val h = categoryBarHeightPx(density)
        val radius = h / 2f
        val overflowSlot = if (showOverflow) 26f * density else 0f
        val limit = right - overflowSlot

        val oldAlign = textPaint.textAlign
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 11f * density
        val baseline = top + h / 2f + 3.5f * density

        if (isStrip) {
            var x = left
            val chips = listOf(
                context.getString(com.nexus.launcher.R.string.preview_cat_all_apps) to true,
                context.getString(com.nexus.launcher.R.string.preview_cat_social) to false,
                context.getString(com.nexus.launcher.R.string.preview_cat_media) to false,
                context.getString(com.nexus.launcher.R.string.preview_cat_games) to false,
            )
            for ((label, isActive) in chips) {
                val w = textPaint.measureText(label) + 22f * density
                // The strip scrolls on the real drawer — clip the run rather than shrinking chips.
                if (x + w > limit) break
                rect.set(x, top, x + w, top + h)
                pillPaint.color = if (isActive) tokens.surfaceRaised else tokens.surface
                canvas.drawRoundRect(rect, radius, radius, pillPaint)
                if (isActive) {
                    borderPaint.color = tokens.divider
                    canvas.drawRoundRect(rect, radius, radius, borderPaint)
                }
                textPaint.color = if (isActive) tokens.textPrimary else tokens.textSecondary
                canvas.drawText(label, x + 11f * density, baseline, textPaint)
                x += w + 6f * density
            }
        } else {
            val label = context.getString(com.nexus.launcher.R.string.preview_cat_all_apps)
            val w = textPaint.measureText(label) + 34f * density
            rect.set(left, top, left + w, top + h)
            pillPaint.color = tokens.surfaceRaised
            canvas.drawRoundRect(rect, radius, radius, pillPaint)
            borderPaint.color = tokens.divider
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
            textPaint.color = tokens.textPrimary
            canvas.drawText(label, left + 11f * density, baseline, textPaint)
            drawChevron(
                canvas, borderPaint, tokens, density,
                left + 11f * density + textPaint.measureText(label) + 5f * density,
                top + h / 2f
            )
        }

        if (showOverflow) {
            drawOverflowGlyph(canvas, borderPaint, tokens, density, limit + 8f * density, top + h / 2f)
        }
        textPaint.textAlign = oldAlign
    }

    /** Two short strokes — cheaper and crisper at this size than a scaled asset. Returns its right edge. */
    private fun drawChevron(
        canvas: Canvas, paint: Paint, tokens: NexusColorTokens, density: Float, x: Float, cy: Float
    ): Float {
        val w = 4f * density
        paint.color = tokens.textSecondary
        canvas.drawLine(x, cy - w / 2f, x + w, cy + w / 2f, paint)
        canvas.drawLine(x + w, cy + w / 2f, x + w * 2f, cy - w / 2f, paint)
        return x + w * 2f
    }

    private fun drawOverflowGlyph(
        canvas: Canvas, paint: Paint, tokens: NexusColorTokens, density: Float, left: Float, cy: Float
    ) {
        paint.color = tokens.textPrimary
        val rightEdge = left + 12f * density
        val gap = 4f * density
        canvas.drawLine(left, cy - gap, rightEdge, cy - gap, paint)
        canvas.drawLine(left, cy, rightEdge, cy, paint)
        canvas.drawLine(left, cy + gap, rightEdge, cy + gap, paint)
    }
}
