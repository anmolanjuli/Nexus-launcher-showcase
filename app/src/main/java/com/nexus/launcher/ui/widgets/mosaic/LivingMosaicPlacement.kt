package com.nexus.launcher.ui.widgets.mosaic

import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.HomeScreenViewModel
import com.nexus.launcher.ui.widgets.NexusWidgetKinds

/** Places an empty Living Mosaic box on the home screen (no AppWidget allocate). */
object LivingMosaicPlacement {

    const val PACKAGE = "com.nexus.launcher.mosaic"

    fun placeEmpty(
        viewModel: HomeScreenViewModel,
        nexusKind: String?,
        page: Int,
        xFraction: Float,
        yFraction: Float
    ) {
        val (spanX, spanY) = NexusWidgetKinds.mosaicSpan(nexusKind)
        viewModel.addMosaicToHomeScreen(
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
        itemType = HomeItemTypes.MOSAIC,
        appWidgetId = -1,
        spanX = spanX,
        spanY = spanY,
        folderConfigJson = MosaicConfig().toJson()
    )
}
