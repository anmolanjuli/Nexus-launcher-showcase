package com.nexus.launcher.ui.island

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Dedicated draw helper for incoming phone and VoIP call projection in Nexus Island.
 */
object IslandCallDraw {

    private const val COLOR_CALL_GREEN = 0xFF34C759.toInt()
    private const val COLOR_CALL_RED = 0xFFFF3B30.toInt()

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val btnIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawCompact(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        glyphPaint: Paint,
        titlePaint: TextPaint,
        density: Float,
        contentAlpha: Float,
    ) {
        val pad = 10f * density
        val glyph = (pill.height() * 0.48f).coerceIn(12f * density, 16f * density)
        val cx = pill.left + pad + glyph / 2f
        val cy = pill.centerY()

        // Pulsing green call glyph
        val t = (SystemClock.uptimeMillis() % 1000L) / 1000f
        val pulse = 0.7f + 0.3f * kotlin.math.sin(t * Math.PI.toFloat() * 2f)
        glyphPaint.color = COLOR_CALL_GREEN
        glyphPaint.alpha = (contentAlpha * pulse * 255f).toInt()
        IslandGlyphs.draw(canvas, IslandKind.CALL, cx, cy, glyph, glyphPaint)

        // Green answer dot on right
        val rightEdge = pill.right - pad
        glyphPaint.color = COLOR_CALL_GREEN
        glyphPaint.alpha = (contentAlpha * 255f).toInt()
        canvas.drawCircle(rightEdge - 6f * density, cy, 5f * density, glyphPaint)

        // Caller name in center
        titlePaint.color = Color.WHITE
        titlePaint.alpha = (contentAlpha * 255f).toInt()
        titlePaint.textSize = (pill.height() * 0.40f).coerceIn(10f * density, 13f * density)
        val textBaseline = cy - (titlePaint.ascent() + titlePaint.descent()) / 2f
        val textLeft = cx + glyph / 2f + 8f * density
        val avail = (rightEdge - 16f * density - textLeft).coerceAtLeast(0f)
        if (avail > 20f * density) {
            val text = TextUtils.ellipsize(payload.title, titlePaint, avail, TextUtils.TruncateAt.END)
            canvas.drawText(text.toString(), textLeft, textBaseline, titlePaint)
        }
    }

    fun drawExpanded(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        titlePaint: TextPaint,
        subPaint: TextPaint,
        density: Float,
        pad: Float,
        declineRect: RectF,
        answerRect: RectF,
        context: Context,
    ) {
        val btnSize = 44f * density
        val rowTop = pill.bottom - pad - btnSize - 4f * density

        // Buttons row at bottom: Decline (red) on left, Answer (green) on right
        val marginH = 28f * density
        declineRect.set(pill.left + marginH, rowTop, pill.left + marginH + btnSize, rowTop + btnSize)
        answerRect.set(pill.right - marginH - btnSize, rowTop, pill.right - marginH, rowTop + btnSize)

        // Caller info header
        titlePaint.textSize = 17f * density
        titlePaint.color = Color.WHITE
        titlePaint.isFakeBoldText = true
        val titleAvail = (pill.width() - pad * 2f - 40f * density).coerceAtLeast(0f)
        val caller = TextUtils.ellipsize(payload.title, titlePaint, titleAvail, TextUtils.TruncateAt.END)
        val titleY = pill.top + pad + 20f * density
        canvas.drawText(caller.toString(), pill.left + pad + 16f * density, titleY, titlePaint)

        subPaint.textSize = 12f * density
        subPaint.color = COLOR_CALL_GREEN
        val subY = titleY + 18f * density
        val statusText = payload.subtitle.takeIf { it.isNotEmpty() } ?: context.getString(R.string.island_incoming_call)
        canvas.drawText(statusText, pill.left + pad + 16f * density, subY, subPaint)

        // Decline Button (Red circle with Decline label/glyph)
        btnBgPaint.color = COLOR_CALL_RED
        btnBgPaint.style = Paint.Style.FILL
        canvas.drawCircle(declineRect.centerX(), declineRect.centerY(), btnSize / 2f, btnBgPaint)
        btnIconPaint.color = Color.WHITE
        btnIconPaint.textSize = 11f * density
        btnIconPaint.textAlign = Paint.Align.CENTER
        val declineLabel = context.getString(R.string.island_call_decline)
        canvas.drawText(declineLabel, declineRect.centerX(), declineRect.centerY() + 4f * density, btnIconPaint)

        // Answer Button (Green circle with Answer label/glyph)
        btnBgPaint.color = COLOR_CALL_GREEN
        canvas.drawCircle(answerRect.centerX(), answerRect.centerY(), btnSize / 2f, btnBgPaint)
        val answerLabel = context.getString(R.string.island_call_answer)
        canvas.drawText(answerLabel, answerRect.centerX(), answerRect.centerY() + 4f * density, btnIconPaint)
        btnIconPaint.textAlign = Paint.Align.LEFT
    }
}
