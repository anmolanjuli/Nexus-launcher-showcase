package com.nexus.launcher.ui.folder

import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView

class FolderFlipModeLauncher(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: com.nexus.launcher.ui.HomeScreenViewModel
) {
    fun showFolderFlipMode(folderItem: HomeScreenItem) {
        val rootLayout = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        
        val scrimView = com.nexus.launcher.ui.folder.FolderWindowScrimView(activity).apply {
            tag = "folder_flip_scrim"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        rootLayout.addView(scrimView)
        
        var activeBackCallback: OnBackPressedCallback? = null

        val exitFn: () -> Unit = {
            activeBackCallback?.remove()
            activeBackCallback = null
            canvasView.setFolderOpenState(null, 0f)
            rootLayout.findViewWithTag<android.view.View>("folder_flip_scrim")?.let { rootLayout.removeView(it) }
            rootLayout.findViewWithTag<android.view.View>("folder_flip_overlay")?.let { rootLayout.removeView(it) }
            Unit
        }
        
        canvasView.setFolderOpenState(folderItem.id, 1f)
        
        val overlayView = FolderFlipOverlayView(
            context = activity,
            canvasView = canvasView,
            homeScreenViewModel = homeScreenViewModel,
            originalFolderItem = folderItem,
            onExit = exitFn
        ).apply {
            tag = "folder_flip_overlay"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitFn()
                isEnabled = false
            }
        }
        activeBackCallback = callback
        activity.onBackPressedDispatcher.addCallback(activity, callback)

        scrimView.setOnClickListener {
            exitFn()
        }
        
        overlayView.onConfigChanged = {
            scrimView.setHighlight(overlayView.getHighlightBounds())
        }
        
        rootLayout.addView(overlayView)
        overlayView.post {
            overlayView.requestFocus()
            scrimView.setHighlight(overlayView.getHighlightBounds())
        }
    }
}
