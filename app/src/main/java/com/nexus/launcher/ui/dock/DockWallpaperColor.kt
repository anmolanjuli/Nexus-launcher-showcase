package com.nexus.launcher.ui.dock

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Samples wallpaper color from the bottom dock region for frosted-glass backgrounds. */
internal object DockWallpaperColor {

    private const val DOCK_SLICE_FRACTION = 0.15f
    private const val OUTPUT_ALPHA = 0x8C
    private const val FALLBACK_ARGB = 0x8C0E0C18.toInt()

    private var colorsListener: WallpaperManager.OnColorsChangedListener? = null
    private var wallpaperReceiver: BroadcastReceiver? = null
    private var registeredContext: Context? = null
    private var onWallpaperChanged: (() -> Unit)? = null
    @Volatile private var lastSampledArgb: Int = FALLBACK_ARGB

    fun frostedGlassColor(context: Context, dock: View? = null): Int =
        if (dock != null) sampleNow(dock) else sampleNow(context)

    fun semiTransparentAutoColor(context: Context, dock: View?): Int =
        if (dock != null) sampleNow(dock) else sampleNow(context)

    fun sampleNow(dockView: View): Int {
        return try {
            val sampled = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                sampleWallpaperColor(dockView.context, dockView)
            }
            lastSampledArgb = sampled
            sampled
        } catch (_: Exception) {
            lastSampledArgb
        }
    }

    fun sampleNow(context: Context): Int {
        return try {
            val sampled = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                sampleWallpaperColor(context, null)
            }
            lastSampledArgb = sampled
            sampled
        } catch (_: Exception) {
            lastSampledArgb
        }
    }

    suspend fun sampleWallpaperColor(context: Context, dockView: View? = null): Int =
        withContext(Dispatchers.IO) {
            computeSample(context, dockView)
        }

    fun registerOnWallpaperChanged(context: Context, onChanged: () -> Unit) {
        unregisterOnWallpaperChanged()
        val appContext = context.applicationContext
        registeredContext = appContext
        onWallpaperChanged = onChanged

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val listener = WallpaperManager.OnColorsChangedListener { _, _ ->
                onWallpaperChanged?.invoke()
            }
            colorsListener = listener
            WallpaperManager.getInstance(appContext).addOnColorsChangedListener(
                listener,
                Handler(Looper.getMainLooper())
            )
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_WALLPAPER_CHANGED) {
                    onWallpaperChanged?.invoke()
                }
            }
        }
        wallpaperReceiver = receiver
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterOnWallpaperChanged() {
        val appContext = registeredContext ?: return
        colorsListener?.let { listener ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                WallpaperManager.getInstance(appContext).removeOnColorsChangedListener(listener)
            }
        }
        colorsListener = null
        wallpaperReceiver?.let { receiver ->
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        wallpaperReceiver = null
        registeredContext = null
        onWallpaperChanged = null
    }

    private fun computeSample(context: Context, dockView: View?): Int {
        return try {
            val wallpaper = WallpaperManager.getInstance(context)
            val bitmap = loadWallpaperBitmap(context, wallpaper) ?: return FALLBACK_ARGB
            val crop = dockCropRect(context, dockView, bitmap.width, bitmap.height)
            val averaged = averageGridSample(
                bitmap,
                crop.left,
                crop.top,
                crop.width(),
                crop.height()
            )
            bitmap.recycle()
            Color.argb(
                OUTPUT_ALPHA,
                Color.red(averaged),
                Color.green(averaged),
                Color.blue(averaged)
            )
        } catch (_: SecurityException) {
            FALLBACK_ARGB
        } catch (_: Exception) {
            FALLBACK_ARGB
        }
    }

    private fun loadWallpaperBitmap(context: Context, wallpaper: WallpaperManager): Bitmap? {
        wallpaper.peekDrawable(WallpaperManager.FLAG_SYSTEM)?.let { drawable ->
            renderDrawableAtDesiredSize(wallpaper, drawable)?.let { return it }
        }
        wallpaper.drawable?.let { drawable ->
            renderDrawableAtDesiredSize(wallpaper, drawable)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaper.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { parcel ->
                BitmapFactory.decodeFileDescriptor(parcel.fileDescriptor)?.let { return it }
            }
        }
        return null
    }

    private fun renderDrawableAtDesiredSize(
        wallpaper: WallpaperManager,
        drawable: Drawable
    ): Bitmap? {
        val width = wallpaper.desiredMinimumWidth.coerceAtLeast(1)
        val height = wallpaper.desiredMinimumHeight.coerceAtLeast(1)
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    private data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun width(): Int = (right - left).coerceAtLeast(1)
        fun height(): Int = (bottom - top).coerceAtLeast(1)
    }

    private fun dockCropRect(
        context: Context,
        dockView: View?,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): CropRect {
        val metrics = context.resources.displayMetrics
        val screenH = metrics.heightPixels.coerceAtLeast(1)
        val scaleY = bitmapHeight.toFloat() / screenH.toFloat()

        if (dockView != null && dockView.height > 0) {
            val loc = IntArray(2)
            dockView.getLocationOnScreen(loc)
            val dockTopScreen = loc[1].coerceAtLeast(0)
            val dockHeightScreen = dockView.height.coerceAtLeast(1)
            val top = (dockTopScreen * scaleY).toInt().coerceIn(0, bitmapHeight - 1)
            val bottom = ((dockTopScreen + dockHeightScreen) * scaleY)
                .toInt()
                .coerceIn(top + 1, bitmapHeight)
            return CropRect(0, top, bitmapWidth, bottom)
        }

        val cropTop = (bitmapHeight * (1f - DOCK_SLICE_FRACTION)).toInt()
            .coerceIn(0, bitmapHeight - 1)
        return CropRect(0, cropTop, bitmapWidth, bitmapHeight)
    }

    /** 4×4 grid — 16 sample points averaged. */
    private fun averageGridSample(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        width: Int,
        height: Int
    ): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val x = (left + ((col + 0.5f) / 4f) * width).toInt()
                    .coerceIn(0, bitmap.width - 1)
                val y = (top + ((row + 0.5f) / 4f) * height).toInt()
                    .coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)
                r += Color.red(pixel)
                g += Color.green(pixel)
                b += Color.blue(pixel)
            }
        }
        return Color.rgb((r / 16).toInt(), (g / 16).toInt(), (b / 16).toInt())
    }
}
