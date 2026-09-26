package com.nexus.launcher.ui.canvas

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState

/**
 * STEP 2 page-transition router. Camera/Matrix allocated once for cube/flip.
 * Gravity Fold draws through [PageFoldRenderer] via [PageFoldGestureHandler.draw]
 * in this same grid/canvas layer — never from a later onDraw step.
 */
object PageTransitionDispatcher {
    private val transitionCamera = Camera()
    private val transitionMatrix = Matrix()
    private var normalizedTransitionStyle = "slide"

    fun draw(
        view: LauncherCanvasView,
        targetCanvas: Canvas,
        offsetFraction: Float,
        drawHomeAlpha: Int,
        homeOffset: Float
    ) {
        val activeTransitionStyle = resolvedStyle(view)
        val targetBounce = if (view.hoveredMergeTarget != null) 1f else 0f
        if (com.nexus.launcher.ui.dock.DockSpringPhysics.stepCustom(
                view.mergeBounceSpring, targetBounce, 0.35f, 0.60f
            )
        ) {
            view.invalidate()
        }
        val bounceScale = 1.0f + 0.15f * view.mergeBounceSpring.position

        if (activeTransitionStyle == PageFoldGestureHandler.STYLE_KEY &&
            view.pageFoldHandler.isActivelyDrawing
        ) {
            val saveCount = targetCanvas.save()
            targetCanvas.translate(0f, homeOffset)
            view.pageFoldHandler.draw(targetCanvas, drawHomeAlpha, bounceScale)
            targetCanvas.restoreToCount(saveCount)
            return
        }

        val styleForPages = if (activeTransitionStyle == PageFoldGestureHandler.STYLE_KEY) {
            "slide"
        } else activeTransitionStyle

        if (SelectionModePageCards.isActive(view)) {
            val dragPx = view.dragScrollOffset
            for (pageIndex in SelectionModeCardTrack.pagesToRender(view, dragPx)) {
                SelectionModePageCards.drawPageContent(
                    view, targetCanvas, pageIndex, dragPx,
                    drawHomeAlpha, homeOffset, bounceScale
                )
            }
            return
        }

        drawSinglePage(
            view, targetCanvas, view.currentPage, offsetFraction,
            styleForPages, drawHomeAlpha, homeOffset, bounceScale
        )
        if (offsetFraction != 0f) {
            val adjacentPage = if (offsetFraction > 0f) view.currentPage - 1 else view.currentPage + 1
            if (adjacentPage in 0 until view.totalPages) {
                drawSinglePage(
                    view, targetCanvas, adjacentPage, offsetFraction,
                    styleForPages, drawHomeAlpha, homeOffset, bounceScale
                )
            }
        }
    }

    private fun drawSinglePage(
        view: LauncherCanvasView,
        targetCanvas: Canvas,
        pageIndex: Int,
        offsetFraction: Float,
        styleForPages: String,
        drawHomeAlpha: Int,
        homeOffset: Float,
        bounceScale: Float
    ) {
        if (pageIndex < 0 || pageIndex >= view.totalPages) return
        val pageOffset = (pageIndex - view.currentPage) + offsetFraction
        val alpha = (255 * (1f - Math.abs(pageOffset))).toInt().coerceIn(0, 255)
        val saveCount = targetCanvas.save()
        targetCanvas.translate(0f, homeOffset)
        applyStyle(styleForPages, targetCanvas, view, pageOffset, pageIndex, alpha)
        DrawEngineHomeIcons.drawPage(
            view, targetCanvas, pageIndex, drawHomeAlpha, bounceScale
        )
        drawSelectionOverlays(view, targetCanvas, pageIndex, drawHomeAlpha)
        targetCanvas.restoreToCount(saveCount)
    }

    private fun resolvedStyle(view: LauncherCanvasView): String {
        val raw = if (normalizedTransitionStyle != view.transitionStyle.lowercase()) {
            view.transitionStyle.lowercase().also { normalizedTransitionStyle = it }
        } else normalizedTransitionStyle
        if (raw == PageFoldGestureHandler.STYLE_KEY &&
            PageFoldGestureHandler.isReduceMotion(view.context)
        ) {
            return "fade"
        }
        return raw
    }

