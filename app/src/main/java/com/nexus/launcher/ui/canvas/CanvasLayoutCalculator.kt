package com.nexus.launcher.ui.canvas

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.data.LayoutShapeState
import com.nexus.launcher.ui.immersive.ImmersiveStatus
import com.nexus.launcher.ui.widgets.WidgetCoordinateSpace

/**
 * Calculates insets, grid metrics, and layout parameters for [LauncherCanvasView].
 * Extracted from LauncherCanvasView to keep that file well below 400 lines.
 */
internal object CanvasLayoutCalculator {

    fun recalculateTopInset(view: LauncherCanvasView) {
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusBars = maxOf(
            insets?.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())?.top ?: 0,
            ImmersiveStatus.reservedTopPx
        )
        view.topInset = statusBars + 16
        SideInsets.update(view)
        recalculateLayout(view)
    }

    fun recalculateLayout(view: LauncherCanvasView) {
        if (view.viewWidth > 0 && view.viewHeight > 0) {
            val density = view.resources.displayMetrics.density
            val dockReserve = if (view.isLandscape) {
                0
            } else {
                (((view.viewWidth / 5) * 0.55f).coerceAtMost(56f * density) + 8f * density).toInt()
            }
            view.dockBottomReserve = dockReserve
            com.nexus.launcher.ui.canvas.GridMetrics.compute(
                view.gridAreaWidth.toFloat(),
                (view.viewHeight - view.topInset - view.bottomBarHeight - dockReserve).toFloat().coerceAtLeast(0f),
                view.currentGridCols,
                view.currentGridRows,
                view.homePaddingLeftRightDp,
                view.homePaddingTopBottomDp,
                view.homeGapHorizontalDp,
                view.homeGapVerticalDp,
                density
            )
            view.homeScreenRenderer.userIconSizeMultiplier = view.homeIconSizeMultiplier
            view.homeScreenRenderer.gridRows = view.currentGridRows
        }
        view.drawEngine.recalculateLayout()
        WidgetCoordinateSpace.updateGridFrame(view.gridAreaLeft.toFloat(), view.gridAreaWidth.toFloat())
        LandscapeGridSpec.recordPortraitPitch(view)
        LayoutShapeState.liveColumns = view.currentGridCols
        LayoutShapeState.liveRows = view.currentGridRows
        updateTotalPages(view)
    }

    fun updateTotalPages(view: LauncherCanvasView, settingPageCount: Int = 1) {
        val maxVisualPage = view.homeScreenItems.maxOfOrNull {
            view.fractionDerivedPositions[it.id]?.first ?: it.page
        } ?: 0
        val pageCountKey = "home_page_count"
        val prefs = view.context.getSharedPreferences("nexus_prefs", android.content.Context.MODE_PRIVATE)
        val newTotal = Math.max(prefs.getInt(pageCountKey, 1), maxVisualPage + 1)
        if (newTotal != view.totalPages) {
            view.totalPages = newTotal
            if (view.currentPage >= view.totalPages) {
                view.setCurrentPage(view.totalPages - 1)
            }
            view.invalidate()
        }
    }
}
