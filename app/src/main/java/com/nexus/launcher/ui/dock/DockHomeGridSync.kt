package com.nexus.launcher.ui.dock

import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.ui.canvas.HomeGridAvailableSpace
import com.nexus.launcher.ui.canvas.HomeGridNetInputs
import com.nexus.launcher.ui.canvas.HomeGridNetSnapshot
import com.nexus.launcher.ui.canvas.HomeGridOverlapDiag
import com.nexus.launcher.ui.canvas.LauncherCanvasView

/**
 * Keeps home-grid [LauncherCanvasView.dockBottomReserve] aligned with the real
 * [DockLayout] top edge (height + margins + label band), not a canvas estimate.
 */
object DockHomeGridSync {

    private const val INDICATOR_BAND_DP = HomeGridAvailableSpace.INDICATOR_BAND_DP

    /** Page dots plus breathing room between the last landscape row and the gesture bar. */
    private const val LANDSCAPE_DOT_BAND_DP = 20f

    /** Space kept under the last row instead of a dot band when the page indicator is off. */
    private const val NO_INDICATOR_GAP_DP = 8f

    /** [dotsDp] of room for the page dots, or just a small gap when they are turned off. */
    private fun dotBand(view: LauncherCanvasView, dotsDp: Float, density: Float): Int =
        ((if (view.drawEngine.isPageIndicatorShown) dotsDp else NO_INDICATOR_GAP_DP) * density).toInt()

    fun applyReserve(view: LauncherCanvasView, fallbackDockIconSize: Int, density: Float) {
        applyDockReserve(view, fallbackDockIconSize, density)
        // A status row along the bottom edge takes its height from the grid, like the dock does.
        view.dockBottomReserve += com.nexus.launcher.ui.immersive.ImmersiveStatus.reservedBottomPx
    }

    private fun applyDockReserve(view: LauncherCanvasView, fallbackDockIconSize: Int, density: Float) {
        val indicatorBand = dotBand(view, INDICATOR_BAND_DP, density)
        if (!DockPresence.enabled) {
            applyNoDockReserve(view, density)
            return
        }
        val dock = DockLayout.findFrom(view)
        if (view.isLandscape) {
            // The dock is on the side, but the gesture bar is still at the bottom. The page dots
            // sit just above it (DrawEnginePageIndicator draws 8dp above dockVisualTopY), and the
            // last grid row just above the dots.
            val navBottom = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            view.dockVisualTopY = (view.viewHeight - navBottom).toFloat()
            // The rows fill the rest of the height (LandscapeGridSpec picks the nearest whole
            // number of portrait-height rows), so no band is left between them and the dots.
            view.dockBottomReserve = navBottom + dotBand(view, LANDSCAPE_DOT_BAND_DP, density)
            // Valid landscape dock: vertical strip (width is thickness < screen width / 2, height spans screen)
            val isValidLandscapeBounds = dock != null && dock.isLaidOut && dock.width > 0 &&
                dock.width < view.viewWidth / 2 && dock.height >= view.viewHeight / 2
            if (isValidLandscapeBounds && dock != null) {
                val sideReserve = if (view.dockOnRight) {
                    (view.viewWidth - dock.left).coerceAtLeast(dock.width)
                } else {
                    dock.right.coerceAtLeast(dock.width)
                }
                view.dockStripWidth = sideReserve
                HomeGridOverlapDiag.noteHomeDock(
                    source = "landscape_live_DockLayout",
                    measuredHeightPx = dock.width,
                    dockTopInCanvasPx = view.viewHeight,
                    indicatorBandPx = 0
                )
                HomeGridOverlapDiag.logDock(
                    caller = HomeGridOverlapDiag.CALLER_HOME,
                    source = "landscape_live_DockLayout",
                    reservePx = sideReserve
                )
                publishNet(view, "landscape_live_DockLayout")
                return
            }
            val fallbackStrip = ((com.nexus.launcher.ui.dock.settings.DockSettingsRepository.DEFAULT_DOCK_HEIGHT_DP + 16) * density).toInt()
            view.dockStripWidth = fallbackStrip
            HomeGridOverlapDiag.noteHomeDock(
                source = "landscape_fallback",
                measuredHeightPx = fallbackStrip,
                dockTopInCanvasPx = view.viewHeight,
                indicatorBandPx = 0
            )
            HomeGridOverlapDiag.logDock(
                caller = HomeGridOverlapDiag.CALLER_HOME,
                source = "landscape_fallback",
                reservePx = fallbackStrip
            )
            publishNet(view, "landscape_fallback")
            return
        }
        view.dockStripWidth = 0
        // Valid portrait dock: bottom strip (top is in bottom half of screen, width spans screen)
        val isValidPortraitBounds = dock != null && dock.isLaidOut && dock.height > 0 &&
            dock.top >= view.viewHeight / 2 && dock.width >= view.viewWidth / 2
        if (isValidPortraitBounds && dock != null) {
            val dockTopInCanvas = dock.top - view.top
            if (dockTopInCanvas in 1 until view.viewHeight) {
                view.dockVisualTopY = dockTopInCanvas.toFloat()
                // Clear the dock band itself, plus page-indicator space above it.
                view.dockBottomReserve =
                    (view.viewHeight - dockTopInCanvas).coerceAtLeast(0) + indicatorBand
                HomeGridOverlapDiag.noteHomeDock(
                    source = "live_DockLayout",
                    measuredHeightPx = dock.height,
                    dockTopInCanvasPx = dockTopInCanvas,
                    indicatorBandPx = indicatorBand
                )
                HomeGridOverlapDiag.logDock(
                    caller = HomeGridOverlapDiag.CALLER_HOME,
                    source = "live_DockLayout",
                    reservePx = view.dockBottomReserve,
                    measuredHeightPx = dock.height,
                    dockTopInCanvasPx = dockTopInCanvas,
                    indicatorBandPx = indicatorBand
                )
                publishNet(view, "live_DockLayout")
                return
            }
        }
        val labelBand = (HomeGridAvailableSpace.DOCK_LABEL_BAND_DP * density).toInt()
        val dockBase = fallbackDockIconSize + labelBand
        view.dockBottomReserve = dockBase + indicatorBand
        view.dockVisualTopY = view.viewHeight - dockBase - 16f * density
        HomeGridOverlapDiag.noteHomeDock(
            source = "fallback_dockIconPlusLabelPlusIndicator",
            measuredHeightPx = fallbackDockIconSize + labelBand,
            dockTopInCanvasPx = view.dockVisualTopY.toInt(),
            indicatorBandPx = indicatorBand
        )
        HomeGridOverlapDiag.logDock(
            caller = HomeGridOverlapDiag.CALLER_HOME,
            source = "fallback_dockIconPlusLabelPlusIndicator",
            reservePx = view.dockBottomReserve,
            measuredHeightPx = fallbackDockIconSize + labelBand,
            dockTopInCanvasPx = view.dockVisualTopY.toInt(),
            indicatorBandPx = indicatorBand,
            dockIconPx = fallbackDockIconSize,
            labelBandPx = labelBand
        )
        publishNet(view, "fallback_dockIconPlusLabelPlusIndicator")
    }

