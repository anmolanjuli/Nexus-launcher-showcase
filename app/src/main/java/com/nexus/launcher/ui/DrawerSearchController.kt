package com.nexus.launcher.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/**
 * Wires the legacy inline search overlay and the window-inset listeners that size the drawer.
 * Extracted from [MainActivity].
 *
 * The drawer's category filter now lives in the Split Search Pill's dropdown
 * ([DrawerCategoryDropdown]) rather than a chip strip pinned across the drawer's top edge, so
 * nothing here builds category chips any more.
 */
class DrawerSearchController(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val viewModel: MainViewModel,
    private val uiHelpers: LauncherUiHelpers
) {

    fun setup(
        searchFab: android.widget.ImageView,
        searchOverlay: android.widget.LinearLayout,
        searchCloseBtn: android.widget.ImageView,
        searchBar: android.widget.EditText,
        newScroll: android.widget.HorizontalScrollView,
        newTitle: android.widget.TextView,
        recentScroll: android.widget.HorizontalScrollView,
        recentTitle: android.widget.TextView
    ) {
        canvasView.onDrawerRailVisibility = { visible ->
            canvasView.railRenderer.isVisible = visible
            canvasView.invalidate()
        }

        // FAB replaced by the drawer chrome owned by [DrawerChromeController].
        activity.findViewById<android.view.View>(R.id.search_fab_container)?.visibility = android.view.View.GONE

        searchOverlay.background = null

        searchBar.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                viewModel.searchQuery.value = query
                canvasView.isSearchQueryEmpty = query.isEmpty()
                canvasView.resetScrollY()
                canvasView.invalidate()

                if (query.isNotEmpty()) {
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        canvasView.setRenderEffect(null)
                    }
                    newScroll.visibility = View.GONE
                    newTitle.visibility = View.GONE
                    recentScroll.visibility = View.GONE
                    recentTitle.visibility = View.GONE
                } else {
                    if (android.os.Build.VERSION.SDK_INT >= 31
                        && canvasView.isSearchMode) {
                        canvasView.setRenderEffect(
                            com.nexus.launcher.ui.glass.ChromeBackdrop.effect(com.nexus.launcher.ui.glass.ChromeBackdrop.LIGHT_RADIUS_PX)
                        )
                    }
                    newScroll.visibility = View.VISIBLE
                    newTitle.visibility = View.VISIBLE
                    recentScroll.visibility = View.VISIBLE
                    recentTitle.visibility = View.VISIBLE
                }

                searchOverlay.post {
                    canvasView.currentOverlayTotalHeight = searchOverlay.measuredHeight
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        searchFab.setOnClickListener {
            com.nexus.launcher.search.ui.NexusSearchOverlay.show(
                activity, com.nexus.launcher.search.SearchContext.APP_DRAWER
            )
        }

        searchCloseBtn.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                canvasView.setRenderEffect(null)
            }
            uiHelpers.closeSearchMode(searchOverlay, searchBar, newScroll, newTitle, recentScroll, recentTitle)
        }

        ViewCompat.setOnApplyWindowInsetsListener(canvasView) { _, insets ->
            com.nexus.launcher.ui.canvas.SideInsets.update(canvasView, insets)
            // Bars really showing (not a transient peek) while immersive: put them away again.
            if (insets.isVisible(WindowInsetsCompat.Type.statusBars()) ||
                insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            ) ImmersiveModeController.reassertSoon(canvasView, activity)
            val statusShowing = ImmersiveModeController.isActive &&
                insets.isVisible(WindowInsetsCompat.Type.statusBars())
            com.nexus.launcher.ui.immersive.ImmersiveStatus.setSystemStatusShowing(statusShowing)
            if (statusShowing) canvasView.post { ImmersiveModeController.hideStatusBarNow(activity) }
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            viewModel.isGestureNav = navBars.bottom == 0

            val totalBottomBarHeight = navBars.bottom
            canvasView.setBottomBarHeight(totalBottomBarHeight)

            // topInset stays status-bar-only; the drawer layout paths add the pill's own reserve
            // (drawerChrome.topReservePx) so the home grid is never shrunk by drawer chrome.
            // Status bar OR camera cutout: with the bars hidden (immersive mode) the status bar
            // inset is 0, but content must still start below the camera.
            // ...and below the immersive status row, where there is no cutout band (landscape).
            val statusBarTop = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top,
                com.nexus.launcher.ui.immersive.ImmersiveStatus.reservedTopPx
            )
            canvasView.topInset = statusBarTop + 16
            canvasView.recalculateLayout()

            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(searchOverlay) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomPadding = maxOf(imeInsets.bottom, navInsets.bottom)
            view.setPadding(0, 0, 0, bottomPadding)

            view.post {
                canvasView.currentOverlayTotalHeight = view.measuredHeight
            }

            insets
        }
    }
}
