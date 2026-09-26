package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Rect

/**
 * Side placement for widget context menus when neither above nor below the widget has room
 * for the menu — the normal case in phone landscape, where the screen is only ~400dp tall.
 * The menu then opens beside the widget, on whichever side has room, spanning the usable
 * height, kept inside the home grid area (clear of the dock and the camera cutout).
 */
object SideMenuPlacement {

    data class Placement(val left: Int, val top: Int, val height: Int)

    /**
     * Null when the menu should stay above/below: [availableAbove] or [availableBelow] can hold
     * [minComfortableHeight], or there is no room beside the widget either.
     */
    fun placeBeside(
        context: Context,
        widgetRect: Rect,
        menuWidth: Int,
        availableAbove: Int,
        availableBelow: Int,
        minComfortableHeight: Int,
        density: Float
    ): Placement? {
        if (maxOf(availableAbove, availableBelow) >= minComfortableHeight) return null
        val gap = (12f * density).toInt()
        val canvas = com.nexus.launcher.ui.folder.FolderBlurCoordinator.findCanvas(context)
        val screenW = context.resources.displayMetrics.widthPixels
        val screenH = context.resources.displayMetrics.heightPixels
        val areaLeft = canvas?.gridAreaLeft ?: 0
        val areaRight = canvas?.let { it.gridAreaLeft + it.gridAreaWidth } ?: screenW
        val top = canvas?.topInset ?: (24f * density).toInt()
        val bottom = screenH - (canvas?.dockBottomReserve ?: (16f * density).toInt())
        val left = when {
            widgetRect.right + gap + menuWidth <= areaRight -> widgetRect.right + gap
            widgetRect.left - gap - menuWidth >= areaLeft -> widgetRect.left - gap - menuWidth
            else -> return null
        }
        return Placement(left, top, (bottom - top).coerceAtLeast((120f * density).toInt()))
    }
}
