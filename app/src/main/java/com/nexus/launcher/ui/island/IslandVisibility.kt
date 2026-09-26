package com.nexus.launcher.ui.island

import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.folder.FolderOverlayController
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState

object IslandVisibility {
    fun shouldShow(
        activity: MainActivity,
        enabled: Boolean,
        drawerAlpha: Float,
    ): Boolean {
        if (!enabled) return false
        if (drawerAlpha <= 0.05f) return false
        if (activity.viewModel.uiState.value != LauncherState.HOME) return false
        if (FolderOverlayController.isShowing()) return false
        val editing = activity.findViewById<FrameLayout>(R.id.main_container)
            ?.findViewWithTag<View>("edit_mode_sheet") != null
        if (editing) return false
        if (activity.canvasView.selectionState is SelectionState.Selecting) return false
        val overlay = activity.findViewById<FrameLayout>(R.id.main_container)
            ?.findViewWithTag<View>("SearchOverlay")
        return overlay == null
    }
}
