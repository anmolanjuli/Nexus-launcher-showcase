package com.nexus.launcher.ui.folder

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.canvas.CanvasRenderer
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.util.BitmapSizeGuard
import java.lang.ref.WeakReference

/**
 * Wallpaper backdrop sampling and frame caching utility for frosted glass surfaces.
 * Zero-allocation drawing for stationary and scrolling surfaces alike.
 */
object HomeScreenFrameCache {

    /** See [com.nexus.launcher.ui.WallpaperFrostCapture] — the write side of this file. */
    const val FROST_CAPTURE_FILE_NAME = "wallpaper_frost_capture.jpg"

    // Deliberately a STRONG reference, unlike the gallery/gradient caches below. This is the
    // single wallpaper source for every glass surface app-wide (widgets, boxes, folders, dock,
    // mosaic) — a WeakReference here was getting collected under ordinary memory pressure (most
    // reliably reproduced by locking the screen, which triggers aggressive GC while the UI is
    // hidden), silently dropping back to a flat fallback fill (read as "frost went fully
    // transparent") until something forced a fresh decode. One screen-resolution JPEG decoded
    // once is a small, worthwhile cost to hold onto for the app's lifetime.
    private var cachedFrostCaptureBmp: Bitmap? = null
    private var cachedFrostCaptureModifiedAt: Long = -1L

    private fun frostCaptureCache(context: Context): Bitmap? {
        val file = java.io.File(context.filesDir, FROST_CAPTURE_FILE_NAME)
        if (!file.exists()) return null
        val modifiedAt = file.lastModified()
        if (modifiedAt == cachedFrostCaptureModifiedAt) {
            val existing = cachedFrostCaptureBmp
            if (existing != null && !existing.isRecycled) return existing
        }
        val bmp = try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
        if (bmp != null) {
            BitmapSizeGuard.guard("HomeScreenFrameCache.frostCapture", bmp)
            cachedFrostCaptureBmp = bmp
            cachedFrostCaptureModifiedAt = modifiedAt
        }
        return bmp
    }

    private var cachedCanvasRef: WeakReference<LauncherCanvasView>? = null
    private var cachedComposite: WeakReference<Bitmap>? = null
    private var lastPageIndex: Int = -1
    private var lastFrameW: Int = 0
    private var lastFrameH: Int = 0
    private var lastSourceKey: String = ""
    private var backdropVersion: Long = 0L

    fun getBackdropVersion(): Long = backdropVersion

    private var cachedGalleryBmp: WeakReference<Bitmap>? = null
    private var cachedGalleryPath: String? = null

    // Zero-allocation draw fields for onDraw safety
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wallpaperSrcRect = Rect()
    private val wallpaperDstRect = RectF()
    private var lastGradientKey: String = ""

