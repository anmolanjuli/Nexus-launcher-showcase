package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Closed-folder icon plate — mirrors [com.nexus.launcher.ui.widgets.NexusWidgetRenderer]
 * background structure (glass / neumorphic margin + raised surface).
 */
object FolderIconPlateDraw {

    private const val NEUMORPHIC_MARGIN_DP = 1.5f

    // Pre-allocated static paint objects — zero Paint allocations inside onDraw
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val strokeBoundsRect = RectF()


    fun normalizeBackgroundMode(mode: String?): String = when (mode?.uppercase()) {
        NexusWidgetConfig.BG_GLASS, NexusWidgetConfig.BG_SOLID -> NexusWidgetConfig.BG_GLASS
        NexusWidgetConfig.BG_NEUMORPHIC, "SOFT_UI" -> NexusWidgetConfig.BG_NEUMORPHIC
        else -> NexusWidgetConfig.BG_GLASS
    }

    fun isNeumorphic(config: FolderConfig): Boolean {
        val mode = normalizeBackgroundMode(config.iconBackgroundMode)
        return NexusWidgetConfig.isNeumorphicSurface(mode, config.isExpressive)
    }

    fun isGlass(config: FolderConfig): Boolean {
        val mode = normalizeBackgroundMode(config.iconBackgroundMode)
        return NexusWidgetConfig.isGlassSurface(mode, config.isExpressive)
    }

    fun cardBounds(rect: RectF, density: Float, config: FolderConfig): RectF {
        val margin = if (isNeumorphic(config)) NEUMORPHIC_MARGIN_DP * density else 0f
        return RectF(rect.left + margin, rect.top + margin, rect.right - margin, rect.bottom - margin)
    }

    fun cardRadius(radius: Float, density: Float, config: FolderConfig): Float {
        val margin = if (isNeumorphic(config)) NEUMORPHIC_MARGIN_DP * density else 0f
        return (radius - margin).coerceAtLeast(4f * density)
    }

    fun mapShapeStyle(shapeStyle: Int): Int = when (shapeStyle) {
        FolderShapeStyle.SOFT_CAPSULE -> FolderShapeStyle.SQUIRCLE
        else -> shapeStyle
    }

    /**
     * @return true when plate drawing handled this shape; false lets Ball use sphere rendering.
     */
    fun drawIconPlate(
        context: Context,
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        shapeStyle: Int,
        config: FolderConfig,
        density: Float,
        folderId: Long = -1L,
        previewHostView: android.view.View? = null
    ): Boolean {
        if (shapeStyle == 6) return true
        if (shapeStyle == FolderShapeStyle.BALL) return false

        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        if (op <= 0.001f) return true

        val mode = normalizeBackgroundMode(config.iconBackgroundMode)
        val drawShape = mapShapeStyle(shapeStyle)
        val alpha = (op * 255f).toInt().coerceIn(0, 255)
        val cardBounds = cardBounds(rect, density, config)
        val cardR = cardRadius(radius, density, config)

        if (isNeumorphic(config)) {
            val palette = NexusNeumorphicDraw.resolvePalette(context, config.themeMode)
            if (FrostedGlassEngine.isDefaultFlatStyleEnabled) {
                NexusNeumorphicDraw.drawFlatSurface(canvas, cardBounds, cardR, drawShape, palette, density)
            } else {
                NexusNeumorphicDraw.drawRaisedSurface(canvas, cardBounds, cardR, drawShape, palette, density)
            }
        } else {
            drawFlatPlate(context, canvas, rect, radius, drawShape, config, density, mode, alpha, folderId, previewHostView)
        }

        return true
    }

