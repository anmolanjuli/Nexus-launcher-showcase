package com.nexus.launcher.ui.canvas

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build

class CanvasWallpaperEffects(private val context: Context) {
    var wallpaperType: String = "system"
    var wallpaperSolidColor: String = "#0D1117"
    var wallpaperGradientStart: String = "#240b36"
    var wallpaperGradientEnd: String = "#c31432"
    var wallpaperGradientDirection: String = "top_bottom"
    var wallpaperGalleryPath: String = ""
    var wallpaperBlur: Float = 0f
    var wallpaperTintColor: String = "#00000000"
    var wallpaperTintStrength: Float = 0f
    
    var wallpaperLuminance: Float = 0f
    
    var dynamicBlur: Float = 0f
    var dynamicTintStrength: Float = 0f
    var invalidateCallback: (() -> Unit)? = null
    
    internal var cachedSystemWallpaper: android.graphics.Bitmap? = null
    internal var lastWallpaperSource: Any? = null
    
    // Blur Cache
    private var lastBlurKey = ""
    private var lastContentKey = ""
    private var cachedBlurredBitmap: android.graphics.Bitmap? = null
    private var cachedBlurNode: android.graphics.RenderNode? = null
    
    fun drawBlurred(
        canvas: Canvas,
        width: Int,
        height: Int,
        contentKey: String,
        blurValue: Float,
        galleryPaint: android.graphics.Paint,
        galleryDstRect: android.graphics.RectF,
        alpha: Float = 1f,
        drawContent: (Canvas) -> Unit
    ) {
        val blurKey = "$contentKey|$blurValue"
        val dm = context.resources.displayMetrics
        
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val contentChanged = contentKey != lastContentKey
            val blurChanged = blurKey != lastBlurKey
            val isColdStart = cachedBlurNode == null
            val isInvalidated = !isColdStart && !cachedBlurNode!!.hasDisplayList()
            
            val needsRecord = contentChanged || isColdStart || isInvalidated
            
            if (needsRecord || blurChanged) {
                lastContentKey = contentKey
                lastBlurKey = blurKey
                cachedBlurredBitmap?.recycle()
                cachedBlurredBitmap = null
                
                val renderNode = cachedBlurNode ?: android.graphics.RenderNode("WallpaperBlurNode")
                
                if (needsRecord) {
                    renderNode.setPosition(0, 0, width, height)
                    val nodeCanvas = renderNode.beginRecording()
                    drawContent(nodeCanvas)
                    renderNode.endRecording()
                }
                
                if (blurChanged || needsRecord) {
                    val blurRadius = Math.max(1f, blurValue * dm.density)
                    renderNode.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP))
                }
                cachedBlurNode = renderNode
            }
            cachedBlurNode?.let {
                it.alpha = alpha.coerceIn(0f, 1f)
                if (canvas.isHardwareAccelerated) {
                    canvas.drawRenderNode(it)
                } else {
                    // Fallback if canvas lost hardware acceleration
                    drawContent(canvas)
                }
            }
        } else {
            if (blurKey != lastBlurKey || cachedBlurredBitmap == null) {
                lastBlurKey = blurKey
                cachedBlurredBitmap?.recycle()
                
                val downscale = 1f / (1f + blurValue / 10f)
                val smallW = (width * downscale).toInt().coerceAtLeast(1)
                val smallH = (height * downscale).toInt().coerceAtLeast(1)
                val smallBmp = android.graphics.Bitmap.createBitmap(smallW, smallH, android.graphics.Bitmap.Config.ARGB_8888)
                val smallCanvas = Canvas(smallBmp)
                smallCanvas.scale(downscale, downscale)
                drawContent(smallCanvas)
                
                cachedBlurredBitmap = smallBmp
            }
            cachedBlurredBitmap?.let {
                galleryDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
                galleryPaint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt().coerceIn(0, 255)
                canvas.drawBitmap(it, null, galleryDstRect, galleryPaint)
                galleryPaint.alpha = 255
            }
        }
    }

    fun invalidateSystemWallpaperCache() {
        if (lastWallpaperSource == "system") {
            lastWallpaperSource = null
            cachedSystemWallpaper = null
        }
    }

    @android.annotation.SuppressLint("NewApi")
    private val colorsListener: android.app.WallpaperManager.OnColorsChangedListener? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            android.app.WallpaperManager.OnColorsChangedListener { colors, which ->
                if ((which and android.app.WallpaperManager.FLAG_SYSTEM) != 0) {
                    if (wallpaperType == "system") {
                        val wm = WallpaperManager.getInstance(context)
                        if (wm.wallpaperInfo != null) {
                            updateLiveWallpaperColors(colors)
                        } else {
                            invalidateSystemWallpaperCache()
                            invalidateCallback?.invoke()
                        }
                    }
                }
            }
        } else null

    private var isListenerRegistered = false

    fun ensureListenerRegistered() {
        if (!isListenerRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = WallpaperManager.getInstance(context)
                colorsListener?.let {
                    wm.addOnColorsChangedListener(it, android.os.Handler(android.os.Looper.getMainLooper()))
                    isListenerRegistered = true
                }
            } catch (e: Exception) {}
        }
    }

    fun unregisterListener() {
        if (isListenerRegistered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val wm = WallpaperManager.getInstance(context)
                colorsListener?.let { wm.removeOnColorsChangedListener(it) }
                isListenerRegistered = false
            } catch (e: Exception) {}
        }
    }

    private fun updateLiveWallpaperColors(colors: android.app.WallpaperColors?) {
        val wm = WallpaperManager.getInstance(context)
        if (wm.wallpaperInfo == null) return // Only for live wallpapers

        var primary: Int = Color.parseColor("#0D1117")
        var secondary: Int = Color.parseColor("#0D1117")
        if (colors != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            primary = colors.primaryColor.toArgb()
            secondary = colors.secondaryColor?.toArgb() ?: primary
        }

        val bmp = android.graphics.Bitmap.createBitmap(100, 200, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f, 0f, 200f, intArrayOf(primary, secondary), null, android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, 100f, 200f, paint)

        cachedSystemWallpaper?.recycle()
        cachedSystemWallpaper = bmp
        invalidateCallback?.invoke()
    }

    fun readSystemWallpaperLuminance(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            return 0f
        }
        return try {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            val primary = colors?.primaryColor?.toArgb() ?: return 0f
            val r = Color.red(primary) / 255f
            val g = Color.green(primary) / 255f
            val b = Color.blue(primary) / 255f
            0.299f * r + 0.587f * g + 0.114f * b
        } catch (_: Exception) {
            0f
        }
    }
}
