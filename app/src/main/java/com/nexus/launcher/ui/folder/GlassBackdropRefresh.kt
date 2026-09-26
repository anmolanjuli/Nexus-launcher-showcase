package com.nexus.launcher.ui.folder

import android.view.View
import android.view.ViewGroup

/**
 * Makes every glass surface re-sample the wallpaper now, after the wallpaper (or its dim/tint)
 * changed.
 *
 * Bumping [HomeScreenFrameCache]'s version is not enough on its own: a glass view only reads the
 * version when it redraws, and `invalidate()` on the widget overlay or dock redraws that container
 * alone — its children keep their recorded drawing. The frost under a new wallpaper therefore
 * showed only after a page scroll or a screen off/on redrew them. This invalidates the whole tree.
 */
object GlassBackdropRefresh {

    fun refresh(anyView: View) {
        HomeScreenFrameCache.invalidate()
        val context = anyView.context
        FolderBlurCoordinator.findWidgetOverlay(context)?.let(::invalidateTree)
        FolderBlurCoordinator.findDock(context)?.let(::invalidateTree)
        FolderBlurCoordinator.findCanvas(context)?.invalidate() // folder plates draw on the canvas
    }

    private fun invalidateTree(view: View) {
        view.invalidate()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) invalidateTree(view.getChildAt(i))
        }
    }
}
