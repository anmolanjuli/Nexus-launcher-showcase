package com.nexus.launcher.ui.widgets.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * High-quality album art decoder and downsampler for music widgets.
 * Decodes or downsamples album art at the target display resolution with
 * [BitmapFactory.Options.inSampleSize] downsampling and bilinear filtering.
 *
 * Reading the art means opening a content stream from the playing app and decoding a full-size
 * cover, which is tens of milliseconds at best and happens on a track change — so asking for it
 * from a drawing pass used to stall the frame. A miss is now handed to a background thread and
 * answered with null; when the decode lands, [onArtDecoded] asks for a redraw. Anything calling
 * off the main thread still gets its answer directly.
 */
object RetroMusicArtDecoder {

    private data class CachedArt(
        val bitmap: Bitmap,
        val key: String,
        val targetPx: Int
    )

    private val cached = mutableMapOf<Int, CachedArt>()

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "nexus-album-art").apply { priority = Thread.MIN_PRIORITY }
    }
    private val main = Handler(Looper.getMainLooper())

    /** The decode already running, so a stream of frames asks for it only once. */
    @Volatile
    private var pending: String? = null

    /** Tracks with no art to find, so a frame does not ask for the same nothing again. */
    private val fruitless = mutableSetOf<String>()

    /** Set by [NexusMusicManager]: art has arrived, so whatever draws it should draw again. */
    @Volatile
    var onArtDecoded: (() -> Unit)? = null

    /**
     * Obtains album art scaled to [targetDisplayPx] by downsampling on decode when reading
     * from Media URI, or by scaling with bilinear filtering from an existing source bitmap.
     */
    fun getScaledAlbumArt(
        context: Context,
        metadata: MediaMetadata?,
        targetDisplayPx: Int
    ): Bitmap? {
        if (metadata == null || targetDisplayPx <= 0) return null

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val key = "${title}_${artist}"

        val c = cached[targetDisplayPx]
        if (c != null && c.key == key && !c.bitmap.isRecycled) {
            return c.bitmap
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            val request = "$key@$targetDisplayPx"
            if (pending != request && request !in fruitless) {
                pending = request
                val app = context.applicationContext
                worker.execute {
                    val bitmap = decode(app, metadata, targetDisplayPx)
                    main.post {
                        if (pending == request) pending = null
                        if (bitmap == null) {
                            if (fruitless.size > 16) fruitless.clear()
                            fruitless.add(request)
                        } else {
                            store(bitmap, key, targetDisplayPx)
                            onArtDecoded?.invoke()
                        }
                    }
                }
            }
            // No art this frame; the redraw that follows the decode will have it.
            return null
        }
        return decode(context, metadata, targetDisplayPx)?.also { store(it, key, targetDisplayPx) }
    }

    private fun store(bitmap: Bitmap, key: String, targetDisplayPx: Int) {
        // Keyed by the size asked for, so two callers at two sizes both keep their copy.
        if (cached.size >= MAX_SIZES) cached.clear()
        cached[targetDisplayPx] = CachedArt(bitmap, key, targetDisplayPx)
    }

    private fun decode(context: Context, metadata: MediaMetadata, targetDisplayPx: Int): Bitmap? {
        // 1. Try decoding directly from URI with inSampleSize if available
        val artUriStr = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

        var decodedBmp: Bitmap? = null
        if (!artUriStr.isNullOrEmpty()) {
            decodedBmp = decodeFromUri(context, artUriStr, targetDisplayPx)
        }

        // 2. Fall back to embedded Bitmap from metadata
        if (decodedBmp == null) {
            val sourceBmp = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            if (sourceBmp != null) {
                decodedBmp = downscaleIfNeeded(sourceBmp, targetDisplayPx)
            }
        }

        return decodedBmp
    }

    private fun decodeFromUri(context: Context, uriStr: String, targetPx: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriStr)
            val resolver = context.contentResolver

            // Pass 1: decode bounds only
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // Calculate sample size for downsampling on decode
            options.inSampleSize = calculateInSampleSize(options, targetPx, targetPx)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            var bmp: Bitmap? = null
            resolver.openInputStream(uri)?.use { stream ->
                bmp = BitmapFactory.decodeStream(stream, null, options)
            }
            bmp?.let { downscaleIfNeeded(it, targetPx) }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun downscaleIfNeeded(source: Bitmap, targetPx: Int): Bitmap {
        if (source.width <= targetPx && source.height <= targetPx) {
            return source
        }
        return Bitmap.createScaledBitmap(source, targetPx, targetPx, true)
    }

    fun clearCache() {
        cached.clear()
        fruitless.clear()
        pending = null
    }

    /** The island and the widget each ask at their own size; a couple more is plenty. */
    private const val MAX_SIZES = 4
}
