package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Shared canvas primitives for the App Drawer Categories live preview.
 * Paints come from [DrawerPreviewView] — nothing is allocated in onDraw.
 */
object DrawerPreviewCategoriesChrome {

    fun plate(
        canvas: Canvas,
        plate: RectF,
        centerX: Float,
        top: Float,
        width: Float,
        height: Float,
        tokens: NexusColorTokens,
        fill: Paint,
        border: Paint,
        density: Float,
        radiusDp: Float = 14f,
    ) {
        plate.set(centerX - width / 2f, top, centerX + width / 2f, top + height)
        fill.color = tokens.surfaceRaised
        val r = radiusDp * density
        canvas.drawRoundRect(plate, r, r, fill)
        canvas.drawRoundRect(plate, r, r, border)
    }

    fun icon(
        context: Context,
        canvas: Canvas,
        settings: PendingDrawerSettings,
        tokens: NexusColorTokens,
        isLight: Boolean,
        index: Int,
        left: Float,
        top: Float,
        size: Float,
        iconPaint: Paint,
        destRect: Rect,
    ) {
        val bmp = SettingsPreviewIconRenderer.getBitmap(
            context, index, settings.iconShape, isLight, tokens,
        )
        destRect.set(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        canvas.drawBitmap(bmp, null, destRect, iconPaint)
    }

    fun iconRow(
        context: Context,
        canvas: Canvas,
        settings: PendingDrawerSettings,
        tokens: NexusColorTokens,
        isLight: Boolean,
        startIndex: Int,
        count: Int,
        left: Float,
        top: Float,
        width: Float,
        iconSize: Float,
        iconPaint: Paint,
        destRect: Rect,
        density: Float,
    ) {
        val gap = 6f * density
        val cell = ((width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val size = iconSize.coerceAtMost(cell)
        for (i in 0 until count) {
            val cx = left + i * (cell + gap) + cell / 2f
            icon(
                context, canvas, settings, tokens, isLight, startIndex + i,
                cx - size / 2f, top, size, iconPaint, destRect,
            )
        }
    }

    fun iconGrid(
        context: Context,
        canvas: Canvas,
        settings: PendingDrawerSettings,
        tokens: NexusColorTokens,
        isLight: Boolean,
        startIndex: Int,
        cols: Int,
        rows: Int,
        left: Float,
        top: Float,
        width: Float,
        iconSize: Float,
        gap: Float,
        iconPaint: Paint,
        destRect: Rect,
    ) {
        val cell = width / cols
        var index = startIndex
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cx = left + col * cell + cell / 2f
                val cy = top + row * (iconSize + gap)
                icon(
                    context, canvas, settings, tokens, isLight, index,
                    cx - iconSize / 2f, cy, iconSize, iconPaint, destRect,
                )
                index++
            }
        }
    }

    fun rail(
        canvas: Canvas,
        railPaint: TextPaint,
        tokens: NexusColorTokens,
        density: Float,
        x: Float,
        top: Float,
        bottom: Float,
    ) {
        val letters = arrayOf("A", "D", "M", "S", "Z")
        val h = (bottom - top).coerceAtLeast(1f)
        val step = h / letters.size
        railPaint.textAlign = Paint.Align.CENTER
        railPaint.textSize = 9f * density
        railPaint.color = tokens.textSecondary
        letters.forEachIndexed { i, letter ->
            canvas.drawText(letter, x, top + step * i + step * 0.7f, railPaint)
        }
    }

    fun glyph(
        context: Context,
        canvas: Canvas,
        destRect: Rect,
        @DrawableRes res: Int,
        color: Int,
        left: Float,
        top: Float,
        size: Float,
    ) {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        destRect.set(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        drawable.setBounds(destRect)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    fun chevron(
        canvas: Canvas,
        paint: Paint,
        tokens: NexusColorTokens,
        density: Float,
        x: Float,
        cy: Float,
    ) {
        val w = 4f * density
        val old = paint.color
        paint.color = tokens.textSecondary
        canvas.drawLine(x, cy - w / 2f, x + w, cy, paint)
        canvas.drawLine(x + w, cy, x + w * 2f, cy - w / 2f, paint)
        paint.color = old
    }
}
