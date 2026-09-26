package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.model.LauncherState
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState

class DrawEngineDrawerPass(private val view: LauncherCanvasView) {
    private val drawerBorderPaint = android.graphics.Paint().apply {
        strokeWidth = 0f // set in onDraw
        style = android.graphics.Paint.Style.STROKE
    }

    private val drawerScrimPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
    }

    private var blurRenderNode: RenderNode? = null
    private val drawerScreenLoc = IntArray(2)
    private var lastBlurW = -1
    private var lastBlurH = -1
    private var lastBlurScreenX = -1
    private var lastBlurScreenY = -1
    private var lastBlurBackdropVersion = -1L
    private var lastBlurStep = -1

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode = RenderNode("DrawerFrostedBlurNode")
        }
    }



    private val categoryFadePaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
    }
    private var fadeTop = 0f
    private var fadeBottom = 0f

    fun updateFadeGradient() {
        val density = view.resources.displayMetrics.density
        fadeTop = view.topInset.toFloat() + view.drawerChrome.topReservePx.toFloat()
        fadeBottom = fadeTop + 12f * density
        val tokens = view.currentThemeTokens
        val scrimBase = drawerScrimBaseColor(tokens)
        categoryFadePaint.shader = android.graphics.LinearGradient(
            0f,
            fadeTop,
            0f,
            fadeBottom,
            intArrayOf(Color.argb(180, Color.red(scrimBase), Color.green(scrimBase), Color.blue(scrimBase)), Color.TRANSPARENT),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
    }

    fun setAccentColor(color: Int) {
        val neutral = view.currentThemeTokens.textSecondary
        drawerBorderPaint.color = (neutral and 0x00FFFFFF) or (0x33 shl 24)
    }

    private val drawerBlurPass = DrawEngineDrawerBlurPass(view)

    fun draw(canvas: Canvas, drawerAlpha: Int, gridBottom: Int, itemRenderer: DrawEngineItemRenderer) {
        val progress = DrawerProgress.of(view)
        if (drawerAlpha > 0) {
            val sheetSave = canvas.save()
            canvas.clipRect(0f, 0f, view.viewWidth.toFloat(), view.viewHeight.toFloat())
            drawFrostedBackdrop(canvas, progress)
            val tokens = view.currentThemeTokens
            val scrimBase = drawerScrimBaseColor(tokens)
            val baseScrimAlpha = (FrostedGlassEngine.frostFillAlpha(1f, 0.70f) * 255f).toInt().coerceIn(0, 255)
            val scrimAlpha = (baseScrimAlpha * (drawerAlpha / 255f)).toInt().coerceIn(0, 255)
            drawerScrimPaint.color = Color.argb(
                scrimAlpha,
                Color.red(scrimBase),
                Color.green(scrimBase),
                Color.blue(scrimBase)
            )
            canvas.drawRect(0f, 0f, view.viewWidth.toFloat(), view.viewHeight.toFloat(), drawerScrimPaint)
            if (view.showSearchBackground) {
                val origAlpha = view.searchBgPaint.alpha
                view.searchBgPaint.alpha = drawerAlpha
                canvas.drawRect(0f, 0f, view.viewWidth.toFloat(), view.viewHeight.toFloat(), view.searchBgPaint)
                view.searchBgPaint.alpha = origAlpha
            }
            canvas.restoreToCount(sheetSave)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lastBlurStep != 0) {
                blurRenderNode?.setRenderEffect(null)
                lastBlurStep = 0
            }
            lastBlurStep = -1
        }

        contentGridBottom = gridBottom
        contentItemRenderer = itemRenderer
        drawerBlurPass.draw(canvas, progress, drawerAlpha, drawContentsFn)
    }

    // Handed to the blur pass as one reference made once. A lambda written at the call site
    // captures gridBottom and the renderer, which is a new object on every frame.
    private var contentGridBottom = 0
    private var contentItemRenderer: DrawEngineItemRenderer? = null
    private val drawContentsFn: (Canvas, Int) -> Unit = ::drawContents

    private fun drawContents(targetCanvas: Canvas, contentAlpha: Int) {
        val itemRenderer = contentItemRenderer ?: return
        targetCanvas.save()
        val clipTop = (view.topInset + view.drawerChrome.topReservePx).toFloat()
        val clipBottom = contentGridBottom.toFloat().coerceAtMost(view.viewHeight.toFloat())
        targetCanvas.clipRect(
            SideInsets.left.toFloat(),
            clipTop,
            (view.viewWidth - SideInsets.right).toFloat(),
            clipBottom
        )
        targetCanvas.translate(0f, -view.scrollY)

        view.textPaint.alpha = contentAlpha
        val listLayout = (view.gridRenderer.layoutMode == "list" ||
            view.gridRenderer.layoutMode == "list_1" ||
            view.gridRenderer.layoutMode == "list_2") && !view.isSearchMode
        if (!(view.isSearchMode && view.isSearchQueryEmpty)) {
            val skipIcons = view.gridRenderer.layoutMode ==
                com.nexus.launcher.data.prefs.DrawerLayoutModes.CATEGORIES && !view.isSearchMode
            if (!skipIcons) {
                val items = view.drawerItems
                for (i in items.indices) { // index loop: no iterator per frame
                    val item = items[i]
                    val top = item.drawRect.top - view.scrollY
                    val bottom = item.drawRect.bottom - view.scrollY
                    if (bottom >= 0f && top <= view.viewHeight.toFloat()) {
                        itemRenderer.drawItem(targetCanvas, item, contentAlpha, listLayout, isDrawerContext = true)
                    }
                }
            }
        }
        if (view.selectionState is SelectionState.Selecting &&
            (view.selectionState as SelectionState.Selecting).source == SelectionSource.DRAWER
        ) {
            val selected = (view.selectionState as SelectionState.Selecting).selectedPackages
            view.selectionRenderer.drawDrawerSelection(targetCanvas, view.drawerItems, selected, contentAlpha)
        }
        targetCanvas.restore()
    }

    private fun drawFrostedBackdrop(canvas: Canvas, progress: Float) {
        val node = blurRenderNode ?: return
        if (!canvas.isHardwareAccelerated || view.viewWidth <= 0 || view.viewHeight <= 0) return

        view.getLocationOnScreen(drawerScreenLoc)
        val curX = drawerScreenLoc[0]
        val curY = drawerScreenLoc[1]
        val currentVersion = HomeScreenFrameCache.getBackdropVersion()

        if (view.viewWidth != lastBlurW || view.viewHeight != lastBlurH ||
            curX != lastBlurScreenX || curY != lastBlurScreenY ||
            currentVersion != lastBlurBackdropVersion || !node.hasDisplayList()
        ) {
            lastBlurW = view.viewWidth
            lastBlurH = view.viewHeight
            lastBlurScreenX = curX
            lastBlurScreenY = curY
            lastBlurBackdropVersion = currentVersion
            node.setPosition(0, 0, view.viewWidth, view.viewHeight)
            val nodeCanvas = node.beginRecording()
            HomeScreenFrameCache.drawWallpaperOnly(nodeCanvas, view.context, curX, curY, view.viewWidth, view.viewHeight)
            node.endRecording()
        }

        node.alpha = progress.coerceIn(0f, 1f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maxBlur = 28f
            val blurStep = ((progress * maxBlur) / 4f).toInt()
            if (blurStep != lastBlurStep) {
                lastBlurStep = blurStep
                val blurRadius = blurStep * 4f
                if (blurRadius > 0.5f) {
                    node.setRenderEffect(
                        com.nexus.launcher.ui.glass.BlurEffectCache.get(blurRadius, Shader.TileMode.CLAMP)
                    )
                } else {
                    node.setRenderEffect(null)
                }
            }
        }

        canvas.drawRenderNode(node)
    }

    /** Same token the home canvas paints (LauncherCanvasView.overlayPaint) so drawer and home
     *  share one background; the themed-icon plate (surface) then separates from it in every
     *  theme, including Light where surface is white. */
    private fun drawerScrimBaseColor(tokens: com.nexus.launcher.theme.NexusColorTokens): Int = tokens.bg
}
