package com.nexus.launcher.ui.canvas

/**
 * Last live home-grid net rectangle. Preview must consume this snapshot so
 * cell size cannot drift from a fallback dock estimate. Fallback is used
 * only when home has never published (Settings before first home layout).
 */
data class HomeGridNetInputs(
    val gridWidthPx: Int,
    val canvasHeightPx: Int,
    val statusBarPx: Int,
    val topInsetPx: Int,
    val dockReservePx: Int,
    val dockSource: String
) {
    val availableWidthPx: Float
        get() = gridWidthPx.toFloat().coerceAtLeast(1f)
    val availableHeightPx: Float
        get() = (canvasHeightPx - topInsetPx - dockReservePx).toFloat().coerceAtLeast(1f)
}

object HomeGridNetSnapshot {
    @Volatile
    var last: HomeGridNetInputs? = null
        private set

    fun publish(inputs: HomeGridNetInputs) {
        if (inputs.gridWidthPx <= 0 || inputs.canvasHeightPx <= 0) return
        last = inputs
    }

    fun resolve(
        displayWidth: Int,
        displayHeight: Int,
        statusBarPx: Int,
        density: Float
    ): HomeGridNetInputs {
        last?.let { return it }
        val top = HomeGridAvailableSpace.topInset(statusBarPx)
        val dock = HomeGridAvailableSpace.fallbackDockBottomReserve(displayWidth, density)
        return HomeGridNetInputs(
            gridWidthPx = displayWidth,
            canvasHeightPx = displayHeight,
            statusBarPx = statusBarPx,
            topInsetPx = top,
            dockReservePx = dock,
            dockSource = "fallback_dockIconPlusLabelPlusIndicator"
        )
    }
}
