package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Canvas drawing routines for synthetic widget previews (Search pill, Living Mosaic, and Shortcut/App boxes).
 */
object NexusWidgetPreviewDrawers {

    fun renderSearchPreview(context: Context, w: Int, h: Int, dp: Float, tokens: NexusColorTokens): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val palette = NexusNeumorphicDraw.resolvePalette(context)
        val bounds = RectF(6f * dp, 6f * dp, w - 6f * dp, h - 6f * dp)
        val radius = bounds.height() / 2f

        val isGlass = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val isDefault = FrostedGlassEngine.isDefaultFlatStyleEnabled

        when {
            isGlass -> {
                val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (0.70f * 255f).toInt()
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(
                        fillAlpha,
                        Color.red(frostedTokens.surface),
                        Color.green(frostedTokens.surface),
                        Color.blue(frostedTokens.surface)
                    )
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.25f * dp
                    color = frostedTokens.border
                }
                canvas.drawRoundRect(bounds, radius, radius, bgPaint)
                canvas.drawRoundRect(bounds, radius, radius, strokePaint)
            }
            isDefault -> {
                NexusNeumorphicDraw.drawFlatSurface(canvas, bounds, radius, 11, palette, dp)
            }
            else -> {
                NexusNeumorphicDraw.drawRaisedSurface(canvas, bounds, radius, 11, palette, dp)
            }
        }

        val textColor = if (isGlass || isDefault) tokens.textSecondary else palette.textSecondary
        val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = minOf(14f * dp, h * 0.35f)
            typeface = android.graphics.Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            style = Paint.Style.STROKE
            strokeWidth = 2f * dp
            strokeCap = Paint.Cap.ROUND
        }

        val cx = bounds.left + 18f * dp
        val cy = bounds.centerY()
        val r = 6f * dp
        canvas.drawCircle(cx, cy - 2f * dp, r, iconPaint)
        canvas.drawLine(cx + 4f * dp, cy + 2f * dp, cx + 9f * dp, cy + 7f * dp, iconPaint)

        canvas.drawText(context.getString(R.string.search_hint), cx + 18f * dp, cy + 5f * dp, searchPaint)
        return bmp
    }

    fun renderMosaicPreview(w: Int, h: Int, dp: Float, spanX: Int, spanY: Int, tokens: NexusColorTokens): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bounds = RectF(4f * dp, 4f * dp, w - 4f * dp, h - 4f * dp)

        val isGlass = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) {
                Color.argb(160, Color.red(frostedTokens.surface), Color.green(frostedTokens.surface), Color.blue(frostedTokens.surface))
            } else {
                tokens.surfaceRaised
            }
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) frostedTokens.border else tokens.divider
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
        }
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) {
                Color.argb(120, Color.red(frostedTokens.surface), Color.green(frostedTokens.surface), Color.blue(frostedTokens.surface))
            } else {
                tokens.surface
            }
        }
        val tileStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) frostedTokens.border else tokens.divider
            style = Paint.Style.STROKE
            strokeWidth = 1f * dp
        }

        canvas.drawRoundRect(bounds, 16f * dp, 16f * dp, bgPaint)
        canvas.drawRoundRect(bounds, 16f * dp, 16f * dp, strokePaint)

        val pad = 8f * dp
        val gap = 4f * dp
        val box = RectF(bounds.left + pad, bounds.top + pad, bounds.right - pad, bounds.bottom - pad)
        val cols = spanX.coerceAtLeast(2)
        val rows = spanY.coerceAtLeast(2)
        val tw = (box.width() - gap * (cols - 1)) / cols
        val th = (box.height() - gap * (rows - 1)) / rows

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = box.left + c * (tw + gap)
                val top = box.top + r * (th + gap)
                val tRect = RectF(left, top, left + tw, top + th)
                canvas.drawRoundRect(tRect, 8f * dp, 8f * dp, tilePaint)
                canvas.drawRoundRect(tRect, 8f * dp, 8f * dp, tileStroke)
            }
        }
        return bmp
    }

    fun renderBoxPreview(context: Context, w: Int, h: Int, dp: Float, tokens: NexusColorTokens): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bounds = RectF(6f * dp, 6f * dp, w - 6f * dp, h - 6f * dp)
        val radius = 14f * dp

        val isGlass = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val isDefault = FrostedGlassEngine.isDefaultFlatStyleEnabled
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val palette = NexusNeumorphicDraw.resolvePalette(context)

        when {
            isGlass -> {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(160, Color.red(frostedTokens.surface), Color.green(frostedTokens.surface), Color.blue(frostedTokens.surface))
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = frostedTokens.border
                    style = Paint.Style.STROKE
                    strokeWidth = 1.25f * dp
                }
                canvas.drawRoundRect(bounds, radius, radius, bgPaint)
                canvas.drawRoundRect(bounds, radius, radius, strokePaint)
            }
            isDefault -> {
                NexusNeumorphicDraw.drawFlatSurface(canvas, bounds, radius, 1, palette, dp)
            }
            else -> {
                NexusNeumorphicDraw.drawRaisedSurface(canvas, bounds, radius, 1, palette, dp)
            }
        }

        val wellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isGlass) {
                Color.argb(120, Color.red(frostedTokens.surface), Color.green(frostedTokens.surface), Color.blue(frostedTokens.surface))
            } else if (isDefault) {
                tokens.surface
            } else {
                palette.debossedBg
            }
        }
        val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2f * dp
            color = if (isGlass || isDefault) tokens.textSecondary else palette.textSecondary
        }

        val gap = 4f * dp
        val pad = 8f * dp
        val tw = (bounds.width() - pad * 2f - gap) / 2f
        val th = (bounds.height() - pad * 2f - gap) / 2f
        for (r in 0 until 2) {
            for (c in 0 until 2) {
                val left = bounds.left + pad + c * (tw + gap)
                val top = bounds.top + pad + r * (th + gap)
                val sRect = RectF(left, top, left + tw, top + th)
                canvas.drawRoundRect(sRect, 8f * dp, 8f * dp, wellPaint)
                val cx = sRect.centerX()
                val cy = sRect.centerY()
                canvas.drawLine(cx - 3f * dp, cy, cx + 3f * dp, cy, plusPaint)
                canvas.drawLine(cx, cy - 3f * dp, cx, cy + 3f * dp, plusPaint)
            }
        }
        return bmp
    }
}
