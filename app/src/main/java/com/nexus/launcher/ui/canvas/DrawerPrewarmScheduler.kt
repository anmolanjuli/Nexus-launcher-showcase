package com.nexus.launcher.ui.canvas

import com.nexus.launcher.ui.model.LauncherState

/**
 * Idle + post-snap drawer icon prewarm.
 * Cancels in-flight work when a swipe-up starts so rasterize doesn't fight the gesture.
 */
class DrawerPrewarmScheduler(private val view: LauncherCanvasView) {

    private val folderPrewarmRunnable = object : Runnable {
        override fun run() {
            if (view.viewHeight <= 0 || view.isDragging) return
            if (prewarmNextFolder()) view.postOnAnimation(this)
        }
    }

    private val idleRunnable = Runnable {
        if (view.uiState == LauncherState.HOME && view.viewHeight > 0 &&
            view.drawerTranslationY >= view.viewHeight - 1f
        ) {
            triggerNow()
            view.removeCallbacks(folderPrewarmRunnable)
            view.post(folderPrewarmRunnable)
        }
    }

    fun onGridUpdated() {
        view.removeCallbacks(idleRunnable)
        if (view.uiState == LauncherState.HOME) {
            view.postDelayed(idleRunnable, 450L)
        }
    }

    /** Stop background toBitmap work immediately (call when finger starts opening drawer). */
    fun cancelInFlight() {
        view.removeCallbacks(idleRunnable)
        view.removeCallbacks(folderPrewarmRunnable)
        view.drawerIconCache.cancelPrewarm()
    }

    fun triggerNow() {
        val iconSizePx =
            if (view.drawerItems.isNotEmpty()) view.drawerItems[0].drawRect.width() else -1
        if (iconSizePx <= 0) return
        val visiblePkgs = visibleDrawerPackages()
        view.drawerIconCache.schedulePrewarm(
            context = view.context,
            scope = view.renderScope,
            items = view.rawDrawerApps,
            iconSizePx = iconSizePx,
            onBatchReady = { view.postInvalidateOnAnimation() },
            priorityPackages = visiblePkgs
        )
        view.removeCallbacks(folderPrewarmRunnable)
        view.post(folderPrewarmRunnable)
    }

    private fun visibleDrawerPackages(): Set<String> {
        // Approximate first screen of drawer content (scrollY ≈ 0 at open).
        val h = view.viewHeight.toFloat()
        return view.drawerItems.asSequence()
            .filter { it.drawRect.bottom >= 0f && it.drawRect.top <= h }
            .mapNotNull { it.intent?.component?.packageName }
            .toSet()
    }

    private fun prewarmNextFolder(): Boolean {
        for (item in view.drawerItems) {
            val folderId = item.intent?.getLongExtra("folderId", -1L) ?: continue
            val folder = view.folderById[folderId] ?: continue
            if (view.drawerFolderTileCache.prewarm(
                    context = view.context,
                    folder = folder,
                    contents = view.homeScreenRenderer.folderContentsForItem(folder),
                    iconCache = view.homeScreenRenderer.iconCache,
                    density = view.resources.displayMetrics.density,
                    width = item.drawRect.width(),
                    height = item.drawRect.height()
                )
            ) return true
        }
        return false
    }

    fun cancel() {
        view.removeCallbacks(idleRunnable)
        view.removeCallbacks(folderPrewarmRunnable)
        view.drawerIconCache.cancelPrewarm()
    }
}
