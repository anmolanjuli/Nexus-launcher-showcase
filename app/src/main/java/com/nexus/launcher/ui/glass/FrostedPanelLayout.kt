package com.nexus.launcher.ui.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.folder.HomeScreenFrameCache

/**
 * The frosted **panel**: a bounded surface that carries its own blurred wallpaper, rather than a
 * translucent fill hoping something behind it happens to be blurred.
 *
 * ## Why panels frost themselves
 *
 * The alternative is a window blur (`FLAG_BLUR_BEHIND`) under a translucent card. That has two
 * costs this avoids. Raising and tearing down a cross-process blur is what made the drawer's
 * overflow menu feel heavy, which is why that menu had been made opaque and un-frosted. And a
 * translucent card over a window blur shows whatever is under it — over the drawer, that is the
 * app grid, legible straight through the menu rows.
 *
 * A panel samples only the *wallpaper* slice at its own screen position, from
 * [HomeScreenFrameCache], through one [RenderNode] blur. So it reads as the same frosted glass
 * wherever it sits — over the drawer, mid-transition, on the home screen, inside a dialog window —
 * and an opaque base underneath means the content behind it can never bleed through.
 *
 * ## Use [applyStyle], not the individual setters
 *
 * The setters exist, but every panel should be styled through [applyStyle] so the recipe — which
 * UI Style blurs, what the base is, which border — lives in one place. That recipe drifting apart
 * across call sites is exactly how the launcher's surfaces stopped matching each other.
 *
 * Formerly `SearchPillFrostedLayout`; moved here once it was clearly the general answer rather
 * than a detail of the search pill.
 */
open class FrostedPanelLayout(context: Context) : LinearLayout(context) {

    /**
     * How much the panel's tint hides of its own blurred backdrop.
     *
     * [DENSE] is for panels with content scrolling *behind* them — the drawer's pill and chips, and
     * menus opened over the app grid. At sheet density the icons underneath stay legible through
     * the blur. [SHEET] is the lighter fill every dialog-hosted sheet uses.
     */
    enum class Density(val refraction: Float) {
        DENSE(0.10f),
        SHEET(0.70f),
    }

    /**
     * What the panel becomes in Neumorphism. [FLAT] keeps the plain surface every sheet and dialog
     * uses — a floating surface over busy content has no matching ground for soft shadows to read
     * against. [RAISED] and [SUNKEN] are for bounded controls that sit on the screen's own surface:
     * a chip, a search field.
     */
    enum class Relief { FLAT, RAISED, SUNKEN }

    /** Set before [applyStyle]; see [Relief]. */
    var relief: Relief = Relief.FLAT

    private var styledTokens: NexusColorTokens? = null

    /**
     * Styles the panel for the active UI Style. The one function every panel goes through.
     *
     * Frosted Glass: blurred wallpaper under a translucent tint, with the canonical glass border.
     * Default and Neumorphism: a flat opaque `surface` fill, no blur and no stroke — sampling a
     * wallpaper slice every frame would be pure waste behind an opaque tint. In Neumorphism a
     * panel whose [relief] is not [Relief.FLAT] draws the soft raised or sunken surface instead.
     */
    fun applyStyle(tokens: NexusColorTokens, density: Density = Density.SHEET) {
        val px = resources.displayMetrics.density
        val frosted = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        setBlurEnabled(frosted)
        styledTokens = tokens
        if (relief != Relief.FLAT && NeumorphicSurfaces.isActive) {
            basePaint.color = Color.TRANSPARENT // not setBaseColor, which forces it opaque
            setTint(Color.TRANSPARENT)
            setBorder(Color.TRANSPARENT, 0f)
            applyRelief()
            return
        }
        if (background != null && relief != Relief.FLAT) {
            background = null
            clipToOutline = cornerRadiusPx > 0f
        }
        if (frosted) {
            val glass = FrostedGlassEngine.resolveFrostedTokens(tokens)
            val alpha = when (density) {
                Density.DENSE -> FrostedGlassEngine.frostFillAlpha(1f, density.refraction)
                Density.SHEET -> FrostedGlassEngine.sheetFillAlpha(1f, density.refraction)
            }
            setBaseColor(tokens.bg)
            setTint((glass.surface and 0x00FFFFFF) or ((alpha * 255f).toInt() shl 24))
            setBorder(glass.border, (1 * px).coerceAtLeast(1f))
        } else {
            setBaseColor(tokens.surface)
            setTint(tokens.surface or 0xFF000000.toInt())
            setBorder(Color.TRANSPARENT, 0f)
        }
    }

