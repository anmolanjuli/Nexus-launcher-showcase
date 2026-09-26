package com.nexus.launcher.ui.folder

import android.content.Context
import com.nexus.launcher.data.FolderConfig

object FolderEditShapeBinder {

    fun bind(
        context: Context,
        bodyViews: FolderEditBodyViews,
        getConfig: () -> FolderConfig,
        previewBinderProvider: () -> FolderEditSheetPreviewBinder?,
        onConfigChanged: (FolderConfig) -> Unit
    ) {
        refreshShapeUi(context, bodyViews, getConfig, previewBinderProvider)

        bodyViews.shapeThumbs.onValueChanged = { value: String ->
            val shape = value.toIntOrNull() ?: FolderShapeStyle.SQUIRCLE
            val nextConfig = getConfig().copy(shapeStyle = shape)
            onConfigChanged(nextConfig)
            previewBinderProvider()?.updateConfig(nextConfig)
        }
    }

    fun refreshShapeUi(
        context: Context,
        bodyViews: FolderEditBodyViews,
        getConfig: () -> FolderConfig,
        previewBinderProvider: () -> FolderEditSheetPreviewBinder?
    ) {
        FolderEditShapeHelper.bind(
            context,
            getConfig().shapeStyle,
            bodyViews.shapeThumbs
        )
        previewBinderProvider()?.updateConfig(getConfig())
    }
}
