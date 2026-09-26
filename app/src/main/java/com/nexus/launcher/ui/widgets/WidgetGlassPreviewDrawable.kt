package com.nexus.launcher.ui.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import com.nexus.launcher.ui.folder.FolderIconShapeDraw
import com.nexus.launcher.ui.glass.GlassPreviewComposer
import java.lang.ref.WeakReference

/**
 * Background for a widget preview [ImageView]: the home screen behind the widget — wallpaper, and
 * the widget's glass blur when it has one — with the rendered widget bitmap drawn over it by the
 * ImageView as before. See [GlassPreviewComposer] for why the bitmap alone was not a preview.
 *
 * It works out where FIT_CENTER put the bitmap on its own, so it lines up with the image without
 * the ImageView reporting it. The glass outline is the renderer's own plate path, so the blur sits
 * exactly under the tint the bitmap carries.
 *
 * Install with [install] once, then call [bind] after each `setImageBitmap`.
 */
class WidgetGlassPreviewDrawable private constructor(private val owner: ImageView) : Drawable() {

    private val composer = GlassPreviewComposer(owner.context)
    private var config: NexusWidgetConfig.InstanceConfig? = null
    private var bmpW = 0
    private var bmpH = 0
    private val dst = RectF()
    private val clip = Path()
    private val matrix = Matrix()
    private val loc = IntArray(2)

    override fun draw(canvas: Canvas) {
        val cfg = config ?: return
        if (bmpW <= 0 || bmpH <= 0) return
        val availW = (owner.width - owner.paddingLeft - owner.paddingRight).toFloat()
        val availH = (owner.height - owner.paddingTop - owner.paddingBottom).toFloat()
        if (availW <= 0f || availH <= 0f) return
        val scale = minOf(availW / bmpW, availH / bmpH)
        val left = owner.paddingLeft + (availW - bmpW * scale) / 2f
        val top = owner.paddingTop + (availH - bmpH * scale) / 2f
        dst.set(left, top, left + bmpW * scale, top + bmpH * scale)

        // The wallpaper under the real widget when it is on screen, and at the real widget's
        // scale. A carousel tile renders smaller than the widget and in its own proportions, so
        // it samples a region of the tile's shape with the widget's area, centred on the widget:
        // sampling at the tile's own size made the same blur radius cover far more of the tile
        // than of the widget, and the tiles read heavier-frosted than the widget they preview.
        // Under the preview itself when the widget is not on screen.
        val real = anchor?.get()?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }
        val srcW: Int
        val srcH: Int
        val sx: Int
        val sy: Int
        if (real != null) {
            val k = kotlin.math.sqrt(real.width.toFloat() * real.height / (bmpW.toFloat() * bmpH))
            srcW = (bmpW * k).toInt().coerceAtLeast(1)
            srcH = (bmpH * k).toInt().coerceAtLeast(1)
            GlassPreviewComposer.restingLocationOnScreen(real, loc)
            sx = loc[0] + (real.width - srcW) / 2
            sy = loc[1] + (real.height - srcH) / 2
        } else {
            srcW = bmpW
            srcH = bmpH
            owner.getLocationOnScreen(loc)
            sx = loc[0] + left.toInt()
            sy = loc[1] + top.toInt()
        }

        val dp = owner.resources.displayMetrics.density
        composer.draw(
            canvas, dst, sx, sy, srcW, srcH,
            frameCornerPx = 14f * dp,
            glassClip = if (hasGlass(cfg)) glassPath(cfg, scale, dp) else null,
            refraction = cfg.glassRefraction,
            opacity = cfg.backgroundOpacity,
        )
    }

    /** Same rule as the home screen's backdrop (AppWidgetOverlayBinder.syncGlassBackdrop). */
    private fun hasGlass(cfg: NexusWidgetConfig.InstanceConfig): Boolean {
        if (cfg.isMosaicEmbedded || cfg.backgroundOpacity <= 0.01f) return false
        if (cfg.clockStyle > 0) {
            val retroSurface = com.nexus.launcher.ui.widgets.music.RetroMusicConfig.read(owner.context, cfg.appWidgetId).resolveEffectiveSurface()
            return retroSurface == com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.FROSTED
        }
        return NexusWidgetConfig.isGlassSurface(cfg.backgroundMode, cfg.isExpressive)
    }

    /** The renderer's plate (NexusWidgetRenderer), in bitmap space, mapped onto [dst]. */
    private fun glassPath(cfg: NexusWidgetConfig.InstanceConfig, scale: Float, dp: Float): Path {
        val visualR = cfg.cornerRadius * dp
        val r = if (cfg.shapeStyle == 1) visualR / 0.4f else visualR
        val plate = NexusWidgetShapeGeometry.plate(cfg.shapeStyle, bmpW.toFloat(), bmpH.toFloat())
        clip.set(FolderIconShapeDraw.getShapePath(plate, r, cfg.shapeStyle))
        matrix.setScale(scale, scale)
        matrix.postTranslate(dst.left, dst.top)
        clip.transform(matrix)
        return clip
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /**
         * The widget being edited, whose screen position decides which wallpaper the previews
         * show. Set by the settings sheet while it is open; weak, so a closed sheet leaks nothing.
         */
        @JvmStatic
        var anchor: WeakReference<View>? = null

        fun install(view: ImageView) {
            if (view.background !is WidgetGlassPreviewDrawable) view.background = WidgetGlassPreviewDrawable(view)
        }

        /** Call after `setImageBitmap(bitmap)` with the config it was rendered from. */
        fun bind(view: ImageView, config: NexusWidgetConfig.InstanceConfig, bitmap: Bitmap?) {
            install(view)
            val d = view.background as WidgetGlassPreviewDrawable
            d.config = config
            d.bmpW = bitmap?.width ?: 0
            d.bmpH = bitmap?.height ?: 0
            d.invalidateSelf()
        }
    }
}
