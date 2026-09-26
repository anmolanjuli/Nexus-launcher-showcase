package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.folder.FolderIconShapeDraw
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Reusable Neumorphic (Soft UI) canvas drawing utilities for first-party Nexus widgets,
 * dock tiles, shortcut boxes, and tactile controls.
 * Features true 3D elevation across all themes (Light, Dark, Calm, AMOLED) with zero Paint allocations.
 */
object NexusNeumorphicDraw {

    // Pre-allocated static paint objects to strictly avoid allocations during drawing
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bevelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val debossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tempRect = RectF()
    private val tempStrokeRect = RectF()
    private val debossPath = Path()
    private val roundRectBasePath = Path()
    private val roundRectBevelPath = Path()

    data class SoftPalette(
        val surfaceLight: Int,
        val surfaceDark: Int,
        val shadow: Int,
        val highlight: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val accent: Int,
        val todayText: Int,
        val debossedBg: Int,
        val isLight: Boolean,
        val isAmoled: Boolean = false
    )

    fun resolvePalette(tokens: NexusColorTokens): SoftPalette =
        NexusNeumorphicPaletteResolver.resolvePalette(tokens)

    fun resolvePalette(context: Context, themeMode: String? = null): SoftPalette =
        NexusNeumorphicPaletteResolver.resolvePalette(context, themeMode)

    /**
     * Draws the main raised Neumorphic surface with 3D elevation across all themes.
     */
    fun drawRaisedSurface(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        shapeStyle: Int,
        palette: SoftPalette,
        dp: Float
    ) {
        val basePath = FolderIconShapeDraw.getShapePath(bounds, radius, shapeStyle)
        val strokeWidth = 1f * dp
        tempStrokeRect.set(
            bounds.left + strokeWidth / 2f,
            bounds.top + strokeWidth / 2f,
            bounds.right - strokeWidth / 2f,
            bounds.bottom - strokeWidth / 2f
        )
        val bevelPath = FolderIconShapeDraw.getShapePath(tempStrokeRect, (radius - strokeWidth / 2f).coerceAtLeast(0f), shapeStyle)
        drawRaisedPath(canvas, bounds, basePath, bevelPath, shapeKey(radius, shapeStyle), palette, dp)
    }

    /** What distinguishes one path from another of the same size, for [NeumorphicShadowCache]. */
    private fun shapeKey(radius: Float, shapeStyle: Int): Long =
        (radius.toRawBits().toLong() shl 32) or (shapeStyle.toLong() and 0xFFFFFFFFL)

    /**
     * Draws a tactile raised Neumorphic rounded rectangle with explicit corner radius.
     * Prevents oval distortion on non-square bounds.
     */
    fun drawRaisedRoundRect(
        canvas: Canvas,
        bounds: RectF,
        cornerRadius: Float,
        palette: SoftPalette,
        dp: Float
    ) {
        val r = buildRoundRectPaths(bounds, cornerRadius, dp)
        // Style -1: a plain round rect, never one of the folder shape styles.
        drawRaisedPath(canvas, bounds, roundRectBasePath, roundRectBevelPath, shapeKey(r, -1), palette, dp)
    }

    /**
     * [drawRaisedRoundRect] in two halves, for a surface whose view clips to its own outline (a
     * Living Mosaic tile or cell): its parent draws the shadow, outside that clip, and the view
     * draws the face. [alpha] follows the view's own alpha.
     */
    fun drawRaisedRoundRectShadow(canvas: Canvas, bounds: RectF, cornerRadius: Float, palette: SoftPalette, dp: Float, alpha: Int) {
        val r = buildRoundRectPaths(bounds, cornerRadius, dp)
        NeumorphicShadowCache.draw(canvas, bounds, roundRectBasePath, shapeKey(r, -1), palette, dp, drawShadowsFn, alpha)
    }

    fun drawRaisedRoundRectFace(canvas: Canvas, bounds: RectF, cornerRadius: Float, palette: SoftPalette, dp: Float) {
        buildRoundRectPaths(bounds, cornerRadius, dp)
        drawSurface(canvas, bounds, roundRectBasePath, roundRectBevelPath, palette, dp)
    }

    /** Fills [roundRectBasePath] and its 1dp-inset bevel path; returns the clamped radius. */
    private fun buildRoundRectPaths(bounds: RectF, cornerRadius: Float, dp: Float): Float {
        val r = cornerRadius.coerceAtLeast(0f)
        roundRectBasePath.reset()
        roundRectBasePath.addRoundRect(bounds, r, r, Path.Direction.CW)
        val strokeWidth = 1f * dp
        tempStrokeRect.set(
            bounds.left + strokeWidth / 2f,
            bounds.top + strokeWidth / 2f,
            bounds.right - strokeWidth / 2f,
            bounds.bottom - strokeWidth / 2f
        )
        roundRectBevelPath.reset()
        val innerR = (r - strokeWidth / 2f).coerceAtLeast(0f)
        roundRectBevelPath.addRoundRect(tempStrokeRect, innerR, innerR, Path.Direction.CW)
        return r
    }

