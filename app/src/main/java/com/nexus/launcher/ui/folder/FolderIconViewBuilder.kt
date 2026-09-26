package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.icons.IconResolverEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * Builds one open-folder grid cell (icon + optional label).
 * Icons are always baked to a [BitmapDrawable] so ImageViews never share
 * live [com.nexus.launcher.ui.icons.IconShapeMasker] instances (blank first tiles).
 */
object FolderIconViewBuilder {
    fun build(
        context: Context,
        item: HomeScreenItem,
        config: FolderConfig,
        col: Int,
        row: Int,
        iconSizePx: Int,
        marginPx: Int,
        density: Float,
        iconCache: Map<String, Drawable>,
        badgeCount: Int = 0,
        badgeStyleApp: Int = 1
    ): LinearLayout {
        val size = iconSizePx.coerceAtLeast(1)
        val itemContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isHapticFeedbackEnabled = true
            layoutParams = GridLayout.LayoutParams().apply {
                width = size
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(col, 1, GridLayout.FILL, 1f)
                rowSpec = GridLayout.spec(row, 1, GridLayout.FILL)
                setMargins(marginPx, marginPx, marginPx, marginPx)
            }

            val iconView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(size, size)
                resolveIcon(context, item.packageName, iconCache, size)?.let { raw ->
                    setImageDrawable(applyBadgeToIcon(raw, badgeCount, badgeStyleApp, size))
                }
            }
            addView(iconView)

            if (config.showLabels) {
                val tokens = try {
                    com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
                } catch (_: Exception) {
                    com.nexus.launcher.theme.NexusColorTokens.Dark
                }
                addView(TextView(context).apply {
                    text = labelFor(context, item)
                    com.nexus.launcher.typography.NexusTypeScale.iconLabel.bindTo(
                        this,
                        tokens.textPrimary
                    )
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (4 * density).toInt() }
                })
            }
        }
        return itemContainer
    }

    private fun applyBadgeToIcon(
        icon: Drawable,
        count: Int,
        style: Int,
        sizePx: Int
    ): Drawable {
        if (style == 0 || count <= 0) return icon
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(canvas)
        val density = android.content.res.Resources.getSystem().displayMetrics.density
        com.nexus.launcher.ui.canvas.BadgeRenderer(density).drawBadge(
            canvas,
            android.graphics.Rect(0, 0, sizePx, sizePx),
            count, style
        )
        return BitmapDrawable(android.content.res.Resources.getSystem(), bmp)
    }

    /**
     * Fresh IconResolver drawable, baked to a bitmap. Never returns a shared cache
     * Drawable — cache hits that only [Drawable.mutate] caused blank tiles.
     */
    private fun resolveIcon(
        context: Context,
        packageName: String,
        iconCache: Map<String, Drawable>,
        sizePx: Int
    ): Drawable? {
        val raw = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext, IconResolverEntryPoint::class.java
            ).iconResolver().getIcon(packageName)
        } catch (_: Exception) {
            iconCache[packageName]?.constantState?.newDrawable()?.mutate()
                ?: try {
                    context.packageManager.getApplicationIcon(packageName).mutate()
                } catch (_: Exception) {
                    null
                }
        } ?: return null
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        raw.setBounds(0, 0, sizePx, sizePx)
        raw.draw(canvas)
        return BitmapDrawable(context.resources, bmp)
    }

    private fun labelFor(context: Context, item: HomeScreenItem): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(item.packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            item.packageName
        }
    }
}