    private fun galleryDecodeCache(path: String): Bitmap? {
        if (path.isEmpty()) return null
        if (path == cachedGalleryPath) {
            val existing = cachedGalleryBmp?.get()
            if (existing != null && !existing.isRecycled) return existing
        }
        val bmp = try {
            val raw = BitmapFactory.decodeFile(path) ?: return null
            val maxDim = 1080f
            val maxOriginal = maxOf(raw.width, raw.height).toFloat()
            if (maxOriginal > maxDim) {
                val scale = maxDim / maxOriginal
                val scaledW = (raw.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (raw.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(raw, scaledW, scaledH, true)
                if (scaled != raw) raw.recycle()
                scaled
            } else {
                raw
            }
        } catch (_: Exception) {
            null
        }
        if (bmp != null) {
            BitmapSizeGuard.guard("HomeScreenFrameCache.gallery", bmp)
            cachedGalleryBmp = WeakReference(bmp)
            cachedGalleryPath = path
        }
        return bmp
    }

    private fun resolveCanvas(context: Context): LauncherCanvasView? {
        val existing = cachedCanvasRef?.get()
        if (existing != null && existing.isAttachedToWindow) return existing
        val found = FolderBlurCoordinator.findCanvas(context)
        if (found != null) {
            cachedCanvasRef = WeakReference(found)
        }
        return found
    }

    /**
     * Draws the wallpaper translated for an arbitrary on-screen offset.
     * Zero-allocation, safe to call from View.onDraw every frame.
     */
    fun drawWallpaperOnly(
        canvas: Canvas,
        context: Context,
        screenX: Int,
        screenY: Int,
        width: Int,
        height: Int
    ) {
        val renderer = resolveCanvas(context)?.canvasRenderer
        val dm = context.resources.displayMetrics
        canvas.save()
        canvas.translate(-screenX.toFloat(), -screenY.toFloat())
        if (renderer != null) {
            drawWallpaperBackdrop(canvas, renderer, dm.widthPixels, dm.heightPixels, context)
            // The dim and tint are part of how the wallpaper looks, not decoration on top of it.
            // Without them here a glass surface sampled the raw wallpaper and sat there brighter
            // than everything around it — most obviously on a box turned fully transparent,
            // where its whole area lit up against a dimmed screen.
            drawWallpaperTint(canvas, renderer, dm.widthPixels, dm.heightPixels)
        } else {
            drawSystemOrFallback(canvas, null, dm.widthPixels, dm.heightPixels, context)
        }
        canvas.restore()
    }

    /** The same dim and tint [CanvasRenderer] lays over the wallpaper on the home screen. */
    private fun drawWallpaperTint(canvas: Canvas, renderer: CanvasRenderer, w: Int, h: Int) {
        val dim = renderer.wallpaperBlur
        if (dim > 0f) {
            tintPaint.color = Color.argb((dim.coerceIn(0f, 100f) * 2.55f).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), tintPaint)
        }
        val strength = renderer.wallpaperTintStrength
        if (strength > 0f) {
            val colorInt = try {
                Color.parseColor(renderer.wallpaperTintColor)
            } catch (_: Exception) {
                Color.TRANSPARENT
            }
            tintPaint.color = Color.argb(
                (strength * 255).toInt().coerceIn(0, 255),
                Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt),
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), tintPaint)
        }
    }

    private fun drawWallpaperBackdrop(
        canvas: Canvas,
        renderer: CanvasRenderer,
        w: Int,
        h: Int,
        context: Context
    ) {
        if (renderer.backgroundLayerMode == com.nexus.launcher.theme.BackgroundLayerMode.THEME_COLOR) {
            val tokens = renderer.themeTokens ?: try {
                ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
            if (tokens.bgTop != null && tokens.bgBottom != null) {
                val calmKey = "${tokens.bgTop}|${tokens.bgBottom}|${w}x$h"
                if (calmKey != lastGradientKey) {
                    lastGradientKey = calmKey
                    gradientPaint.shader = LinearGradient(
                        0f, 0f, 0f, h.toFloat(),
                        tokens.bgTop, tokens.bgBottom,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradientPaint)
            } else {
                fallbackPaint.color = tokens.bg
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fallbackPaint)
            }
            return
        }

        when (renderer.wallpaperType) {
            "solid" -> {
                val color = try {
                    Color.parseColor(renderer.wallpaperSolidColor)
                } catch (_: Exception) {
                    Color.parseColor("#0D1117")
                }
                canvas.drawColor(color)
            }
            "gradient" -> {
                val gradKey = "${renderer.wallpaperGradientStart}|${renderer.wallpaperGradientEnd}|${renderer.wallpaperGradientDirection}|${w}x$h"
                if (gradKey != lastGradientKey) {
                    lastGradientKey = gradKey
                    val start = try {
                        Color.parseColor(renderer.wallpaperGradientStart)
                    } catch (_: Exception) {
                        Color.parseColor("#240B36")
                    }
                    val end = try {
                        Color.parseColor(renderer.wallpaperGradientEnd)
                    } catch (_: Exception) {
                        Color.parseColor("#C31432")
                    }
                    gradientPaint.shader = LinearGradient(
                        0f, 0f,
                        if (renderer.wallpaperGradientDirection == "left_right") w.toFloat() else 0f,
                        if (renderer.wallpaperGradientDirection != "left_right") h.toFloat() else 0f,
                        start, end, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradientPaint)
            }
            "gallery" -> {
                val galleryBmp = galleryDecodeCache(renderer.wallpaperGalleryPath)
                if (galleryBmp != null && !galleryBmp.isRecycled) {
                    drawScaledBitmap(canvas, galleryBmp, w, h)
                } else {
                    canvas.drawColor(Color.parseColor("#0D1117"))
                }
            }
            else -> drawSystemOrFallback(canvas, renderer, w, h, context)
        }
    }

    private fun drawSystemOrFallback(
        canvas: Canvas,
        renderer: CanvasRenderer?,
        w: Int,
        h: Int,
        context: Context
    ) {
        // 1. Clean MediaProjection capture (highest fidelity for external or live wallpaper)
        val sysBmp = frostCaptureCache(context)
        if (sysBmp != null && !sysBmp.isRecycled) {
            drawScaledBitmap(canvas, sysBmp, w, h)
            return
        }

        // 2. Cached system wallpaper from CanvasRenderer (extracted via WallpaperManager or synthesized live wallpaper gradient)
        val cached = renderer?.effects?.cachedSystemWallpaper
        if (cached != null && !cached.isRecycled) {
            drawScaledBitmap(canvas, cached, w, h)
            return
        }

        // 3. Direct WallpaperManager peekDrawable()
        if (drawSystemWallpaperDrawable(context, canvas, w, h)) {
            return
        }

        // 4. System wallpaper dominant colors
        if (drawSystemWallpaperColors(context, canvas, w, h)) {
            return
        }

        // 5. Calm theme gradient or translucent background — NEVER an opaque flat surface
        drawFallbackBackground(canvas, context, w, h)
    }

    private fun drawScaledBitmap(canvas: Canvas, bmp: Bitmap, w: Int, h: Int) {
        val scale = maxOf(w / bmp.width.toFloat(), h / bmp.height.toFloat())
        val cropW = (w / scale).toInt().coerceIn(1, bmp.width)
        val cropH = (h / scale).toInt().coerceIn(1, bmp.height)
        wallpaperSrcRect.set(
            (bmp.width - cropW) / 2, (bmp.height - cropH) / 2,
            (bmp.width + cropW) / 2, (bmp.height + cropH) / 2
        )
        wallpaperDstRect.set(0f, 0f, w.toFloat(), h.toFloat())
        canvas.drawBitmap(bmp, wallpaperSrcRect, wallpaperDstRect, bitmapPaint)
    }

    private fun drawSystemWallpaperDrawable(context: Context, canvas: Canvas, w: Int, h: Int): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    @android.annotation.SuppressLint("MissingPermission")
                    wm.getDrawable()
                } catch (_: SecurityException) {
                    wm.peekDrawable()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.peekDrawable(WallpaperManager.FLAG_SYSTEM)
            } else {
                @Suppress("DEPRECATION")
                wm.peekDrawable()
            }
            if (drawable != null) {
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun drawSystemWallpaperColors(context: Context, canvas: Canvas, w: Int, h: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            return try {
                val wm = WallpaperManager.getInstance(context)
                val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (colors != null) {
                    val primary = colors.primaryColor.toArgb()
                    val secondary = colors.secondaryColor?.toArgb() ?: primary
                    val key = "colors|$primary|$secondary|${w}x$h"
                    if (key != lastGradientKey) {
                        lastGradientKey = key
                        gradientPaint.shader = LinearGradient(
                            0f, 0f, 0f, h.toFloat(),
                            primary, secondary, Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradientPaint)
                    true
                } else false
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    private fun drawFallbackBackground(canvas: Canvas, context: Context, w: Int, h: Int) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        if (tokens.bgTop != null && tokens.bgBottom != null) {
            val calmKey = "calm|${tokens.bgTop}|${tokens.bgBottom}|${w}x$h"
            if (calmKey != lastGradientKey) {
                lastGradientKey = calmKey
                gradientPaint.shader = LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    tokens.bgTop, tokens.bgBottom, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradientPaint)
        } else {
            fallbackPaint.color = tokens.bg
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fallbackPaint)
        }
    }

    fun invalidate() {
        backdropVersion++
        cachedComposite?.get()?.recycle()
        cachedComposite = null
        cachedCanvasRef = null
        lastPageIndex = -1
        lastFrameW = 0
        lastFrameH = 0
        lastSourceKey = ""
        cachedGalleryBmp = null
        cachedGalleryPath = null
        lastGradientKey = ""
        cachedFrostCaptureBmp?.recycle()
        cachedFrostCaptureBmp = null
        cachedFrostCaptureModifiedAt = -1L
    }
}
