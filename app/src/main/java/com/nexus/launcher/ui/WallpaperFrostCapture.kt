package com.nexus.launcher.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import java.io.File
import java.io.FileOutputStream

/**
 * One-time, user-initiated capture of a CLEAN (icons/widgets/dock-free) snapshot of the current
 * wallpaper, used as the frosted-glass blur source for external and live wallpapers.
 *
 * `WallpaperManager.getDrawable()`/`peekDrawable()` — the "obvious" way to read the wallpaper
 * file directly — require the legacy `READ_EXTERNAL_STORAGE` permission, which is capped at
 * `maxSdkVersion=32` in this app's manifest and, per Google's own issue tracker (issues
 * 236690156 / 237124750, marked Won't Fix), is deliberately restricted to privileged system apps
 * regardless of any permission grant. So that path can never work here.
 *
 * An earlier implementation used [android.view.PixelCopy] against this app's own `Window` to grab
 * a "clean" frame instead (no explicit user consent needed, since it's the app's own surface). That
 * turned out to be fundamentally broken: `PixelCopy.request(Window, ...)` only ever captures the
 * content actually drawn into that window's own `ViewRootImpl` — it does NOT include the system
 * wallpaper, which Android composites in separately as its own `TYPE_WALLPAPER` surface, shown
 * through `FLAG_SHOW_WALLPAPER`/`windowShowWallpaper` (both of which this app sets). Hiding this
 * app's own views before such a capture therefore yields a fully transparent buffer, which JPEG
 * compression (no alpha channel) turns solid black — explaining why a capture would report success
 * but never show any real wallpaper texture behind frosted surfaces.
 *
 * This uses [MediaProjection] instead, which captures Android's actual compositor output — every
 * surface on screen, wallpaper included — at the cost of a one-time system consent dialog per
 * capture and a transient "screen is being recorded" indicator while the capture is in flight
 * (well under a second: hide own UI, wait for it to composite, grab one frame, tear everything
 * down immediately). Capture ONCE and save the single clean frame permanently (like the existing
 * Gallery wallpaper flow) rather than resampling continuously — this app's wallpaper never shifts
 * with page-swipe scrolling, so one frame stays valid until the user changes their wallpaper.
 */
object WallpaperFrostCapture {

    private const val REGISTRY_KEY = "wallpaper_frost_capture_projection"

    // Must match HomeScreenFrameCache's read side — kept in sync via HomeScreenFrameCache
    // exposing this same constant rather than duplicating the literal.
    val FILE_NAME get() = HomeScreenFrameCache.FROST_CAPTURE_FILE_NAME

    fun capturedFile(context: android.content.Context): File = File(context.filesDir, FILE_NAME)

    fun hasCapture(context: android.content.Context): Boolean = capturedFile(context).exists()

