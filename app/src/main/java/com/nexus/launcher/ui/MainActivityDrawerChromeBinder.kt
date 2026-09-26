package com.nexus.launcher.ui

import android.view.View
import com.nexus.launcher.ui.canvas.Drawer3DViewTransform
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.drawercategories.CategoriesDrawerController

object MainActivityDrawerChromeBinder {
    /**
     * @param chromeViews the drawer's floating bars, read fresh on every frame because
     *   [DrawerChromeController] rebuilds them whenever the user changes their arrangement.
     * @param railOverflowVisible whether the legacy rail-edge overflow button is the current home
     *   for the overflow trigger — true only when neither the search pill nor the category bar is
     *   on screen to host it.
     */
    fun bind(
        activity: MainActivity,
        canvasView: LauncherCanvasView,
        overflowBtn: View,
        fabContainer: View,
        isHomeEditMode: () -> Boolean,
        chromeViews: () -> List<View>,
        railOverflowVisible: () -> Boolean
    ) {
        // Track the last blur radius sent to SurfaceFlinger so we only issue the cross-process
        // IPC when the value changes meaningfully (>2 out of 28 steps ≈ >0.02 progress units).
        // This reduces IPC calls from ~120/s to at most ~50/s on 120Hz devices during a drag.
        var lastBlurRadius = -1
        var lastProgress = Float.NaN

        val dockSync = com.nexus.launcher.ui.dock.DockDrawerSync(canvasView)
        val previousProgress = canvasView.onDrawerAnimationProgress
        canvasView.onDrawerAnimationProgress = chrome@{ progress ->
            previousProgress?.invoke(progress)
            dockSync.sync()
            // Fires whenever the drawer's translation or state changes. The settled-home pass
            // only needs running once; skip it until progress moves again.
            if (progress == 0f && lastProgress == 0f) return@chrome
            lastProgress = progress

            val slideY = 0f
            val chromeAlpha = progress
            val bars = chromeViews()
            // Make views visible before animation starts
            if (progress > 0f && progress < 1f) {
                for (i in bars.indices) bars[i].visibility = View.VISIBLE
                overflowBtn.visibility =
                    if (railOverflowVisible()) View.VISIBLE else View.GONE
                fabContainer.visibility = View.GONE
            }
            overflowBtn.translationY = slideY
            overflowBtn.alpha = chromeAlpha
            fabContainer.translationY = slideY
            // The pill, category bar and overflow are the drawer: they follow its 3D transition
            // along with the canvas contents. Index loops — this runs on every drawer frame.
            Drawer3DViewTransform.applyDrawer(overflowBtn, canvasView, progress)
            for (i in bars.indices) {
                val bar = bars[i]
                bar.translationY = slideY
                bar.alpha = chromeAlpha
                Drawer3DViewTransform.applyDrawer(bar, canvasView, progress)
            }

            if (android.os.Build.VERSION.SDK_INT >= 31) {
                if (!isHomeEditMode()) {
                    val targetRadius = (progress * 28f).toInt()
                    // Only call into SurfaceFlinger when the integer radius actually changes.
                    if (targetRadius != lastBlurRadius) {
                        lastBlurRadius = targetRadius
                        activity.window.setBackgroundBlurRadius(targetRadius)
                    }
                }
            }

            if (progress == 0f) {
                overflowBtn.visibility = View.GONE
                fabContainer.visibility = View.GONE
                for (i in bars.indices) bars[i].visibility = View.GONE
                lastBlurRadius = -1 // reset so next open starts fresh
            }
            // Reset translation and alpha when fully open
            if (progress == 1f) {
                overflowBtn.translationY = 0f
                overflowBtn.alpha = 1f
                fabContainer.translationY = 0f
                bars.forEach {
                    it.translationY = 0f
                    it.alpha = 1f
                }
            }
        }

        // Brute-force enforcer (same pattern DockLifecycle already uses for this exact class of
        // bug): the drawer has two independent animation systems — DrawerSnapAnimator (drag) and
        // CanvasStateAnimator (tap-triggered opens, e.g. gesture actions) — and only the former
        // reliably drives onDrawerAnimationProgress above. onStateChanged fires for both, once
        // the transition actually settles, so re-asserting visibility there is a second,
        // independent correction pass that can't be skipped by whichever animator handled this
        // particular open/close.
        val previousStateChanged = canvasView.onStateChanged
        canvasView.onStateChanged = { state ->
            previousStateChanged?.invoke(state)
            val bars = chromeViews()
            val dock = com.nexus.launcher.ui.dock.DockLayout.findFrom(canvasView)
            if (state == com.nexus.launcher.ui.model.LauncherState.HOME) {
                overflowBtn.visibility = View.GONE
                fabContainer.visibility = View.GONE
                for (i in bars.indices) bars[i].visibility = View.GONE
                dock?.alpha = 1f
                dock?.visibility = View.VISIBLE
            } else {
                overflowBtn.visibility =
                    if (railOverflowVisible()) View.VISIBLE else View.GONE
                fabContainer.visibility = View.GONE
                bars.forEach { it.visibility = View.VISIBLE }
                dock?.alpha = 0f
                dock?.visibility = View.GONE
            }
        }

        CategoriesDrawerController(activity, canvasView, activity.viewModel)
            .attach(activity.findViewById(com.nexus.launcher.R.id.main_container))
    }
}
