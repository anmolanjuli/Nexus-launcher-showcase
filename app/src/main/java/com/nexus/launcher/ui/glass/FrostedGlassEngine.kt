package com.nexus.launcher.ui.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.folder.HomeScreenFrameCache

import androidx.core.graphics.ColorUtils

/**
 * Shared rendering primitive for frosted glass (acrylic / blurred translucency).
 * Hardware-accelerated on API 31+ via [RenderEffect].
 */
object FrostedGlassEngine {

    const val DEFAULT_BLUR_RADIUS_PX = 60f
    const val DEFAULT_TINT_ALPHA = 0.32f
    const val CANONICAL_GLASS_BORDER_ARGB = 0x21FFFFFF

    /**
     * Global "Frosted Glass" kill switch — Nexus Settings > Appearance's toggle
     * (`NexusSettingsData.frostedGlassEnabled`). Kept as a plain mutable field, mirroring
     * `DockBackgroundRenderer.frostedGlassEnabled`, and updated by the same settings-flow
     * collector that already drives the Dock's copy (see `DockLayoutHelper`) — this is what
     * every widget/box/folder `isGlass` check now also ANDs against, since previously this
     * setting only ever reached the Dock: widgets and folders had no code path reading it at
     * all, so turning it off had no visible effect anywhere except the dock's TRANSPARENT mode.
     * Defaults to `true` so nothing changes before the first settings read arrives.
     */
    @Volatile
    var isGlobalFrostedGlassEnabled: Boolean = true

    // The three values NexusSettingsData.uiStyleMode / the Settings > Appearance "UI Style"
    // picker use — kept as plain string constants (not a Kotlin enum) since the value round-trips
    // through DataStore as a string and NexusSegmentedRow's option keys are strings too.
    const val UI_STYLE_NEUMORPHISM = "NEUMORPHISM"
    const val UI_STYLE_FROSTED_GLASS = "FROSTED_GLASS"
    const val UI_STYLE_DEFAULT = "DEFAULT"

