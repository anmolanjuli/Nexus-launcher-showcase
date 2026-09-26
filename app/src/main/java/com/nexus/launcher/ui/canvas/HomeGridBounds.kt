package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.ui.folder.FolderBlurCoordinator

/** Live home-grid column/row counts — never use [NexusDefaults] as a placement ceiling. */
object HomeGridBounds {
    fun columns(raw: Int): Int = raw.coerceIn(
        NexusDefaults.HOME_COLUMNS_MIN,
        NexusDefaults.HOME_COLUMNS_MAX
    )

    fun rows(raw: Int): Int = raw.coerceIn(
        NexusDefaults.HOME_ROWS_MIN,
        NexusDefaults.HOME_ROWS_MAX
    )

    fun liveOrDefault(context: android.content.Context?): Pair<Int, Int> {
        val canvas = context?.let { FolderBlurCoordinator.findCanvas(it) }
        if (canvas != null && canvas.currentGridCols > 0 && canvas.currentGridRows > 0) {
            return canvas.currentGridCols to canvas.currentGridRows
        }
        return NexusDefaults.HOME_COLUMNS to NexusDefaults.HOME_ROWS
    }
}