    /** Space below the page dots when there is no dock under them. */
    private const val NO_DOCK_EDGE_DP = 8f

    /**
     * Dock turned off: the grid runs down to the page dots, which sit just above the navigation
     * bar (or the screen edge in immersive mode); in landscape the side strip goes to the grid.
     * Landscape keeps its usual bottom band — the dock was on the side there anyway.
     */
    private fun applyNoDockReserve(view: LauncherCanvasView, density: Float) {
        val navBottom = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
        view.dockStripWidth = 0
        if (view.isLandscape) {
            view.dockVisualTopY = (view.viewHeight - navBottom).toFloat()
            view.dockBottomReserve = navBottom + dotBand(view, LANDSCAPE_DOT_BAND_DP, density)
        } else {
            // DrawEnginePageIndicator draws the dots 8dp above dockVisualTopY.
            val edge = (NO_DOCK_EDGE_DP * density).toInt()
            view.dockVisualTopY = (view.viewHeight - navBottom - edge).toFloat()
            view.dockBottomReserve = navBottom + edge + dotBand(view, INDICATOR_BAND_DP, density)
        }
        publishNet(view, "no_dock")
    }

    private fun publishNet(view: LauncherCanvasView, source: String) {
        val statusBar = (view.topInset - HomeGridAvailableSpace.TOP_INSET_EXTRA_PX)
            .coerceAtLeast(0)
        HomeGridNetSnapshot.publish(
            HomeGridNetInputs(
                gridWidthPx = view.gridAreaWidth,
                canvasHeightPx = view.viewHeight,
                statusBarPx = statusBar,
                topInsetPx = view.topInset,
                dockReservePx = view.dockBottomReserve,
                dockSource = source
            )
        )
    }

    /** Call when [DockLayout] size changes so the home grid remeasures against the new top. */
    fun notifyCanvasFromDock(dock: DockLayout) {
        val canvas = findCanvas(dock) ?: return
        if (canvas.viewWidth <= 0 || canvas.viewHeight <= 0) return
        canvas.post {
            canvas.recalculateLayout()
            canvas.invalidate()
            findWidgetOverlay(dock)?.rebindCachedWidgets()
        }
    }

    private fun findCanvas(anchor: View): LauncherCanvasView? {
        var parent = anchor.parent
        while (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is LauncherCanvasView) return child
            }
            parent = parent.parent
        }
        return null
    }

    private fun findWidgetOverlay(anchor: View): com.nexus.launcher.ui.widgets.WidgetOverlayLayout? {
        var parent = anchor.parent
        while (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is com.nexus.launcher.ui.widgets.WidgetOverlayLayout) return child
            }
            parent = parent.parent
        }
        return null
    }
}
