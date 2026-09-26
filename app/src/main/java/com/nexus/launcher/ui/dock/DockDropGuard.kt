package com.nexus.launcher.ui.dock

import android.app.Activity
import android.content.Context
import android.view.HapticFeedbackConstants
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.model.DisplayItem

object DockDropGuard {

    private fun rejectHaptic(context: Context) {
        (context as? Activity)?.window?.decorView?.performHapticFeedback(
            HapticFeedbackConstants.REJECT
        )
    }

    private fun dockOccupancyExcluding(
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        excludeId: Int,
        excludePackage: String?
    ): Int {
        val appCount = items.count {
            it.page == HomeScreenViewModel.DOCK_CONTAINER &&
                it.column < maxDockIcons &&
                it.id != excludeId &&
                !(excludeId == 0 && excludePackage != null && it.packageName == excludePackage)
        }
        val searchCount = if (DockSearchSlot.enabled) 1 else 0
        return appCount + searchCount
    }

    fun shouldReject(
        context: Context,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        droppedHomeItem: HomeScreenItem?
    ): Boolean {
        if (droppedHomeItem == null) return false
        val alreadyInDock = items.any {
            (droppedHomeItem.id != 0 && it.id == droppedHomeItem.id) ||
                it.packageName == droppedHomeItem.packageName
        }
        if (alreadyInDock) return false
        val occupancy = dockOccupancyExcluding(
            items, maxDockIcons, droppedHomeItem.id, droppedHomeItem.packageName
        )
        if (occupancy >= maxDockIcons) {
            rejectHaptic(context)
            return true
        }
        return false
    }

    fun shouldReject(
        context: Context,
        items: List<HomeScreenItem>,
        maxDockIcons: Int,
        displayItem: DisplayItem
    ): Boolean {
        val pkg = displayItem.intent?.component?.packageName ?: displayItem.intent?.`package`
            ?: return false
        val alreadyInDock = items.any { it.packageName == pkg }
        if (alreadyInDock) return false
        val occupancy = dockOccupancyExcluding(items, maxDockIcons, 0, pkg)
        if (occupancy >= maxDockIcons) {
            rejectHaptic(context)
            return true
        }
        return false
    }
}
