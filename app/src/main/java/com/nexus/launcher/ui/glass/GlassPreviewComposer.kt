package com.nexus.launcher.ui.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import kotlin.math.ceil

/**
 * What sits behind a glass surface on the home screen, drawn into a preview that shows the surface
 * smaller than life: the wallpaper under it and, when the surface is glass, that wallpaper blurred
 * the way the real one blurs it, clipped to the surface's shape. The caller draws the surface
 * itself — tint, border, content — on top.
 *
 * ## Why previews need this
 *
 * On the home screen a glass surface is two layers: a blurred-wallpaper view behind, and the
 * surface's own tint and content on top. The widget and folder previews drew only the top layer.
 * At an honest tint (`frostFillAlpha` is about 0.4 at half opacity) that layer is mostly
 * see-through by design, so over the sheet it nearly vanished, and no preview showed refraction.
 *
 * The blur is scaled with the preview. A 60px blur on a surface shown at 40% is a 24px blur in the
 * preview, so the frost reads the same as on the home screen, not heavier or lighter.
 */
class GlassPreviewComposer(private val context: Context) {

    private val node: RenderNode? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) RenderNode("GlassPreview") else null
    private var recordedKey = ""
    private val frame = Path()
    private val local = RectF()

    /**
     * @param dst where the surface is drawn in the preview, in canvas coordinates.
     * @param screenX where the real surface sits on screen — the wallpaper under it is what shows.
     * @param srcW the real surface's size; [dst] shows it scaled to fit.
     * @param frameCornerPx corner of the wallpaper window around the surface, or negative to leave
     *   the sharp wallpaper out (when the surface never sits on bare wallpaper).
     * @param glassClip the glass outline in canvas coordinates, or null when the surface is not glass.
     */
    fun draw(
        canvas: Canvas, dst: RectF, screenX: Int, screenY: Int, srcW: Int, srcH: Int,
        frameCornerPx: Float, glassClip: Path?, refraction: Float, opacity: Float,
    ) {
        if (dst.isEmpty || srcW <= 0 || srcH <= 0) return
        if (frameCornerPx >= 0f) {
            canvas.save()
            frame.reset()
            frame.addRoundRect(dst, frameCornerPx, frameCornerPx, Path.Direction.CW)
            canvas.clipPath(frame)
            drawWallpaper(canvas, dst, screenX, screenY, srcW, srcH)
            canvas.restore()
        }
        if (glassClip == null) return

        canvas.save()
        canvas.clipPath(glassClip)
        val scale = dst.width() / srcW
        val radius = (FrostedGlassEngine.glassBlurRadius(opacity, refraction) * scale).coerceAtLeast(0.5f)
        val n = node
        if (n != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
            val w = ceil(dst.width()).toInt().coerceAtLeast(1)
            val h = ceil(dst.height()).toInt().coerceAtLeast(1)
            val key = "$w|$h|$screenX|$screenY|$srcW|$srcH|$radius|${HomeScreenFrameCache.getBackdropVersion()}"
            if (key != recordedKey || !n.hasDisplayList()) {
                n.setPosition(0, 0, w, h)
                n.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
                local.set(0f, 0f, dst.width(), dst.height())
                val rec = n.beginRecording(w, h)
                try {
                    drawWallpaper(rec, local, screenX, screenY, srcW, srcH)
                } finally {
                    n.endRecording()
                }
                recordedKey = key
            }
            canvas.translate(dst.left, dst.top)
            canvas.drawRenderNode(n)
        } else {
            // No blur off a hardware canvas or before Android 12 — the real backdrops fall back
            // to the unblurred slice there too, so the preview still matches them.
            drawWallpaper(canvas, dst, screenX, screenY, srcW, srcH)
        }
        canvas.restore()
    }

    /** Frees the recorded blur; call when the preview leaves the screen. */
    fun release() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) node?.discardDisplayList()
        recordedKey = ""
    }

    companion object {
        /**
         * [view]'s screen position with every translation on the way up taken out — where it sits
         * at rest. Edit sheets lift the workspace while they are open, and the preview should show
         * the wallpaper under the widget where it lives, not where the sheet has pushed it.
         */
        fun restingLocationOnScreen(view: android.view.View, out: IntArray) {
            view.getLocationOnScreen(out)
            var v: android.view.View? = view
            var dx = 0f
            var dy = 0f
            while (v != null) {
                dx += v.translationX
                dy += v.translationY
                v = v.parent as? android.view.View
            }
            out[0] -= dx.toInt()
            out[1] -= dy.toInt()
        }
    }

    private fun drawWallpaper(canvas: Canvas, dst: RectF, screenX: Int, screenY: Int, srcW: Int, srcH: Int) {
        canvas.save()
        canvas.translate(dst.left, dst.top)
        canvas.scale(dst.width() / srcW, dst.height() / srcH)
        HomeScreenFrameCache.drawWallpaperOnly(canvas, context, screenX, screenY, srcW, srcH)
        canvas.restore()
    }
}
