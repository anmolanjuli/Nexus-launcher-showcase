package com.nexus.launcher.ui.dock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.ui.dock.settings.DockBackgroundMode
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/** Dock background modes — frosted gradient child + optional solid canvas fill. */
internal object DockBackgroundRenderer {

    @Volatile var backgroundMode: DockBackgroundMode = DockBackgroundMode.TRANSPARENT
    @Volatile var solidColorArgb: Int = DockSettingsRepository.DEFAULT_SOLID_COLOR_ARGB
    @Volatile var frostedGradientIndex: Int = DockSettingsRepository.DEFAULT_FROSTED_GRADIENT_INDEX
    @Volatile var backgroundOpacity: Float = DockSettingsRepository.DEFAULT_DOCK_BACKGROUND_OPACITY
    @Volatile var glassRefraction: Float = DockSettingsRepository.DEFAULT_DOCK_GLASS_REFRACTION
    @Volatile var frostedGlassEnabled: Boolean = false
    @Volatile var isSuppressed: Boolean = false

    private var frostedView: DockFrostedBackgroundView? = null
    private var dockHost: View? = null

    fun attachFrostedLayer(dock: DockLayout, frostedChild: DockFrostedBackgroundView) {
        frostedView = frostedChild
        dockHost = dock
        applyDockClipOutline(dock)
        syncBackgroundLayer(dock)
        dock.applyCornerRadiusOutline()
    }

    fun detachFrostedLayer() {
        frostedView = null
        dockHost = null
    }

    fun invalidateFrostedBackdrop() {
        frostedView?.invalidateBackdrop()
    }

    fun applyDockClipOutline(dock: ViewGroup, clipLeft: Float = 0f, clipRight: Float = Float.NaN) {
        if (dock.width <= 0 || dock.height <= 0) return
        val isVertical = (dock as? DockLayout)?.let { DockAxis.isVertical(it) } ?: false
        val mainMax = if (isVertical) dock.height.toFloat() else dock.width.toFloat()
        val effectiveRight = if (clipRight.isNaN()) mainMax else clipRight
        val thickness = if (isVertical) dock.width else dock.height
        val density = dock.resources.displayMetrics.density
        val radius = DockCornerRadius.cornerRadiusPx(thickness, density)
        val rect = if (isVertical) {
            android.graphics.RectF(0f, clipLeft, dock.width.toFloat(), effectiveRight)
        } else {
            android.graphics.RectF(clipLeft, 0f, effectiveRight, dock.height.toFloat())
        }
        applyDockClipOutline(dock, rect, radius)
    }

    fun applyDockClipOutline(
        dock: ViewGroup,
        clipRect: android.graphics.RectF,
        radius: Float
    ) {
        if (dock.width <= 0 || dock.height <= 0) return
        if (isSuppressed || shouldDrawNeumorphicOnCanvas()) {
            dock.outlineProvider = null
            dock.clipToOutline = false
            dock.clipChildren = false
            return
        }
        DockCornerRadius.applyClipOutline(dock, clipRect, radius)
        dock.clipChildren = true
    }

    /**
     * Sync frosted child visibility/paint. Never call [View.invalidate] on [dock] from
     * inside [DockLayout.onDraw] — that creates a continuous redraw loop / flicker.
     * Pass [requestDockInvalidate]=true only from settings/bind paths outside draw.
     */
    fun syncBackgroundLayer(
        dock: View,
        clipLeft: Float? = null,
        clipRight: Float? = null,
        requestDockInvalidate: Boolean = true
    ) {
        val isVertical = (dock as? DockLayout)?.let { DockAxis.isVertical(it) } ?: false
        val rect = if (isVertical) {
            val t = clipLeft ?: DockLayoutRenderer.currentAnimatedBgLeft()
            val b = clipRight ?: if (DockLayoutRenderer.currentAnimatedBgWidth() > 0f) {
                t + DockLayoutRenderer.currentAnimatedBgWidth()
            } else {
                dock.height.toFloat()
            }
            android.graphics.RectF(0f, t, dock.width.toFloat(), b)
        } else {
            val l = clipLeft ?: DockLayoutRenderer.currentAnimatedBgLeft()
            val r = clipRight ?: if (DockLayoutRenderer.currentAnimatedBgWidth() > 0f) {
                l + DockLayoutRenderer.currentAnimatedBgWidth()
            } else {
                (dock as? DockLayout)?.let { d ->
                    val m = DockSlotLayout.metrics(d.width, d.pageItemCount(), d.maxDockIcons, density = d.resources.displayMetrics.density)
                    m.startOffset + m.totalOccupiedWidth
                } ?: dock.width.toFloat()
            }
            android.graphics.RectF(l, 0f, r, dock.height.toFloat())
        }
        syncBackgroundLayer(dock, rect, isVertical, requestDockInvalidate)
    }

