package com.nexus.launcher.ui.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderIconRenderer
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches the fully composed drawer-folder tile. Folder chrome and preview icons are
 * expensive to redraw while scrolling, unlike ordinary drawer app bitmaps.
 */
class DrawerFolderTileCache {

    private val bitmaps = ConcurrentHashMap<String, Bitmap>()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun clear() {
        bitmaps.clear()
    }

    fun draw(
        canvas: Canvas,
        folder: HomeScreenItem,
        contents: List<HomeScreenItem>,
        bounds: Rect,
        alpha: Int
    ): Boolean {
        val key = cacheKey(folder, contents, bounds.width(), bounds.height())
        val bitmap = bitmaps[key] ?: return false
        bitmapPaint.alpha = alpha
        canvas.drawBitmap(bitmap, null, bounds, bitmapPaint)
        return true
    }

    /** Builds one tile while the home screen is idle; returns true only for new work. */
    fun prewarm(
        context: Context,
        folder: HomeScreenItem,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        density: Float,
        width: Int,
        height: Int
    ): Boolean {
        if (width <= 0 || height <= 0) return false
        val key = cacheKey(folder, contents, width, height)
        if (bitmaps.containsKey(key)) return false
        bitmaps[key] = render(context, folder, contents, iconCache, density, width, height)
        return true
    }

    private fun render(
        context: Context,
        folder: HomeScreenItem,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        density: Float,
        width: Int,
        height: Int
    ): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val bounds = RectF(0f, 0f, safeWidth.toFloat(), safeHeight.toFloat())
        FolderIconRenderer(context).drawFolder(
            canvas = Canvas(bitmap),
            cx = bounds.centerX(),
            cy = bounds.centerY(),
            folderItem = folder,
            contents = contents,
            iconCache = iconCache,
            density = density,
            folderRadiusOverride = minOf(safeWidth, safeHeight) / 2f,
            folderBoundsOverride = bounds,
            matchAppIconSize = true
        )
        return bitmap
    }

    private fun cacheKey(
        folder: HomeScreenItem,
        contents: List<HomeScreenItem>,
        width: Int,
        height: Int
    ): String = buildString {
        append(folder.id)
        append('|')
        append(folder.folderConfigJson.hashCode())
        append('|')
        append(width)
        append('x')
        append(height)
        contents.forEach {
            append('|')
            append(it.packageName)
        }
    }
}
