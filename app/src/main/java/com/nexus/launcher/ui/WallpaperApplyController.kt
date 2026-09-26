package com.nexus.launcher.ui

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class PendingWallpaper {
    object System : PendingWallpaper()
    data class Gradient(val start: String, val end: String, val direction: String) : PendingWallpaper()
    data class Gallery(val path: String) : PendingWallpaper()
}


class WallpaperApplyController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onApplyHomeScreen: (PendingWallpaper, Float, String, Float) -> Unit,
    private val onDismissSheet: () -> Unit
) {

    companion object {
        @Volatile
        var lastSelfWallpaperSetTime: Long = 0L
    }
    fun showApplyChoiceDialog(pending: PendingWallpaper, blur: Float, tintColor: String, tintStrength: Float) {
        val density = context.resources.displayMetrics.density
        val dialog = android.app.Dialog(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setDimAmount(0.6f)
        // Blurs only in Frosted Glass; the 0.6 dim carries the separation in the other styles.
        dialog.window?.let { com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(it, radiusDp = 20f) }
        val tokens = runCatching { com.nexus.launcher.theme.ThemeObserver.currentTokens(context) }
            .getOrDefault(com.nexus.launcher.theme.NexusColorTokens.Dark)

        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            // Was bg_folder_frosted_sheet, which is fully transparent: the dialog's text sat on the
            // dimmed screen with only the window blur behind it, and on bare wallpaper once that
            // blur is off. The shared sheet card gives it a surface in every style.
            background = com.nexus.launcher.ui.glass.FloatingSurfaces.sheetCard(tokens, 20f * density, density)
            setPadding((32 * density).toInt(), (32 * density).toInt(), (32 * density).toInt(), (32 * density).toInt())
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val title = android.widget.TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.wallpaper_set_title)
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24 * density).toInt() }
        }
        container.addView(title)

        fun createOptionRow(iconRes: Int, textStr: String, action: () -> Unit): android.view.View {
            val rowContainer = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                    null, null
                )
                setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * density).toInt() }

                val icon = android.widget.ImageView(context).apply {
                    setImageResource(iconRes)
                    imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    layoutParams = android.widget.LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply {
                        marginEnd = (12 * density).toInt()
                    }
                }

                val label = android.widget.TextView(context).apply {
                    text = textStr
                    textSize = 16f
                    setTextColor(Color.WHITE)
                }

                addView(icon)
                addView(label)
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    action()
                }
            }
            return rowContainer
        }

        container.addView(createOptionRow(com.nexus.launcher.R.drawable.ic_wallpaper_home, context.getString(com.nexus.launcher.R.string.wallpaper_option_home_screen)) {
            onApplyHomeScreen(pending, blur, tintColor, tintStrength)
            dialog.dismiss()
            onDismissSheet()
        })

        container.addView(createOptionRow(com.nexus.launcher.R.drawable.ic_wallpaper_lock, context.getString(com.nexus.launcher.R.string.wallpaper_option_lock_screen)) {
            if (pending is PendingWallpaper.System) {
                Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_wallpaper_system_isolate_error), Toast.LENGTH_SHORT).show()
            } else {
                applyToLockScreen(pending, blur, tintColor, tintStrength)
            }
            dialog.dismiss()
            onDismissSheet()
        })

        container.addView(createOptionRow(com.nexus.launcher.R.drawable.ic_wallpaper_home_lock, context.getString(com.nexus.launcher.R.string.wallpaper_option_both)) {
            if (pending is PendingWallpaper.System) {
                Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_wallpaper_system_isolate_error), Toast.LENGTH_SHORT).show()
                onApplyHomeScreen(pending, blur, tintColor, tintStrength)
            } else {
                // Lock first; Home is committed only via onSuccess so a lock
                // failure never leaves a partial Home-only apply
                applyToLockScreen(pending, blur, tintColor, tintStrength) {
                    onApplyHomeScreen(pending, blur, tintColor, tintStrength)
                }
            }
            dialog.dismiss()
            onDismissSheet()
        })

        val cancel = android.widget.TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.action_cancel)
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#99FFFFFF"))
            gravity = android.view.Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")),
                null, null
            )
            setPadding(0, (16 * density).toInt(), 0, (8 * density).toInt())
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * density).toInt() }
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                dialog.dismiss()
            }
        }
        container.addView(cancel)

        dialog.setContentView(container)
        
        // Ensure standard dialog width behavior
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        dialog.show()
    }

    /** [onSuccess] runs on the main thread only after the lock wallpaper is
     *  actually committed — the Both flow applies Home through it so a lock
     *  failure can never leave Home committed with Lock silently skipped. */
    private fun applyToLockScreen(
        pending: PendingWallpaper,
        blur: Float,
        tintColor: String,
        tintStrength: Float,
        onSuccess: (() -> Unit)? = null
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                // Rasterization stays INSIDE the try: it previously ran before
                // it, so its exception escaped the coroutine and killed the app
                val bitmap = rasterizeWallpaper(pending, blur, tintColor, tintStrength)
                if (bitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_wallpaper_lock_render_error), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val wm = WallpaperManager.getInstance(context)
                lastSelfWallpaperSetTime = System.currentTimeMillis()
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_wallpaper_lock_updated), Toast.LENGTH_SHORT).show()
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                android.util.Log.e("WallpaperApply", "Lock screen apply failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_wallpaper_lock_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Rasterizes the pending wallpaper and commits it to the OS system wallpaper slot
     * (WallpaperManager.FLAG_SYSTEM). This is the missing link that ensures the OS
     * reports the correct wallpaper in Recent Apps thumbnails and when other launchers
     * query the system wallpaper. Must be called on the Home Screen apply path for
     * Gradient and Gallery types. System type already uses the OS wallpaper, so no-op.
     */
    fun syncSystemWallpaper(pending: PendingWallpaper, blur: Float, tintColor: String, tintStrength: Float) {
        // Runs on every home-screen apply, so this is where the choice is final: keep the copy
        // just applied, drop previews and older wallpapers (see WallpaperGallerySource.prune).
        val keep = (pending as? PendingWallpaper.Gallery)?.path
        scope.launch(Dispatchers.IO) { WallpaperGallerySource.prune(context, keep) }
        if (pending is PendingWallpaper.System) return // OS wallpaper already correct
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = rasterizeWallpaper(pending, blur, tintColor, tintStrength)
                if (bitmap == null) {
                    android.util.Log.w("WallpaperApply", "syncSystemWallpaper: rasterize returned null, skipping FLAG_SYSTEM write")
                    return@launch
                }
                val wm = WallpaperManager.getInstance(context)
                lastSelfWallpaperSetTime = System.currentTimeMillis()
                wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                android.util.Log.d("WallpaperApply", "syncSystemWallpaper: FLAG_SYSTEM committed successfully")
            } catch (e: Exception) {
                android.util.Log.e("WallpaperApply", "syncSystemWallpaper FLAG_SYSTEM write failed", e)
            }
        }
    }

    private fun rasterizeWallpaper(pending: PendingWallpaper, blur: Float, tintColor: String, tintStrength: Float): Bitmap? {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        
        var baseBmp: Bitmap? = null

        when (pending) {
            is PendingWallpaper.Gradient -> {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f,
                    if (pending.direction == "left_right") w.toFloat() else 0f,
                    if (pending.direction != "left_right") h.toFloat() else 0f,
                    safeColor(pending.start, "#240b36"),
                    safeColor(pending.end, "#c31432"),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                baseBmp = bmp
            }
            is PendingWallpaper.Gallery -> {
                try {
                    baseBmp = BitmapFactory.decodeFile(pending.path)
                } catch (e: Exception) {
                    return null
                }
            }
            is PendingWallpaper.System -> return null
        }
        
        if (baseBmp == null) return null
        
        // Scale/Crop to screen bounds
        val finalBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        
        val scale = maxOf(w / baseBmp.width.toFloat(), h / baseBmp.height.toFloat())
        val cropW = (w / scale).toInt().coerceIn(1, baseBmp.width)
        val cropH = (h / scale).toInt().coerceIn(1, baseBmp.height)
        val srcRect = android.graphics.Rect(
            (baseBmp.width - cropW) / 2, (baseBmp.height - cropH) / 2,
            (baseBmp.width + cropW) / 2, (baseBmp.height + cropH) / 2
        )
        val dstRect = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
        
        // Always rasterize the sharp center-crop first
        canvas.drawBitmap(baseBmp, srcRect, dstRect, paint)
        baseBmp.recycle()

        // Blur — ALWAYS the shared software downscale/upscale helper here,
        // regardless of API level. This canvas is a software Canvas(bitmap):
        // RenderNode/drawRenderNode is ILLEGAL on it at any API and crashes
        // with "Software rendering doesn't support drawRenderNode". Never
        // reintroduce RenderNode in this rasterization path — the live
        // preview View may use it (hardware canvas), a flat bitmap may not.
        val output = if (blur > 0f) {
            val divisor = (blur * dm.density / 2f).toInt().coerceIn(2, 128)
            val blurred = WallpaperSheetFrost.fallbackBlur(finalBmp, divisor) // recycles finalBmp
            val mutableBlurred = blurred.copy(Bitmap.Config.ARGB_8888, true)
            if (blurred !== mutableBlurred) blurred.recycle()
            mutableBlurred
        } else {
            finalBmp
        }

        // Apply tint if requested (onto the blurred output, matching preview)
        if (tintStrength > 0f) {
            val colorInt = safeColor(tintColor, "#00000000")
            val alpha = (tintStrength * 255).toInt().coerceIn(0, 255)
            val tintedColor = Color.argb(alpha, Color.red(colorInt), Color.green(colorInt), Color.blue(colorInt))
            val tintPaint = Paint().apply { color = tintedColor }
            Canvas(output).drawRect(dstRect, tintPaint)
        }

        return output
    }

    private fun safeColor(hex: String, fallback: String): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) {
        Color.parseColor(fallback)
    }
}

