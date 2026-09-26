package com.nexus.launcher.ui.island

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.theme.NexusColorTokens
import java.util.Locale

/**
 * A live activity: what an app says it is doing, with its progress and its own buttons.
 *
 * Opened, it reads as one line of what, one of how far, a bar, and the buttons the app offered.
 * Collapsed, it is the app's icon and a thin progress line — enough to answer "how far along?"
 * without opening anything.
 */
object IslandActivityDraw {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /** The card is black whatever the theme is, so its actions are white, not theme-coloured. */
    private val ACTION_TEXT = android.graphics.Color.WHITE
    private val HAIRLINE = android.graphics.Color.argb(46, 255, 255, 255)
    private val pillTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val iconRect = RectF()
    private val barRect = RectF()

    /** Where each action button ended up, for the tap to find. */
    val actionRects = mutableListOf<RectF>()

    fun drawExpanded(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        titlePaint: TextPaint,
        subPaint: TextPaint,
        density: Float,
        pad: Float,
    ) {
        actionRects.clear()
        val iconSize = 22f * density
        val left = pill.left + pad + 4f * density
        val top = pill.top + pad + 2f * density
        payload.icon?.let { bmp ->
            if (!bmp.isRecycled) {
                iconRect.set(left, top, left + iconSize, top + iconSize)
                canvas.drawBitmap(bmp, null, iconRect, bitmapPaint)
            }
        }
        val textLeft = left + iconSize + 10f * density
        val textRight = pill.right - pad - 4f * density
        val textWidth = (textRight - textLeft).coerceAtLeast(1f)

        titlePaint.textSize = 14f * density
        canvas.drawText(
            TextUtils.ellipsize(payload.title, titlePaint, textWidth, TextUtils.TruncateAt.END).toString(),
            textLeft, top + 14f * density, titlePaint,
        )
        val second = secondLine(payload)
        if (second.isNotBlank()) {
            subPaint.textSize = 11f * density
            canvas.drawText(
                TextUtils.ellipsize(second, subPaint, textWidth, TextUtils.TruncateAt.END).toString(),
                textLeft, top + 29f * density, subPaint,
            )
        }

        var y = top + 40f * density
        if (payload.progress >= 0f || payload.busy) {
            drawBar(canvas, left, textRight, y, payload, tokens, density)
            y += 14f * density
        }
        drawActions(canvas, pill, y, payload, density, pad)
    }

    /** The collapsed pill: the app's icon, and how far along it is underneath. */
    fun drawCompact(
        canvas: Canvas,
        pill: RectF,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        titlePaint: TextPaint,
        density: Float,
        alpha: Float,
        zones: IslandCompactZones.Zones = IslandCompactZones.Zones(),
    ) {
        val iconSize = (pill.height() * 0.52f).coerceAtMost(20f * density)
        val left = if (zones.split) (zones.leftEnd - iconSize) else pill.left + 10f * density
        val cy = pill.centerY()
        payload.icon?.let { bmp ->
            if (!bmp.isRecycled && (!zones.split || zones.leftWidth >= iconSize)) {
                iconRect.set(left, cy - iconSize / 2f, left + iconSize, cy + iconSize / 2f)
                bitmapPaint.alpha = (alpha * 255f).toInt()
                canvas.drawBitmap(bmp, null, iconRect, bitmapPaint)
                bitmapPaint.alpha = 255
            }
        }
        val textLeft = if (zones.split) zones.rightStart else left + iconSize + 8f * density
        val textRight = if (zones.split) zones.rightEnd else pill.right - 12f * density
        titlePaint.textSize = 12f * density
        val line = secondLine(payload).ifBlank { payload.title }
        canvas.drawText(
            TextUtils.ellipsize(line, titlePaint, (textRight - textLeft).coerceAtLeast(1f), TextUtils.TruncateAt.END)
                .toString(),
            textLeft, cy + 4f * density, titlePaint,
        )
    }

    /** Percentage, remaining time, or whatever the app wrote as its second line. */
    fun secondLine(payload: IslandPayload): String {
        if (payload.chronoBaseMs > 0L) {
            val deltaMs = if (payload.countDown) {
                payload.chronoBaseMs - System.currentTimeMillis()
            } else {
                System.currentTimeMillis() - payload.chronoBaseMs
            }
            return clock(deltaMs.coerceAtLeast(0L))
        }
        if (payload.progress >= 0f) {
            return "${(payload.progress * 100f).toInt()}%"
        }
        return payload.subtitle
    }

    private fun clock(ms: Long): String {
        val total = ms / 1000L
        val hours = total / 3600L
        val minutes = (total % 3600L) / 60L
        val seconds = total % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun drawBar(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float,
        payload: IslandPayload,
        tokens: NexusColorTokens,
        density: Float,
    ) {
        val height = 4f * density
        val radius = height / 2f
        trackPaint.color = tokens.divider
        barPaint.color = tokens.accent
        barRect.set(left, y, right, y + height)
        canvas.drawRoundRect(barRect, radius, radius, trackPaint)
        if (payload.busy) {
            // Indeterminate: a short bar sliding along the track, so it reads as "working".
            val span = (right - left) * 0.3f
            val travel = (right - left - span).coerceAtLeast(0f)
            val phase = (SystemClock.elapsedRealtime() % 1600L) / 1600f
            val x = left + travel * kotlin.math.abs(1f - phase * 2f)
            barRect.set(x, y, x + span, y + height)
        } else {
            barRect.set(left, y, left + (right - left) * payload.progress, y + height)
        }
        canvas.drawRoundRect(barRect, radius, radius, barPaint)
    }

    private fun drawActions(
        canvas: Canvas,
        pill: RectF,
        y: Float,
        payload: IslandPayload,
        density: Float,
        pad: Float,
    ) {
        if (payload.actions.isEmpty()) return
        // Buttons of their own read as three grey lozenges dropped onto a black card. The shade
        // itself draws notification actions as plain text on a rule, and so does this.
        val height = 30f * density
        val left = pill.left + pad + 4f * density
        val right = pill.right - pad - 4f * density
        val width = (right - left) / payload.actions.size
        pillPaint.color = HAIRLINE
        pillPaint.strokeWidth = 1f * density
        canvas.drawLine(left, y, right, y, pillPaint)
        pillTextPaint.color = ACTION_TEXT
        pillTextPaint.textSize = 12f * density
        for ((index, action) in payload.actions.withIndex()) {
            val rect = RectF(left + width * index, y, left + width * (index + 1), y + height)
            if (index > 0) {
                canvas.drawLine(
                    rect.left, rect.top + height * 0.24f, rect.left, rect.bottom - height * 0.24f,
                    pillPaint,
                )
            }
            val label = TextUtils.ellipsize(
                action.title, pillTextPaint, width - 10f * density, TextUtils.TruncateAt.END,
            ).toString()
            canvas.drawText(label, rect.centerX(), rect.centerY() + 4f * density, pillTextPaint)
            actionRects.add(rect)
        }
    }
}
