package com.nexus.launcher.ui.canvas

import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity

/**
 * Enters icon corner-drag resize.
 */
class IconResizeModeLauncher(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val homeScreenViewModel: HomeScreenViewModel
) {
    fun show(iconItem: HomeScreenItem) {
        if (iconItem.itemType != 0) return
        val page = iconItem.page
        val rootLayout = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        val scrimView = android.view.View(activity).apply {
            tag = "icon_resize_scrim"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        rootLayout.addView(scrimView)
        var activeBackCallback: OnBackPressedCallback? = null
        val exitFn: () -> Unit = {
            activeBackCallback?.remove()
            activeBackCallback = null
            rootLayout.findViewWithTag<android.view.View>("icon_resize_scrim")
                ?.let { rootLayout.removeView(it) }
            rootLayout.findViewWithTag<android.view.View>("icon_resize_overlay")
                ?.let { rootLayout.removeView(it) }
            canvasView.draftResizeItem = null
            canvasView.invalidate()
            Unit
        }
        val overlayView = IconResizeOverlayView(
            context = activity,
            canvasView = canvasView,
            homeScreenViewModel = homeScreenViewModel,
            originalItem = iconItem,
            onExit = exitFn
        ).apply {
            tag = "icon_resize_overlay"
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                overlayView.cancelAndExit()
                isEnabled = false
            }
        }
        activeBackCallback = callback
        activity.onBackPressedDispatcher.addCallback(activity, callback)
        scrimView.setOnClickListener { overlayView.cancelAndExit() }
        rootLayout.addView(overlayView)
        overlayView.requestFocus()
    }
}