    /**
     * Per-folder blurred-wallpaper cache — [drawIconPlate] runs for every visible folder icon
     * on every home-screen redraw (including during scroll), so this must not allocate/blur a
     * fresh bitmap per call. Keyed by folder id; only recomputed when that folder's screen
     * position, size, or refraction actually changed. Capped to avoid unbounded growth if many
     * folders are ever created.
     */
    // Bitmap downscale-blur is the pre-API31 fallback only; the primary path in
    // [drawFolderIconWallpaper] is a real RenderNode GPU Gaussian blur (same technique as
    // WidgetGlassLiveBackdropView / AppBoxRenderer / ShortcutBoxRenderer) so the folder icon's
    // frost strength matches widgets' exactly instead of a weaker CPU approximation.
    private class CachedIconBlur(
        var bmp: Bitmap?,
        var node: android.graphics.RenderNode?,
        var x: Int, var y: Int, var w: Int, var h: Int, var refr: Float,
        var backdropVersion: Long
    )
    private val iconBlurCache = LinkedHashMap<Long, CachedIconBlur>()
    private const val ICON_BLUR_CACHE_CAP = 64
    private val iconScreenLoc = IntArray(2)
    /** Scratch for the page-transform map — never allocate a RectF on this draw path. */
    private val transformedRect = RectF()

    /**
     * While set, a plate whose only change is its on-screen *position* keeps the wallpaper slice
     * it already recorded instead of re-sampling and re-blurring it.
     *
     * The cache below is keyed on screen position, which is correct for a plate sitting still in
     * the grid but pathological for one following a finger: it misses every single frame, and
     * each miss re-records the wallpaper and re-runs a 60–90 px blur over the plate's whole
     * area. That area scales with spanX/spanY, which is why a 1x1 folder tracked the finger while
     * a 2x2 or 3x3 visibly lagged behind it.
     *
     * A lifted object should not be sampling the wallpaper directly beneath it as though it were
     * still flush with the surface, so freezing is also the more correct look — the plate keeps
     * the reflection it lifted off with and the shadow carries the elevation.
     */
    private var backdropFrozen = false

    /** Runs [block] with the wallpaper sample frozen. See [backdropFrozen]. */
    inline fun <T> withFrozenBackdrop(block: () -> T): T {
        val previous = isBackdropFrozen()
        setBackdropFrozen(true)
        try {
            return block()
        } finally {
            setBackdropFrozen(previous)
        }
    }

    @PublishedApi internal fun isBackdropFrozen(): Boolean = backdropFrozen

    @PublishedApi internal fun setBackdropFrozen(frozen: Boolean) {
        backdropFrozen = frozen
    }

