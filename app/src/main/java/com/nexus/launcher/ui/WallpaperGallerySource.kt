package com.nexus.launcher.ui

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

/**
 * Gallery wallpaper source: launches the system photo picker and copies the
 * selection into app-internal storage (decoded + center-cropped to screen
 * resolution). The original content URI is read exactly once and never
 * retained — no persistable URI permission is requested.
 */
object WallpaperGallerySource {

    private const val FILE_PREFIX = "wallpaper_gallery_"
    private const val REGISTRY_KEY = "wallpaper_gallery_pick"

    /**
     * Opens the picker via the activity result registry (no Activity source
     * changes needed) and invokes [onCopied] on the main thread with the
     * internal file path, or null if cancelled / copy failed.
     */
    fun launchPicker(activity: ComponentActivity, onCopied: (String?) -> Unit) {
        var launcher: ActivityResultLauncher<Intent>? = null
        launcher = activity.activityResultRegistry.register(
            REGISTRY_KEY, ActivityResultContracts.StartActivityForResult()
        ) { result ->
            launcher?.unregister()
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val path = copyToInternal(activity.applicationContext, uri)
                    withContext(Dispatchers.Main) { onCopied(path) }
                }
            } else {
                onCopied(null)
            }
        }
        launcher.launch(buildPickerIntent())
    }

    /** Modern Photo Picker on API 33+, ACTION_OPEN_DOCUMENT fallback below. */
    private fun buildPickerIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 33) {
            Intent(MediaStore.ACTION_PICK_IMAGES)
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
        }

    /**
     * Decodes the picked image (sampled near screen size), center-crops it to
     * exactly screen resolution, and saves it as an internal JPEG. Returns the
     * internal absolute path, or null on any failure. Call on Dispatchers.IO.
     */
    fun copyToInternal(context: Context, uri: Uri): String? {
        return try {
            val dm = context.resources.displayMetrics
            val screenW = dm.widthPixels.coerceAtLeast(1)
            val screenH = dm.heightPixels.coerceAtLeast(1)

            // Pass 1: bounds only, to pick a sample size without full decode.
            // NOTE: decodeStream returns null by design when inJustDecodeBounds
            // is set — only the STREAM may be null-checked here, never the
            // decode result.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= screenW &&
                bounds.outHeight / (sample * 2) >= screenH
            ) {
                sample *= 2
            }

            // Pass 2: sampled decode
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val rawSrc = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null
            val src = com.nexus.launcher.util.BitmapSizeGuard.guard("WallpaperGallerySource.decode($uri)", rawSrc) ?: return null

            // Center-crop into an exactly screen-resolution bitmap
            val out = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val scale = maxOf(screenW / src.width.toFloat(), screenH / src.height.toFloat())
            val cropW = (screenW / scale).toInt().coerceIn(1, src.width)
            val cropH = (screenH / scale).toInt().coerceIn(1, src.height)
            val srcRect = Rect(
                (src.width - cropW) / 2, (src.height - cropH) / 2,
                (src.width + cropW) / 2, (src.height + cropH) / 2
            )
            canvas.drawBitmap(
                src, srcRect,
                RectF(0f, 0f, screenW.toFloat(), screenH.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            src.recycle()

            // Unique name per pick: a changed path guarantees the settings
            // StateFlow emits (data class would dedupe an identical path) and
            // path-keyed bitmap caches reload. Older copies cleaned up after.
            val file = File(context.filesDir, "$FILE_PREFIX${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { stream ->
                out.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            out.recycle()
            // No cleanup here. A copy is made for every pick, including one only previewed and
            // never applied — deleting the others at this point deleted the wallpaper still in
            // use, and the next launch drew nothing (NexusWallpaperDebug, 2026-09-24: saved path,
            // fileExists=false). Old copies go when a wallpaper is actually applied: [prune].
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes every gallery copy except [keepPath] — the one now applied, or null when the
     * applied wallpaper is not a gallery one. Call only once the choice is saved; the copies
     * exist until then so a preview can be applied. Call on Dispatchers.IO.
     */
    fun prune(context: Context, keepPath: String?) {
        context.filesDir.listFiles { f ->
            f.name.startsWith(FILE_PREFIX) && f.absolutePath != keepPath
        }?.forEach { it.delete() }
    }

    private var hasPromptedForPermission = false

    fun getRequiredPermission(): String = if (Build.VERSION.SDK_INT >= 33) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasPermission(context: Context): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, getRequiredPermission()
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadRecentImages(context: Context): List<Uri> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext emptyList()
        val uris = mutableListOf<Uri>()
        try {
            val collection = if (Build.VERSION.SDK_INT >= 29) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext() && uris.size < 6) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(collection, id)
                    uris.add(contentUri)
                }
            }
        } catch (_: Exception) {
            // Ignore gracefully
        }
        uris
    }

    fun checkAndRequestPermission(
        activity: ComponentActivity,
        onResult: (Boolean) -> Unit
    ) {
        if (hasPermission(activity)) {
            onResult(true)
            return
        }
        if (hasPromptedForPermission) {
            onResult(false)
            return
        }
        hasPromptedForPermission = true
        
        var launcher: ActivityResultLauncher<String>? = null
        launcher = activity.activityResultRegistry.register(
            "wallpaper_gallery_permission", ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            launcher?.unregister()
            onResult(isGranted)
        }
        launcher.launch(getRequiredPermission())
    }
}
