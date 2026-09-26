package com.nexus.launcher.ui.folder

import android.content.Context

/**
 * Side placement for the folder context menu when it fits neither above nor below the folder —
 * the usual case in phone landscape. Screen coordinates, kept inside the home grid area so the
 * menu clears the dock and the camera cutout.
 */
internal object FolderMenuSidePlacement {

    /**
     * Left edge for a menu beside the folder, or null when the chosen above/below position
     * ([menuTop], [flippedBelow]) already fits on screen or no side has room.
     */
    fun xBeside(
        context: Context,
        iconX: Float,
        iconSize: Float,
        menuWidth: Int,
        menuTop: Float,
        menuHeight: Int,
        flippedBelow: Boolean,
        gap: Float
    ): Int? {
        val canvas = FolderBlurCoordinator.findCanvas(context)
        val screenH = context.resources.displayMetrics.heightPixels
        val bottomLimit = screenH - (canvas?.dockBottomReserve ?: 0)
        val fits = if (flippedBelow) menuTop + menuHeight <= bottomLimit else true
        if (fits) return null
        val areaLeft = (canvas?.gridAreaLeft ?: 0).toFloat()
        val areaRight = canvas?.let { (it.gridAreaLeft + it.gridAreaWidth).toFloat() }
            ?: context.resources.displayMetrics.widthPixels.toFloat()
        val right = iconX + iconSize / 2f + gap
        val left = iconX - iconSize / 2f - gap - menuWidth
        return when {
            right + menuWidth <= areaRight -> right.toInt()
            left >= areaLeft -> left.toInt()
            else -> null
        }
    }

    /** Top for a side-placed menu: centred on the folder, kept between status bar and bottom. */
    fun centeredTop(context: Context, iconY: Float, menuHeight: Int, statusBarTop: Float): Float {
        val canvas = FolderBlurCoordinator.findCanvas(context)
        val screenH = context.resources.displayMetrics.heightPixels
        val bottom = (screenH - (canvas?.dockBottomReserve ?: 0)).toFloat()
        val top = statusBarTop + 8f * context.resources.displayMetrics.density
        return (iconY - menuHeight / 2f).coerceIn(top, (bottom - menuHeight).coerceAtLeast(top))
    }
}
