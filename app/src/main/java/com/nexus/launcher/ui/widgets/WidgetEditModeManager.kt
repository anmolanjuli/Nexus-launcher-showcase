package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.util.Log
import android.view.View
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.LauncherCanvasView

class WidgetEditModeManager(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val widgetOverlayLayout: WidgetOverlayLayout,
    private val widgetViewModel: WidgetViewModel,
    private val appWidgetController: AppWidgetController,
    private val appWidgetManager: AppWidgetManager,
    private val appWidgetHost: AppWidgetHost
) {
    private val mainContainer by lazy { activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.main_container) }
    private var activeResizeOverlay: WidgetResizeOverlay? = null
    private var activeContextMenuView: WidgetContextMenuView? = null
    private var activeScrimView: WidgetMenuScrimView? = null
    private var activeMoveArrowView: WidgetMoveArrowView? = null
    private var activeResizeArrowView: WidgetResizeArrowView? = null

    var onDismissAll: (() -> Unit)? = null

    fun dismissAllOverlays(source: String) {
        Log.d("OverlayCleanup", "Sweeping overlays from: $source")
        var removed = 0
        for (i in mainContainer.childCount - 1 downTo 0) {
            val child = mainContainer.getChildAt(i)
            if (child.tag == "widget_edit_overlay") {
                mainContainer.removeView(child)
                removed++
            }
        }
        Log.d("OverlayCleanup", "Total removed: $removed")
        activeResizeOverlay = null
        activeMoveArrowView = null
        activeResizeArrowView = null
        activeContextMenuView = null
        activeScrimView = null
        canvasView.setDragOverviewActive(false)
        widgetOverlayLayout.draggingWidgetView = null
        onDismissAll?.invoke()
    }

    fun hasActiveOverlays(): Boolean =
        activeContextMenuView != null ||
        activeResizeOverlay != null ||
        activeMoveArrowView != null ||
        activeResizeArrowView != null

    fun dismissOverlaysFromActivity() =
        dismissAllOverlays("back_press_activity")

    init {
        widgetOverlayLayout.onWidgetLongPress = { item, widgetView ->
            dismissAllOverlays("new_long_press")
            val overlayRect = android.graphics.Rect()
            widgetView.getGlobalVisibleRect(overlayRect)
            val containerLoc = IntArray(2)
            mainContainer.getLocationOnScreen(containerLoc)
            overlayRect.offset(-containerLoc[0], -containerLoc[1])
            
            val density = activity.resources.displayMetrics.density
            widgetView.animate().scaleX(1.03f).scaleY(1.03f).translationZ(8f*density).setDuration(200).start()
            
            if (widgetView is NexusWidgetView) {
                widgetView.setResizeModeActive(true)
            }
            
            var currentX = item.xFraction
            var currentY = item.yFraction

            var pendingMovePersist = false

            // No workspace blur here, and none through move or resize (2026-09-24): the
            // surrounding widgets, folders and icons are what a resize is measured against, so
            // they stay sharp. The menu panel frosts itself (WidgetDrawerRowView.createDrawerPanel).

            val scrim = WidgetMenuScrimView(activity, overlayRect)
            scrim.tag = "widget_edit_overlay"
            activeScrimView = scrim
            mainContainer.addView(scrim)

            val appWidgetInfo = appWidgetManager.getAppWidgetInfo(item.appWidgetId)
            val isNexusWidget = appWidgetInfo?.provider?.packageName == activity.packageName

            val menu = WidgetContextMenuView(
                context = activity,
                widgetRect = overlayRect,
                accentColor = canvasView.accentColor,
                initialZIndex = item.zIndex,
                initialPaddingEnabled = item.paddingEnabled,
                onZIndexChanged = { newZ ->
                    widgetViewModel.updateWidgetZIndex(item, newZ)
                },
                onReplaceClicked = {
                    dismissAllOverlays("replace_widget")
                    val live = widgetOverlayLayout.liveItems[item.appWidgetId] ?: item
                    appWidgetController.replaceWidget(live)
                },
                onRemoveClicked = {
                    widgetViewModel.removeWidget(item, appWidgetHost)
                    dismissAllOverlays("remove_tapped")
                },
                onSettingsClicked = buildSettingsAction(item, isNexusWidget, appWidgetInfo, overlayRect, widgetView),
                onModeChanged = { isMoveMode ->
                    if (isMoveMode) {
                        activeScrimView?.animateDim(0f)
                        activeResizeOverlay?.visibility = View.GONE
                        (widgetView as? NexusWidgetView)?.setMoveModeActive(true)
                        (widgetView as? com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView)?.setMoveModeActive(true)
                        (widgetView as? com.nexus.launcher.ui.widgets.appbox.AppBoxView)?.setMoveModeActive(true)
                        (widgetView as? com.nexus.launcher.ui.widgets.liveapp.LiveAppView)?.setMoveModeActive(true)
                        
                        val startItem = widgetOverlayLayout.liveItems[item.appWidgetId] ?: item
                        currentX = startItem.xFraction
                        currentY = startItem.yFraction

                        val arrows = WidgetMoveArrowView(
                            context = activity,
                            widgetView = widgetView,
                            page = startItem.page,
                            pageWidth = WidgetCoordinateSpace.pageWidthOf(widgetOverlayLayout),
                            onNudge = { dx, dy ->
                                val pw = canvasView.width.toFloat()
                                val ph = canvasView.height.toFloat()
                                val deltaXF = dx / pw
                                val deltaYF = dy / ph
                                
                                currentX += deltaXF
                                currentY += deltaYF
                                
                                val gridHeight = (canvasView.height - canvasView.topInset - canvasView.dockBottomReserve)
                                    .toFloat().coerceAtLeast(0f)
                                val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
                                    canvasView.gridAreaWidth.toFloat(),
                                    gridHeight,
                                    canvasView.effectiveHomeColumns,
                                    canvasView.effectiveHomeRows,
                                    canvasView.homePaddingLeftRightDp,
                                    canvasView.homePaddingTopBottomDp,
                                    canvasView.homeGapHorizontalDp,
                                    canvasView.homeGapVerticalDp,
                                    canvasView.resources.displayMetrics.density
                                )
                                val targetId = if (item.appWidgetId != -1) item.appWidgetId else item.id
                                val liveItem = widgetOverlayLayout.liveItems[targetId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
                                val spanX = liveItem.spanX
                                val spanY = liveItem.spanY
                                val maxCol = (canvasView.effectiveHomeColumns - spanX).coerceAtLeast(0)
                                val maxRow = (canvasView.effectiveHomeRows - spanY).coerceAtLeast(0)
                                val gridLeft = canvasView.gridAreaLeft.toFloat()
                                val gridTop = canvasView.topInset.toFloat()
                                val minB = metrics.getCellBounds(0, 0, spanX, spanY, gridLeft, gridTop)
                                val maxB = metrics.getCellBounds(maxCol, maxRow, spanX, spanY, gridLeft, gridTop)
                                val w = canvasView.width.toFloat().coerceAtLeast(1f)
                                val h = canvasView.height.toFloat().coerceAtLeast(1f)
                                currentX = currentX.coerceIn(
                                    (minB.left + minB.right) / 2f / w,
                                    (maxB.left + maxB.right) / 2f / w
                                )
                                currentY = currentY.coerceIn(
                                    (minB.top + minB.bottom) / 2f / h,
                                    (maxB.top + maxB.bottom) / 2f / h
                                )

                                val nudgeItem = widgetOverlayLayout.liveItems[targetId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
                                widgetOverlayLayout.updateWidgetLayoutParams(targetId, currentX, currentY, nudgeItem.spanX, nudgeItem.spanY)
                                pendingMovePersist = true
                                
                                val lp = widgetView.layoutParams as android.widget.FrameLayout.LayoutParams
                                val pageW = WidgetCoordinateSpace.pageWidthOf(widgetOverlayLayout)
                                val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
                                    lp.leftMargin.toFloat(), nudgeItem.page, pageW
                                ).toInt()
                                activeMoveArrowView?.updateWidgetBounds(screenLeft, lp.topMargin, lp.width, lp.height)
                            }
                        )
                        arrows.tag = "widget_edit_overlay"
                        activeMoveArrowView = arrows
                        mainContainer.addView(arrows)
                    } else {
                        activeScrimView?.animateDim(1f)
                        activeResizeOverlay?.visibility = View.VISIBLE
                        (widgetView as? NexusWidgetView)?.setMoveModeActive(false)
                        (widgetView as? com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView)?.setMoveModeActive(false)
                        (widgetView as? com.nexus.launcher.ui.widgets.appbox.AppBoxView)?.setMoveModeActive(false)
                        (widgetView as? com.nexus.launcher.ui.widgets.liveapp.LiveAppView)?.setMoveModeActive(false)
                        activeMoveArrowView?.let { mainContainer.removeView(it) }
                        activeMoveArrowView = null
                        if (pendingMovePersist) {
                            pendingMovePersist = false
                            val targetId = if (item.appWidgetId != -1) item.appWidgetId else item.id
                            val currentItem = widgetOverlayLayout.liveItems[targetId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
                            widgetViewModel.updateWidgetPosition(currentItem, currentX, currentY, activity)
                        }
                    }
                },
                onResizeNudgeModeChanged = { isResizeNudgeMode ->
                    if (isResizeNudgeMode) {
                        activeScrimView?.animateDim(0f)
                        activeResizeOverlay?.visibility = View.GONE
                        val lp = widgetView.layoutParams as android.widget.FrameLayout.LayoutParams
                        val pageW = WidgetCoordinateSpace.pageWidthOf(widgetOverlayLayout)
                        val livePage = widgetOverlayLayout.liveItems[item.appWidgetId]?.page ?: item.page
                        val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
                            lp.leftMargin.toFloat(), livePage, pageW
                        ).toInt()
                        val arrows = WidgetResizeArrowView(
                            context = activity,
                            widgetView = widgetView,
                            overlayLayout = widgetOverlayLayout,
                            appWidgetId = item.appWidgetId,
                            cachedColumns = canvasView.currentGridCols,
                            cachedRows = canvasView.currentGridRows
                        ).apply {
                            tag = "widget_edit_overlay"
                            updateWidgetBounds(screenLeft, lp.topMargin, lp.width, lp.height)
                        }
                        activeResizeArrowView = arrows
                        mainContainer.addView(arrows, android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                        ))
                    } else {
                        activeScrimView?.animateDim(1f)
                        activeResizeArrowView?.persistAndExit(widgetViewModel, appWidgetManager, item)
                        activeResizeArrowView?.let { mainContainer.removeView(it) }
                        activeResizeArrowView = null
                        activeResizeOverlay?.visibility = View.VISIBLE
                    }
                },
                onPaddingToggled = { enabled ->
                    val live = widgetOverlayLayout.liveItems[item.appWidgetId]
                    if (live != null && (live.xFraction != item.xFraction || live.yFraction != item.yFraction || live.spanX != item.spanX || live.spanY != item.spanY)) {
                        widgetViewModel.updateWidgetBounds(item, live.spanX, live.spanY, live.xFraction, live.yFraction, activity)
                    }
                    widgetViewModel.updateWidgetPadding(live ?: item, enabled)
                    val pad = if (!enabled) 0 else if (android.os.Build.VERSION.SDK_INT >= 31) (8f * activity.resources.displayMetrics.density).toInt() else 0
                    widgetView.setPadding(pad, pad, pad, pad)
                },
                onDismissStarted = {
                    activeScrimView?.dismiss()
                },
                onDismiss = {
                    widgetView.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
                    (widgetView as? NexusWidgetView)?.setResizeModeActive(false)
                    (widgetView as? NexusWidgetView)?.setMoveModeActive(false)
                    (widgetView as? com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView)?.setResizeModeActive(false)
                    (widgetView as? com.nexus.launcher.ui.widgets.shortcutbox.ShortcutBoxView)?.setMoveModeActive(false)
                    (widgetView as? com.nexus.launcher.ui.widgets.appbox.AppBoxView)?.setResizeModeActive(false)
                    (widgetView as? com.nexus.launcher.ui.widgets.appbox.AppBoxView)?.setMoveModeActive(false)
                    (widgetView as? com.nexus.launcher.ui.widgets.liveapp.LiveAppView)?.setResizeModeActive(false)
                    (widgetView as? com.nexus.launcher.ui.widgets.liveapp.LiveAppView)?.setMoveModeActive(false)
                    if (pendingMovePersist) {
                        pendingMovePersist = false
                        val targetId = if (item.appWidgetId != -1) item.appWidgetId else item.id
                        val currentItem = widgetOverlayLayout.liveItems[targetId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
                        widgetViewModel.updateWidgetPosition(currentItem, currentX, currentY, activity)
                    }
                    dismissAllOverlays("menu_dismiss")
                }
            ).apply { tag = "widget_edit_overlay" }
            
            val overlayView = WidgetResizeOverlay(
                boundsFromLayoutParams = true,
                context = activity,
                widgetView = widgetView,
                overlayLayout = widgetOverlayLayout,
                appWidgetId = item.appWidgetId,
                currentXFraction = item.xFraction,
                currentYFraction = item.yFraction,
                cachedColumns = canvasView.currentGridCols,
                cachedRows = canvasView.currentGridRows,
                appWidgetManager = appWidgetManager,
                onConfirm = { spanX, spanY, xFraction, yFraction, wFrac, hFrac ->
                    widgetViewModel.updateWidgetFreeSize(item, spanX, spanY, xFraction, yFraction, wFrac, hFrac, activity)
                }
            ).apply { 
                tag = "widget_edit_overlay"
            }
            overlayView.tag = "widget_edit_overlay"
            overlayView.onResizeStarted = {
                activeContextMenuView?.visibility = View.GONE
                activeMoveArrowView?.let { mainContainer.removeView(it) }
                activeMoveArrowView = null
            }
            overlayView.onResizeFinished = {
                val targetId = if (item.appWidgetId != -1) item.appWidgetId else item.id
                val lp = widgetView.layoutParams as? android.widget.FrameLayout.LayoutParams
                if (lp != null) {
                    val currentLive = widgetOverlayLayout.liveItems[targetId] ?: widgetOverlayLayout.liveMosaics[item.id] ?: item
                    val pageW = WidgetCoordinateSpace.pageWidthOf(widgetOverlayLayout)
                    val screenLeft = WidgetCoordinateSpace.absoluteToScreenX(
                        lp.leftMargin.toFloat(), currentLive.page, pageW
                    ).toInt()
                    val newRect = android.graphics.Rect(
                        screenLeft, lp.topMargin, screenLeft + lp.width, lp.topMargin + lp.height
                    )
                    activeContextMenuView?.updateForNewWidgetBounds(newRect)
                    activeScrimView?.updateWidgetBounds(newRect)
                }
                activeContextMenuView?.visibility = View.VISIBLE
                activeContextMenuView?.requestFocus()
            }
            
            activeContextMenuView = menu
            activeResizeOverlay = overlayView
            
            mainContainer.addView(menu)
            activeContextMenuView?.requestFocus()
            mainContainer.addView(overlayView, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            ))

            WidgetEditDragHelper.wire(
                widgetView = widgetView,
                item = item,
                activity = activity,
                canvasView = canvasView,
                widgetOverlayLayout = widgetOverlayLayout,
                widgetViewModel = widgetViewModel,
                menu = menu,
                density = density,
                dismissAllOverlays = ::dismissAllOverlays
            )
        }
    }

    private fun buildSettingsAction(
        item: com.nexus.launcher.data.HomeScreenItem,
        isNexusWidget: Boolean,
        appWidgetInfo: android.appwidget.AppWidgetProviderInfo?,
        overlayRect: android.graphics.Rect,
        widgetView: View?
    ): (() -> Unit)? = WidgetSettingsSheetLauncher.buildSettingsAction(
        activity, item, isNexusWidget, appWidgetInfo, overlayRect, widgetView
    )
}
