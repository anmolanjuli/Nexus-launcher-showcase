package com.nexus.launcher.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.ui.canvas.CanvasRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Frost backdrop for sheets and panels, replicating FolderCardFrostApplier's
 * pattern: a child-free background view carries the wallpaper+dock snapshot,
 * a RenderEffect gaussian blur is applied to that view ONLY (API 31+, radius
 * stronger than the App Picker's — see attach), the container's effect is
 * explicitly nulled, and both the blur view and the card clip to the same outline.
 *
 * On API 31+: Uses GPU-accelerated RenderEffect.createBlurEffect() with zero
 * bitmap allocations and zero IPC overhead.
 * On API < 31: Dispatches bitmap allocation and software bilinear blur to
 * background coroutines (Dispatchers.IO / Dispatchers.Default).
 */
object WallpaperSheetFrost {

    private const val SCRIM_ALPHA = 0x80

    /** Creates the blur view inside [card] and wires layout-driven refresh. */
    fun attach(card: ViewGroup, density: Float, refresh: (View) -> Unit): View {
        val blurBg = View(card.context).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        0, 0, view.width, view.height,
                        WallpaperSheetChrome.CORNER_DP * density
                    )
                }
            }
        }
        card.addView(blurBg, 0)
        card.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            blurBg.layout(0, 0, right - left, bottom - top)
            refresh(blurBg)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            blurBg.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val radius = 35f * density
            blurBg.setRenderEffect(
                RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP
                )
            )
            blurBg.background = ColorDrawable(Color.argb(SCRIM_ALPHA, 0, 0, 0))
            card.setRenderEffect(null)
        }
        return blurBg
    }

    /** Rebuilds the window-space wallpaper+dock snapshot behind [blurBg]. */
    fun refresh(
        blurBg: View,
        canvasRenderer: CanvasRenderer? = null,
        dockView: View? = null,
        scope: CoroutineScope? = null
    ) {
        val density = blurBg.resources.displayMetrics.density

        // API 31+: Instant GPU-accelerated RenderEffect — zero allocations, zero IPC
        if (Build.VERSION.SDK_INT >= 31) {
            val radius = 35f * density
            blurBg.setRenderEffect(
                RenderEffect.createBlurEffect(
                    radius, radius, Shader.TileMode.CLAMP
                )
            )
            blurBg.background = ColorDrawable(Color.argb(SCRIM_ALPHA, 0, 0, 0))
            blurBg.invalidate()
            return
        }

        // Fallback for API < 31: Background thread bitmap allocation + software blur
        val w = blurBg.width
        val h = blurBg.height
        if (w <= 0 || h <= 0) return

        val loc = IntArray(2)
        blurBg.getLocationInWindow(loc)
        val dm = blurBg.resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val resources = blurBg.resources
        val context = blurBg.context

        val dockLoc = IntArray(2)
        val dockBmp = if (dockView != null && dockView.width > 0 && dockView.height > 0) {
            dockView.getLocationInWindow(dockLoc)
            val db = Bitmap.createBitmap(dockView.width, dockView.height, Bitmap.Config.ARGB_8888)
            val dc = Canvas(db)
            dockView.draw(dc)
            db
        } else null

        val targetScope = scope
            ?: (context as? LifecycleOwner)?.lifecycleScope
            ?: (context as? Activity as? LifecycleOwner)?.lifecycleScope
            ?: CoroutineScope(Dispatchers.Main)

        targetScope.launch(Dispatchers.IO) {
            val type = canvasRenderer?.wallpaperType ?: "system"

            val systemDrawable = if (type !in listOf("solid", "gradient", "gallery")) {
                WallpaperPreviewView.loadSystemWallpaper(context)
            } else null

            val content = withContext(Dispatchers.Default) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.save()
                c.translate(-loc[0].toFloat(), -loc[1].toFloat())

                if (type == "solid" || type == "gradient" || type == "gallery") {
                    canvasRenderer?.draw(c, screenWidth, screenHeight)
                } else {
                    if (systemDrawable != null) {
                        systemDrawable.setBounds(0, 0, screenWidth, screenHeight)
                        systemDrawable.draw(c)
                    } else {
                        c.drawColor(Color.parseColor("#0E0C18"))
                    }
                }

                if (dockBmp != null) {
                    c.save()
                    c.translate(dockLoc[0].toFloat(), dockLoc[1].toFloat())
                    c.drawBitmap(dockBmp, 0f, 0f, null)
                    c.restore()
                    dockBmp.recycle()
                }
                c.restore()

                fallbackBlur(bmp)
            }

            withContext(Dispatchers.Main) {
                blurBg.background = LayerDrawable(arrayOf(
                    BitmapDrawable(resources, content),
                    ColorDrawable(Color.argb(SCRIM_ALPHA, 0, 0, 0))
                ))
                blurBg.invalidate()
            }
        }
    }

    /** Downscale/upscale software blur — safe on any bitmap/thread because it
     *  never touches RenderNode. Shared by the pre-31 frost fallback AND by
     *  WallpaperApplyController's rasterization. */
    fun fallbackBlur(src: Bitmap, divisor: Int = 12): Bitmap {
        if (divisor <= 1) return src.copy(src.config ?: Bitmap.Config.ARGB_8888, true)

        val targetW = (src.width / divisor).coerceAtLeast(1)
        val targetH = (src.height / divisor).coerceAtLeast(1)

        var current = src
        var w = src.width
        var h = src.height

        while (w > targetW * 2 && h > targetH * 2) {
            w /= 2
            h /= 2
            val next = Bitmap.createScaledBitmap(current, w, h, true)
            if (current !== src) current.recycle()
            current = next
        }

        val down = Bitmap.createScaledBitmap(current, targetW, targetH, true)
        if (current !== src && current !== down) current.recycle()

        val up = Bitmap.createScaledBitmap(down, src.width, src.height, true)
        if (down !== up && down !== src) down.recycle()
        if (up !== src) src.recycle()

        return up
    }
}
