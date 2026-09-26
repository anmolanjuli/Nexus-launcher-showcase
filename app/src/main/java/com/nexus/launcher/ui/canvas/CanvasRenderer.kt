package com.nexus.launcher.ui.canvas

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.launch

@Singleton
class CanvasRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val effects = CanvasWallpaperEffects(context)
    
    var wallpaperType: String
        get() = effects.wallpaperType
        set(value) { effects.wallpaperType = value }
    var wallpaperSolidColor: String
        get() = effects.wallpaperSolidColor
        set(value) { effects.wallpaperSolidColor = value }
    var wallpaperGradientStart: String
        get() = effects.wallpaperGradientStart
        set(value) { effects.wallpaperGradientStart = value }
    var wallpaperGradientEnd: String
        get() = effects.wallpaperGradientEnd
        set(value) { effects.wallpaperGradientEnd = value }
    var wallpaperGradientDirection: String
        get() = effects.wallpaperGradientDirection
        set(value) { effects.wallpaperGradientDirection = value }
    var wallpaperGalleryPath: String
        get() = effects.wallpaperGalleryPath
        set(value) { effects.wallpaperGalleryPath = value }
    var wallpaperBlur: Float
        get() = effects.wallpaperBlur
        set(value) { effects.wallpaperBlur = value }
    var wallpaperTintColor: String
        get() = effects.wallpaperTintColor
        set(value) { effects.wallpaperTintColor = value }
    var wallpaperTintStrength: Float
        get() = effects.wallpaperTintStrength
        set(value) { effects.wallpaperTintStrength = value }
    var wallpaperLuminance: Float
        get() = effects.wallpaperLuminance
        set(value) { effects.wallpaperLuminance = value }
    var dynamicBlur: Float
        get() = effects.dynamicBlur
        set(value) { effects.dynamicBlur = value }
    var dynamicTintStrength: Float
        get() = effects.dynamicTintStrength
        set(value) { effects.dynamicTintStrength = value }
    var invalidateCallback: (() -> Unit)?
        get() = effects.invalidateCallback
        set(value) { effects.invalidateCallback = value }
        
    internal var cachedSystemWallpaper: android.graphics.Bitmap?
        get() = effects.cachedSystemWallpaper
        set(value) { effects.cachedSystemWallpaper = value }

    fun invalidateSystemWallpaperCache() = effects.invalidateSystemWallpaperCache()
    internal fun ensureListenerRegistered() = effects.ensureListenerRegistered()
    fun unregisterListener() = effects.unregisterListener()
    var isWorkspaceBlurred: Boolean = false

    var themeTokens: com.nexus.launcher.theme.NexusColorTokens? = null
    var backgroundLayerMode: com.nexus.launcher.theme.BackgroundLayerMode = com.nexus.launcher.theme.BackgroundLayerMode.WALLPAPER
    private val calmGradientPaint = android.graphics.Paint()
    private var lastCalmKey = ""

    private var gradientPaint = android.graphics.Paint()
    private var lastGradientKey = ""

    private var galleryBitmap: android.graphics.Bitmap? = null
    private var galleryLoadedPath = ""
    private val galleryPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val gallerySrcRect = android.graphics.Rect()
    private val galleryDstRect = android.graphics.RectF()

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }

        val drawTint = {
            if (wallpaperBlur > 0f) {
                val dimAlpha = (wallpaperBlur.coerceIn(0f, 100f) * 2.55f).toInt()
                val dimColor = Color.argb(dimAlpha, 0, 0, 0)
                tintPaint.color = dimColor
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
            }
            if (wallpaperTintStrength > 0f) {
                val colorInt = try { Color.parseColor(wallpaperTintColor) } catch (e: Exception) { Color.TRANSPARENT }
                val alpha = (wallpaperTintStrength * 255).toInt().coerceIn(0, 255)
                val tintedColor = Color.argb(alpha, Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
                tintPaint.color = tintedColor
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), tintPaint)
            }
        }

        if (backgroundLayerMode == com.nexus.launcher.theme.BackgroundLayerMode.THEME_COLOR) {
            val tokens = themeTokens
            if (tokens?.bgTop != null && tokens.bgBottom != null) {
                val calmKey = "${tokens.bgTop}|${tokens.bgBottom}|${width}x${height}"
                if (calmKey != lastCalmKey) {
                    lastCalmKey = calmKey
                    calmGradientPaint.shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        tokens.bgTop, tokens.bgBottom,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), calmGradientPaint)
            } else {
                canvas.drawColor(tokens?.bg ?: android.graphics.Color.parseColor("#121212"))
            }
            drawTint()
            return
        }

        when (wallpaperType) {
            "solid" -> {
                if (effects.lastWallpaperSource != wallpaperSolidColor) {
                    effects.lastWallpaperSource = wallpaperSolidColor
                    try {
                        val color = android.graphics.Color.parseColor(wallpaperSolidColor)
                        val r = android.graphics.Color.red(color) / 255f
                        val g = android.graphics.Color.green(color) / 255f
                        val b = android.graphics.Color.blue(color) / 255f
                        wallpaperLuminance = 0.299f*r + 0.587f*g + 0.114f*b
                    } catch (e: Exception) {
                        wallpaperLuminance = 0f
                    }
                }
                canvas.drawColor(android.graphics.Color.parseColor(wallpaperSolidColor))
                drawTint()
            }
            "gradient" -> {
                val gradientKey = "$wallpaperGradientStart|$wallpaperGradientEnd|$wallpaperGradientDirection|${width}x${height}"
                if (effects.lastWallpaperSource != gradientKey) {
                    effects.lastWallpaperSource = gradientKey
                    wallpaperLuminance = 0f // default fallback
                }
                if (gradientKey != lastGradientKey) {
                    lastGradientKey = gradientKey
                    val startColor = android.graphics.Color.parseColor(wallpaperGradientStart)
                    val endColor = android.graphics.Color.parseColor(wallpaperGradientEnd)
                    val shader = android.graphics.LinearGradient(
                        0f, 0f,
                        if (wallpaperGradientDirection == "left_right") width.toFloat() else 0f,
                        if (wallpaperGradientDirection != "left_right") height.toFloat() else 0f,
                        startColor, endColor,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    gradientPaint.shader = shader
                }
                
                if (dynamicBlur > 0f) {
                    effects.drawBlurred(canvas, width, height, "gradient|$gradientKey", dynamicBlur, galleryPaint, galleryDstRect) { c ->
                        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
                    }
                } else {
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
                }
                drawTint()
            }
            "gallery" -> {
                if (effects.lastWallpaperSource != wallpaperGalleryPath ||
                    galleryLoadedPath != wallpaperGalleryPath) {
                    effects.lastWallpaperSource = wallpaperGalleryPath
                    galleryLoadedPath = wallpaperGalleryPath
                    galleryBitmap = try {
                        val raw = android.graphics.BitmapFactory.decodeFile(wallpaperGalleryPath)
                        if (raw != null) {
                            val maxDim = 1080f
                            val maxOriginal = maxOf(raw.width, raw.height).toFloat()
                            if (maxOriginal > maxDim) {
                                val scale = maxDim / maxOriginal
                                val scaledW = (raw.width * scale).toInt().coerceAtLeast(1)
                                val scaledH = (raw.height * scale).toInt().coerceAtLeast(1)
                                val scaled = android.graphics.Bitmap.createScaledBitmap(raw, scaledW, scaledH, true)
                                raw.recycle()
                                com.nexus.launcher.util.BitmapSizeGuard.guard("CanvasRenderer.galleryBitmap.scaled($wallpaperGalleryPath)", scaled)
                            } else {
                                com.nexus.launcher.util.BitmapSizeGuard.guard("CanvasRenderer.galleryBitmap.raw($wallpaperGalleryPath)", raw)
                            }
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                    wallpaperLuminance = 0f
                }
                val bmp = galleryBitmap
                if (bmp != null) {
                    if (dynamicBlur > 0f) {
                        effects.drawBlurred(canvas, width, height, "gallery|$galleryLoadedPath|${width}x${height}", dynamicBlur, galleryPaint, galleryDstRect) { c ->
                            val scale = maxOf(width / bmp.width.toFloat(), height / bmp.height.toFloat())
                            val cropW = (width / scale).toInt().coerceIn(1, bmp.width)
                            val cropH = (height / scale).toInt().coerceIn(1, bmp.height)
                            gallerySrcRect.set(
                                (bmp.width - cropW) / 2, (bmp.height - cropH) / 2,
                                (bmp.width + cropW) / 2, (bmp.height + cropH) / 2
                            )
                            galleryDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                            c.drawBitmap(bmp, gallerySrcRect, galleryDstRect, galleryPaint)
                        }
                    } else {
                        val scale = maxOf(width / bmp.width.toFloat(), height / bmp.height.toFloat())
                        val cropW = (width / scale).toInt().coerceIn(1, bmp.width)
                        val cropH = (height / scale).toInt().coerceIn(1, bmp.height)
                        gallerySrcRect.set(
                            (bmp.width - cropW) / 2, (bmp.height - cropH) / 2,
                            (bmp.width + cropW) / 2, (bmp.height + cropH) / 2
                        )
                        galleryDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                        canvas.drawBitmap(bmp, gallerySrcRect, galleryDstRect, galleryPaint)
                    }
                    drawTint()
                } else {
                    canvas.drawColor(android.graphics.Color.parseColor("#0D1117"))
                }
            }
            else -> {
                if (effects.lastWallpaperSource != "system") {
                    effects.lastWallpaperSource = "system"
                    wallpaperLuminance = effects.readSystemWallpaperLuminance()
                    
                    effects.cachedSystemWallpaper?.recycle()
                    effects.cachedSystemWallpaper = null
                    
                    // Attempt to fetch the actual system wallpaper for hardware blurring.
                    // On Android 13+, getDrawable() throws SecurityException for third-party wallpapers;
                    // peekDrawable() reliably provides the wallpaper drawable without storage permissions.
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val wm = WallpaperManager.getInstance(context)
                            var bmp: android.graphics.Bitmap? = null
                            
                            val isLiveWallpaper = wm.wallpaperInfo != null
                            
                            if (!isLiveWallpaper) {
                                val drawable = try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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
                                } catch (_: Exception) {
                                    try { wm.peekDrawable() } catch (_: Exception) { null }
                                }
                                
                                if (drawable != null) {
                                    // Big enough for the longest edge of this screen, not a fixed
                                    // 1920: a 1080x2400 wallpaper capped at 1920 became 864 wide,
                                    // and in landscape that 864 is centre-cropped and stretched
                                    // across 2400px — mush, even behind a blur.
                                    val dm = context.resources.displayMetrics
                                    val maxDim = maxOf(dm.widthPixels, dm.heightPixels)
                                        .coerceIn(1920, 2560).toFloat()
                                    val maxOriginal = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).toFloat()
                                    val scale = if (maxOriginal > maxDim) maxDim / maxOriginal else 1f
                                    val w = (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1)
                                    val h = (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1)
                                    bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                                    val c = Canvas(bmp)
                                    drawable.setBounds(0, 0, w, h)
                                    drawable.draw(c)
                                    com.nexus.launcher.util.BitmapSizeGuard.guard("CanvasRenderer.cachedSystemWallpaper", bmp)
                                }
                            }
                            
                            if (bmp == null) {
                                var primary: Int = Color.parseColor("#0D1117")
                                var secondary: Int = Color.parseColor("#0D1117")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                                    if (colors != null) {
                                        primary = colors.primaryColor.toArgb()
                                        secondary = colors.secondaryColor?.toArgb() ?: primary
                                    }
                                }
                                bmp = android.graphics.Bitmap.createBitmap(100, 200, android.graphics.Bitmap.Config.ARGB_8888)
                                val c = Canvas(bmp)
                                val paint = android.graphics.Paint().apply {
                                    shader = android.graphics.LinearGradient(
                                        0f, 0f, 0f, 200f,
                                        intArrayOf(primary, secondary),
                                        null as FloatArray?,
                                        android.graphics.Shader.TileMode.CLAMP
                                    )
                                }
                                c.drawRect(0f, 0f, 100f, 200f, paint)
                            }
                            
                            effects.cachedSystemWallpaper = bmp
                            invalidateCallback?.invoke()
                        } catch (e: Exception) {
                            // Ignored
                        }
                    }
                }
                
                val isBlurred = dynamicBlur > 0f || isWorkspaceBlurred
                if (isBlurred) {
                    val bmp = effects.cachedSystemWallpaper
                    if (bmp != null) {
                        val blurAmount = if (dynamicBlur > 0f) dynamicBlur else 28f
                        val blurAlpha = if (dynamicBlur > 0f) (dynamicBlur / 28f).coerceIn(0f, 1f) else 1f
                        
                        val scale = maxOf(width / bmp.width.toFloat(), height / bmp.height.toFloat())
                        val cropW = (width / scale).toInt().coerceIn(1, bmp.width)
                        val cropH = (height / scale).toInt().coerceIn(1, bmp.height)
                        gallerySrcRect.set(
                            (bmp.width - cropW) / 2, (bmp.height - cropH) / 2,
                            (bmp.width + cropW) / 2, (bmp.height + cropH) / 2
                        )
                        galleryDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                        
                        effects.drawBlurred(
                            canvas, width, height,
                            "system|${bmp.hashCode()}|${width}x${height}",
                            blurAmount, galleryPaint, galleryDstRect,
                            alpha = blurAlpha
                        ) { c ->
                            c.drawBitmap(bmp, gallerySrcRect, galleryDstRect, galleryPaint)
                        }
                        drawTint()
                        return
                    }
                }
                
                // System wallpaper is rendered natively by WindowManager via FLAG_SHOW_WALLPAPER.
                // The canvas MUST always remain Color.TRANSPARENT on the home screen so the real wallpaper is visible.
                canvas.drawColor(Color.TRANSPARENT)
                drawTint()
            }
        }
    }
}