    /**
     * True when the UI Style picker is set to "Default" — a third style, structurally identical
     * to Neumorphism (same outer-plate shape/margins/corner rounding) but flat: a single solid
     * fill in the same color Neumorphism would use, with no raised shadow/highlight/bevel effect.
     * Every renderer's `isNeumorphic` branch (widgets, boxes, folders, dock) checks this to choose
     * [com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawFlatSurface] instead of
     * `drawRaisedSurface` when true. Mutually exclusive with [isGlobalFrostedGlassEnabled] in
     * practice (the picker only ever has one of the three selected), but this is intentionally a
     * separate flag rather than folded into that one, so every existing `isGlass`-gated call site
     * is completely unaffected by this addition.
     */
    @Volatile
    var isDefaultFlatStyleEnabled: Boolean = false

    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = CANONICAL_GLASS_BORDER_ARGB
    }

    /**
     * Applies hardware GPU blur to [view] on API 31+.
     * Safe no-op on older Android versions.
     */
    fun applyTo(view: View, blurRadiusPx: Float = DEFAULT_BLUR_RADIUS_PX) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val clampedRadius = blurRadiusPx.coerceAtLeast(1f)
            view.setRenderEffect(
                RenderEffect.createBlurEffect(
                    clampedRadius,
                    clampedRadius,
                    Shader.TileMode.CLAMP
                )
            )
        }
    }

    /**
     * Clears hardware GPU blur from [view].
     */
    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
    }

    /**
     * Shared "frosted dialog" window chrome — edge-to-edge transparent bars plus a native
     * blur-behind pass on the dialog's OWN window (stacking as a second blur pass on top of
     * whatever workspace blur, e.g. [com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur],
     * is already active behind it).
     *
     * Deliberately does NOT add [android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND]: an
     * alpha-dim on the whole window compounds multiplicatively with the sheet's own translucent
     * card fill (typically ~70% opaque via [frostFillAlpha]) sitting on top of it — two ~70%
     * layers stacked leaves under 10% wallpaper transmittance, which reads as flat black rather
     * than frosted, not "more frosted." One translucent fill layer over a blurred backdrop is
     * the same recipe every other frosted surface in the app uses (widgets, folders, context
     * menus); a dialog-hosted sheet should stay consistent with that rather than adding a second
     * darkening layer just because it happens to have a window.
     */
    fun applyDialogWindowChrome(window: android.view.Window, blurRadiusDp: Float = 25f) {
        val density = window.context.resources.displayMetrics.density
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        // The master toggle (Nexus Settings > Appearance) is meant to be a true "no glass
        // anywhere" switch — previously this unconditionally blurred behind every edit sheet
        // regardless of the toggle, so turning it off still left sheets blurred while every
        // other surface correctly went flat.
        FloatingSurfaces.applyBlurBehind(window, blurRadiusDp)
    }

    private var cachedTintShader: LinearGradient? = null
    private var cachedTintKey = ""

    data class FrostedTokens(
        val surface: Int,
        val surfaceRaised: Int,
        val border: Int
    )

    fun resolveFrostedTokens(tokens: NexusColorTokens?): FrostedTokens {
        if (tokens == null) {
            return FrostedTokens(
                surface = Color.parseColor("#24262C"),
                surfaceRaised = Color.parseColor("#363840"),
                border = CANONICAL_GLASS_BORDER_ARGB
            )
        }
        val isLight = tokens === NexusColorTokens.Light ||
            (Color.red(tokens.surface) > 200 && Color.green(tokens.surface) > 200)
        if (isLight) {
            return FrostedTokens(
                surface = Color.parseColor("#FFFFFF"),
                surfaceRaised = tokens.surfaceRaised,
                border = Color.argb(60, 100, 110, 120)
            )
        }
        val isCalm = tokens.bgTop != null
        if (isCalm) {
            val base = tokens.surfaceRaised or 0xFF000000.toInt()
            val raised = ColorUtils.blendARGB(base, Color.WHITE, 0.16f)
            return FrostedTokens(
                surface = base,
                surfaceRaised = raised,
                border = tokens.divider
            )
        }
        val isAmoled = (tokens.bg and 0x00FFFFFF) == 0 && tokens.bgTop == null
        if (isAmoled) {
            // AMOLED Theme: rich deep neutral OLED charcoal #1C1D21, matching widgets
            val amoledSurface = Color.parseColor("#1C1D21")
            val amoledRaised = Color.parseColor("#2C2E35")
            return FrostedTokens(
                surface = amoledSurface,
                surfaceRaised = amoledRaised,
                border = CANONICAL_GLASS_BORDER_ARGB
            )
        }
        // Standard Dark Theme: solid rich dark slate #24262C, matching widgets
        val darkSurface = Color.parseColor("#24262C")
        val darkRaised = Color.parseColor("#363840")
        return FrostedTokens(
            surface = darkSurface,
            surfaceRaised = darkRaised,
            border = CANONICAL_GLASS_BORDER_ARGB
        )
    }

    fun resolveGlassBorderColor(tokens: NexusColorTokens?): Int =
        resolveFrostedTokens(tokens).border

    /**
     * Canonical Glass fill alpha, shared by every surface (widgets, boxes, folder icon/card)
     * so opacity and refraction behave identically everywhere instead of each renderer
     * inventing its own cap. Opacity controls overall frost density; refraction (0..1) trades
     * substance for background transmittance — higher refraction lets more of what's behind
     * the glass bleed through, matching the "Glass Refraction" slider's stated purpose.
     *
     * At refr=0: up to 92% of [opacity] (near-opaque "perfect frost").
     * At refr=1: up to 64% of [opacity] (rich background bleed).
     */
    fun frostFillAlpha(opacity: Float, refraction: Float): Float {
        val op = opacity.coerceIn(0f, 1f)
        val refr = refraction.coerceIn(0f, 1f)
        val density = 0.92f - 0.28f * refr
        return (op * density).coerceIn(0f, 0.95f)
    }

    /**
     * Blur radius, in px, for a glass surface at [opacity] and [refraction] — the one rule every
     * opacity-driven glass surface uses (widgets, Mosaic, folders, dock, boxes, previews).
     *
     * Refraction sets the full-strength radius: 90px at 0 (frostiest) to 30px at 1. Opacity then
     * thins it below [FULL_FROST_OPACITY]. The blur used to be full strength at any opacity above
     * zero and then vanish at zero, and the blur — not the tint — is most of what reads as frost:
     * the bottom quarter of the slider looked unchanged, then snapped to clear. Thinning it on a
     * 1.5 curve makes the last stretch a continuous fade from frosted to clear glass.
     */
    fun glassBlurRadius(opacity: Float, refraction: Float): Float {
        val full = 90f - 60f * refraction.coerceIn(0f, 1f)
        val t = (opacity.coerceIn(0f, 1f) / FULL_FROST_OPACITY).coerceAtMost(1f)
        return (full * t * kotlin.math.sqrt(t)).coerceAtLeast(MIN_BLUR_PX)
    }

    /** Opacity at and above which glass gets its full blur. */
    const val FULL_FROST_OPACITY = 0.5f

    /** RenderEffect wants a positive radius; this is visually no blur. */
    private const val MIN_BLUR_PX = 0.5f

    /**
     * Fill alpha for a settings-sheet card's own background — translucent (the normal frosted
     * look, same formula as [frostFillAlpha]) when the master toggle is on, fully OPAQUE when
     * it's off. Every edit sheet (`FolderEditSheetDecor`, `IconEditSheetDecor`,
     * `NexusWidgetSettingsSheet`, etc.) used to compute its card fill via [frostFillAlpha]
     * unconditionally, with only the window-level blur-behind gated on the toggle
     * ([applyDialogWindowChrome]) — so turning the toggle off removed the blur but left the card
     * itself thin/translucent with nothing behind it to diffuse, reading as "went transparent"
     * rather than falling back to a solid, readable card. The card's own surface COLOR
     * (`frostedTokens.surface`) is unchanged either way — this only controls its opacity.
     */
    fun sheetFillAlpha(opacity: Float = 1f, refraction: Float = 0.70f): Float =
        if (isGlobalFrostedGlassEnabled) frostFillAlpha(opacity, refraction) else 1f

    /**
     * Draws a cheap box-blurred wallpaper snapshot at [screenX],[screenY] into [canvas].
     *
     * For surfaces that bake once into a plain software Canvas/Bitmap — AppWidgetProvider
     * bitmaps, AppBox/ShortcutBox, folder plates — none of which can host a live
     * RenderEffect/RenderNode the way the Dock's always-composited View does (a `Canvas`
     * backed by a `Bitmap`, or a one-shot draw call, is never hardware-accelerated).
     * Downscale-then-upscale is the same technique used elsewhere in this codebase for static
     * blur; [divisor] trades blur strength for sharpness — lower for small surfaces (icons,
     * boxes) where a heavy divisor would crush all detail into a flat color, matching the
     * lighter divisor already tuned for widget-scale content vs. full-screen folder content.
     *
     * Returns false (draws nothing) if the bitmap couldn't be allocated — callers should fall
     * back to a plain theme-color fill in that case.
     */
    fun drawBlurredWallpaper(
        canvas: Canvas,
        context: Context,
        screenX: Int,
        screenY: Int,
        width: Int,
        height: Int,
        divisor: Int = 6
    ): Boolean {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val div = divisor.coerceAtLeast(2)
        val downW = (w / div).coerceAtLeast(2)
        val downH = (h / div).coerceAtLeast(2)

        val small = try {
            Bitmap.createBitmap(downW, downH, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return false
        }
        val smallCanvas = Canvas(small)
        smallCanvas.scale(downW / w.toFloat(), downH / h.toFloat())
        HomeScreenFrameCache.drawWallpaperOnly(smallCanvas, context, screenX, screenY, w, h)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(small, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), paint)
        small.recycle()
        return true
    }

    /**
     * Draws a frosted glass tint overlay onto [canvas] with theme surface color.
     */
    fun drawGlassTint(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusPx: Float,
        surfaceColorArgb: Int,
        surfaceRaisedArgb: Int = surfaceColorArgb,
        alpha: Float = DEFAULT_TINT_ALPHA
    ) {
        val clampedAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        val c1 = (clampedAlpha shl 24) or (surfaceColorArgb and 0x00FFFFFF)
        val c2 = (clampedAlpha shl 24) or (surfaceRaisedArgb and 0x00FFFFFF)

        if (surfaceColorArgb != surfaceRaisedArgb && top < bottom) {
            val key = "$left,$top,$right,$bottom,$c1,$c2"
            if (key != cachedTintKey || cachedTintShader == null) {
                cachedTintShader = LinearGradient(
                    left, top, left, bottom,
                    c1, c2, Shader.TileMode.CLAMP
                )
                cachedTintKey = key
            }
            tintPaint.shader = cachedTintShader
        } else {
            tintPaint.shader = null
            tintPaint.color = c1
        }

        if (radiusPx <= 0f) {
            canvas.drawRect(left, top, right, bottom, tintPaint)
        } else {
            canvas.drawRoundRect(left, top, right, bottom, radiusPx, radiusPx, tintPaint)
        }
    }

    /**
     * Draws a crisp 1dp glass border onto [canvas].
     */
    fun drawGlassBorder(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radiusPx: Float,
        density: Float,
        borderColorArgb: Int = CANONICAL_GLASS_BORDER_ARGB
    ) {
        val strokeWidth = 1f * density
        borderPaint.strokeWidth = strokeWidth
        borderPaint.color = borderColorArgb

        val halfStroke = strokeWidth / 2f
        val insetLeft = left + halfStroke
        val insetTop = top + halfStroke
        val insetRight = right - halfStroke
        val insetBottom = bottom - halfStroke

        if (insetRight <= insetLeft || insetBottom <= insetTop) return

        val adjustedRadius = (radiusPx - halfStroke).coerceAtLeast(0f)
        if (adjustedRadius <= 0f) {
            canvas.drawRect(insetLeft, insetTop, insetRight, insetBottom, borderPaint)
        } else {
            canvas.drawRoundRect(
                insetLeft,
                insetTop,
                insetRight,
                insetBottom,
                adjustedRadius,
                adjustedRadius,
                borderPaint
            )
        }
    }
}