    /**
     * The soft surface is this view's background. A raised one is mostly shadow outside the
     * bounds, so the rounded outline clip — which would cut it off — is dropped; children sit
     * inside the padding either way.
     */
    private fun applyRelief() {
        val tokens = styledTokens ?: return
        background = if (relief == Relief.SUNKEN) {
            NeumorphicSurfaces.field(this, tokens, cornerRadiusPx)
        } else {
            NeumorphicSurfaces.card(this, tokens, cornerRadiusPx)
        }
        clipToOutline = false
    }

    private val isRelieved: Boolean
        get() = relief != Relief.FLAT && styledTokens != null && NeumorphicSurfaces.isActive

    private var blurRenderNode: RenderNode? = null
    private val screenLoc = IntArray(2)
    private var lastW = -1
    private var lastH = -1
    private var lastScreenX = -1
    private var lastScreenY = -1
    private var lastBackdropVersion = -1L
    private var cornerRadiusPx = 0f
    private val clipPath = Path()
    private var blurRadiusPx = 60f
    private var blurEnabled = true

    /**
     * Frost the real home screen behind the panel — icons, widgets, dock — not only the
     * wallpaper. For panels over a sharp workspace (the widget and Mosaic long-press menus, the
     * resize pad), which otherwise read as a fixed picture next to folder and icon menus, whose
     * see-through fill sits over a blurred workspace. The workspace is recorded once into
     * [workspaceNode]; moving the panel only re-slices it. Off by default: over the drawer the
     * workspace is not what is behind the panel.
     */
    var frostWorkspace = false
        set(value) {
            if (field != value) { field = value; invalidateBackdrop() }
        }
    private var workspaceNode: RenderNode? = null
    private var workspaceRecorded = false

