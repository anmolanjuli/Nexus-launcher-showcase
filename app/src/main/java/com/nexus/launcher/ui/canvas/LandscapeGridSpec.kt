package com.nexus.launcher.ui.canvas

import android.content.Context
import kotlin.math.abs

/**
 * Grid size for phone landscape: as many cells of the PORTRAIT cell pitch as fit, so icons and
 * widgets are the same size in both orientations instead of being stretched into a swapped grid.
 *
 * The size is measured once, from a settled landscape layout, and persisted with the portrait
 * pitch it was derived from. It is only recomputed when that pitch changes (the portrait grid
 * or its spacing changed) — never from transient layout values, because a change of landscape
 * size discards the landscape arrangement.
 */
object LandscapeGridSpec {

    private const val PREFS = "nexus_prefs"
    private const val KEY_PITCH_X = "portrait_cell_pitch_x"
    private const val KEY_PITCH_Y = "portrait_cell_pitch_y"
    private const val KEY_COLS = "landscape_grid_cols"
    private const val KEY_ROWS = "landscape_grid_rows"
    private const val KEY_FROM_PITCH_X = "landscape_grid_from_pitch_x"
    private const val KEY_FROM_PITCH_Y = "landscape_grid_from_pitch_y"
    private const val KEY_VERSION = "landscape_grid_version"
    /** Bumped when the sizing rule changes, so a stored size is measured again. */
    private const val SPEC_VERSION = 6
    private const val PITCH_TOLERANCE_PX = 1f

    @Volatile private var loaded = false
    @Volatile private var pitchX = 0f
    @Volatile private var pitchY = 0f
    @Volatile private var cols = 0
    @Volatile private var rows = 0
    @Volatile private var fromPitchX = 0f
    @Volatile private var fromPitchY = 0f
    @Volatile private var version = 0

    private fun load(context: Context) {
        if (loaded) return
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        pitchX = p.getFloat(KEY_PITCH_X, 0f)
        pitchY = p.getFloat(KEY_PITCH_Y, 0f)
        cols = p.getInt(KEY_COLS, 0)
        rows = p.getInt(KEY_ROWS, 0)
        fromPitchX = p.getFloat(KEY_FROM_PITCH_X, 0f)
        fromPitchY = p.getFloat(KEY_FROM_PITCH_Y, 0f)
        version = p.getInt(KEY_VERSION, 0)
        loaded = true
    }

    /** Landscape columns; the swapped portrait grid until a size has been measured. */
    fun columns(view: LauncherCanvasView): Int {
        load(view.context)
        return if (isCurrent()) cols else view.gridRenderer.homeRows
    }

    fun rows(view: LauncherCanvasView): Int {
        load(view.context)
        return if (isCurrent()) rows else view.gridRenderer.homeColumns
    }

    private fun isCurrent(): Boolean = version == SPEC_VERSION && cols > 0 && rows > 0 && pitchX > 0f && pitchY > 0f &&
        abs(fromPitchX - pitchX) <= PITCH_TOLERANCE_PX && abs(fromPitchY - pitchY) <= PITCH_TOLERANCE_PX

    /** Called after every portrait layout: remembers the cell pitch the landscape grid copies. */
    fun recordPortraitPitch(view: LauncherCanvasView) {
        load(view.context)
        val c = view.currentGridCols
        val cells = view.homeGridCells
        // Mid-rotation the configuration can already say portrait while the view still has its
        // landscape size (or the reverse); a pitch taken then is wrong and would re-measure
        // (and so discard) the landscape grid.
        if (view.isLandscape || view.viewHeight <= view.viewWidth || c < 2 || cells.size <= c) return
        val px = cells[1].left - cells[0].left
        val py = cells[c].top - cells[0].top
        if (px <= 0f || py <= 0f) return
        if (abs(px - pitchX) <= PITCH_TOLERANCE_PX && abs(py - pitchY) <= PITCH_TOLERANCE_PX) return
        pitchX = px
        pitchY = py
        view.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_PITCH_X, px).putFloat(KEY_PITCH_Y, py).apply()
    }

    /**
     * From a settled landscape layout, measures the landscape size if it is missing or was
     * derived from a different portrait pitch. Returns true when the size changed — the caller
     * must then discard landscape positions and lay out again.
     */
    fun ensureMeasured(view: LauncherCanvasView): Boolean {
        load(view.context)
        if (!view.isLandscape || view.viewWidth <= view.viewHeight ||
            pitchX <= 0f || pitchY <= 0f || isCurrent()) return false
        val density = view.resources.displayMetrics.density
        val padX = 2f * view.homePaddingLeftRightDp * density
        val padY = 2f * view.homePaddingTopBottomDp * density
        val availW = HomeGridArea.usableWidth(view) - padX
        val availH = availableHeight(view) - padY
        if (availW <= 0f || availH <= 0f) return false
        // Columns: whole portrait-width cells (GridMetrics lays cells edge to edge, so pitch =
        // cell width); leftover width becomes a margin on the dock side (sideSlack). Rows: the
        // nearest whole number of portrait-height rows, filling the height — rows come out a
        // little shorter than portrait rather than leaving a band of empty space. Free-sized
        // widgets keep their portrait pixel size regardless (WidgetFreeSize).
        val newRows = Math.round(availH / pitchY).coerceIn(MIN_CELLS, MAX_ROWS)
        val newCols = (availW / pitchX).toInt().coerceIn(MIN_CELLS, MAX_COLS)
        cols = newCols
        rows = newRows
        fromPitchX = pitchX
        fromPitchY = pitchY
        version = SPEC_VERSION
        view.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_COLS, newCols).putInt(KEY_ROWS, newRows)
            .putFloat(KEY_FROM_PITCH_X, pitchX).putFloat(KEY_FROM_PITCH_Y, pitchY)
            .putInt(KEY_VERSION, SPEC_VERSION).apply()
        return true
    }

    private fun availableHeight(view: LauncherCanvasView): Float =
        (view.viewHeight - view.topInset - view.dockBottomReserve).toFloat()

    /** Measured landscape size (columns to rows), or null until one has been measured. */
    fun sizeIfMeasured(context: Context): Pair<Int, Int>? {
        load(context)
        return if (isCurrent()) cols to rows else null
    }

    /**
     * Width the landscape grid leaves unused so its cells are exactly the portrait cell size,
     * given [usableWidth] (screen minus dock strip and side insets). Placed on the dock side by
     * [HomeGridArea]. 0 in portrait or before a size has been measured.
     */
    fun sideSlack(view: LauncherCanvasView, usableWidth: Int): Int {
        load(view.context)
        if (!view.isLandscape || !isCurrent()) return 0
        val density = view.resources.displayMetrics.density
        val padX = 2f * view.homePaddingLeftRightDp * density
        return (usableWidth - (padX + cols * pitchX)).toInt().coerceAtLeast(0)
    }

    private const val MIN_CELLS = 3
    private const val MAX_COLS = 20
    private const val MAX_ROWS = 12
}
