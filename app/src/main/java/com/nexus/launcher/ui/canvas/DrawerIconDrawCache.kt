package com.nexus.launcher.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-frame drawer draw helpers: bitmap + ellipsize caches and a reusable folder fallback paint.
 * Avoids PackageManager vector rasterization, TextUtils allocation, and Color.parseColor each frame.
 */
class DrawerIconDrawCache {

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val labelCache = ConcurrentHashMap<String, String>()
    private var lastIconSize = -1

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Reused for missing drawer-folder placeholder — never allocate in onDraw. */
    val folderFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x33, 0xFF, 0xFF, 0xFF)
        style = Paint.Style.FILL
    }

    private val subtitleColor = Color.argb(0x8A, 0xFF, 0xFF, 0xFF)
    private var lastAppFingerprint: String? = null

    fun subtitleColor(): Int = subtitleColor

    fun clear() {
        bitmapCache.clear()
        labelCache.clear()
        lastIconSize = -1
        lastAppFingerprint = null
    }

    /**
     * Clears only when the visible app set actually changes — avoids wiping bitmaps on
     * every filteredDrawerItems re-emit after restore (that caused mid-fling toBitmap jank).
     */
    fun clearIfAppsChanged(drawer: List<com.nexus.launcher.ui.model.DisplayItem>) {
        val fingerprint = buildString(drawer.size * 12) {
            for (item in drawer) {
                append(item.intent?.component?.packageName ?: item.label)
                append('\u0001')
                append(item.label)
                append('\u0002')
            }
        }
        if (fingerprint == lastAppFingerprint) return
        bitmapCache.clear()
        labelCache.clear()
        lastIconSize = -1
        lastAppFingerprint = fingerprint
    }

    fun drawIcon(
        canvas: Canvas,
        icon: Drawable,
        bounds: Rect,
        cacheKey: String,
        alpha: Int
    ) {
        val w = bounds.width().coerceAtLeast(1)
        val h = bounds.height().coerceAtLeast(1)
        if (w != lastIconSize && lastIconSize > 0 && kotlin.math.abs(w - lastIconSize) > 2) {
            bitmapCache.clear()
        }
        lastIconSize = w
        val key = "$cacheKey@$w"
        val bmp = bitmapCache[key] ?: run {
            val created = try {
                icon.toBitmap(w, h)
            } catch (_: Exception) {
                null
            }
            if (created != null) {
                bitmapCache[key] = created
                created
            } else {
                null
            }
        }
        if (bmp != null && !bmp.isRecycled) {
            bitmapPaint.alpha = alpha
            canvas.drawBitmap(bmp, null, bounds, bitmapPaint)
        } else {
            // Restore the Drawable's alpha afterwards. Icon Drawables are shared — the same
            // instance (and, via ConstantState, its siblings) is drawn by the home screen, the
            // dock and folder previews — so leaving a faded value here bleeds into whoever
            // draws it next. While the drawer fades out, every icon on this uncached path was
            // stamping its current alpha onto shared state, which is what read as icons
            // flickering against the ones served from the bitmap cache.
            // HomeScreenBatchDragRenderer already uses this save/restore pattern.
            val previousAlpha = icon.alpha
            icon.alpha = alpha
            icon.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            icon.draw(canvas)
            icon.alpha = previousAlpha
        }
    }

    fun ellipsizedLabel(
        raw: String,
        paint: android.text.TextPaint,
        maxWidth: Float,
        textSize: Float
    ): String {
        val key = "$raw|${maxWidth.toInt()}|${textSize.toInt()}"
        return labelCache.getOrPut(key) {
            TextUtils.ellipsize(raw, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        }
    }
}
