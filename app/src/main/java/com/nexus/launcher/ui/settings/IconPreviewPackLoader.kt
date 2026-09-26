package com.nexus.launcher.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.nexus.launcher.ui.icons.IconPackParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves sample preview icons for [IconsPreviewView] in their original shape and color
 * when an icon pack is active.
 *
 * Uses a three-tier lookup strategy:
 * 1. Installed launcher apps matching the slot category via [IconPackParser.getDrawableId].
 * 2. Keyword scan across the pack's mapped components via [IconPackParser.findDrawableIdByKeyword].
 * 3. Direct drawable resource name probing in the pack's [Resources].
 *
 * Icons are rendered unmasked in their authentic designed palette and silhouette.
 */
object IconPreviewPackLoader {

    private val CATEGORY_KEYWORDS = listOf(
        listOf("camera", "googlecamera"),
        listOf("gallery", "photos"),
        listOf("settings"),
        listOf("deskclock", "clock"),
        listOf("dialer", "phone", "chrome", "home")
    )

    private val CANDIDATE_DRAWABLE_NAMES = listOf(
        listOf("camera", "ic_camera", "app_camera", "google_camera", "com_google_android_googlecamera", "com_android_camera", "com_android_camera2"),
        listOf("photos", "gallery", "ic_gallery", "app_gallery", "google_photos", "com_google_android_apps_photos", "com_android_gallery3d"),
        listOf("settings", "ic_settings", "app_settings", "com_android_settings", "settings_gear", "icon_settings"),
        listOf("clock", "deskclock", "ic_clock", "app_clock", "google_clock", "com_google_android_deskclock", "com_android_deskclock"),
        listOf("phone", "dialer", "ic_phone", "app_phone", "home", "ic_home", "google_dialer", "com_google_android_dialer", "com_android_dialer", "chrome", "com_android_chrome")
    )

    private val BACKUP_KEYWORDS = listOf(
        "chrome", "gmail", "youtube", "maps", "messages", "calculator", "calendar", "music", "browser", "play_store"
    )

    suspend fun loadPreviewIcons(
        context: Context,
        packPackage: String,
        iconPackParser: IconPackParser? = null
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        if (packPackage.isBlank() || packPackage == "none") {
            return@withContext listOf(null, null, null, null, null)
        }

        val pm = context.packageManager
        val packRes: Resources = try {
            pm.getResourcesForApplication(packPackage)
        } catch (_: Exception) {
            return@withContext listOf(null, null, null, null, null)
        }

        iconPackParser?.let { parser ->
            try {
                if (parser.loadedPack != packPackage) {
                    parser.loadPack(packPackage)
                }
            } catch (_: Exception) {}
        }

        val launcherApps = try {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
        } catch (_: Exception) {
            emptyList()
        }

        val density = context.resources.displayMetrics.density
        val size = (80 * density).toInt().coerceAtLeast(128)
        val bitmaps = ArrayList<Bitmap?>(5)

        for (i in 0 until 5) {
            val keywords = CATEGORY_KEYWORDS[i]
            val candidates = CANDIDATE_DRAWABLE_NAMES[i]
            var drawableId: Int? = null

            // Tier 1: Check installed launcher app on device
            if (iconPackParser != null && launcherApps.isNotEmpty()) {
                for (kw in keywords) {
                    val app = launcherApps.firstOrNull {
                        it.activityInfo.packageName.contains(kw, ignoreCase = true) ||
                            it.activityInfo.name.contains(kw, ignoreCase = true)
                    }
                    if (app != null) {
                        val comp = ComponentName(app.activityInfo.packageName, app.activityInfo.name)
                        val id = iconPackParser.getDrawableId(comp)
                        if (id != null && id != 0) {
                            drawableId = id
                            break
                        }
                    }
                }
            }

            // Tier 2: Search parsed appfilter nameMap by keyword
            if ((drawableId == null || drawableId == 0) && iconPackParser != null) {
                for (kw in keywords) {
                    val id = iconPackParser.findDrawableIdByKeyword(kw)
                    if (id != null && id != 0) {
                        drawableId = id
                        break
                    }
                }
            }

            // Tier 3: Probe candidate names directly in pack resources
            if (drawableId == null || drawableId == 0) {
                for (name in candidates) {
                    val id = try {
                        packRes.getIdentifier(name, "drawable", packPackage)
                    } catch (_: Exception) {
                        0
                    }
                    if (id != 0) {
                        drawableId = id
                        break
                    }
                }
            }

            // Tier 4: Fallback to popular pack drawables if this slot was missing
            if ((drawableId == null || drawableId == 0) && iconPackParser != null) {
                val backupKw = BACKUP_KEYWORDS.getOrNull(i)
                if (backupKw != null) {
                    drawableId = iconPackParser.findDrawableIdByKeyword(backupKw)
                }
            }

            // Convert to bitmap in original shape and color
            val bmp = if (drawableId != null && drawableId != 0) {
                try {
                    val raw = ResourcesCompat.getDrawable(packRes, drawableId, null)
                    if (raw != null) drawableToBitmap(raw, size) else null
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            bitmaps.add(bmp)
        }

        bitmaps
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bmp
    }
}
