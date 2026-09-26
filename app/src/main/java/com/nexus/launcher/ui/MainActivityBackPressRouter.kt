package com.nexus.launcher.ui

import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import com.nexus.launcher.R
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.contextmenu.ContextMenuManager
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState
import com.nexus.launcher.ui.widgets.WidgetHostLifecycle

/**
 * System Back routing for [MainActivity].
 * Sequential early-return guards — order is intentional and must be preserved.
 * Callback is permanently enabled so MainActivity as the home launcher never calls
 * finish() / recreates, eliminating the default-page reload flicker.
 */
object MainActivityBackPressRouter {

    fun install(
        activity: FragmentActivity,
        widgetHostLifecycle: WidgetHostLifecycle,
        canvasView: LauncherCanvasView,
        homeScreenViewModel: HomeScreenViewModel,
        homeEditController: HomeEditController,
        overflowController: OverflowMenuController,
        contextMenuManager: ContextMenuManager,
        viewModel: MainViewModel,
        uiHelpers: LauncherUiHelpers,
        searchOverlay: View,
        searchBar: EditText,
        newScroll: HorizontalScrollView,
        newTitle: TextView,
        recentScroll: HorizontalScrollView,
        recentTitle: TextView
    ) {
        val mainContainer = activity.findViewById<FrameLayout>(R.id.main_container)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                // Predictive back started: app handles back, preventing WindowManager from shrinking launcher window
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                // Progressed: hold launcher window scale steady at 100%
            }

            override fun handleOnBackCancelled() {
                // Cancelled: no-op
            }

            override fun handleOnBackPressed() {
                ImmersiveModeController.reassertSoon(canvasView, activity)
                if (com.nexus.launcher.ui.onboarding.OnboardingPilotController.isShowing()) {
                    com.nexus.launcher.ui.onboarding.OnboardingPilotController.dismiss(activity)
                    return
                }
                val pickerOverlay = mainContainer
                    ?.findViewWithTag<com.nexus.launcher.ui.picker.PickerOverlayShell>(
                        com.nexus.launcher.ui.picker.PickerOverlayShell.TAG
                    )
                if (pickerOverlay != null) {
                    pickerOverlay.dismiss()
                    return
                }
                val widgetPicker = mainContainer
                    ?.findViewWithTag<com.nexus.launcher.ui.widgets.WidgetPickerOverlay>(
                        "widget_picker_overlay"
                    )
                if (widgetPicker != null) {
                    widgetPicker.dismiss()
                    return
                }
                if (widgetHostLifecycle.hasActiveOverlays()) {
                    widgetHostLifecycle.dismissOverlaysFromActivity()
                    return
                }
                val feedHandled = (activity as? MainActivity)?.let { act ->
                    runCatching { act.feedPanelController.handleBackPressed() }.getOrDefault(false)
                } ?: false
                if (feedHandled) {
                    return
                }
                if (canvasView.selectionState is SelectionState.Selecting) {
                    homeScreenViewModel.clearSelection()
                    return
                }
                if (homeEditController.dismissWallpaperSheet()) {
                    return
                }
                if (homeEditController.dismissPageAddPickerIfShowing()) {
                    return
                }
                if (homeEditController.managePagesView != null) {
                    canvasView.setBlurState(false)
                    canvasView.invalidate()
                    homeEditController.dismissManagePages()
                    return
                }
                if (homeEditController.isHomeEditMode) {
                    homeEditController.dismissHomeEditMode()
                    return
                }
                if (overflowController.isShowing()) {
                    overflowController.dismiss()
                    return
                }
                if (contextMenuManager.isMenuVisible()) {
                    contextMenuManager.dismiss()
                    return
                }
                if (activity.supportFragmentManager.backStackEntryCount > 0) {
                    activity.supportFragmentManager.popBackStack()
                    return
                }
                if (searchOverlay.visibility == View.VISIBLE) {
                    uiHelpers.closeSearchMode(
                        searchOverlay, searchBar,
                        newScroll, newTitle, recentScroll, recentTitle
                    )
                    return
                }
                if (viewModel.uiState.value == LauncherState.DRAWER) {
                    viewModel.handleSwipeDown()
                    return
                }
                val defaultPage = homeEditController.getDefaultPage()
                if (canvasView.currentPage != defaultPage) {
                    canvasView.animateScrollToPage(defaultPage)
                    return
                }
                // Root default page: intentionally do nothing. Never finish() or destroy the home launcher.
            }
        }

        activity.onBackPressedDispatcher.addCallback(activity, callback)
    }
}
