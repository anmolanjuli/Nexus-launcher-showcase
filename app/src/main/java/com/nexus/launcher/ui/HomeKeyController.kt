package com.nexus.launcher.ui

import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState

/**
 * Handles "go home" behaviour: the ACTION_CLOSE_SYSTEM_DIALOGS receiver (home /
 * recents key) and the shared close-search / close-drawer / clear-selection logic
 * reused by onNewIntent and onResume.
 */
class HomeKeyController(
    private val activity: MainActivity,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val uiHelpers: LauncherUiHelpers,
    private val contextMenuManager: com.nexus.launcher.ui.contextmenu.ContextMenuManager,
    private val overflowController: OverflowMenuController,
    private val homeEditController: HomeEditController,
    private val searchBar: android.widget.EditText,
    private val newScroll: android.widget.HorizontalScrollView,
    private val newTitle: android.widget.TextView,
    private val recentScroll: android.widget.HorizontalScrollView,
    private val recentTitle: android.widget.TextView
) {
    private var homeGestureReceiver: android.content.BroadcastReceiver? = null

    /**
     * Close search / drawer / selection / overlays without changing the home page.
     * Used when returning from an app (onResume) so the user stays on the page they left.
     */
    fun settleLauncherChrome() {
        activity.canvasView.setBlurState(false)
        activity.widgetHostLifecycle.widgetOverlayLayout.restoreHomePresentation()
        activity.canvasView.invalidate()
        homeEditController.dismissManagePages()
        homeEditController.dismissHomeEditMode()
        val searchOverlay = activity.findViewById<android.widget.LinearLayout>(R.id.search_overlay)

        val mainContainer = activity.findViewById<android.widget.FrameLayout>(R.id.main_container)
            ?: activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val widgetPicker = mainContainer?.findViewWithTag<com.nexus.launcher.ui.widgets.WidgetPickerOverlay>("widget_picker_overlay")
        widgetPicker?.dismiss()
        val pickerOverlay = mainContainer?.findViewWithTag<com.nexus.launcher.ui.picker.PickerOverlayShell>(com.nexus.launcher.ui.picker.PickerOverlayShell.TAG)
        pickerOverlay?.dismiss()

        if (com.nexus.launcher.ui.folder.FolderOverlayController.isShowing()) {
            com.nexus.launcher.ui.folder.FolderWindowLifecycle.dismissFolder()
        }

        if (searchOverlay?.visibility == View.VISIBLE) {
            uiHelpers.closeSearchMode(
                searchOverlay,
                searchBar,
                newScroll, newTitle,
                recentScroll, recentTitle
            )
        }
        if (viewModel.uiState.value == LauncherState.DRAWER) {
            viewModel.handleSwipeDown()
        }
        com.nexus.launcher.search.ui.NexusSearchOverlay.dismiss(activity)
        if (homeScreenViewModel.selectionState.value is SelectionState.Selecting) {
            homeScreenViewModel.clearSelection()
        }
        activity.widgetHostLifecycle.onHomeResumed()
        // The panel survives the trip; its blurred backdrop does not.
        activity.feedPanelController.reassertBlurOnResume()
    }

    /** Explicit Home / recents: settle chrome and scroll to the user's default page. */
    fun navigateHome() {
        // Home means the home screen. With the Feed open, the gesture used to land behind it and
        // read as "the frost fell off the panel" — the panel was still there, the workspace it was
        // frosting had simply been settled underneath it.
        activity.feedPanelController.close(animated = true)
        settleLauncherChrome()
        val defaultPage = activity.getSharedPreferences(
            "nexus_prefs", android.content.Context.MODE_PRIVATE)
            .getInt("default_page", 0)
        if (activity.canvasView.currentPage != defaultPage) {
            activity.canvasView.animateScrollToPage(defaultPage)
        }
    }

    @Suppress("DEPRECATION")
    fun register() {
        val homeFilter = android.content.IntentFilter(android.content.Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        homeGestureReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val reason = intent?.getStringExtra("reason")
                if (reason == "homekey" || reason == "recentapps") {
                    activity.canvasView.setBlurState(false)
                    activity.widgetHostLifecycle.widgetOverlayLayout.restoreHomePresentation()
                    activity.canvasView.invalidate()
                    com.nexus.launcher.search.ui.NexusSearchOverlay.dismiss(activity)
                    homeEditController.dismissManagePages()
                    homeEditController.dismissHomeEditMode()
                    contextMenuManager.dismiss()
                    overflowController.dismiss()
                    if (activity.supportFragmentManager.backStackEntryCount > 0) {
                        activity.supportFragmentManager.popBackStack()
                        viewModel.handleSwipeDown()
                        homeScreenViewModel.clearSelection()
                        return
                    }
                    navigateHome()
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            activity, 
            homeGestureReceiver, 
            homeFilter, 
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )
    }

    fun unregister() {
        homeGestureReceiver?.let {
            try {
                activity.unregisterReceiver(it)
            } catch (e: Exception) {}
        }
    }
}
