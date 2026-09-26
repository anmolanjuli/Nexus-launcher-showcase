package com.nexus.launcher.ui

import android.view.View
import android.widget.FrameLayout

/**
 * Puts the workspace blur back to what is actually on screen when the launcher resumes. Call
 * from `onResume()` unconditionally, and **last** — after the settle pass, which turns blur off.
 *
 * Safety net for [com.nexus.launcher.ui.canvas.LauncherCanvasView]'s own blur (turned on by the
 * wallpaper sheet) getting stuck on after a backgrounding trip — most commonly a third-party
 * wallpaper app from the sheet's "Others" tab. Reported symptom: home, the Feed panel and the
 * drawer all stay blurred until a back press or a lock/unlock. `CanvasOverlayManager.setBlurState`
 * no-ops when its `blurWanted` flag already matches, so a flag left stuck `true` never gets asked
 * for `false` again; `forceSyncBlurState` bypasses that flag and jumps straight to the final
 * `RenderEffect` (a correctness resync, not a user-facing transition).
 *
 * The other direction too: with the wallpaper sheet still open (back from the photo picker), its
 * frost is three layers — the canvas, plus the workspace container (widgets, dock) and the
 * window's blur-behind over the system wallpaper, both owned by [HomeEditBlurCoordinator]. The
 * settle pass (`dismissManagePages`) turns those two off and the window returns without its blur
 * anyway; restoring only the canvas left the sheet over a sharp home (NexusFrostDebug log,
 * 2026-09-23: canvas SET last, frost still gone).
 */
internal object ResumeBlurResync {

    fun reassert(activity: MainActivity) {
        val mainContainer = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container) ?: return
        val wallpaperSheetShowing = mainContainer.findViewWithTag<View>("wallpaper_sheet") != null
        activity.canvasView.forceSyncBlurState(wallpaperSheetShowing)
        if (wallpaperSheetShowing) HomeEditBlurCoordinator.set(activity, true)
    }
}