    /**
     * Launches the system screen-capture consent dialog, then — once granted — hides
     * canvas/widget-overlay/dock, waits for that to actually be composited, captures one real
     * frame via MediaProjection, restores everything, and saves the result to internal storage.
     * [onComplete] fires on the main thread with `true` on success. A no-op (false) below API 26,
     * since MediaProjection's VirtualDisplay + ImageReader flow relies on the same primitives the
     * rest of this codebase already gates at O.
     */
    fun captureCleanWallpaper(
        activity: ComponentActivity,
        extraViewsToHide: List<View> = emptyList(),
        onComplete: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onComplete(false)
            return
        }
        val projectionManager =
            activity.getSystemService(MediaProjectionManager::class.java) ?: run {
                onComplete(false)
                return
            }

        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            REGISTRY_KEY, ActivityResultContracts.StartActivityForResult()
        ) { result ->
            launcher?.unregister()
            val data = result.data
            if (result.resultCode != Activity.RESULT_OK || data == null) {
                onComplete(false)
                return@register
            }
            val resultCode = result.resultCode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+ requires a running mediaProjection-typed foreground service before
                // getMediaProjection() will succeed.
                WallpaperCaptureForegroundService.start(activity.applicationContext) {
                    performCapture(activity, projectionManager, resultCode, data, extraViewsToHide, onComplete)
                }
            } else {
                performCapture(activity, projectionManager, resultCode, data, extraViewsToHide, onComplete)
            }
        }
        launcher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun performCapture(
        activity: Activity,
        projectionManager: MediaProjectionManager,
        resultCode: Int,
        data: Intent,
        extraViewsToHide: List<View>,
        onComplete: (Boolean) -> Unit
    ) {
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (_: Exception) {
            null
        }
        if (projection == null) {
            stopService(activity)
            onComplete(false)
            return
        }

        // Registering a callback before createVirtualDisplay is required on API 34+ (throws
        // otherwise) and harmless on older versions — just do it unconditionally.
        val callback = object : MediaProjection.Callback() {}
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))

        // Whatever's actually calling this (e.g. the Wallpaper settings sheet) is ALSO on
        // screen and would otherwise get baked into the capture right along with the
        // canvas/widgets/dock — extraViewsToHide lets the caller include itself.
        val decorView = activity.window.decorView
        val toHide = listOfNotNull(
            FolderBlurCoordinator.findCanvas(activity) as View?,
            FolderBlurCoordinator.findWidgetOverlay(activity) as View?,
            FolderBlurCoordinator.findDock(activity) as View?
        ) + extraViewsToHide
        if (toHide.isEmpty()) {
            projection.unregisterCallback(callback)
            projection.stop()
            stopService(activity)
            onComplete(false)
            return
        }
        val previousVisibility = toHide.map { it.visibility }
        toHide.forEach { it.visibility = View.INVISIBLE }
        fun restore() {
            toHide.forEachIndexed { i, v -> v.visibility = previousVisibility[i] }
        }

        // Two posts: the first runs after THIS traversal (where the visibility change above
        // actually gets applied); waiting for a second guarantees the hidden state has been
        // composited at least once before the capture below reads it.
        decorView.post {
            decorView.post {
                val dm = activity.resources.displayMetrics
                val width = dm.widthPixels
                val height = dm.heightPixels
                val densityDpi = dm.densityDpi
                if (width <= 0 || height <= 0) {
                    restore()
                    projection.unregisterCallback(callback)
                    projection.stop()
                    stopService(activity)
                    onComplete(false)
                    return@post
                }

                val readerThread = HandlerThread("WallpaperFrostCaptureReader").apply { start() }
                val readerHandler = Handler(readerThread.looper)
                val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
                var virtualDisplay: VirtualDisplay? = null
                var finished = false

                fun finish(success: Boolean) {
                    if (finished) return
                    finished = true
                    Handler(Looper.getMainLooper()).post {
                        restore()
                        virtualDisplay?.release()
                        imageReader.close()
                        readerThread.quitSafely()
                        projection.unregisterCallback(callback)
                        projection.stop()
                        stopService(activity)
                        if (success) {
                            HomeScreenFrameCache.invalidate()
                            runCatching { FolderBlurCoordinator.findCanvas(activity)?.invalidate() }
                            val overlay = runCatching { FolderBlurCoordinator.findWidgetOverlay(activity) }.getOrNull()
                            runCatching { overlay?.invalidate() }
                            runCatching { overlay?.rebindCachedWidgets() }
                            runCatching { com.nexus.launcher.ui.widgets.NexusWidgetGlobalRefresh.refreshAllGlassCapableWidgets(activity) }
                            runCatching { com.nexus.launcher.ui.folder.FolderWindowRefresh.refreshActiveBackgroundIfShowing(activity) }
                        }
                        onComplete(success)
                    }
                }

                imageReader.setOnImageAvailableListener({ reader ->
                    val image = try {
                        reader.acquireLatestImage()
                    } catch (_: Exception) {
                        null
                    }
                    if (image == null) return@setOnImageAvailableListener
                    val bitmap = try {
                        imageToBitmap(image, width, height)
                    } finally {
                        image.close()
                    }
                    if (bitmap != null) {
                        val saved = saveCapture(activity, bitmap)
                        bitmap.recycle()
                        if (saved) HomeScreenFrameCache.invalidate()
                        finish(saved)
                    } else {
                        finish(false)
                    }
                }, readerHandler)

                virtualDisplay = try {
                    projection.createVirtualDisplay(
                        "WallpaperFrostCapture",
                        width, height, densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.surface, null, readerHandler
                    )
                } catch (_: Exception) {
                    null
                }
                if (virtualDisplay == null) {
                    finish(false)
                }
            }
        }
    }

    /** Converts an RGBA_8888 [Image] frame to a tightly-cropped [Bitmap], stripping row padding. */
    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val paddedWidth = width + rowPadding / pixelStride
            val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(buffer)
            if (rowPadding == 0) {
                padded
            } else {
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                cropped
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopService(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            WallpaperCaptureForegroundService.stop(activity.applicationContext)
        }
    }

    private fun saveCapture(context: android.content.Context, bitmap: Bitmap): Boolean {
        return try {
            FileOutputStream(capturedFile(context)).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun clearCapture(context: android.content.Context) {
        capturedFile(context).delete()
        HomeScreenFrameCache.invalidate()
        runCatching { FolderBlurCoordinator.findCanvas(context)?.invalidate() }
        val overlay = runCatching { FolderBlurCoordinator.findWidgetOverlay(context) }.getOrNull()
        runCatching { overlay?.invalidate() }
        runCatching { overlay?.rebindCachedWidgets() }
        runCatching { com.nexus.launcher.ui.widgets.NexusWidgetGlobalRefresh.refreshAllGlassCapableWidgets(context) }
        runCatching { com.nexus.launcher.ui.folder.FolderWindowRefresh.refreshActiveBackgroundIfShowing(context) }
    }
}
