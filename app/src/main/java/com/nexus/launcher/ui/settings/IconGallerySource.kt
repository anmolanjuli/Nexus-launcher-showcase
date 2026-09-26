package com.nexus.launcher.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object IconGallerySource {

    fun launchPicker(activity: ComponentActivity, packageName: String, onCopied: (String?) -> Unit) {
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            "icon_gallery_pick_$packageName", ActivityResultContracts.StartActivityForResult()
        ) { result ->
            launcher?.unregister()
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val path = copyToInternal(activity.applicationContext, packageName, uri)
                    withContext(Dispatchers.Main) { onCopied(path) }
                }
            } else {
                onCopied(null)
            }
        }
        
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }
        launcher.launch(intent)
    }

    internal fun copyToInternal(context: Context, packageName: String, uri: Uri): String? {
        return try {
            val dm = context.resources.displayMetrics
            val iconSize = (96 * dm.density).toInt() // Standard max icon size bounds

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= iconSize &&
                bounds.outHeight / (sample * 2) >= iconSize
            ) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val src = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val out = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val scale = maxOf(iconSize / src.width.toFloat(), iconSize / src.height.toFloat())
            val cropW = (iconSize / scale).toInt().coerceIn(1, src.width)
            val cropH = (iconSize / scale).toInt().coerceIn(1, src.height)
            val srcRect = Rect(
                (src.width - cropW) / 2, (src.height - cropH) / 2,
                (src.width + cropW) / 2, (src.height + cropH) / 2
            )
            canvas.drawBitmap(
                src, srcRect,
                RectF(0f, 0f, iconSize.toFloat(), iconSize.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            src.recycle()

            val file = File(context.filesDir, "custom_icon_${packageName}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { stream ->
                out.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            out.recycle()
            
            // Cleanup older copies for THIS package
            context.filesDir.listFiles { f ->
                f.name.startsWith("custom_icon_${packageName}_") && f.name != file.name
            }?.forEach { it.delete() }
            
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
