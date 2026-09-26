package com.nexus.launcher.ui.widgets

/**
 * Single source of truth for widget overlay X coordinates.
 *
 * LayoutParams.leftMargin is **absolute** (page * pageWidth + page-local).
 * Chrome on mainContainer and xFraction math need **screen / page-local** X
 * (absolute − page * pageWidth). On page 0 these are identical.
 */
object WidgetCoordinateSpace {

    /**
     * Keeps a widget inside the workspace: below whatever the top reserves (the status bar, the
     * camera band, or the immersive status row) and above the dock.
     *
     * The hosted-widget binder and the Living Mosaic have always done this; the app box, the
     * shortcut box and the live app box did not, which is how they came to be drawn across the
     * immersive status row in landscape, where the grid is short and a y fraction lands high.
     */
    /**
     * The top of the workspace: the larger of the canvas inset and the immersive reserve.
     *
     * A negative vertical grid padding is allowed to pull items up — that is what it is for —
     * but not into the band kept clear for the status row. With a -24dp padding and a 73px
     * reserve the old rule gave 7px, and that is how widgets came to be drawn across the row.
     */
    fun minTopOf(canvasView: com.nexus.launcher.ui.canvas.LauncherCanvasView?): Int {
        val reserved = com.nexus.launcher.ui.immersive.ImmersiveStatus.reservedTopPx
        return canvasView?.let {
            val padding = it.homePaddingTopBottomDp * it.resources.displayMetrics.density
            val padded = maxOf(it.topInset, reserved) + minOf(0f, padding).toInt()
            maxOf(padded, reserved)
        } ?: reserved
    }

    fun clampTop(
        topMargin: Int,
        height: Int,
        canvasView: com.nexus.launcher.ui.canvas.LauncherCanvasView?,
        overlayHeight: Int,
        dockReserve: Int,
    ): Int {
        // Mid-rotation the canvas can still be carrying the old orientation's inset, or none at
        // all; the immersive row's own reserve is known either way.
        val minTop = minTopOf(canvasView)
        val maxTop = canvasView?.let { it.viewHeight - it.dockBottomReserve }
            ?: (overlayHeight - dockReserve)
        return topMargin.coerceIn(minTop, (maxTop - height).coerceAtLeast(minTop))
    }

    fun pageOriginX(page: Int, pageWidth: Float): Float =
        page.coerceAtLeast(0) * pageWidth

    /** Absolute content X → screen / page-local X. */
    fun absoluteToScreenX(absoluteX: Float, page: Int, pageWidth: Float): Float =
        absoluteX - pageOriginX(page, pageWidth)

    /** Screen / page-local X → absolute content X (LayoutParams). */
    fun screenToAbsoluteX(screenX: Float, page: Int, pageWidth: Float): Float =
        screenX + pageOriginX(page, pageWidth)

    fun pageWidthOf(overlay: WidgetOverlayLayout): Float {
        val fromCanvas = overlay.canvasView?.width?.toFloat()?.takeIf { it > 0f }
        return fromCanvas ?: overlay.viewWidth
    }

    fun pageOf(overlay: WidgetOverlayLayout, appWidgetId: Int, fallback: Int = 0): Int =
        overlay.liveItems[appWidgetId]?.page ?: fallback

    /** Center X in page-local space → xFraction in 0..1 for that page. */
    fun xFractionFromAbsoluteCenter(
        absoluteCenterX: Float,
        page: Int,
        pageWidth: Float
    ): Float {
        if (pageWidth <= 0f) return 0.5f
        return xFractionFromCenterX(absoluteToScreenX(absoluteCenterX, page, pageWidth), pageWidth)
    }

    /*
     * xFraction is measured across the home GRID AREA, not the whole page. They are the same in
     * portrait; in phone landscape the dock takes a strip on one side (which side depends on the
     * rotation), and measuring across the grid area keeps a stored fraction on the same cells
     * either way. Kept current by the canvas after every layout.
     */
    @Volatile private var frameLeftPx = 0f
    @Volatile private var frameWidthPx = 0f

    fun updateGridFrame(left: Float, width: Float) {
        frameLeftPx = left
        frameWidthPx = width
    }

    private fun frameWidth(pageWidth: Float): Float =
        if (frameWidthPx > 0f && frameWidthPx <= pageWidth + 1f) frameWidthPx else pageWidth

    private fun frameLeft(pageWidth: Float): Float =
        if (frameWidthPx > 0f && frameWidthPx <= pageWidth + 1f) frameLeftPx else 0f

    /** Page-local center X for [xFraction]. */
    fun centerXFromFraction(xFraction: Float, pageWidth: Float): Float =
        frameLeft(pageWidth) + xFraction * frameWidth(pageWidth)

    /** [xFraction] for a page-local center X — the inverse of [centerXFromFraction]. */
    fun xFractionFromCenterX(centerX: Float, pageWidth: Float): Float {
        val w = frameWidth(pageWidth)
        if (w <= 0f) return 0.5f
        return (centerX - frameLeft(pageWidth)) / w
    }
}
