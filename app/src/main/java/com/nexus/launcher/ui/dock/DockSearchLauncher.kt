package com.nexus.launcher.ui.dock

import com.nexus.launcher.search.SearchContext
import com.nexus.launcher.search.ui.NexusSearchOverlay
import com.nexus.launcher.ui.MainActivity

/** Handles dock search slot tap — opens [NexusSearchOverlay] on the home-screen context. */
object DockSearchLauncher {

    fun onSearchSlotTapped(activity: MainActivity) {
        NexusSearchOverlay.show(activity, SearchContext.HOME_SCREEN)
    }
}
