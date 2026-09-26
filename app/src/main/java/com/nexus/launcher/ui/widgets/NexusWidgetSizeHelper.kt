package com.nexus.launcher.ui.widgets

enum class WidgetSize {
    TINY, SMALL, MEDIUM, LARGE
}

object NexusWidgetSizeHelper {
    /**
     * Resolves the widget size bracket based on standard dp dimensions.
     * Breakpoints map to the typical grid spans and widget complexity logic.
     */
    fun resolve(widthDp: Int, heightDp: Int): WidgetSize {
        return when {
            widthDp < 100 || heightDp < 80 -> WidgetSize.TINY
            widthDp < 200 || heightDp < 150 -> WidgetSize.SMALL
            widthDp < 300 || heightDp < 250 -> WidgetSize.MEDIUM
            else -> WidgetSize.LARGE
        }
    }
}
