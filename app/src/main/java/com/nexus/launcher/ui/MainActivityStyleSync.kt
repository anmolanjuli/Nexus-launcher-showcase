package com.nexus.launcher.ui

import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.folder.FolderWindowRefresh
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import com.nexus.launcher.ui.glass.UiStyleCoordinator
import com.nexus.launcher.ui.widgets.NexusWidgetGlobalRefresh
import java.lang.ref.WeakReference

/**
 * Refreshes the launcher's own surfaces — canvas, widgets, boxes, folders, feed — when the UI
 * Style changes.
 *
 * It no longer decides *whether* the style changed, and no longer writes the style flags:
 * [UiStyleCoordinator] owns both, because this was not the only writer and comparing against
 * shared mutable flags made every writer's change detection depend on who ran first. This now
 * just reports what it saw and registers what it wants done.
 */
object MainActivityStyleSync {

    private var activityRef: WeakReference<MainActivity>? = null

    fun syncStyle(activity: MainActivity, settings: NexusSettingsData) {
        if (activityRef?.get() !== activity) {
            activityRef = WeakReference(activity)
            // Keyed, so a recreated activity replaces its predecessor's registration instead of
            // stacking a second one; weakly held, so it cannot retain a destroyed activity.
            UiStyleCoordinator.setListener(UiStyleCoordinator.LISTENER_LAUNCHER) {
                activityRef?.get()?.let { refreshSurfaces(it) }
            }
        }
        UiStyleCoordinator.apply(settings.uiStyleMode, settings.frostedGlassEnabled)
    }

    private fun refreshSurfaces(activity: MainActivity) {
        HomeScreenFrameCache.invalidate()
        activity.canvasView.invalidate()

        runCatching {
            activity.widgetHostLifecycle.widgetOverlayLayout.rebindCachedWidgets()
            activity.widgetHostLifecycle.widgetOverlayLayout.invalidate()
        }
        runCatching {
            NexusWidgetGlobalRefresh.refreshAllGlassCapableWidgets(activity)
        }
        runCatching {
            FolderWindowRefresh.refreshActiveBackgroundIfShowing(activity)
        }
        runCatching {
            activity.feedPanelController.onUiStyleChanged()
        }
    }
}
