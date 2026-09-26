package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState

/**
 * Pixel-style selection overview: each page is an 80% card in full-screen coordinates.
 * The horizontal track owns scroll; [workspace_container] is never scaled.
 */
object SelectionModeTransform {
    const val SCALE = 0.80f
    const val CORNER_RADIUS_DP = 20f

    fun isHomeSelecting(view: LauncherCanvasView): Boolean {
        return view.selectionState is SelectionState.Selecting &&
            (view.selectionState as SelectionState.Selecting).source == SelectionSource.HOME_SCREEN &&
            view.uiState == LauncherState.HOME
    }

    fun isHomeDragging(view: LauncherCanvasView): Boolean {
        return view.isHomeDragOverviewActive && view.uiState == LauncherState.HOME
    }

    fun blocksFeed(view: LauncherCanvasView): Boolean = isHomeSelecting(view) || isHomeDragging(view)

    fun isCardTrackActive(view: LauncherCanvasView): Boolean = isHomeSelecting(view) || isHomeDragging(view)

    /** Map screen touch to workspace coordinates on [targetPage] (inverse card transform). */
    fun mapTouchIfSelecting(view: LauncherCanvasView, x: Float, y: Float, targetPage: Int = view.currentPage): Pair<Float, Float> {
        if (!isCardTrackActive(view)) return x to y
        return SelectionModeCardTrack.mapTouchToPage(view, x, y, targetPage)
    }

    fun drawCardBorder(canvas: Canvas, cardBounds: RectF, density: Float) {
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = android.graphics.Color.parseColor("#99FFFFFF")
        }
        val inset = 1f * density
        canvas.drawRoundRect(
            cardBounds.left + inset, cardBounds.top + inset,
            cardBounds.right - inset, cardBounds.bottom - inset,
            CORNER_RADIUS_DP * density, CORNER_RADIUS_DP * density, borderPaint
        )
    }
}
