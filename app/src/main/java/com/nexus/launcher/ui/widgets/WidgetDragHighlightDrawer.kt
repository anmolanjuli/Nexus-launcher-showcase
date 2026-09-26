package com.nexus.launcher.ui.widgets

import androidx.core.graphics.withClip

class WidgetDragHighlightDrawer(private val layout: WidgetOverlayLayout) {
    private var isDragHighlightActive = false
    private var isSuppressed = false
    /** True while a widget drag highlight is being shown; used to guard invalidate() in WidgetOverlayLayout. */
    val isActive: Boolean get() = isDragHighlightActive && !isSuppressed
    private var highlightSpanX = 0
    private var highlightSpanY = 0
    private var highlightLeft = 0f
    private var highlightTop = 0f
    private var lastHighlightCol = -1
    private var lastHighlightRow = -1
    private var lastHighlightPage = 0
    private var pixelSize: Pair<Int, Int>? = null

    private val highlightRect = android.graphics.RectF()
    private val cardClipPath = android.graphics.Path()
    private val cardClipRect = android.graphics.RectF()

    private val tokens = try {
        com.nexus.launcher.theme.ThemeObserver.currentTokens(layout.context)
    } catch (_: Exception) {
        com.nexus.launcher.theme.NexusColorTokens.Dark
    }

    private val highlightBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.textPrimary
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = layout.context.resources.displayMetrics.density * 2f
    }

    fun onThemeChanged() {
        val current = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(layout.context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        highlightBorderPaint.color = current.textPrimary
    }

    fun startDragHighlight(spanX: Int, spanY: Int, size: Pair<Int, Int>? = null) {
        pixelSize = size
        highlightSpanX = spanX
        highlightSpanY = spanY
        isDragHighlightActive = true
        isSuppressed = false
        lastHighlightCol = -1
        lastHighlightRow = -1
        layout.invalidate()
    }

    fun setSuppressed(suppressed: Boolean) {
        if (suppressed) {
            com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragPoint = null
            com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragBounds = null
        }
        if (isSuppressed != suppressed) {
            isSuppressed = suppressed
            layout.invalidate()
        }
    }

    fun updateDragHighlight(hoverPage: Int, pageLocalLeft: Float, pageLocalTop: Float): WidgetDragHighlight.CellSnap? {
        if (!isDragHighlightActive || layout.cachedColumns == 0 || layout.cachedRows == 0) return null
        val canvas = layout.canvasView ?: return null
        val snap = WidgetDragHighlight.snapTopLeft(
            canvas = canvas,
            gridAreaHeight = layout.gridAreaHeight,
            columns = layout.cachedColumns,
            rows = layout.cachedRows,
            spanX = highlightSpanX,
            spanY = highlightSpanY,
            hoverPage = hoverPage,
            pageLocalLeft = pageLocalLeft,
            pageLocalTop = pageLocalTop,
            pageW = layout.singlePageWidth,
            pixelSize = pixelSize
        ) ?: return null
        if (!isSuppressed && WidgetDragHighlight.tickIfCellChanged(
                layout, lastHighlightCol, lastHighlightRow, snap.col, snap.row
            )
        ) {
            lastHighlightCol = snap.col
            lastHighlightRow = snap.row
        }
        highlightLeft = snap.drawLeft
        highlightTop = snap.drawTop
        lastHighlightPage = snap.hoverPage
        layout.invalidate()
        reportDragPoint(canvas, pageLocalLeft, pageLocalTop)
        return snap
    }

    fun updateDragHighlight(x: Float, y: Float): WidgetDragHighlight.CellSnap? =
        updateDragHighlight(layout.canvasView?.currentPage ?: 0, x, y)

    /** Feeds the canvas dot field the dragged widget's center and bounds (see DrawEngineDragShadow). */
    private fun reportDragPoint(canvas: com.nexus.launcher.ui.canvas.LauncherCanvasView, left: Float, top: Float) {
        val cell = canvas.homeGridCells.firstOrNull()
        if (isSuppressed || cell == null) {
            com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragPoint = null
            com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragBounds = null
        } else {
            val w = pixelSize?.first?.toFloat() ?: (highlightSpanX * cell.width())
            val h = pixelSize?.second?.toFloat() ?: (highlightSpanY * cell.height())
            com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragPoint = android.graphics.PointF(
                left + w / 2f,
                top + h / 2f
            )
            com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragBounds = android.graphics.RectF(
                left, top, left + w, top + h
            )
        }
        canvas.invalidate()
    }

    fun clearDragHighlight() {
        com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragPoint = null
        com.nexus.launcher.ui.canvas.DrawEngineDragShadow.widgetDragBounds = null
        layout.canvasView?.invalidate()
        isDragHighlightActive = false
        isSuppressed = false
        lastHighlightCol = -1
        lastHighlightRow = -1
        lastHighlightPage = 0
        layout.invalidate()
    }

    fun onDraw(canvas: android.graphics.Canvas) {
        if (isDragHighlightActive && !isSuppressed && layout.cachedColumns > 0 && layout.cachedRows > 0) {
            val cv = layout.canvasView
            val density = layout.context.resources.displayMetrics.density
            val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
                availableWidthPx = (cv?.gridAreaWidth?.toFloat() ?: layout.viewWidth),
                availableHeightPx = layout.gridAreaHeight,
                columns = layout.cachedColumns,
                rows = layout.cachedRows,
                paddingLeftRightDp = cv?.homePaddingLeftRightDp ?: 0f,
                paddingTopBottomDp = cv?.homePaddingTopBottomDp ?: 0f,
                gapHorizontalDp = cv?.homeGapHorizontalDp ?: 0f,
                gapVerticalDp = cv?.homeGapVerticalDp ?: 0f,
                density = density
            )
            val pixelWidth = pixelSize?.first?.toFloat() ?: (highlightSpanX * metrics.cellWidthPx)
            val pixelHeight = pixelSize?.second?.toFloat() ?: (highlightSpanY * metrics.cellHeightPx)
            val padding = 4f * density
            highlightRect.set(
                highlightLeft + padding,
                highlightTop + padding,
                highlightLeft + pixelWidth - padding,
                highlightTop + pixelHeight - padding
            )
            val cornerRadius = 16f * density

            if (cv != null && com.nexus.launcher.ui.canvas.SelectionModeTransform.isCardTrackActive(cv)) {
                val cardCorner = com.nexus.launcher.ui.canvas.SelectionModeTransform.CORNER_RADIUS_DP * density
                val pageW = layout.singlePageWidth
                val cardLeft = WidgetOverlayCardOffsetHelper.pageLeft(cv, lastHighlightPage, pageW)
                cardClipPath.reset()
                cardClipRect.set(cardLeft, 0f, cardLeft + pageW, layout.height.toFloat())
                cardClipPath.addRoundRect(cardClipRect, cardCorner, cardCorner, android.graphics.Path.Direction.CW)
                canvas.withClip(cardClipPath) {
                    drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightBorderPaint)
                }
            } else {
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightBorderPaint)
            }
        }
    }
}