    fun syncBackgroundLayer(
        dock: View,
        clipRect: android.graphics.RectF,
        isVertical: Boolean,
        requestDockInvalidate: Boolean = true
    ) {
        val child = frostedView ?: return
        if (isSuppressed) {
            if (child.visibility != View.GONE) child.visibility = View.GONE
            return
        }
        val isGlassMode = backgroundMode == DockBackgroundMode.FROSTED || backgroundMode == DockBackgroundMode.TRANSPARENT
        val hasGradient = backgroundMode == DockBackgroundMode.FROSTED
        val showGlass = frostedGlassEnabled && isGlassMode
        val showFlatGradient = !frostedGlassEnabled && hasGradient
        val shouldShowFrostedView = showGlass || showFlatGradient
        val newVis = if (shouldShowFrostedView) View.VISIBLE else View.GONE
        if (child.visibility != newVis) child.visibility = newVis
        if (shouldShowFrostedView) {
            val tokens = (dock as? DockLayout)?.currentThemeTokens
            val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
            val isLight = tokens === com.nexus.launcher.theme.NexusColorTokens.Light ||
                (tokens != null && Color.red(tokens.surface) > 200 && Color.green(tokens.surface) > 200)
            child.setFrostedGlassState(
                enabled = frostedGlassEnabled,
                surfaceColorArgb = frostedTokens.surface,
                surfaceRaisedColorArgb = frostedTokens.surfaceRaised,
                alpha = backgroundOpacity,
                hasGradient = hasGradient,
                refraction = glassRefraction
            )
            child.setFrostedGradient(
                DockFrostedGradients.startArgb(frostedGradientIndex, isLight),
                DockFrostedGradients.endArgb(frostedGradientIndex, isLight)
            )
            child.setClipBounds(clipRect.left, clipRect.top, clipRect.right, clipRect.bottom, isVertical)
            child.alpha = 1f
            if (requestDockInvalidate) child.invalidate()
        }
        if (requestDockInvalidate) dock.invalidate()
    }

    fun shouldDrawSolidOnCanvas(): Boolean = !isSuppressed && backgroundMode == DockBackgroundMode.SOLID
    fun shouldDrawNeumorphicOnCanvas(): Boolean = !isSuppressed && (
        backgroundMode == DockBackgroundMode.NEUMORPHIC ||
            // With the master toggle off, a Frosted/Transparent-styled dock has no frosted view
            // to fall back on (see shouldShowFrostedView above) — collapse to the same soft/
            // neumorphic look every other surface reverts to when Glass is globally disabled.
            // EXCEPT in "Default" style specifically — per explicit request, Default means the
            // dock shows icons only with no background plate or color strip at all (unlike
            // widgets/folders, which DO get a flat matching-color plate in Default) — so this
            // fallback is skipped entirely when isDefaultFlatStyleEnabled, leaving the dock truly
            // transparent instead of falling back to a flat/raised squircle behind it.
            // FROSTED is excluded here: with a gradient selected the dock now paints that
            // gradient itself in every UI Style (see syncBackgroundLayer), so adding the soft
            // fallback plate underneath would stack a neumorphic squircle behind it.
            (!frostedGlassEnabled && !FrostedGlassEngine.isDefaultFlatStyleEnabled &&
                backgroundMode == DockBackgroundMode.TRANSPARENT)
        )

    fun resolvedSolidColor(context: Context, dock: View? = null): Int {
        val alphaInt = (backgroundOpacity * 255).toInt().coerceIn(0, 255)
        val baseColor = if (solidColorArgb != DockSettingsRepository.DEFAULT_SOLID_COLOR_ARGB) {
            solidColorArgb
        } else {
            (dock as? DockLayout)?.currentThemeTokens?.surface ?: solidColorArgb
        }
        return (baseColor and 0x00FFFFFF) or (alphaInt shl 24)
    }

    fun currentDockBackgroundColor(context: Context, dock: View? = null): Int {
        return when (backgroundMode) {
            DockBackgroundMode.TRANSPARENT -> Color.TRANSPARENT
            DockBackgroundMode.FROSTED -> DockFrostedGradients.labelColorForLabels(frostedGradientIndex)
            DockBackgroundMode.SOLID -> if (solidColorArgb != DockSettingsRepository.DEFAULT_SOLID_COLOR_ARGB) {
                solidColorArgb
            } else {
                (dock as? DockLayout)?.currentThemeTokens?.surface ?: solidColorArgb
            }
            DockBackgroundMode.NEUMORPHIC -> (dock as? DockLayout)?.currentThemeTokens?.surface ?: Color.TRANSPARENT
        }
    }

    fun drawSolidBackground(canvas: Canvas, width: Int, height: Int, color: Int, density: Float, left: Float = 0f, right: Float = width.toFloat()) {
        DockCornerRadius.drawSolidBackground(canvas, left, right, height, color, density)
    }

    fun drawHoverSlotHighlight(
        canvas: Canvas,
        width: Int,
        height: Int,
        slotLeft: Float,
        slotWidth: Float,
        highlightPaint: Paint,
        density: Float
    ) {
        if (width <= 0 || height <= 0) return
        val cornerRadius = DockCornerRadius.cornerRadiusPx(height, density)
        val left = slotLeft.coerceIn(0f, width.toFloat())
        val right = (left + slotWidth).coerceAtMost(width.toFloat())
        val slotRadius = (cornerRadius / 2f).coerceAtLeast(0f)
        if (slotRadius <= 0f) {
            canvas.drawRect(left, 0f, right, height.toFloat(), highlightPaint)
        } else {
            canvas.drawRoundRect(
                left, 0f, right, height.toFloat(), slotRadius, slotRadius, highlightPaint
            )
        }
    }

    /** @deprecated Use [attachFrostedLayer]. */
    fun attachBlurLayer(dock: DockLayout, blurChild: DockFrostedBackgroundView) =
        attachFrostedLayer(dock, blurChild)

    /** @deprecated Use [detachFrostedLayer]. */
    fun detachBlurLayer() = detachFrostedLayer()

    /** @deprecated Use [syncBackgroundLayer]. */
    fun syncBlurLayer(dock: View) = syncBackgroundLayer(dock)
}
