package com.nexus.launcher.ui.widgets.appbox

import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.widgets.NexusWidgetKinds

/** Places an empty App Box on the home screen. */
object AppBoxPlacement {

    const val PACKAGE = "com.nexus.launcher.appbox"

    fun placeEmpty(
        viewModel: HomeScreenViewModel,
        nexusKind: String?,
        page: Int,
        xFraction: Float,
        yFraction: Float
    ) {
        val (spanX, spanY) = NexusWidgetKinds.mosaicSpan(nexusKind)
        viewModel.addAppBoxToHomeScreen(
            page = page,
            spanX = spanX,
            spanY = spanY,
            xFraction = xFraction,
            yFraction = yFraction
        )
    }

    fun newItem(
        page: Int,
        column: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        xFraction: Float,
        yFraction: Float
    ): HomeScreenItem = HomeScreenItem(
        packageName = PACKAGE,
        page = page,
        column = column,
        row = row,
        xFraction = xFraction,
        yFraction = yFraction,
        itemType = HomeItemTypes.APP_BOX,
        appWidgetId = -1,
        spanX = spanX,
        spanY = spanY,
        folderConfigJson = AppBoxConfig().toJson()
    )
}
