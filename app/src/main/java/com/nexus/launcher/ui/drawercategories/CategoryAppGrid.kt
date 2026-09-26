package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout

object CategoryAppGrid {
    fun create(context: Context, columns: Int = 4): GridLayout {
        return GridLayout(context).apply {
            columnCount = columns
            alignmentMode = GridLayout.ALIGN_BOUNDS
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    fun bind(
        grid: GridLayout,
        apps: List<CategoryApp>,
        columns: Int = 4,
        iconDp: Float = 52f,
        showLabel: Boolean = true,
        lightPlate: Boolean = false,
        onAppClick: ((CategoryApp) -> Unit)? = null,
        onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
        gapDp: Float = 4f,
    ) {
        grid.removeAllViews()
        grid.columnCount = columns
        val density = grid.resources.displayMetrics.density
        val pad = (gapDp * density).toInt()
        val size = CategoryIconSize.scale(iconDp)
        apps.forEachIndexed { index, app ->
            val tile = CategoryAppTile(grid.context)
            tile.bind(app, size, showLabel, lightPlate)
            com.nexus.launcher.ui.drawercategories.CategoriesDrawerIconTouch.bind(
                tile, app, onAppClick, onAppLongPress,
            )
            tile.layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(index % columns, 1f)
                rowSpec = GridLayout.spec(index / columns)
                setMargins(pad, pad, pad, pad)
            }
            grid.addView(tile)
        }
    }

    fun bindColumnMajor(
        host: LinearLayout,
        apps: List<CategoryApp>,
        viewportWidthPx: Int,
        maxRows: Int = 3,
        iconDp: Float = 44f,
        showLabel: Boolean = true,
        lightPlate: Boolean = false,
        onAppClick: ((CategoryApp) -> Unit)? = null,
        onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
    ) {
        host.removeAllViews()
        host.orientation = LinearLayout.VERTICAL
        host.isBaselineAligned = false
        if (apps.isEmpty()) return
        val density = host.resources.displayMetrics.density
        val pad = (4f * density).toInt()
        val size = CategoryIconSize.scale(iconDp)
        val tileW = ((size + 12f) * density).toInt()
        val visibleCols = (viewportWidthPx / (tileW + pad)).coerceAtLeast(1)
        val count = apps.size
        // As many rows as it takes to fill the visible columns, up to [maxRows]; then each
        // column is filled top to bottom before the next one, so nothing is left empty on the
        // right and the overflow simply scrolls.
        val rows = ((count + visibleCols - 1) / visibleCols).coerceIn(1, maxRows)
        val byRow = Array(rows) { mutableListOf<CategoryApp>() }
        for (i in apps.indices) {
            byRow[i % rows].add(apps[i])
        }
        for (rowApps in byRow) {
            val rowLayout = LinearLayout(host.context).apply {
                orientation = LinearLayout.HORIZONTAL
                isBaselineAligned = false
            }
            for (app in rowApps) {
                val tile = CategoryAppTile(host.context)
                tile.bind(app, size, showLabel, lightPlate)
                com.nexus.launcher.ui.drawercategories.CategoriesDrawerIconTouch.bind(
                    tile, app, onAppClick, onAppLongPress,
                )
                tile.layoutParams = LinearLayout.LayoutParams(tileW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = pad
                }
                rowLayout.addView(tile)
            }
            rowLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = pad }
            host.addView(rowLayout)
        }
    }

    fun bindRow(
        row: LinearLayout,
        apps: List<CategoryApp>,
        iconDp: Float,
        showLabel: Boolean,
        lightPlate: Boolean,
        onAppClick: ((CategoryApp) -> Unit)? = null,
        onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
    ) {
        row.removeAllViews()
        row.orientation = LinearLayout.HORIZONTAL
        row.isBaselineAligned = false
        val size = CategoryIconSize.scale(iconDp)
        apps.forEach { app ->
            val tile = CategoryAppTile(row.context)
            tile.bind(app, size, showLabel, lightPlate)
            com.nexus.launcher.ui.drawercategories.CategoriesDrawerIconTouch.bind(
                tile, app, onAppClick, onAppLongPress,
            )
            tile.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(tile)
        }
    }

    fun bindScrollRow(
        row: LinearLayout,
        apps: List<CategoryApp>,
        iconDp: Float,
        showLabel: Boolean,
        lightPlate: Boolean,
        onAppClick: ((CategoryApp) -> Unit)? = null,
        onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
    ) {
        row.removeAllViews()
        row.orientation = LinearLayout.HORIZONTAL
        row.isBaselineAligned = false
        val density = row.resources.displayMetrics.density
        val size = CategoryIconSize.scale(iconDp)
        val tileW = ((size + 12f) * density).toInt()
        val gap = (8f * density).toInt()
        apps.forEach { app ->
            val tile = CategoryAppTile(row.context)
            tile.bind(app, size, showLabel, lightPlate)
            com.nexus.launcher.ui.drawercategories.CategoriesDrawerIconTouch.bind(
                tile, app, onAppClick, onAppLongPress,
            )
            tile.layoutParams = LinearLayout.LayoutParams(tileW, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = gap
            }
            row.addView(tile)
        }
    }
}
