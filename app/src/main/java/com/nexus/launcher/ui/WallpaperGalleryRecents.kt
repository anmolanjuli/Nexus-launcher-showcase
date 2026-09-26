@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Grid of recently captured camera images and custom gallery wallpapers. */
object WallpaperGalleryRecents {

    fun build(
        context: Context,
        activity: ComponentActivity,
        density: Float,
        tokens: NexusColorTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark },
        onCopied: (String) -> Unit,
        onMoreClicked: () -> Unit
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val gridLayout = GridLayout(context).apply {
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(gridLayout)

        val dp = density
        val itemMargin = (4 * dp).toInt()
        val cornerRadius = 12 * dp

        fun createMoreTile(): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(tokens.surfaceRaised)
                    this.cornerRadius = cornerRadius
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }

                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = (100 * dp).toInt()
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(itemMargin, itemMargin, itemMargin, itemMargin)
                }
                layoutParams = params

                val icon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_arrow_forward)
                    imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
                }

                val label = TextView(context).apply {
                    text = context.getString(com.nexus.launcher.R.string.wallpaper_more)
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                    setPadding(0, (8 * dp).toInt(), 0, 0)
                }

                addView(icon)
                addView(label)

                setOnClickListener {
                    performHapticFeedback(
                        HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    onMoreClicked()
                }
            }
        }

        fun populateGrid(uris: List<Uri>) {
            gridLayout.removeAllViews()
            uris.take(6).forEach { uri ->
                val imageView = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                        }
                    }
                    val params = GridLayout.LayoutParams().apply {
                        width = 0
                        height = (100 * dp).toInt()
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(itemMargin, itemMargin, itemMargin, itemMargin)
                    }
                    layoutParams = params

                    // Not setImageURI: that decodes the photo at full size on the main thread,
                    // and a 50 MP camera shot is a ~200 MB bitmap — the canvas refuses to draw
                    // it and the launcher crashed opening the gallery tab. Tile-sized, off-main.
                    val tile = this
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        val thumb = thumbnail(context, uri, (100 * dp).toInt())
                        withContext(Dispatchers.Main) { thumb?.let(tile::setImageBitmap) }
                    }

                    setOnClickListener {
                        performHapticFeedback(
                            HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                        )
                        activity.lifecycleScope.launch(Dispatchers.IO) {
                            val path = WallpaperGallerySource.copyToInternal(context, uri)
                            if (path != null) {
                                withContext(Dispatchers.Main) {
                                    onCopied(path)
                                }
                            }
                        }
                    }
                }
                gridLayout.addView(imageView)
            }
            gridLayout.addView(createMoreTile())
        }

        WallpaperGallerySource.checkAndRequestPermission(activity) { isGranted ->
            if (isGranted) {
                activity.lifecycleScope.launch(Dispatchers.Main) {
                    val uris = WallpaperGallerySource.loadRecentImages(context)
                    populateGrid(uris)
                }
            } else {
                populateGrid(emptyList())
            }
        }

        return container
    }

    /**
     * A bitmap about [sizePx] on its short side. The platform's cached thumbnail on API 29+,
     * otherwise a power-of-two sampled decode. Null if the image cannot be read. Call off-main.
     */
    private fun thumbnail(context: Context, uri: Uri, sizePx: Int): android.graphics.Bitmap? =
        runCatching {
            val size = sizePx.coerceAtLeast(1)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return@runCatching context.contentResolver.loadThumbnail(
                    uri, android.util.Size(size, size), null,
                )
            }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull()
}
