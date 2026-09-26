package com.nexus.launcher.ui.canvas

import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/** Applies the card-track transform to widget overlay and dock; clears on exit. */
internal object SelectionModeChromeSync {

    fun onPageScroll(view: LauncherCanvasView, activity: MainActivity) {
        if (!SelectionModePageCards.isActive(view)) return
        val workspace = activity.findViewById<FrameLayout>(R.id.workspace_container) ?: return
        val mainContainer = activity.findViewById<FrameLayout>(R.id.main_container)
        findDock(workspace)?.let {
            SelectionModeCardTrack.applyDockViewTransform(view, it)
        }
        findStatusBar(mainContainer)?.let {
            SelectionModeCardTrack.applyStatusBarViewTransform(view, it)
        }
    }

    fun onSelectionChanged(view: LauncherCanvasView, activity: MainActivity) {
        apply(view, activity)
    }

    fun apply(view: LauncherCanvasView, activity: MainActivity) {
        val workspace = activity.findViewById<FrameLayout>(R.id.workspace_container) ?: return
        val mainContainer = activity.findViewById<FrameLayout>(R.id.main_container)
        val dock = findDock(workspace)
        val widgets = findWidgetOverlay(workspace)
        val statusBar = findStatusBar(mainContainer)
        if (!SelectionModePageCards.isActive(view)) {
            if (mainContainer != null && !SelectionModeTransform.isHomeSelecting(view)) {
                mainContainer.findViewWithTag<android.view.View>("selection_backdrop")?.let {
                    mainContainer.removeView(it)
                }
            }
            dock?.let {
                it.freezeTranslation = false
                SelectionModeCardTrack.clearViewTransform(it)
            }
            widgets?.let { clearWidgetOverlay(it) }
            statusBar?.let { SelectionModeCardTrack.clearViewTransform(it) }
            return
        }
        if (mainContainer != null && mainContainer.findViewWithTag<android.view.View>("selection_backdrop") == null) {
            val backdrop = SelectionModeBackdropView(activity, view.canvasRenderer).apply {
                tag = "selection_backdrop"
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            mainContainer.addView(backdrop, 0)
        }
        widgets?.let { overlay ->
            overlay.syncScroll(view.currentPage, view.dragScrollOffset)
        }
        dock?.let {
            it.freezeTranslation = true
            SelectionModeCardTrack.applyDockViewTransform(view, it)
        }
        statusBar?.let {
            SelectionModeCardTrack.applyStatusBarViewTransform(view, it)
        }
    }

    private fun findStatusBar(mainContainer: FrameLayout?): android.view.View? {
        val view = com.nexus.launcher.ui.immersive.ImmersiveStatus.statusBarView
        if (view != null && view.isAttachedToWindow) return view
        if (mainContainer == null) return null
        for (i in 0 until mainContainer.childCount) {
            val child = mainContainer.getChildAt(i)
            if (child is com.nexus.launcher.ui.immersive.ImmersiveStatusBarView) return child
        }
        return null
    }

    private fun clearWidgetOverlay(overlay: WidgetOverlayLayout) {
        SelectionModeCardTrack.clearViewTransform(overlay)
        overlay.clearSelectionCardPageOffsets()
        overlay.resyncScroll()
    }

    private fun findDock(workspace: FrameLayout): DockLayout? {
        for (i in 0 until workspace.childCount) {
            val child = workspace.getChildAt(i)
            if (child is DockLayout) return child
        }
        return null
    }

    private fun findWidgetOverlay(workspace: FrameLayout): WidgetOverlayLayout? {
        for (i in 0 until workspace.childCount) {
            val child = workspace.getChildAt(i)
            if (child is WidgetOverlayLayout) return child
        }
        return null
    }
}