    private fun applyStyle(
        style: String,
        targetCanvas: Canvas,
        view: LauncherCanvasView,
        pageOffset: Float,
        pageIndex: Int,
        alpha: Int
    ) {
        when (style) {
            "cube" -> {
                transitionCamera.save()
                transitionCamera.translate(0f, 0f, Math.abs(pageOffset) * view.viewWidth * 0.5f)
                transitionCamera.rotateY(pageOffset * 90f)
                transitionCamera.getMatrix(transitionMatrix)
                transitionCamera.restore()
                val pivotX = if (pageOffset < 0f) view.viewWidth.toFloat() else 0f
                transitionMatrix.preTranslate(-pivotX, -view.viewHeight / 2f)
                transitionMatrix.postTranslate(pivotX, view.viewHeight / 2f)
                targetCanvas.translate(pageOffset * view.viewWidth, 0f)
                targetCanvas.concat(transitionMatrix)
            }
            "fade" -> {
                targetCanvas.translate(pageOffset * view.viewWidth, 0f)
                targetCanvas.saveLayerAlpha(
                    0f, 0f, view.viewWidth.toFloat(), view.viewHeight.toFloat(), alpha
                )
            }
            "zoom" -> {
                val scale = 1f - (Math.abs(pageOffset) * 0.15f)
                targetCanvas.scale(scale, scale, view.viewWidth / 2f, view.viewHeight / 2f)
                targetCanvas.translate(pageOffset * view.viewWidth, 0f)
                targetCanvas.saveLayerAlpha(
                    0f, 0f, view.viewWidth.toFloat(), view.viewHeight.toFloat(), alpha
                )
            }
            "flip" -> {
                if (Math.abs(pageOffset) < 0.5f) {
                    transitionCamera.save()
                    transitionCamera.rotateY(pageOffset * -180f)
                    transitionCamera.getMatrix(transitionMatrix)
                    transitionCamera.restore()
                    transitionMatrix.preTranslate(-view.viewWidth / 2f, -view.viewHeight / 2f)
                    transitionMatrix.postTranslate(view.viewWidth / 2f, view.viewHeight / 2f)
                    targetCanvas.concat(transitionMatrix)
                } else {
                    targetCanvas.translate(view.viewWidth.toFloat() * 2, 0f)
                }
            }
            "none", "slide" -> {
                targetCanvas.translate(pageOffset * view.viewWidth, 0f)
            }
            else -> {
                targetCanvas.translate(pageOffset * view.viewWidth, 0f)
            }
        }
    }

    private fun drawSelectionOverlays(
        view: LauncherCanvasView,
        targetCanvas: Canvas,
        pageIndex: Int,
        drawHomeAlpha: Int
    ) {
        drawSelectionOverlaysForPage(view, targetCanvas, pageIndex, drawHomeAlpha)
    }

    internal fun drawSelectionOverlaysForPage(
        view: LauncherCanvasView,
        targetCanvas: Canvas,
        pageIndex: Int,
        drawHomeAlpha: Int
    ) {
        if (view.selectionState !is SelectionState.Selecting) return
        if ((view.selectionState as SelectionState.Selecting).source != SelectionSource.HOME_SCREEN) return
        val selectedIds = (view.selectionState as SelectionState.Selecting).selectedIds
        val targets = view.homeScreenItems
            .filter { it.itemType == 0 || it.itemType == 1 || it.itemType == 2 }
            .mapNotNull { item ->
                val pos = view.fractionDerivedPositions[item.id]
                    ?: Triple(item.page, item.column, item.row)
                val idx = pos.third * view.currentGridCols + pos.second
                if (pos.first != pageIndex || idx !in view.homeGridCells.indices) return@mapNotNull null
                HomeSelectionTarget(
                    rect = view.homeScreenRenderer.getIconRect(item, view.homeGridCells[idx]),
                    isFolder = item.itemType == 1,
                    isSelected = item.id in selectedIds
                )
            }
        view.selectionRenderer.drawHomeSelection(targetCanvas, targets, drawHomeAlpha)
    }
}
