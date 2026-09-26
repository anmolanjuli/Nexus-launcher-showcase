package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WidgetProviderCatalog(private val context: Context) {

    companion object {
        @Volatile
        private var cachedGroups: List<WidgetAppGroup>? = null

        fun invalidateCache() {
            cachedGroups = null
        }
        
        fun getCached(): List<WidgetAppGroup>? = cachedGroups
    }

    suspend fun loadGroupedProviders(): List<WidgetAppGroup> = withContext(Dispatchers.IO) {
        cachedGroups?.let { return@withContext it }
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val packageManager = context.packageManager

        val providers = appWidgetManager.installedProviders
        val grouped = providers.groupBy { it.provider.packageName }
        val appGroups = mutableListOf<WidgetAppGroup>()

        val density = context.resources.displayMetrics.density
        val densityDpi = context.resources.displayMetrics.densityDpi

        for ((packageName, widgetInfos) in grouped) {
            val appInfo = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w("WidgetProviderCatalog", "Package not found for $packageName", e)
                continue
            }

            val appLabel = packageManager.getApplicationLabel(appInfo).toString()
            val appIcon = packageManager.getApplicationIcon(appInfo)

            val entries = widgetInfos.map { info ->
                val label = info.loadLabel(packageManager)
                var previewBitmap: Bitmap? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val previewDrawable = info.loadPreviewImage(context, densityDpi)
                    if (previewDrawable != null) {
                        try {
                            previewBitmap = if (previewDrawable is BitmapDrawable) {
                                previewDrawable.bitmap
                            } else {
                                val w = if (previewDrawable.intrinsicWidth > 0) previewDrawable.intrinsicWidth else 1
                                val h = if (previewDrawable.intrinsicHeight > 0) previewDrawable.intrinsicHeight else 1
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                previewDrawable.setBounds(0, 0, canvas.width, canvas.height)
                                previewDrawable.draw(canvas)
                                bmp
                            }
                        } catch (e: Exception) {
                            Log.w("WidgetProviderCatalog", "Failed to load preview image for ${info.provider}", e)
                        }
                    }
                }

                if (previewBitmap == null) {
                    try {
                        val iconDrawable = info.loadIcon(context, densityDpi)
                        if (iconDrawable != null) {
                            previewBitmap = if (iconDrawable is BitmapDrawable) {
                                iconDrawable.bitmap
                            } else {
                                val w = if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 1
                                val h = if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 1
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                                iconDrawable.draw(canvas)
                                bmp
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("WidgetProviderCatalog", "Failed to load icon for ${info.provider}", e)
                    }
                }

                // AppWidgetProviderInfo minWidth/minHeight fields are documented by Android to be in dp
                val minWidthDp = info.minWidth
                val minHeightDp = info.minHeight

                WidgetProviderEntry(
                    info = info,
                    label = label,
                    previewBitmap = previewBitmap,
                    minWidthDp = minWidthDp,
                    minHeightDp = minHeightDp
                )
            }

            appGroups.add(WidgetAppGroup(packageName, appLabel, appIcon, entries))
        }

        appGroups.sortBy { it.appLabel.lowercase() }
        cachedGroups = appGroups
        appGroups
    }
}
