package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

/** Mini phone-screen live preview; renders from own state so taps reflect instantly. */
class WallpaperPreviewView(
    context: Context,
    private val density: Float
) : View(context) {

    private var mode = "system"
    private var solidColor = "#0D1117"
    private var gradStart = "#240b36"
    private var gradEnd = "#c31432"
    private var gradDirection = "left_right"
    
    private var treatmentBlur = 0f
    private var treatmentTintHue = 0f
    private var treatmentTintStrength = 0f
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    // Phone-frame bezel: glass ring around the mockup + thin dark rim at
    // the screen edge for depth
    private val bezelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.parseColor("#4DFFFFFF")
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#66000000")
    }
    private val clipPath = Path()
    private val boundsRect = RectF()
    private val screenRect = RectF()
    private val bezelRect = RectF()
    private val srcRect = Rect()
    private var systemBitmap: Bitmap? = null
    private var galleryBitmap: Bitmap? = null
    private var galleryLoadedPath: String? = null

    fun showSystem() {
        mode = "system"
        // Always reload — OS wallpaper may have changed since last open
        systemBitmap = null
        ensureSystemBitmap()
        rebuild()
    }

    fun showGallery(path: String) {
        mode = "gallery"
        if (galleryLoadedPath != path) {
            galleryLoadedPath = path
            // Sampled decode — the internal copy is screen-res, preview is tiny
            galleryBitmap = try {
                val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })
                com.nexus.launcher.util.BitmapSizeGuard.guard("WallpaperPreviewView.galleryBitmap($path)", decoded)
            } catch (_: Exception) {
                null
            }
        }
        rebuild()
    }

    fun showSolid(hex: String) {
        mode = "solid"
        solidColor = hex
        rebuild()
    }

    fun showGradient(start: String, end: String, direction: String) {
        mode = "gradient"
        gradStart = start
        gradEnd = end
        gradDirection = direction
        rebuild()
    }
    
    fun applyLiveTreatment(blur: Float, tintHue: Float, tintStrength: Float) {
        treatmentBlur = blur
        treatmentTintHue = tintHue
        treatmentTintStrength = tintStrength
        rebuild()
    }

    private fun rebuild() {
        if (width > 0 && height > 0) {
            when (mode) {
                "solid" -> {
                    fillPaint.shader = null
                    fillPaint.color = safeColor(solidColor, "#0D1117")
                }
                "gradient" -> fillPaint.shader = LinearGradient(
                    0f, 0f,
                    if (gradDirection == "left_right") width.toFloat() else 0f,
                    if (gradDirection != "left_right") height.toFloat() else 0f,
                    safeColor(gradStart, "#240b36"), safeColor(gradEnd, "#c31432"),
                    Shader.TileMode.CLAMP
                )
            }
        }
        invalidate()
    }

    private fun ensureSystemBitmap() {
        if (systemBitmap != null) return
        systemBitmap = com.nexus.launcher.util.BitmapSizeGuard.guard("WallpaperPreviewView.systemBitmap", loadSystemWallpaperBitmap(context))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val bezel = bezelPaint.strokeWidth
        bezelRect.set(bezel / 2f, bezel / 2f, w - bezel / 2f, h - bezel / 2f)
        screenRect.set(bezel, bezel, w - bezel, h - bezel)
        val screenR = 19f * density
        clipPath.reset()
        clipPath.addRoundRect(screenRect, screenR, screenR, Path.Direction.CW)
        rebuild()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clipPath)
        if (mode == "system" || mode == "gallery") {
            val bmp = if (mode == "system") systemBitmap else galleryBitmap
            if (bmp != null) {
                // Center-crop the wallpaper into the mockup screen
                val scale = maxOf(
                    screenRect.width() / bmp.width, screenRect.height() / bmp.height
                )
                val cropW = (screenRect.width() / scale).toInt().coerceIn(1, bmp.width)
                val cropH = (screenRect.height() / scale).toInt().coerceIn(1, bmp.height)
                srcRect.set(
                    (bmp.width - cropW) / 2, (bmp.height - cropH) / 2,
                    (bmp.width + cropW) / 2, (bmp.height + cropH) / 2
                )
                canvas.drawBitmap(bmp, srcRect, screenRect, bitmapPaint)
            } else {
                canvas.drawColor(Color.parseColor("#0E0C18"))
            }
        } else {
            canvas.drawRect(screenRect, fillPaint)
        }
        
        if (treatmentTintStrength > 0f) {
            tintPaint.color = Color.HSVToColor((treatmentTintStrength * 255).toInt(), floatArrayOf(treatmentTintHue, 1f, 1f))
            canvas.drawRect(screenRect, tintPaint)
        }
        
        if (treatmentBlur > 0f) {
            val dimAlpha = (treatmentBlur.coerceIn(0f, 100f) * 2.55f).toInt()
            canvas.drawColor(Color.argb(dimAlpha, 0, 0, 0))
        }
        
        canvas.restore()
        // Phone-frame chrome: glass bezel ring + dark rim at the screen edge
        canvas.drawRoundRect(bezelRect, 22f * density, 22f * density, bezelPaint)
        canvas.drawPath(clipPath, rimPaint)
    }

    private fun safeColor(hex: String, fallback: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor(fallback)
    }

    companion object {
        /**
         * Dominant phone-frame preview: sized to the device's own screen
         * aspect ratio at ~26% of screen height, so it fills most of the
         * space above the sheet body (Google Wallpaper & Style proportions).
         */
        fun createFramed(context: Context, density: Float): WallpaperPreviewView {
            val (w, h) = WallpaperSheetLayout.previewSize(context, density)
            return WallpaperPreviewView(context, density).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(w, h).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
                // Consume taps so touching the preview doesn't fall through
                // to the scrim's dismiss listener
                isClickable = true
            }
        }

        /**
         * Permission-guarded system wallpaper for frost/backdrop callers.
         * Prefer real bitmap; else live thumbnail; else WallpaperColors gradient.
         */
        fun loadSystemWallpaper(context: Context): Drawable? {
            val bmp = loadSystemWallpaperBitmap(context) ?: return null
            return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
        }

        /**
         * Loads a preview-sized bitmap of the current system wallpaper.
         * Order matches home-screen needs + DockWallpaperColor hardening:
         * 1) real static bitmap (peek / drawable / wallpaper file)
         * 2) live-wallpaper package thumbnail (when engine bitmap is unavailable)
         * 3) WallpaperColors primary→secondary gradient (CanvasRenderer port)
         */
        fun loadSystemWallpaperBitmap(context: Context): Bitmap? {
            val wallpaper = android.app.WallpaperManager.getInstance(context)
            val isLive = wallpaper.wallpaperInfo != null
            if (!isLive) {
                tryLoadStaticWallpaperBitmap(wallpaper)?.let { return downscaleForPreview(it) }
            } else {
                tryLoadLiveThumbnail(context, wallpaper)?.let { return downscaleForPreview(it) }
            }
            return synthesizeWallpaperColorsGradient(wallpaper)
        }

        /** Same multi-path static load as DockWallpaperColor.loadWallpaperBitmap. */
        private fun tryLoadStaticWallpaperBitmap(
            wallpaper: android.app.WallpaperManager
        ): Bitmap? {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    wallpaper.peekDrawable(android.app.WallpaperManager.FLAG_SYSTEM)?.let { d ->
                        drawableToBitmap(wallpaper, d)?.let { return it }
                    }
                } catch (_: Exception) { /* fall through */ }
            }
            try {
                @Suppress("MissingPermission")
                val d = try {
                    wallpaper.drawable ?: wallpaper.fastDrawable
                } catch (_: SecurityException) {
                    wallpaper.peekDrawable()
                }
                if (d != null) drawableToBitmap(wallpaper, d)?.let { return it }
            } catch (_: Exception) { /* fall through */ }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    wallpaper.getWallpaperFile(android.app.WallpaperManager.FLAG_SYSTEM)?.use { parcel ->
                        BitmapFactory.decodeFileDescriptor(parcel.fileDescriptor)?.let { return it }
                    }
                } catch (_: Exception) { /* fall through */ }
            }
            return null
        }

        private fun tryLoadLiveThumbnail(
            context: Context,
            wallpaper: android.app.WallpaperManager
        ): Bitmap? {
            return try {
                val info = wallpaper.wallpaperInfo ?: return null
                val pm = context.packageManager
                val thumb = info.loadThumbnail(pm) ?: info.loadIcon(pm) ?: return null
                drawableToBitmap(wallpaper, thumb)
            } catch (_: Exception) {
                null
            }
        }

        private fun drawableToBitmap(
            wallpaper: android.app.WallpaperManager,
            drawable: Drawable
        ): Bitmap? {
            val iw = drawable.intrinsicWidth
            val ih = drawable.intrinsicHeight
            val width = when {
                iw > 0 -> iw
                else -> wallpaper.desiredMinimumWidth.coerceAtLeast(1)
            }
            val height = when {
                ih > 0 -> ih
                else -> wallpaper.desiredMinimumHeight.coerceAtLeast(1)
            }
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(c)
            return bmp
        }

        private fun downscaleForPreview(source: Bitmap): Bitmap {
            val maxEdge = 400
            val scale = (maxEdge.toFloat() / maxOf(source.width, source.height)).coerceAtMost(1f)
            if (scale >= 1f) return source
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(source, w, h, true)
            if (scaled !== source) source.recycle()
            return scaled
        }

        /** Same 100×200 primary→secondary gradient as CanvasRenderer / updateLiveWallpaperColors. */
        private fun synthesizeWallpaperColorsGradient(
            wallpaper: android.app.WallpaperManager
        ): Bitmap {
            var primary = Color.parseColor("#0D1117")
            var secondary = Color.parseColor("#0D1117")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                try {
                    val colors = wallpaper.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
                    if (colors != null) {
                        primary = colors.primaryColor.toArgb()
                        secondary = colors.secondaryColor?.toArgb() ?: primary
                    }
                } catch (_: Exception) { /* keep defaults */ }
            }
            val bmp = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, 200f,
                    intArrayOf(primary, secondary),
                    null as FloatArray?,
                    Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, 100f, 200f, paint)
            return bmp
        }
    }
}
