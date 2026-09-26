package com.nexus.launcher.ui.widgets.mosaic

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.widgets.AppWidgetController
import com.nexus.launcher.ui.widgets.WidgetCoordinateSpace
import com.nexus.launcher.ui.widgets.WidgetMenuScrimView
import com.nexus.launcher.ui.widgets.WidgetMoveArrowView
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout
import com.nexus.launcher.ui.widgets.WidgetResizeArrowView
import com.nexus.launcher.ui.widgets.WidgetResizeOverlay
import com.nexus.launcher.ui.widgets.WidgetViewModel

/**
 * Widget-parity edit session for Living Mosaic: scrim, halo drawer, border resize,
 * move/resize arrows, drag. Free-size (wFrac/hFrac) lives on MosaicConfig and survives config rewrites.
 */
class LivingMosaicEditSession(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val widgetOverlayLayout: WidgetOverlayLayout,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val widgetViewModel: WidgetViewModel,
    private val appWidgetController: AppWidgetController,
    private val appWidgetHost: AppWidgetHost,
    private val appWidgetManager: AppWidgetManager,
    private val mosaicSettingsSheet: LivingMosaicSettingsSheet
) {
    private val mainContainer by lazy {
        activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
    }

    private var activeResizeOverlay: WidgetResizeOverlay? = null
    private var activeContextMenuView: LivingMosaicContextMenuView? = null
    private var activeScrimView: WidgetMenuScrimView? = null
    private var activeMoveArrowView: WidgetMoveArrowView? = null
    private var activeResizeArrowView: WidgetResizeArrowView? = null
    private var activeMosaicView: LivingMosaicView? = null
    private var activeSentinel: Int? = null

    fun hasActive(): Boolean =
        activeContextMenuView?.parent != null ||
            activeResizeOverlay?.parent != null ||
            activeMoveArrowView?.parent != null ||
            activeResizeArrowView?.parent != null

    fun dismiss() = dismissAllOverlays("dismiss")

    fun open(item: HomeScreenItem, mosaicView: LivingMosaicView) {
        dismissAllOverlays("new_long_press")

        val overlayRect = android.graphics.Rect()
        mosaicView.getGlobalVisibleRect(overlayRect)
        val containerLoc = IntArray(2)
        mainContainer.getLocationOnScreen(containerLoc)
        overlayRect.offset(-containerLoc[0], -containerLoc[1])

        val density = activity.resources.displayMetrics.density
        mosaicView.animate().scaleX(1.03f).scaleY(1.03f).translationZ(8f * density).setDuration(200).start()
        mosaicView.setEditChromeActive(true)

        val sentinel = SENTINEL_BASE - item.id
        activeSentinel = sentinel
        activeMosaicView = mosaicView
        // No workspace blur, as with a widget's long-press (2026-09-24): the home screen stays
        // sharp through the menu, move and resize. The menu panel frosts itself instead
        // (WidgetDrawerRowView.createDrawerPanel) — the reason a workspace blur was added here
        // was only that the old translucent fill needed one behind it to read as frosted.
        widgetOverlayLayout.liveMosaics[item.id] = item
        widgetOverlayLayout.liveItems[sentinel] = item // MosaicConfig JSON (may include wFrac/hFrac)

        val sessionState = SessionState(item.xFraction, item.yFraction, false)
        val underCap = MosaicConfig.parse(item.folderConfigJson)
            .currentChildren().size < MosaicConfig.MAX_CHILDREN_PER_PAGE

        val scrim = WidgetMenuScrimView(activity, overlayRect).apply { tag = TAG_EDIT }
        activeScrimView = scrim
        mainContainer.addView(scrim)

        val menu = buildMenu(item, mosaicView, overlayRect, underCap, sessionState, sentinel)
        val overlayView = buildResizeOverlay(item, mosaicView, sentinel)

        overlayView.onResizeStarted = {
            activeContextMenuView?.visibility = View.GONE
            activeMoveArrowView?.let { mainContainer.removeView(it) }
            activeMoveArrowView = null
        }
        overlayView.onResizeFinished = {
            syncChromeToMosaicLayout(mosaicView, item.page)
            activeContextMenuView?.visibility = View.VISIBLE
            activeContextMenuView?.requestFocus()
        }
        overlayView.onBackPressed = { dismissAllOverlays("back_press_during_resize") }

        activeContextMenuView = menu
        activeResizeOverlay = overlayView
        mainContainer.addView(menu)
        menu.requestFocus()
        mainContainer.addView(
            overlayView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        mosaicView.onBodyTappedInMoveMode = { menu.exitMoveMode() }
        LivingMosaicEditSessionModes.wireDrag(
            mosaicView, item, activity, canvasView, widgetOverlayLayout, density,
            dismissAll = { dismissAllOverlays(it) },
            homeScreenViewModel = homeScreenViewModel
        )
    }

    private fun buildMenu(
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        overlayRect: android.graphics.Rect,
        underCap: Boolean,
        sessionState: SessionState,
        sentinel: Int
    ): LivingMosaicContextMenuView {
        return LivingMosaicContextMenuView(
            context = activity,
            widgetRect = overlayRect,
            accentColor = canvasView.accentColor,
            initialZIndex = item.zIndex,
            underChildCap = underCap,
            onMosaicSettings = {
                dismissAllOverlays("mosaic_settings")
                mosaicSettingsSheet.show(item, mosaicView)
            },
            onAddWidget = {
                if (underCap) {
                    dismissAllOverlays("add_widget")
                    appWidgetController.launchWidgetPickerForMosaic(item)
                } else {
                    Toast.makeText(
                        activity,
                        activity.getString(com.nexus.launcher.R.string.toast_max_widgets_per_page, MosaicConfig.MAX_CHILDREN_PER_PAGE),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onRemoveClicked = {
                homeScreenViewModel.removeMosaic(item, appWidgetHost)
                dismissAllOverlays("remove_tapped")
            },
            onZIndexChanged = { newZ -> widgetViewModel.updateWidgetZIndex(item, newZ) },
            onModeChanged = { isMoveMode ->
                LivingMosaicEditSessionModes.onMoveModeChanged(
                    isMoveMode, activity, canvasView, widgetOverlayLayout, mainContainer,
                    item, mosaicView, sessionState, activeScrimView, activeResizeOverlay,
                    setMoveArrow = { activeMoveArrowView = it },
                    getMoveArrow = { activeMoveArrowView },
                    homeScreenViewModel = homeScreenViewModel
                )
            },
            onResizeNudgeModeChanged = { isResize ->
                LivingMosaicEditSessionModes.onResizeNudgeModeChanged(
                    isResize, activity, canvasView, widgetOverlayLayout, mainContainer,
                    item, mosaicView, sentinel, activeScrimView, activeResizeOverlay,
                    setResizeArrow = { activeResizeArrowView = it },
                    getResizeArrow = { activeResizeArrowView },
                    homeScreenViewModel = homeScreenViewModel
                )
            },
            onDismissStarted = { activeScrimView?.dismiss() },
            onDismiss = {
                LivingMosaicEditSessionModes.persistPendingMove(
                    activity, homeScreenViewModel, item, sessionState
                )
                LivingMosaicEditSessionModes.persistResizeNudgeIfNeeded(
                    { activeResizeArrowView }, widgetOverlayLayout, mosaicView,
                    item, sentinel, homeScreenViewModel, canvasView
                )
                mosaicView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
                mosaicView.setEditChromeActive(false)
                mosaicView.setMoveModeActive(false)
                dismissAllOverlays("context_menu_dismissed")
            }
        ).apply { tag = TAG_EDIT }
    }

    private fun buildResizeOverlay(
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        sentinel: Int
    ): WidgetResizeOverlay {
        return WidgetResizeOverlay(
            context = activity,
            widgetView = mosaicView,
            overlayLayout = widgetOverlayLayout,
            appWidgetId = sentinel,
            currentXFraction = item.xFraction,
            currentYFraction = item.yFraction,
            cachedColumns = canvasView.currentGridCols,
            cachedRows = canvasView.currentGridRows,
            appWidgetManager = appWidgetManager,
            persistFreeSize = true,
            boundsFromLayoutParams = true,
            onConfirm = { spanX, spanY, xF, yF, wFrac, hFrac ->
                homeScreenViewModel.updateMosaicFreeSize(
                    item.id, spanX, spanY, xF, yF, wFrac, hFrac
                )
                val base = widgetOverlayLayout.liveMosaics[item.id] ?: item
                val cfg = MosaicConfig.parse(base.folderConfigJson).copy(
                    wFrac = wFrac, hFrac = hFrac
                )
                val updated = base.copy(
                    spanX = spanX,
                    spanY = spanY,
                    xFraction = xF,
                    yFraction = yF,
                    folderConfigJson = cfg.toJson()
                )
                widgetOverlayLayout.liveMosaics[item.id] = updated
                widgetOverlayLayout.liveItems[sentinel] = updated
                // Layout already matches drag; only refresh live maps — avoid snap/rebake jump.
            }
        ).apply { tag = TAG_EDIT }
    }

    private fun syncChromeToMosaicLayout(mosaicView: LivingMosaicView, page: Int) {
        val lp = mosaicView.layoutParams as? FrameLayout.LayoutParams ?: return
        val pageW = WidgetCoordinateSpace.pageWidthOf(widgetOverlayLayout)
        val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
            lp.leftMargin.toFloat(), page, pageW
        ).toInt()
        val newRect = android.graphics.Rect(
            screenLeft, lp.topMargin, screenLeft + lp.width, lp.topMargin + lp.height
        )
        activeContextMenuView?.updateForNewWidgetBounds(newRect)
        activeScrimView?.updateWidgetBounds(newRect)
    }

    private fun dismissAllOverlays(source: String) {
        Log.d("MosaicEditSession", "Sweeping overlays from: $source")
        val sentinel = activeSentinel
        // Keep onDragMoved / onDropDetected — drag may continue after chrome dismiss
        activeMosaicView?.setEditChromeActive(false)
        activeMosaicView?.setMoveModeActive(false)
        activeMosaicView?.onBodyTappedInMoveMode = null
        for (i in mainContainer.childCount - 1 downTo 0) {
            val child = mainContainer.getChildAt(i)
            if (child.tag == TAG_EDIT || child.tag == TAG_MOSAIC_SESSION) {
                mainContainer.removeView(child)
            }
        }
        if (sentinel != null) widgetOverlayLayout.liveItems.remove(sentinel)
        activeResizeOverlay = null
        activeMoveArrowView = null
        activeResizeArrowView = null
        activeContextMenuView = null
        activeScrimView = null
        activeMosaicView = null
        activeSentinel = null
    }

    companion object {
        const val TAG_EDIT = "widget_edit_overlay"
        const val TAG_MOSAIC_SESSION = "mosaic_edit_session"
        const val SENTINEL_BASE = -1_000_000
    }

    class SessionState(
        var currentX: Float,
        var currentY: Float,
        var pendingMovePersist: Boolean
    )
}
