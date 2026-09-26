package com.nexus.launcher.ui.immersive

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.glass.NeumorphicSurfaces

/**
 * Stadium capsules for the immersive status row: wallpaper frost (API 31+), glass fill,
 * hairline, top sheen, and an optional faded background. Drawn on the row's Canvas.
 */
internal class ImmersiveStatusGlass(
    private val view: View,
    private val density: Float,
) {
    private val loc = IntArray(2)
    private val clipPath = Path()
    private val sheenRect = RectF()
    private val insetRect = RectF()
    private val fadeRect = RectF()
    private var sheenKey = 0
    private var sheenShader: LinearGradient? = null
    private var fadeKey = 0
    private var fadeShader: LinearGradient? = null

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1f * density).coerceAtLeast(1f)
    }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val pillNode = node("StatusGlassPill")

    private var blurT = 0f
    private var blurAnimator: ValueAnimator? = null

    fun attach() {
        blurAnimator?.cancel()
        blurT = 0f
        blurAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            addUpdateListener { animator ->
                blurT = animator.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }

    fun detach() {
        blurAnimator?.cancel()
        blurAnimator = null
        blurT = 0f
    }

    fun drawPill(canvas: Canvas, bounds: RectF, background: String, opacity: Int, hairline: Int? = null) {
        if (bounds.width() < 2f || bounds.height() < 2f) return
        paintCapsule(canvas, bounds, pillNode, hairline, round = true, surface = background, opacity = opacity)
    }

    fun drawFade(canvas: Canvas, width: Float, height: Float, fromBottom: Boolean) {
        if (width <= 0f || height <= 0f) return
        val tokens = try {
            ThemeObserver.currentTokens(view.context)
        } catch (_: Exception) {
            return
        }
        fadeRect.set(0f, 0f, width, height)
        val opaqueY = if (fromBottom) height else 0f
        val clearY = if (fromBottom) 0f else height
        val key = width.toInt() * 31 + height.toInt() * 17 + tokens.bg + if (fromBottom) 1 else 0
        if (key != fadeKey || fadeShader == null) {
            fadeKey = key
            fadeShader = LinearGradient(
                0f, opaqueY, 0f, clearY,
                ColorUtils.setAlphaComponent(tokens.bg, FADE_ALPHA),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        fadePaint.shader = fadeShader
        canvas.drawRect(fadeRect, fadePaint)
        fadePaint.shader = null
    }

    private fun paintCapsule(
        canvas: Canvas,
        bounds: RectF,
        node: RenderNode?,
        hairline: Int?,
        round: Boolean,
        surface: String,
        opacity: Int,
    ) {
        val tokens = try {
            ThemeObserver.currentTokens(view.context)
        } catch (_: Exception) {
            return
        }
        val glass = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val radius = if (round) bounds.height() / 2f else 0f
        val frosted = surface == ImmersiveStatusStyle.BACKGROUND_FROSTED
        val solid = surface == ImmersiveStatusStyle.BACKGROUND_SOLID
        val t = (opacity.coerceIn(10, 90) / 100f)

        if (frosted) {
            // Blur first, then one translucent wash — an opaque plate under the tint is what
            // read as two-tone and hid the wallpaper.
            basePaint.color = Color.TRANSPARENT
            val alpha = (FrostedGlassEngine.frostFillAlpha(t, 0.45f) * 255f).toInt().coerceIn(0, 180)
            tintPaint.color = ColorUtils.setAlphaComponent(tokens.bg, alpha)
            strokePaint.color = hairline ?: glass.border
        } else if (solid) {
            val fill = if (NeumorphicSurfaces.isActive) tokens.surfaceRaised else tokens.surface
            basePaint.color = ColorUtils.setAlphaComponent(fill, (t * 255f).toInt().coerceIn(40, 255))
            tintPaint.color = Color.TRANSPARENT
            strokePaint.color = hairline ?: tokens.divider
        } else {
            basePaint.color = Color.TRANSPARENT
            tintPaint.color = Color.TRANSPARENT
            strokePaint.color = hairline ?: glass.border
        }

        fillRound(canvas, bounds, radius, basePaint)
        if (frosted) drawFrost(canvas, bounds, radius, node, t)
        if (tintPaint.alpha > 0) fillRound(canvas, bounds, radius, tintPaint)
        if (frosted) drawSheen(canvas, bounds, radius)
        val inset = strokePaint.strokeWidth / 2f
        insetRect.set(
            bounds.left + inset, bounds.top + inset,
            bounds.right - inset, bounds.bottom - inset,
        )
        val strokeR = (radius - inset).coerceAtLeast(0f)
        canvas.drawRoundRect(insetRect, strokeR, strokeR, strokePaint)
    }

    private fun drawFrost(
        canvas: Canvas, bounds: RectF, corner: Float, node: RenderNode?, opacity: Float,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || node == null) return
        if (!canvas.isHardwareAccelerated) return
        val w = bounds.width().toInt().coerceAtLeast(1)
        val h = bounds.height().toInt().coerceAtLeast(1)
        val radius = FrostedGlassEngine.glassBlurRadius(
            opacity.coerceAtLeast(0.35f), 0.40f,
        ) * blurT.coerceIn(0f, 1f)
        if (radius < 0.5f) return
        view.getLocationOnScreen(loc)
        val screenX = loc[0] + bounds.left.toInt()
        val screenY = loc[1] + bounds.top.toInt()
        node.setRenderEffect(
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
        )
        node.setPosition(0, 0, w, h)
        val recorded = node.beginRecording()
        HomeScreenFrameCache.drawWallpaperOnly(recorded, view.context, screenX, screenY, w, h)
        node.endRecording()
        clipPath.reset()
        clipPath.addRoundRect(bounds, corner, corner, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        canvas.translate(bounds.left, bounds.top)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    private fun drawSheen(canvas: Canvas, bounds: RectF, corner: Float) {
        sheenRect.set(bounds.left, bounds.top, bounds.right, bounds.top + bounds.height() * 0.55f)
        val key = bounds.left.toInt() * 31 + bounds.top.toInt() * 17 +
            bounds.right.toInt() * 13 + sheenRect.bottom.toInt()
        if (key != sheenKey || sheenShader == null) {
            sheenKey = key
            sheenShader = LinearGradient(
                sheenRect.left, sheenRect.top, sheenRect.left, sheenRect.bottom,
                ColorUtils.setAlphaComponent(Color.WHITE, 0x18),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        sheenPaint.shader = sheenShader
        canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(bounds, corner, corner, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.drawRect(sheenRect, sheenPaint)
        canvas.restore()
        sheenPaint.shader = null
    }

    private fun fillRound(canvas: Canvas, bounds: RectF, radius: Float, paint: Paint) {
        if (paint.alpha == 0) return
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }

    private fun node(name: String): RenderNode? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return RenderNode(name)
    }

    private companion object {
        const val FADE_ALPHA = 0x66
    }
}