    private fun drawRaisedPath(
        canvas: Canvas,
        bounds: RectF,
        basePath: Path,
        bevelPath: Path,
        shapeKey: Long,
        palette: SoftPalette,
        dp: Float
    ) {
        // Steps 1–3b, the blurred shadows, come from a bitmap made once per shape — see
        // NeumorphicShadowCache for why blurring them on every draw lagged page swipes.
        NeumorphicShadowCache.draw(canvas, bounds, basePath, shapeKey, palette, dp, drawShadowsFn)
        drawSurface(canvas, bounds, basePath, bevelPath, palette, dp)
    }

    // Made once: handed to the cache on every draw, where a capturing lambda would allocate.
    private val drawShadowsFn: (Canvas, Path, SoftPalette, Float) -> Unit = ::drawShadowLayers

    /** The blurred shadow layers of a raised surface. Drawn into the cache's bitmap, not per frame. */
    private fun drawShadowLayers(canvas: Canvas, basePath: Path, palette: SoftPalette, dp: Float) {
        // 1. Omnidirectional ambient elevation shadow (Lifts card off wallpaper)
        val ambientAlpha = when {
            palette.isLight -> 18
            palette.isAmoled -> 0
            else -> 65 // Dark & Calm: rich ambient shadow against dark background
        }
        if (ambientAlpha > 0) {
            val ambientBlurRadius = 6f * dp
            shadowPaint.maskFilter = NexusNeumorphicShaders.getOrCreateAmbientBlur(ambientBlurRadius)
            shadowPaint.color = palette.shadow
            shadowPaint.alpha = ambientAlpha
            canvas.save()
            canvas.translate(0f, 1.5f * dp)
            canvas.drawPath(basePath, shadowPaint)
            canvas.restore()
        }

        // 2. Directional key drop shadow (Light, Dark, and Calm modes)
        val shadowAlpha = when {
            palette.isLight -> 32
            palette.isAmoled -> 0
            else -> 125 // Dark & Calm: deep directional drop shadow
        }
        if (shadowAlpha > 0) {
            val keyBlurRadius = if (palette.isLight) 8f * dp else 10f * dp
            shadowPaint.maskFilter = NexusNeumorphicShaders.getOrCreateShadowBlur(keyBlurRadius)
            shadowPaint.color = palette.shadow
            shadowPaint.alpha = shadowAlpha
            val dx = if (palette.isLight) 3.5f * dp else 4f * dp
            val dy = if (palette.isLight) 4f * dp else 5f * dp
            canvas.save()
            canvas.translate(dx, dy)
            canvas.drawPath(basePath, shadowPaint)
            canvas.restore()
        }

        // 3. Top-Left specular light glow (Light mode against off-white background)
        if (palette.isLight) {
            val glowBlurRadius = 7f * dp
            highlightPaint.maskFilter = NexusNeumorphicShaders.getOrCreateHighlightBlur(glowBlurRadius)
            highlightPaint.color = palette.highlight
            highlightPaint.alpha = 220
            val gDx = -3.5f * dp
            val gDy = -3.5f * dp
            canvas.save()
            canvas.translate(gDx, gDy)
            canvas.drawPath(basePath, highlightPaint)
            canvas.restore()
        }

        // 3b. AMOLED ambient elevation glow (Subtle soft ambient lift on pitch black)
        if (palette.isAmoled) {
            val amoledBlurRadius = 5f * dp
            highlightPaint.maskFilter = NexusNeumorphicShaders.getOrCreateHighlightBlur(amoledBlurRadius)
            highlightPaint.color = Color.WHITE
            highlightPaint.alpha = 18 // soft 7% ambient glow
            canvas.save()
            canvas.translate(0f, 1f * dp)
            canvas.drawPath(basePath, highlightPaint)
            canvas.restore()
        }

        // Reset maskFilter on reusable paints
        shadowPaint.maskFilter = null
        highlightPaint.maskFilter = null
    }

    /** The live part of a raised surface — fill and edge, both cheap — drawn every time. */
    private fun drawSurface(
        canvas: Canvas,
        bounds: RectF,
        basePath: Path,
        bevelPath: Path,
        palette: SoftPalette,
        dp: Float
    ) {
        // 4. Surface fill with uniform luminance
        fillPaint.shader = NexusNeumorphicShaders.getOrCreateFillShader(bounds, palette.surfaceLight, palette.surfaceDark)
        canvas.drawPath(basePath, fillPaint)

        // 5. Crisp, subtle perimeter edge definition
        val strokeWidth = 1f * dp
        val topLight = when {
            palette.isLight -> Color.argb(160, 255, 255, 255)
            palette.isAmoled -> Color.argb(65, 255, 255, 255)
            else -> Color.argb(50, 255, 255, 255)
        }
        val botShadow = when {
            palette.isLight -> Color.TRANSPARENT
            palette.isAmoled -> Color.argb(20, 255, 255, 255)
            else -> Color.argb(45, 0, 0, 0)
        }
        bevelPaint.strokeWidth = strokeWidth
        bevelPaint.shader = NexusNeumorphicShaders.getOrCreateBevelShader(bounds, topLight, botShadow)
        canvas.drawPath(bevelPath, bevelPaint)
    }

