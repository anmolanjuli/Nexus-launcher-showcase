package com.nexus.launcher.ui

import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.model.GridPlacementEngine
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.picker.HomeScreenAppPickerOverlay
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Helper for launching home icon picker and safely inserting apps. */
internal object HomeEditPickerHelper {

    fun launchIconPicker(
        activity: MainActivity,
        pageProvider: () -> Int,
        homeScreenViewModel: HomeScreenViewModel,
        canvasView: LauncherCanvasView
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            activity.applicationContext,
            DaoEntryPoint::class.java
        )
        val dao = entryPoint.homeScreenDao()
        val page = pageProvider()

        HomeScreenAppPickerOverlay.show(activity) { selectedApps ->
            activity.lifecycleScope.launch(Dispatchers.IO) {
                val currentItems = homeScreenViewModel.homeScreenItems.value.toMutableList()
                val visualPositions = canvasView.fractionDerivedPositions.toMutableMap()
                val cols = canvasView.currentGridCols
                val rows = canvasView.currentGridRows

                selectedApps.forEach { app ->
                    val success = GridPlacementEngine.injectAppIcon(
                        app, page, cols, rows, dao, currentItems, visualPositions, activity.applicationContext
                    )
                    if (success) {
                        val insertedItem = dao.getItemById(app.id)
                        if (insertedItem != null) {
                            currentItems.add(insertedItem)
                            visualPositions[insertedItem.id] = Triple(insertedItem.page, insertedItem.column, insertedItem.row)
                        }
                    }
                }
            }
        }
    }
}
