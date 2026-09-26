package com.nexus.launcher.ui.widgets.liveapp

import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.widgets.NexusWidgetKinds

/** Places a Live App Box on the home screen. */
object LiveAppPlacement {

    const val PACKAGE = "com.nexus.launcher.liveapp"

    fun placeEmpty(
        viewModel: HomeScreenViewModel,
        nexusKind: String?,
        page: Int,
        xFraction: Float,
        yFraction: Float
    ) {
        val (spanX, spanY) = NexusWidgetKinds.mosaicSpan(nexusKind)
        viewModel.addLiveAppBoxToHomeScreen(
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
        itemType = HomeItemTypes.LIVE_APP_BOX,
        appWidgetId = -1,
        spanX = spanX,
        spanY = spanY,
        folderConfigJson = LiveAppConfig().toJson()
    )
}
