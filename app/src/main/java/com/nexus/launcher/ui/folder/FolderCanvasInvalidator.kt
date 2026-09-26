package com.nexus.launcher.ui.folder

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.dock.DockLayout

object FolderCanvasInvalidator {

    fun afterFolderSave(activity: Activity, folderId: Int, config: FolderConfig) {
        findCanvas(activity)?.let { canvas ->
            canvas.homeScreenRenderer.folderIconRenderer.evictConfig(folderId)
            canvas.invalidate()
            FolderWindowManager.refreshOpenWindow(
                activity,
                folderId.toLong(),
                config,
                canvas.homeScreenRenderer.iconCache
            )
        }
        DockLayout.findFrom(activity.findViewById(android.R.id.content))?.invalidate()
    }

    fun findCanvasInTree(view: View): LauncherCanvasView? {
        if (view is LauncherCanvasView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findCanvasInTree(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun findCanvas(activity: Activity): LauncherCanvasView? {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return null
        return findCanvasInTree(content)
    }
}
