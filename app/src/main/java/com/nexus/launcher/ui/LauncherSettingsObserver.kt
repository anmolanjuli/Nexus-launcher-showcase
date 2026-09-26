package com.nexus.launcher.ui

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.card.MaterialCardView
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/**
 * Handles incoming [NexusSettingsData] updates for the launcher canvas, search UI, and global styling.
 */
class LauncherSettingsObserver(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val searchFabContainer: View
) {

    private val immersive = ImmersiveModeController(activity)

    fun applySettings(settings: NexusSettingsData) {
        // Before the canvas pass below, so its layout already sees the dock on or off.
        com.nexus.launcher.ui.dock.DockPresence.apply(
            com.nexus.launcher.ui.dock.DockLayout.findFrom(canvasView), settings.homeShowDock
        )
        canvasView.applySettings(settings)
        val immersiveChanged = settings.immersiveMode != ImmersiveModeController.isActive
        immersive.setEnabled(settings.immersiveMode)
        com.nexus.launcher.ui.immersive.ImmersiveStatus.apply(activity, settings)
        com.nexus.launcher.ui.island.IslandController.apply(activity, settings)
        com.nexus.launcher.service.NotificationHistory.bind(activity)
        if (immersiveChanged) {
            // The dock sits nearer the bottom edge in immersive mode; the grid follows it.
            com.nexus.launcher.ui.dock.DockLayout.findFrom(canvasView)?.let { dock ->
                dock.updateOrientationBounds()
                com.nexus.launcher.ui.dock.DockHomeGridSync.notifyCanvasFromDock(dock)
            }
        }

        // Search FAB and overflow icon are accent-free by design (App Drawer has
        // no accent color anywhere) — neutral token color, not the user's Accent Color.
        val neutral = canvasView.currentThemeTokens.textSecondary

        // MaterialCardView is the direct first child of searchFabContainer
        val fabCardView = (searchFabContainer as? ViewGroup)?.getChildAt(0)
            as? MaterialCardView
        fabCardView?.strokeColor = neutral
        val fabIcon = searchFabContainer.findViewById<ImageView>(R.id.search_fab)
        fabIcon?.imageTintList = ColorStateList.valueOf(neutral)

        // Overflow icon tint
        val overflowIcon = activity.findViewById<ImageView>(R.id.drawer_overflow_icon)
        overflowIcon?.imageTintList = ColorStateList.valueOf(canvasView.currentThemeTokens.textPrimary)

        // Sync global UI style modes across canvas, widgets, boxes, folders, and dock
        MainActivityStyleSync.syncStyle(activity, settings)

    }
}
