package com.nexus.launcher.ui.canvas

import android.graphics.Canvas

/** Page-indicator dots — shared by normal home and selection-mode card draw. */
internal object DrawEnginePageIndicator {

    fun draw(
        canvas: Canvas,
        view: LauncherCanvasView,
        homeAlpha: Int,
        activePaint: android.graphics.Paint,
        inactivePaint: android.graphics.Paint,
        customIndicatorY: Float? = null,
        activePageOverride: Int? = null
    ) {
        if (view.totalPages <= 1 && !view.showFeed) return
        val density = view.resources.displayMetrics.density
        val dotSpacing = 8f * density
        val activeDotRadius = 4f * density
        val inactiveDotRadius = 2.5f * density
        val showFeedDot = view.showFeed
        val feedExtraWidth = if (showFeedDot) (activeDotRadius * 2 + dotSpacing) else 0f
        val totalWidth = view.totalPages * activeDotRadius * 2 +
            (view.totalPages - 1) * dotSpacing + feedExtraWidth
        val startX = (view.viewWidth - totalWidth) / 2f + activeDotRadius

        val indicatorY = customIndicatorY ?: (view.dockVisualTopY - (8f * density))
        val activePage = activePageOverride ?: view.currentPage

        var currentX = startX
        if (showFeedDot) {
            val feedPaint = inactivePaint.apply { alpha = (homeAlpha * 0.45f).toInt() }
            canvas.drawCircle(currentX, indicatorY, 2.5f * density, feedPaint)
            currentX += activeDotRadius * 2 + dotSpacing
        }
        for (i in 0 until view.totalPages) {
            val cx = currentX + i * (activeDotRadius * 2 + dotSpacing)
            val radius = if (i == activePage) activeDotRadius else inactiveDotRadius
            val paint = if (i == activePage) activePaint else inactivePaint
            paint.alpha = if (i == activePage) homeAlpha else (homeAlpha * 0.4f).toInt()
            canvas.drawCircle(cx, indicatorY, radius, paint)
        }
    }
}
