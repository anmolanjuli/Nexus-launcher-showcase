package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.nexus.launcher.ui.NexusDesignSystem

/**
 * Static mock/preview generators for the Widget Picker catalog.
 */
object NexusCatalogPreviewDrawers {

    private var cachedMusicPreview: Bitmap? = null
    private var cachedWeatherPreview: Bitmap? = null
    private var cachedCalendarPreview: Bitmap? = null
    private val cachedMosaicPreviews = mutableMapOf<Pair<Int, Int>, Bitmap>()
    private val cachedLiveAppPreviews = mutableMapOf<Pair<Int, Int>, Bitmap>()

    fun clearCache() {
        cachedMusicPreview = null
        cachedWeatherPreview = null
        cachedCalendarPreview = null
        cachedMosaicPreviews.clear()
        cachedLiveAppPreviews.clear()
    }

    fun buildShortcutBoxPreview(spanX: Int, spanY: Int, dp: Float): Bitmap {
        val w = (160 * dp).toInt().coerceAtLeast(1)
        val h = (120 * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E1E1E") }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = Color.parseColor("#2A2A2A")
        }
        val well = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2C2C2C") }
        val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2f * dp
            color = Color.parseColor("#8E95A0")
        }
        val box = RectF(6f * dp, 6f * dp, w - 6f * dp, h - 6f * dp)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, bg)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, border)

        val gap = 4f * dp
        val cols = spanX.coerceAtLeast(1)
        val rows = spanY.coerceAtLeast(1)
        val pad = 8f * dp
        val tw = (box.width() - pad * 2f - gap * (cols - 1)) / cols
        val th = (box.height() - pad * 2f - gap * (rows - 1)) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = box.left + pad + c * (tw + gap)
                val top = box.top + pad + r * (th + gap)
                val sRect = RectF(left, top, left + tw, top + th)
                canvas.drawRoundRect(sRect, 8f * dp, 8f * dp, well)
                val cx = sRect.centerX()
                val cy = sRect.centerY()
                canvas.drawLine(cx - 3f * dp, cy, cx + 3f * dp, cy, plus)
                canvas.drawLine(cx, cy - 3f * dp, cx, cy + 3f * dp, plus)
            }
        }
        return bmp
    }

    fun buildMosaicPreview(spanX: Int, spanY: Int, dp: Float): Bitmap {
        cachedMosaicPreviews[spanX to spanY]?.let { return it }
        val w = (160 * dp).toInt().coerceAtLeast(1)
        val h = (120 * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_BASE) }
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        }
        val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#287EB8D4") }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f * dp, 16f * dp, bg)
        val inset = 10f * dp
        val box = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, glass)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, border)
        val gap = 4f * dp
        val cols = if (spanX >= 3) 2 else 2
        val rows = if (spanY >= 3) 2 else 2
        val tw = (box.width() - gap * (cols - 1)) / cols
        val th = (box.height() - gap * (rows - 1)) / rows
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val left = box.left + c * (tw + gap)
                val top = box.top + r * (th + gap)
                canvas.drawRoundRect(RectF(left, top, left + tw, top + th), 8f * dp, 8f * dp, tile)
            }
        }
        return bmp.also { cachedMosaicPreviews[spanX to spanY] = it }
    }

    fun buildMusicPreview(context: Context, dp: Float): Bitmap {
        cachedMusicPreview?.let { return it }
        val w = (160 * dp).toInt().coerceAtLeast(1)
        val h = (120 * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_BASE) }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f * dp, 16f * dp, bg)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        }
        val inset = 10f * dp
        val box = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, glass)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, border)

        val artPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#33FFFFFF") }
        val artSize = box.height() - 20f * dp
        val artLeft = box.left + 10f * dp
        val artTop = box.top + 10f * dp
        canvas.drawRect(artLeft, artTop, artLeft + artSize, artTop + artSize, artPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * dp
        }
        canvas.drawText(context.getString(com.nexus.launcher.R.string.preview_music_track), artLeft + artSize + 10f * dp, artTop + 16f * dp, textPaint)
        textPaint.color = Color.parseColor("#99FFFFFF")
        textPaint.textSize = 10f * dp
        canvas.drawText(context.getString(com.nexus.launcher.R.string.preview_music_artist), artLeft + artSize + 10f * dp, artTop + 32f * dp, textPaint)
        return bmp.also { cachedMusicPreview = it }
    }

    fun buildWeatherPreview(context: Context, dp: Float): Bitmap {
        cachedWeatherPreview?.let { return it }
        val w = (160 * dp).toInt().coerceAtLeast(1)
        val h = (120 * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_BASE) }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f * dp, 16f * dp, bg)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        }
        val inset = 10f * dp
        val box = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, glass)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, border)

        val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f * dp
        }
        canvas.drawText("72°", box.left + 14f * dp, box.top + 38f * dp, tempPaint)
        val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99FFFFFF")
            textSize = 10f * dp
        }
        canvas.drawText(context.getString(com.nexus.launcher.R.string.preview_weather_desc), box.left + 14f * dp, box.top + 54f * dp, descPaint)
        return bmp.also { cachedWeatherPreview = it }
    }

    fun buildCalendarPreview(context: Context, dp: Float): Bitmap {
        cachedCalendarPreview?.let { return it }
        val w = (160 * dp).toInt().coerceAtLeast(1)
        val h = (120 * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_BASE) }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f * dp, 16f * dp, bg)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        }
        val inset = 10f * dp
        val box = RectF(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, glass)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, border)

        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
            textSize = 24f * dp
        }
        canvas.drawText("24", box.left + 14f * dp, box.top + 34f * dp, datePaint)
        val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f * dp
        }
        canvas.drawText(java.text.DateFormatSymbols.getInstance(java.util.Locale.getDefault()).months[9].uppercase(), box.left + 50f * dp, box.top + 26f * dp, monthPaint)
        return bmp.also { cachedCalendarPreview = it }
    }

    fun buildLiveAppBoxPreview(context: Context, spanX: Int, spanY: Int, dp: Float): Bitmap {
        cachedLiveAppPreviews[spanX to spanY]?.let { return it }
        val w = (160 * dp).toInt().coerceAtLeast(1)
        val h = (120 * dp).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_BASE) }
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 16f * dp, 16f * dp, bg)
        val glass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_SURFACE) }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * dp
            color = Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        }
        val box = RectF(6f * dp, 6f * dp, w - 6f * dp, h - 6f * dp)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, glass)
        canvas.drawRoundRect(box, 14f * dp, 14f * dp, border)

        val gap = 4f * dp
        val cols = spanX.coerceAtLeast(1)
        val rows = spanY.coerceAtLeast(1)
        val pad = 8f * dp
        val tw = (box.width() - pad * 2f - gap * (cols - 1)) / cols
        val th = (box.height() - pad * 2f - gap * (rows - 1)) / rows
        val tokens = com.nexus.launcher.theme.NexusColorTokens.Dark

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val slotIndex = r * cols + c
                val left = box.left + pad + c * (tw + gap)
                val top = box.top + pad + r * (th + gap)
                val sRect = RectF(left, top, left + tw, top + th)
                val disc = minOf(tw, th) * 0.85f
                val cx = sRect.centerX()
                val cy = sRect.centerY()
                val iconRect = RectF(cx - disc / 2f, cy - disc / 2f, cx + disc / 2f, cy + disc / 2f)
                val iconBmp = com.nexus.launcher.ui.settings.SettingsPreviewIconRenderer.getBitmap(
                    context, slotIndex, 1, false, tokens
                )
                canvas.drawBitmap(iconBmp, null, iconRect, null)
            }
        }
        return bmp.also { cachedLiveAppPreviews[spanX to spanY] = it }
    }
}
