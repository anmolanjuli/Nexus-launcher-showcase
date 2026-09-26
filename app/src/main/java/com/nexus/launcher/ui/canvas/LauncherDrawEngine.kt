package com.nexus.launcher.ui.canvas
import android.graphics.Canvas
import com.nexus.launcher.ui.model.GridItem
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionState
import com.nexus.launcher.util.NexusDiag
class LauncherDrawEngine(
    private val view: LauncherCanvasView
) {
    private val drawerPass = DrawEngineDrawerPass(view)
    fun updateFadeGradient() = drawerPass.updateFadeGradient()
    private val itemRenderer = DrawEngineItemRenderer(view)
    private val layoutHelper = DrawEngineLayout(view)
    private val homeBlurPass = DrawEngineHomeBlurPass(view)
    // Made once and fed through a field: a lambda built in onDraw captured the page offset and
    // was a new object every frame. Home no longer rides the drawer, so its vertical offset is 0.
    private var homeOffsetFraction = 0f
    private val drawHomeContent: (Canvas) -> Unit = { target ->
        PageTransitionDispatcher.draw(view, target, homeOffsetFraction, 255, 0f)
    }
    private var showPageIndicator: Boolean = true
    fun setShowPageIndicator(show: Boolean) { showPageIndicator = show }
    /** Read by DockHomeGridSync: with the dots off, the grid takes their band. */
    val isPageIndicatorShown: Boolean get() = showPageIndicator
    private val indicatorActivePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    private val indicatorInactivePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    private val fastScrollBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val fastScrollTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
    }
    fun setAccentColor(color: Int) {
        drawerPass.setAccentColor(color)
        indicatorActivePaint.color = color
        indicatorInactivePaint.color = (color and 0x00FFFFFF) or (0x55 shl 24)
    }
    fun onDraw(canvas: Canvas) {
        if (view.suppressDraw) return
        // STEP 1: Wallpaper / background
        if (!SelectionModeTransform.isCardTrackActive(view)) {
            view.canvasRenderer.draw(canvas, view.viewWidth, view.viewHeight)
        }
        // Home selection: skip canvas wallpaper so SelectionModeBackdropView shows in card gutters.
        if (!view.isDragging && !view.scroller.isFinished) {
            if (view.scroller.computeScrollOffset()) {
                view.scrollY = view.scroller.currY.toFloat()
                DrawerHaptics.checkScrollBoundaries(view)
                // postInvalidateOnAnimation aligns the next redraw with the Choreographer vsync
                // signal, preventing multiple draw passes being queued ahead of the display refresh.
                view.postInvalidateOnAnimation()
            }
        }
        val labelMargin = 16f * view.resources.displayMetrics.density
        val gridBottom = if (view.isSearchMode) {
            view.viewHeight - view.currentOverlayTotalHeight - labelMargin.toInt()
        } else {
            view.viewHeight
        }
        
        val isHardwareBlurActive = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && 
                                   view.isManagePagesOpen
        if (isHardwareBlurActive) {
            if (view.showDarkOverlay) {
                val overlayPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color
                        .parseColor("#F50D1117")
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f,
                    view.viewWidth.toFloat(),
                    view.viewHeight.toFloat(),
                    overlayPaint)
            }
            return
        }
        // Clip to full screen — no hard clip at topInset that would cause row pop-in.
        canvas.save()
        val paddingTopBottomPx = view.homePaddingTopBottomDp * view.resources.displayMetrics.density
        val clipTop = view.topInset + Math.min(0f, paddingTopBottomPx).toInt()
        canvas.clipRect(0, clipTop, view.viewWidth, view.viewHeight)
        if (NexusDiag.ENABLED) {
            logHomeRow0Clip(clipTop)
        }
        val isSelecting = view.selectionState is SelectionState.Selecting
        val isDrawerOpen = view.drawerTranslationY < view.viewHeight
        // progress: 0f = fully on home screen, 1f = drawer fully open
        val progress = DrawerProgress.of(view)
        // homeAlpha fades home screen out as drawer opens; drawerAlpha fades drawer out as it closes
        val homeAlpha = (((1f - progress) * 255f) * view.pageTransitionAlpha).toInt().coerceIn(0, 255)
        val effectiveHomeAlpha = homeAlpha
        val drawerAlpha = (progress * 255f).toInt().coerceIn(0, 255)
        // Not from here: the views that ride the drawer are repositioned when the translation
        // changes (see LauncherCanvasView.drawerTranslationY), so they are already in place for
        // the frame being drawn rather than being moved halfway through it.
        // Unified canvas offset: home screen and dock ride up with the drawer.
        // When drawerTranslationY == viewHeight (HOME): offset = 0 -> content at natural position.
        // When drawerTranslationY == 0 (DRAWER fully open): offset = -viewHeight -> exits top.
        // STEP 2: Home pages — transition router (PageTransitionDispatcher).
        // Gravity Fold is drawn inside that dispatcher at this same layer.
        homeOffsetFraction = (view.dragScrollOffset / view.viewWidth.toFloat()).coerceIn(-1f, 1f)
        if (!view.isManagePagesOpen && progress < 1f && homeAlpha > 0) {
            homeBlurPass.draw(canvas, progress, homeAlpha, drawHomeContent)
        }

        if (isSelecting && !isDrawerOpen) {
            DrawEngineDragShadow.drawGridHighlight(canvas, view)
            DrawEngineDragShadow.drawHomeDrag(canvas, view)
        }
        canvas.restore() // Restore the clipRect from line 55
        if (SelectionModeTransform.isCardTrackActive(view) && !view.isManagePagesOpen) {
            SelectionModeFrameDraw.draw(view, canvas)
        }
        // STEP 2c: Page indicator dots
        if (showPageIndicator && view.uiState == com.nexus.launcher.ui.model.LauncherState.HOME &&
            (view.totalPages > 1 || view.showFeed)
        ) {
            val isCardTrack = SelectionModeTransform.isCardTrackActive(view)
            val indicatorY = if (isCardTrack) SelectionModeCardTrack.indicatorYPx(view) else null
            val activePage = if (isCardTrack) {
                SelectionModeCardTrack.nearestPageAtScreenPoint(view, view.viewWidth / 2f)
            } else view.currentPage

            DrawEnginePageIndicator.draw(
                canvas, view, effectiveHomeAlpha, indicatorActivePaint, indicatorInactivePaint,
                customIndicatorY = indicatorY,
                activePageOverride = activePage
            )
        }
        drawerPass.draw(canvas, drawerAlpha, gridBottom, itemRenderer)
        // STEP 5: A-Z rail
        // Rail draws letters + active-letter bubble internally
        if ((view.uiState == LauncherState.DRAWER || drawerAlpha > 0) && !view.isSearchMode && view.railRenderer.isVisible) {
            val railTop = if (view.railTopY > 0) view.railTopY else view.drawerGridTop
            val railBottom = if (view.railBottomY > 0) view.railBottomY else view.drawerGridBottom
            // Fades, and does not ride: the translate below is cancelled by the matching
            // subtraction in the rail's own bounds, which pins it to its touch zone on purpose.
            // Pinned and unfading, it simply sat there until the drawer stopped being drawn.
            view.railRenderer.globalAlpha = drawerAlpha
            view.railRenderer.isRtl = view.isRtl
            canvas.save()
            // Keep the rail clear of a camera cutout / side nav bar on ITS side of the screen
            // only (right, or left under RTL) — an inset on the far side must not move it, or it
            // drifts away from its touch zone (LauncherRailTouchHelper).
            canvas.translate(if (view.isRtl) SideInsets.left.toFloat() else 0f, 0f)
            view.railRenderer.drawRail(
                canvas,
                if (view.isRtl) view.viewWidth - SideInsets.left else view.viewWidth - SideInsets.right,
                view.viewHeight,
                railTop.toInt(),
                railBottom.toInt(),
                view.rawDrawerApps,
                view.context
            )
            canvas.restore()
        }
        // STEP 5.5: Fast-Scroll Popup Indicator — this is the big center-screen letter shown
        // while scrubbing the A-Z rail. It's a wholly separate draw call from
        // DrawerRailRenderer's own small scrub bubble (drawn beside the rail); both had the same
        // class of accent-color/hardcoded-white bug, fixed independently in each place.
        val letter = view.fastScrollLetter
        if (isDrawerOpen && letter != null) {
            val density = view.resources.displayMetrics.density
            fastScrollBgPaint.apply {
                color = view.currentThemeTokens.textPrimary
                alpha = 230
            }
            fastScrollTextPaint.apply {
                color = view.currentThemeTokens.bg
                textSize = 80f * density
                typeface = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(
                    view.context,
                    android.graphics.Typeface.BOLD
                )
            }
            val boxSize = 120f * density
            val cx = view.viewWidth / 2f
            val cy = view.viewHeight / 2f
            canvas.drawRoundRect(
                cx - boxSize/2, cy - boxSize/2,
                cx + boxSize/2, cy + boxSize/2,
                30f, 30f, fastScrollBgPaint
            )
            val textOffset = (fastScrollTextPaint.descent() + fastScrollTextPaint.ascent()) / 2
            canvas.drawText(letter, cx, cy - textOffset, fastScrollTextPaint)
        }
        // STEP 6: Search background overlay
        /*if (view.isSearchMode) {
            canvas.drawRect(0f, view.viewHeight - view.currentOverlayTotalHeight.toFloat(), view.viewWidth.toFloat(), view.viewHeight.toFloat(), view.overlayBgPaint)
        }*/
        // STEP 7: Drag shadow — drawer apps or folder preview
        DrawEngineDragShadow.drawDrawerDrag(canvas, view)
        // STEP 7.5 / 8: Home drag highlight + shadow (skipped when drawn inside selection transform)
        if (!SelectionModeTransform.isHomeSelecting(view)) {
            DrawEngineDragShadow.drawGridHighlight(canvas, view)
            DrawEngineDragShadow.drawHomeDrag(canvas, view)
        }
    }
    fun drawItem(canvas: Canvas, item: GridItem, baseAlpha: Int) =
        itemRenderer.drawItem(canvas, item, baseAlpha)
    fun recalculateLayout() = layoutHelper.recalculateLayout()

    private fun logHomeRow0Clip(clipTop: Int) {
        if (!NexusDiag.ENABLED) return
        val cell0 = view.homeGridCells.getOrNull(0) ?: return
        val renderer = view.homeScreenRenderer
        val laid = IconLayoutMetrics.compute(
            cell = cell0,
            gridRows = view.currentGridRows,
            gapHorizontalPx = renderer.gapHorizontalPx,
            gapVerticalPx = renderer.gapVerticalPx,
            spanX = 1,
            spanY = 1,
            showLabels = renderer.showLabels,
            userIconSizeMultiplier = renderer.userIconSizeMultiplier,
            density = view.resources.displayMetrics.density,
            displayWidth = view.resources.displayMetrics.widthPixels,
            displayHeight = view.resources.displayMetrics.heightPixels,
            caller = "clip-diag",
            twoLineLabels = renderer.twoLineLabels
        )
        val iconTopAfterGap = if (renderer.showLabels) {
            (cell0.top + maxOf(renderer.gapVerticalPx, 0f) / 2f).toInt()
        } else {
            (cell0.centerY() - laid.iconRect.height() / 2f).toInt()
        }
        HomeGridOverlapDiag.logHomeClip(
            cellTop = cell0.top,
            iconTopAfterGap = iconTopAfterGap,
            iconTopClamped = laid.iconRect.top,
            clipTop = clipTop,
            topInsetPx = view.topInset
        )
    }
}
