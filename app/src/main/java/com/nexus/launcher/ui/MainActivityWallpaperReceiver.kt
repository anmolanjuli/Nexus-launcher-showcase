package com.nexus.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nexus.launcher.ui.folder.HomeScreenFrameCache

/**
 * Listens for system wallpaper change broadcasts.
 *
 * Invalidates cached wallpaper bitmaps so home screen canvas, widgets, boxes, and folders
 * re-sample fresh wallpaper data, while preserving user-captured frosted glass frames.
 */
class MainActivityWallpaperReceiver(
    private val activity: MainActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) {
            // Always invalidate cache so system-wallpaper-dependent rendering stays accurate
            HomeScreenFrameCache.invalidate()
            activity.canvasRenderer.invalidateSystemWallpaperCache()
            activity.canvasView.invalidate()

            // Ignore broadcasts that arrive within 2 seconds of our own setBitmap call
            // to prevent overriding a newly applied internal Nexus wallpaper.
            if (System.currentTimeMillis() - WallpaperApplyController.lastSelfWallpaperSetTime < 2000L) {
                return
            }
            if (activity.viewModel.nexusSettings.value.wallpaperType != "system") {
                activity.viewModel.updateWallpaperType("system")
            }
        }
    }
}