    private fun drawFolderIconWallpaper(
        context: Context,
        canvas: Canvas,
        shapePath: Path,
        rect: RectF,
        refr: Float,
        opacity: Float,
        folderId: Long,
        previewHostView: android.view.View? = null
    ) {
        val screenX: Int
        val screenY: Int
        val w: Int
        val h: Int
        if (previewHostView != null) {
            // Preview context (e.g. the folder edit sheet's mini icon preview) — this `canvas`
            // is the preview View's own, not the real home canvas, so the page-transform math
            // below is meaningless here. Sample live wallpaper from directly behind wherever
            // the preview itself currently sits, which is what actually reads as a live,
            // representative "here's what glass mode looks like" reflection.
            previewHostView.getLocationOnScreen(iconScreenLoc)
            screenX = iconScreenLoc[0] + rect.left.toInt()
            screenY = iconScreenLoc[1] + rect.top.toInt()
            w = rect.width().toInt().coerceAtLeast(1)
            h = rect.height().toInt().coerceAtLeast(1)
        } else {
            val canvasView = FolderBlurCoordinator.findCanvas(context) ?: return
            canvasView.getLocationOnScreen(iconScreenLoc)
            // Page transitions apply a canvas.translate (see PageTransitionDispatcher.applyStyle)
            // before icons draw, so `rect` alone is page-relative, not the icon's actual current
            // on-screen position — mapping it through the canvas's active matrix accounts for that
            // transform, so the sampled wallpaper slice tracks the icon as it slides during a swipe
            // instead of staying pinned to wherever it sits within its own page.
            transformedRect.set(rect)
            canvas.matrix.mapRect(transformedRect)
            screenX = iconScreenLoc[0] + transformedRect.left.toInt()
            screenY = iconScreenLoc[1] + transformedRect.top.toInt()
            w = transformedRect.width().toInt().coerceAtLeast(1)
            h = transformedRect.height().toInt().coerceAtLeast(1)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
            var entry = iconBlurCache[folderId]
            val radius = FrostedGlassEngine.glassBlurRadius(opacity, refr)
            val currentVersion = HomeScreenFrameCache.getBackdropVersion()
            if (entry?.node == null) {
                val node = android.graphics.RenderNode("FolderIconBlurNode_$folderId").apply {
                    setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                    )
                }
                entry = CachedIconBlur(null, node, -1, -1, -1, -1, -1f, -1L)
                iconBlurCache[folderId] = entry
                if (iconBlurCache.size > ICON_BLUR_CACHE_CAP) {
                    val oldest = iconBlurCache.keys.firstOrNull()
                    if (oldest != null && oldest != folderId) iconBlurCache.remove(oldest)
                }
            }
            val node = entry.node!!
            if (entry.refr != radius) {
                entry.refr = radius // the node path keys on radius: it moves with opacity too
                node.setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                )
            }
            // A position/size change alone does not force a re-record while frozen; a missing
            // display list or a genuinely new wallpaper still does, so the first frame of a drag
            // and any wallpaper change are never left stale.
            val movedOnScreen = entry.x != screenX || entry.y != screenY ||
                entry.w != w || entry.h != h
            if ((movedOnScreen && !backdropFrozen) ||
                entry.backdropVersion != currentVersion || !node.hasDisplayList()
            ) {
                entry.x = screenX; entry.y = screenY; entry.w = w; entry.h = h
                entry.backdropVersion = currentVersion
                node.setPosition(0, 0, w, h)
                val nodeCanvas = node.beginRecording()
                HomeScreenFrameCache.drawWallpaperOnly(nodeCanvas, context, screenX, screenY, w, h)
                node.endRecording()
            }
            canvas.save()
            canvas.clipPath(shapePath)
            canvas.translate(rect.left, rect.top)
            canvas.drawRenderNode(node)
            canvas.restore()
            return
        }

        // Pre-API31 fallback: no RenderNode/RenderEffect available.
        var entry = iconBlurCache[folderId]
        if (entry?.bmp == null || entry.x != screenX || entry.y != screenY || entry.w != w || entry.h != h || entry.refr != refr) {
            val bmp = try {
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            } catch (_: OutOfMemoryError) {
                null
            }
            if (bmp != null) {
                val divisor = (16 - (9 * refr)).toInt().coerceAtLeast(5)
                val ok = FrostedGlassEngine.drawBlurredWallpaper(Canvas(bmp), context, screenX, screenY, w, h, divisor)
                if (ok) {
                    entry?.bmp?.recycle()
                    entry = CachedIconBlur(bmp, null, screenX, screenY, w, h, refr, -1L)
                    iconBlurCache[folderId] = entry
                    if (iconBlurCache.size > ICON_BLUR_CACHE_CAP) {
                        val oldest = iconBlurCache.keys.firstOrNull()
                        if (oldest != null) iconBlurCache.remove(oldest)?.bmp?.recycle()
                    }
                }
            }
        }
        entry?.bmp?.let {
            canvas.save()
            canvas.clipPath(shapePath)
            canvas.drawBitmap(it, rect.left, rect.top, null)
            canvas.restore()
        }
    }


    fun placeholderDotColor(context: Context, config: FolderConfig): Int {
        val palette = NexusNeumorphicDraw.resolvePalette(context, config.themeMode)
        return androidx.core.graphics.ColorUtils.setAlphaComponent(palette.textSecondary, 0x99)
    }

    private fun drawFlatPlate(
        context: Context,
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        drawShape: Int,
        config: FolderConfig,
        density: Float,
        mode: String,
        alpha: Int,
        folderId: Long = -1L,
        previewHostView: android.view.View? = null
    ) {
        val shapePath = FolderIconShapeDraw.getShapePath(rect, radius, drawShape)
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val isGlass = isGlass(config)
        val refr = config.glassRefraction.coerceIn(0f, 1f)

        val effectiveFolderId = if (folderId != -1L) folderId else if (previewHostView != null) -999L else -1L
        if (isGlass && effectiveFolderId != -1L) {
            drawFolderIconWallpaper(context, canvas, shapePath, rect, refr, config.backgroundOpacity, effectiveFolderId, previewHostView)
        }

        // Expressive resolves through FolderIconSurfaceColor, which already reads the chosen
        // gradient (or solid) out of the config. It was a hardcoded dark constant here, so an
        // expressive folder icon ignored the gradient entirely and just went dark - the same
        // gradient the open window picks up correctly.
        val base = if (config.isExpressive) {
            FolderIconSurfaceColor.windowPlateRgb(context, config)
        } else if (isGlass) {
            frostedTokens.surface
        } else {
            tokens.surface
        }

        // Frosted expressive draws the real gradient rather than a flat blend, matching how
        // NexusWidgetRenderer paints an expressive widget.
        val expressivePreset = if (
            config.isExpressive && config.windowBackgroundMode.equals("FROSTED", ignoreCase = true)
        ) {
            DockFrostedGradients.presets[DockFrostedGradients.clampIndex(config.frostedGradientIndex)]
        } else {
            null
        }

        // 1. Surface fill.
        //
        // Icon opacity is a *glass* parameter: it sets how much of the blurred backdrop the
        // frosted plate transmits. In Neumorphism and Default there is no blurred backdrop -
        // `isGlass` is false, so nothing was drawn behind the plate - and thinning the fill just
        // reveals the raw wallpaper. An expressive folder icon therefore looked transparent in
        // those styles at any opacity below full, while the same folder was correct in Frosted
        // Glass. The gradient paints opaque when there is nothing behind it to show through.
        val fillAlpha = when {
            isGlass ->
                (FrostedGlassEngine.frostFillAlpha(config.backgroundOpacity, refr) * 255f).toInt()
            config.isExpressive -> 255
            else -> alpha
        }
        fillPaint.style = Paint.Style.FILL
        if (expressivePreset != null) {
            fillPaint.shader = android.graphics.LinearGradient(
                rect.left, rect.top, rect.right, rect.top,
                Color.argb(
                    fillAlpha,
                    Color.red(expressivePreset.startRgb),
                    Color.green(expressivePreset.startRgb),
                    Color.blue(expressivePreset.startRgb)
                ),
                Color.argb(
                    fillAlpha,
                    Color.red(expressivePreset.endRgb),
                    Color.green(expressivePreset.endRgb),
                    Color.blue(expressivePreset.endRgb)
                ),
                android.graphics.Shader.TileMode.CLAMP
            )
        } else {
            fillPaint.shader = null
            fillPaint.color = Color.argb(fillAlpha, Color.red(base), Color.green(base), Color.blue(base))
        }
        canvas.drawPath(shapePath, fillPaint)
        fillPaint.shader = null

        // 2. Border — flat, matching widgets (no refraction sheen/rim glow; removed per explicit
        // request, see NexusWidgetRenderer's equivalent removal).
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.25f * density
        strokePaint.shader = null
        strokePaint.color = if (config.isExpressive) {
            Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
        } else if (isGlass) {
            frostedTokens.border
        } else {
            Color.argb(alpha, Color.red(tokens.divider), Color.green(tokens.divider), Color.blue(tokens.divider))
        }

        val inset = strokePaint.strokeWidth / 2f
        strokeBoundsRect.set(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        canvas.drawPath(
            FolderIconShapeDraw.getShapePath(strokeBoundsRect, (radius - inset).coerceAtLeast(0f), drawShape),
            strokePaint
        )
    }
}