    /**
     * Draws the "Default" style's flat outer plate — same shape/bounds/radius as
     * [drawRaisedSurface] (so it drops into every call site that already computes those the same
     * way for Neumorphic), but a single solid fill in the SAME color Neumorphism uses, with no
     * shadow, highlight, or bevel. A thin flat edge stroke (not a gradient bevel) gives it enough
     * definition to read as an intentional card rather than a borderless smear.
     */
    fun drawFlatSurface(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        shapeStyle: Int,
        palette: SoftPalette,
        dp: Float
    ) {
        val basePath = FolderIconShapeDraw.getShapePath(bounds, radius, shapeStyle)

        fillPaint.shader = null
        fillPaint.color = palette.surfaceDark
        canvas.drawPath(basePath, fillPaint)

        val strokeWidth = 1f * dp
        tempStrokeRect.set(
            bounds.left + strokeWidth / 2f,
            bounds.top + strokeWidth / 2f,
            bounds.right - strokeWidth / 2f,
            bounds.bottom - strokeWidth / 2f
        )
        val strokePath = FolderIconShapeDraw.getShapePath(tempStrokeRect, (radius - strokeWidth / 2f).coerceAtLeast(0f), shapeStyle)
        bevelPaint.shader = null
        bevelPaint.strokeWidth = strokeWidth
        bevelPaint.color = if (palette.isLight) Color.argb(30, 0, 0, 0) else Color.argb(40, 255, 255, 255)
        canvas.drawPath(strokePath, bevelPaint)
    }

    /**
     * Draws an inset/sunken debossed container or display well.
     */
    fun drawDebossedWell(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        palette: SoftPalette,
        dp: Float
    ) {
        debossPath.reset()
        debossPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)

        // Inner dark base
        debossPaint.shader = null
        debossPaint.color = palette.debossedBg
        canvas.drawPath(debossPath, debossPaint)

        // Top/left inner shadow gradient with cached shader
        val innerShadowStart = if (palette.isLight) Color.argb(55, 90, 105, 125) else Color.argb(90, 0, 0, 0)
        bevelPaint.strokeWidth = 1.8f * dp
        bevelPaint.shader = NexusNeumorphicShaders.getOrCreateDebossShader(bounds, innerShadowStart, dp)
        canvas.drawPath(debossPath, bevelPaint)
    }

    /**
     * Draws a soft high-contrast accent pill/badge (e.g. for the active calendar day).
     */
    fun drawPillBadge(
        canvas: Canvas,
        bounds: RectF,
        radius: Float,
        accentColor: Int,
        dp: Float
    ) {
        // Soft drop shadow beneath the badge
        tempRect.set(bounds.left + 0.5f * dp, bounds.top + 1.5f * dp, bounds.right + 0.5f * dp, bounds.bottom + 1.5f * dp)
        shadowPaint.color = Color.argb(70, 0, 0, 0)
        canvas.drawRoundRect(tempRect, radius, radius, shadowPaint)

        // Main badge fill
        fillPaint.shader = null
        fillPaint.color = accentColor
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)

        // Subtle specular highlight on top half with cached shader
        val highlightStroke = Color.argb(90, 255, 255, 255)
        bevelPaint.strokeWidth = 1f * dp
        bevelPaint.shader = NexusNeumorphicShaders.getOrCreatePillShader(bounds, highlightStroke)
        canvas.drawRoundRect(bounds, radius, radius, bevelPaint)
    }

    /**
     * Draws a tactile Neumorphic circular button consisting of a sunken debossed well
     * with a raised physical center button (matching the media player reference design).
     * In the Default style, one flat circle instead: no socket, no lift.
     */
    fun drawNeumorphicRoundButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        palette: SoftPalette,
        dp: Float
    ) {
        if (FrostedGlassEngine.isDefaultFlatStyleEnabled) {
            tempRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            // Shape 0 is a circle.
            drawFlatSurface(canvas, tempRect, radius, 0, palette, dp)
            return
        }
        // 1. Outer sunken socket / debossed well (strict 1:1 circle aspect ratio)
        tempRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        drawDebossedWell(canvas, tempRect, radius, palette, dp)

        // 2. Inner raised physical button
        val innerR = radius * 0.78f
        tempStrokeRect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        drawRaisedRoundRect(canvas, tempStrokeRect, innerR, palette, dp)
    }
}
