package com.nexus.launcher.ui.widgets.liveapp

import android.content.Context
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.canvas.HomeGridBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Database operations for Live App Box container. */
object LiveAppOperations {

    fun addBox(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        appContext: Context,
        page: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ) {
        scope.launch(Dispatchers.IO) {
            val (maxCols, maxRows) = HomeGridBounds.liveOrDefault(appContext)
            val (col, row) = com.nexus.launcher.ui.canvas.OverlayFractionGrid.originCellOrFallback(
                appContext, xFraction, yFraction, spanX, spanY, "{}", maxCols, maxRows
            )
            dao.insertItem(
                LiveAppPlacement.newItem(page, col, row, spanX, spanY, xFraction, yFraction)
            )
        }
    }

    fun updateConfig(
        scope: CoroutineScope,
        dao: HomeScreenDao,
        boxItem: HomeScreenItem,
        config: LiveAppConfig
    ) {
        scope.launch(Dispatchers.IO) {
            val fresh = dao.getItemById(boxItem.id) ?: boxItem
            dao.updateItem(fresh.copy(folderConfigJson = config.toJson()))
        }
    }
}
