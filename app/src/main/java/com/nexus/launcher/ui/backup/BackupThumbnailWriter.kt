package com.nexus.launcher.ui.backup

import android.content.Context
import android.graphics.Bitmap
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.canvas.CanvasRenderer
import com.nexus.launcher.ui.canvas.HomeThumbnailPainter
import java.io.File
import java.io.FileOutputStream

/**
 * Renders one thumbnail per home page into a backup's staging directory.
 *
 * Runs without a live launcher canvas — [HomeThumbnailPainter] takes the items, grid dimensions
 * and wallpaper settings straight off the same snapshot that goes into `manifest.json`, so the
 * picture on a backup card always matches the layout that backup actually restores.
 *
 * Call on Dispatchers.IO.
 */
object BackupThumbnailWriter {

    /** Directory inside the zip. Written before `assets/` so the catalog can stop reading early. */
    const val THUMBS_DIR = "thumbs"

    /** Rendered once at this width; cards scale it down. Keeps zips small and decode cheap. */
    const val THUMB_WIDTH_PX = 360

    fun fileName(page: Int) = "page_$page.png"

    /**
     * Copies the real home-screen capture [HomeSnapshotStore] takes on the way out of the
     * launcher — actual wallpaper, icons and widget content. This is what a backup card should
     * show; the abstract painter below only stands in when nothing has been captured yet
     * (a backup taken before the launcher was ever left).
     */
    private fun copySnapshots(context: Context, staging: File): Pair<Int, Int>? {
        val snapshots = HomeSnapshotStore.snapshots(context)
        if (snapshots.isEmpty()) return null
        return try {
            val dir = File(staging, THUMBS_DIR).also { it.mkdirs() }
            snapshots.forEachIndexed { page, file ->
                file.copyTo(File(dir, fileName(page)), overwrite = true)
            }
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(snapshots.first().absolutePath, opts)
            (opts.outWidth.takeIf { it > 0 } ?: THUMB_WIDTH_PX) to
                (opts.outHeight.takeIf { it > 0 } ?: THUMB_WIDTH_PX)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * @return the pixel size the thumbnails were rendered at, for the catalog block, or null if
     *   nothing could be rendered.
     */
    fun write(
        context: Context,
        staging: File,
        items: List<HomeScreenItem>,
        settings: NexusSettingsData,
        pageCount: Int
    ): Pair<Int, Int>? {
        copySnapshots(context, staging)?.let { return it }
        return try {
            val dm = context.resources.displayMetrics
            val aspect = dm.heightPixels.toFloat().coerceAtLeast(1f) /
                dm.widthPixels.toFloat().coerceAtLeast(1f)
            val width = THUMB_WIDTH_PX
            val height = (width * aspect).toInt().coerceAtLeast(1)

            val tokens: NexusColorTokens = try {
                ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }

            // A standalone renderer configured from the snapshot — not the live one, which lives
            // on MainActivity's canvas and is not reachable from Settings.
            val renderer = CanvasRenderer(context).apply {
                wallpaperType = settings.wallpaperType
                wallpaperSolidColor = settings.wallpaperSolidColor
                wallpaperGradientStart = settings.wallpaperGradientStart
                wallpaperGradientEnd = settings.wallpaperGradientEnd
                wallpaperGradientDirection = settings.wallpaperGradientDirection
                wallpaperGalleryPath = settings.wallpaperGalleryPath
                wallpaperBlur = settings.wallpaperBlur
                wallpaperTintColor = settings.wallpaperTintColor
                wallpaperTintStrength = settings.wallpaperTintStrength
                themeTokens = tokens
            }

            val source = HomeThumbnailPainter.Source(
                items = items,
                gridCols = settings.homeColumns,
                gridRows = settings.homeRows,
                tokens = tokens,
                density = dm.density,
                canvasRenderer = renderer,
                dockContainerId = HomeScreenViewModel.DOCK_CONTAINER
            )

            val dir = File(staging, THUMBS_DIR).also { it.mkdirs() }
            for (page in 0 until pageCount.coerceAtLeast(1)) {
                val bmp = HomeThumbnailPainter.render(source, page, width, height)
                FileOutputStream(File(dir, fileName(page))).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
            }
            width to height
        } catch (_: Exception) {
            // A backup without previews is still a valid, restorable backup — never fail the
            // export over its cover art.
            null
        }
    }
}