    // Everything (blur, tint, border) is drawn here in onDraw rather than via View.background,
    // since background paints before onDraw — a background tint would sit *under* the blur and
    // be invisible; a background stroke would get painted over by the subsequent full-rect blur.
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val node = RenderNode("FrostedPanelBlurNode")
            node.setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
            blurRenderNode = node
        }
    }

    /** Off for the Default / Neumorphism UI styles, where the pill is a flat surface fill and
     *  sampling a wallpaper slice per frame would be pure waste behind an opaque tint. */
    fun setBlurEnabled(enabled: Boolean) {
        if (blurEnabled != enabled) {
            blurEnabled = enabled
            invalidateBackdrop()
        }
    }

    fun setTint(colorArgb: Int) {
        tintPaint.color = colorArgb
        invalidate()
    }

    /**
     * Opaque colour painted beneath the blurred wallpaper slice. Invisible whenever the slice
     * draws, and the safety net when it does not: the tint alone is translucent, so without a
     * base the drawer's app grid scrolling behind this view reads straight through it.
     */
    fun setBaseColor(colorArgb: Int) {
        basePaint.color = colorArgb or 0xFF000000.toInt()
        invalidate()
    }

    fun setBorder(colorArgb: Int, widthPx: Float) {
        strokePaint.color = colorArgb
        strokePaint.strokeWidth = widthPx
        invalidate()
    }

    fun setCornerRadiusPx(radiusPx: Float) {
        if (cornerRadiusPx != radiusPx) {
            cornerRadiusPx = radiusPx
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    if (v.width <= 0 || v.height <= 0) return
                    outline.setRoundRect(0, 0, v.width, v.height, cornerRadiusPx)
                }
            }
            clipToOutline = true
            invalidateOutline()
            // A corner set after applyStyle reshapes the soft surface too.
            if (isRelieved) applyRelief()
            invalidate()
        }
    }

    /** Refraction (0..1): lower = softer/frostier, higher = sharper — matches every other
     * frosted surface's blur-radius formula (90 - 60*refraction). */
    fun setRefraction(refraction: Float) {
        val radius = FrostedGlassEngine.glassBlurRadius(1f, refraction)
        if (radius != blurRadiusPx) {
            blurRadiusPx = radius
            blurRenderNode?.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
            invalidateBackdrop()
        }
    }

    private fun invalidateBackdrop() {
        lastW = -1
        lastH = -1
        lastBackdropVersion = -1L
        workspaceRecorded = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
            workspaceNode?.discardDisplayList()
        }
        invalidate()
    }

    /** The workspace slice under the panel, from [workspaceNode] (recorded once per backdrop). */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun drawWorkspaceSlice(canvas: Canvas, screenX: Int, screenY: Int) {
        val node = workspaceNode ?: RenderNode("FrostedPanelWorkspace").also { workspaceNode = it }
        if (!workspaceRecorded || !node.hasDisplayList()) {
            val root = rootView
            node.setPosition(0, 0, root.width.coerceAtLeast(1), root.height.coerceAtLeast(1))
            val recording = node.beginRecording()
            val drew = WorkspaceBackdrop.draw(this, recording)
            node.endRecording()
            workspaceRecorded = drew
            if (!drew) return
        }
        canvas.save()
        canvas.translate(-screenX.toFloat(), -screenY.toFloat())
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        if (width > 0 && height > 0 && basePaint.color != 0) {
            if (cornerRadiusPx > 0f) {
                canvas.drawRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    cornerRadiusPx, cornerRadiusPx, basePaint
                )
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)
            }
        }
        val node = blurRenderNode
        if (blurEnabled && node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && width > 0 && height > 0) {
            getLocationOnScreen(screenLoc)
            val curX = screenLoc[0]
            val curY = screenLoc[1]
            val currentVersion = HomeScreenFrameCache.getBackdropVersion()

            if (width != lastW || height != lastH ||
                curX != lastScreenX || curY != lastScreenY ||
                currentVersion != lastBackdropVersion || !node.hasDisplayList()
            ) {
                lastW = width
                lastH = height
                lastScreenX = curX
                lastScreenY = curY
                lastBackdropVersion = currentVersion
                node.setPosition(0, 0, width, height)
                val nodeCanvas = node.beginRecording()
                HomeScreenFrameCache.drawWallpaperOnly(nodeCanvas, context, curX, curY, width, height)
                if (frostWorkspace) drawWorkspaceSlice(nodeCanvas, curX, curY)
                node.endRecording()
            }

            if (canvas.isHardwareAccelerated) {
                if (cornerRadiusPx > 0f) {
                    clipPath.reset()
                    clipPath.addRoundRect(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        cornerRadiusPx, cornerRadiusPx, Path.Direction.CW
                    )
                    canvas.save()
                    canvas.clipPath(clipPath)
                    canvas.drawRenderNode(node)
                    canvas.restore()
                } else {
                    canvas.drawRenderNode(node)
                }
            } else {
                HomeScreenFrameCache.drawWallpaperOnly(canvas, context, curX, curY, width, height)
            }
        }
        if (width > 0 && height > 0) {
            if (cornerRadiusPx > 0f) {
                canvas.drawRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    cornerRadiusPx, cornerRadiusPx, tintPaint
                )
                val inset = strokePaint.strokeWidth / 2f
                canvas.drawRoundRect(
                    inset, inset, width - inset, height - inset,
                    (cornerRadiusPx - inset).coerceAtLeast(0f), (cornerRadiusPx - inset).coerceAtLeast(0f), strokePaint
                )
            } else {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
            }
        }
        super.onDraw(canvas)
    }

    /** Call every time the drawer/search bar moves on screen (e.g. drawer open/close animation)
     * so the sampled wallpaper slice tracks the pill's current position. */
    fun refreshBackdrop() {
        invalidate()
    }

    // The drawer hides its chrome between openings, and a detached/hidden view's RenderNode can
    // come back without its recorded display list — leaving the pill drawing only its
    // translucent tint, so the app grid scrolling behind it reads straight through. Both hooks
    // below force the wallpaper slice to be re-recorded on the next frame it is shown, which is
    // cheap (one slice) and only happens on a visibility/attach edge, not per frame.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidateBackdrop()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidateBackdrop()
    }
}
