package com.nexus.launcher.ui.backup

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps a current picture of the real home screen so a backup can carry one.
 *
 * Backup export runs in SettingsActivity, and `FolderBlurCoordinator.findCanvas` resolves the
 * *calling* activity, so the exporter can never reach the live launcher to draw from.
 * MainActivity captures here on the way out instead; by the time the user has walked into
 * Settings and tapped Back Up Now, the snapshot is seconds old.
 *
 * Capture goes through [PixelCopy] — a read of the window's actual rendered frame. Drawing the
 * view tree into a software `Canvas` instead loses everything the launcher composites through a
 * `RenderNode`, and the entire home icon/folder pass is cached in one: `Canvas.drawRenderNode` is
 * a no-op off a hardware-accelerated canvas. That is why software captures came back holding only
 * the wallpaper and a few plain widget views.
 */
object HomeSnapshotStore {

    private const val DIR = "home_snapshots"

    /** Matches the width backups store previews at, so no second resize on export. */
    private const val OUTPUT_WIDTH_PX = 360

    /** Re-capturing on every pause is wasteful; the home screen rarely changes that fast. */
    private const val MIN_INTERVAL_MS = 1_500L

    private var lastCaptureAt = 0L

    fun dir(context: Context): File = File(context.cacheDir, DIR)

    fun fileFor(context: Context, page: Int) = File(dir(context), "page_$page.png")

    /** Page snapshots in order, or empty when nothing has been captured yet. */
    fun snapshots(context: Context): List<File> {
        val d = dir(context)
        if (!d.isDirectory) return emptyList()
        return generateSequence(0) { it + 1 }
            .map { fileFor(context, it) }
            .takeWhile { it.isFile }
            .toList()
    }

    /**
     * Reads the window's current frame.
     *
     * @param isHomeIdle false whenever something is layered over the home screen — the edit-mode
     *   radial menu, an open drawer, a sheet. Those are transient chrome, and baking one into a
     *   backup's cover misrepresents the layout being saved.
     * @param force skips the rate limit, for an explicit "capture now".
     */
    fun capture(activity: Activity, isHomeIdle: Boolean, force: Boolean = false) {
        if (!isHomeIdle) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCaptureAt < MIN_INTERVAL_MS) return
        val root = activity.findViewById<android.view.View>(com.nexus.launcher.R.id.main_container)
            ?: return
        val width = root.width
        val height = root.height
        if (width <= 0 || height <= 0) return
        val window = activity.window ?: return
        lastCaptureAt = now

        val context = activity.applicationContext
        val full = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return
        }

        val loc = IntArray(2)
        root.getLocationInWindow(loc)
        val region = Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)

        try {
            PixelCopy.request(window, region, full, { result ->
                if (result != PixelCopy.SUCCESS) {
                    full.recycle()
                    return@request
                }
                persist(context, full)
            }, Handler(Looper.getMainLooper()))
        } catch (_: Throwable) {
            full.recycle()
        }
    }

    /** Scales down and writes off the main thread; [full] is recycled either way. */
    private fun persist(context: Context, full: Bitmap) {
        Thread {
            var scaled: Bitmap? = null
            try {
                val outH = (OUTPUT_WIDTH_PX * full.height.toFloat() / full.width)
                    .toInt().coerceAtLeast(1)
                scaled = Bitmap.createScaledBitmap(full, OUTPUT_WIDTH_PX, outH, true)
                val d = dir(context)
                d.deleteRecursively()
                d.mkdirs()
                FileOutputStream(File(d, "page_0.png")).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (_: Throwable) {
                // A stale or missing snapshot only costs preview fidelity, never the backup.
            } finally {
                scaled?.recycle()
                full.recycle()
            }
        }.start()
    }
}
