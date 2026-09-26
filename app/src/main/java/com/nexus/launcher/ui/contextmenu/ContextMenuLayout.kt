package com.nexus.launcher.ui.contextmenu

internal fun safeCoerceIn(value: Float, minBound: Float, maxBound: Float): Float {
    val safeMin = minOf(minBound, maxBound)
    val safeMax = maxOf(minBound, maxBound)
    return value.coerceIn(safeMin, safeMax)
}

internal fun applyMenuLayout(
    menu: ContextMenuView,
    iconCenterX: Float,
    finalX: Float,
    iconCenterY: Float,
    isAbove: Boolean,
    containerHeight: Int,
    cornerRadiusPx: Float,
    retry: Boolean
) {
    val actualWidth = menu.width
    val actualHeight = menu.height
    if (actualWidth == 0 || actualHeight == 0) {
        if (!retry) {
            menu.post {
                applyMenuLayout(
                    menu, iconCenterX, finalX, iconCenterY, isAbove,
                    containerHeight, cornerRadiusPx, retry = true
                )
            }
        }
        return
    }

    menu.x = finalX
    val finalY = if (isAbove) iconCenterY - actualHeight else iconCenterY
    val minBoundY = 0f
    val maxBoundY = (containerHeight - actualHeight).toFloat()
    menu.y = safeCoerceIn(finalY, minBoundY, maxBoundY)

    val recalcNotchX = iconCenterX - menu.x
    menu.setNotchPosition(recalcNotchX)
}

/** Whether the menu fits above or below the item's centre ([iconCenterY]) inside the screen. */
internal fun fitsVertically(
    canvas: com.nexus.launcher.ui.canvas.LauncherCanvasView?,
    iconCenterY: Float,
    menuHeight: Int,
    containerHeight: Int,
    density: Float
): Boolean {
    val top = (canvas?.topInset ?: 0).toFloat()
    val bottom = containerHeight - 32f * density
    return iconCenterY + menuHeight <= bottom || iconCenterY - menuHeight >= top
}

/**
 * X for a menu opened beside the item: right of it when there is room inside the home grid
 * area (clear of the dock and camera cutout), else left of it. Null when neither side fits.
 */
internal fun sideAnchorX(
    canvas: com.nexus.launcher.ui.canvas.LauncherCanvasView?,
    iconCenterX: Float,
    iconHalfWidth: Float,
    menuWidth: Int,
    containerWidth: Int,
    density: Float
): Float? {
    val gap = 8f * density
    val areaLeft = (canvas?.gridAreaLeft ?: 0).toFloat()
    val areaRight = canvas?.let { (it.gridAreaLeft + it.gridAreaWidth).toFloat() } ?: containerWidth.toFloat()
    val right = iconCenterX + iconHalfWidth + gap
    val left = iconCenterX - iconHalfWidth - gap - menuWidth
    return when {
        right + menuWidth <= areaRight -> right
        left >= areaLeft -> left
        else -> null
    }
}

/** Vertically centres a side-anchored menu on the item, kept between status bar and bottom. */
internal fun centerBeside(
    menu: ContextMenuView,
    iconCenterY: Float,
    menuHeight: Int,
    canvas: com.nexus.launcher.ui.canvas.LauncherCanvasView?,
    containerHeight: Int
) {
    if (menuHeight <= 0) return
    val top = (canvas?.topInset ?: 0).toFloat()
    val bottom = (containerHeight - (canvas?.dockBottomReserve ?: 0)).toFloat()
    menu.y = safeCoerceIn(iconCenterY - menuHeight / 2f, top, (bottom - menuHeight).coerceAtLeast(top))
}
