package com.nexus.launcher.ui.drawercategories

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/**
 * A folder's real icon — the same chrome and preview icons the grid and list drawer draw
 * ([com.nexus.launcher.ui.folder.FolderIconRenderer]) — as a Drawable a category tile can show.
 * Categories mode used to use a flat folder glyph, so the same folder looked like two things.
 *
 * Drawn once per folder and size into a bitmap, off the main thread with the rest of the model
 * build; null when the folder is not on the canvas yet, and the tile keeps its glyph.
 */
object CategoryFolderIcon {

    private val cache = HashMap<String, Drawable>()

    fun of(canvas: LauncherCanvasView?, folderId: Long, sizePx: Int): Drawable? {
        canvas ?: return null
        if (sizePx <= 0) return null
        val folder = canvas.folderById[folderId] ?: return null
        val contents = canvas.homeScreenRenderer.folderContentsForItem(folder)
        val key = "$folderId|$sizePx|${folder.folderConfigJson}|${contents.joinToString { it.id.toString() }}"
        cache[key]?.let { return it }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val bounds = RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat())
        canvas.homeScreenRenderer.folderIconRenderer.drawFolder(
            canvas = Canvas(bitmap),
            cx = bounds.centerX(),
            cy = bounds.centerY(),
            folderItem = folder,
            contents = contents,
            iconCache = canvas.homeScreenRenderer.iconCache,
            density = canvas.resources.displayMetrics.density,
            folderRadiusOverride = sizePx / 2f,
            folderBoundsOverride = bounds,
            showLabels = false,
            matchAppIconSize = true,
        )
        val drawable = BitmapDrawable(canvas.resources, bitmap)
        if (cache.size > MAX_CACHED) cache.clear()
        cache[key] = drawable
        return drawable
    }

    /** Sizes vary by layout; one tile size covers the drawer's own tiles. */
    fun sizeFor(canvas: LauncherCanvasView?): Int {
        val density = canvas?.resources?.displayMetrics?.density ?: return 0
        return (TILE_DP * density).toInt()
    }

    private const val TILE_DP = 56f
    private const val MAX_CACHED = 48
}
