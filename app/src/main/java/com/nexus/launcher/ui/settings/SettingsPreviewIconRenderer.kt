package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.icons.IconShapePaths

/**
 * Shared renderer providing cached dock-style app icon bitmaps for settings preview screens
 * (Home Screen Settings, App Drawer Settings).
 *
 * Icons use translucent frosted background shapes with subtle borders and crisp monochrome
 * vector glyphs tinted with textPrimary, matching the Dock Edit Sheet live preview style.
 */
object SettingsPreviewIconRenderer {

    val SAMPLE_ICON_RES_IDS = listOf(
        R.drawable.ic_camera,
        R.drawable.ic_gallery,
        R.drawable.ic_settings,
        R.drawable.ic_apps,
        R.drawable.ic_grid,
        R.drawable.ic_search,
        R.drawable.ic_folder_solid,
        R.drawable.ic_bell,
        R.drawable.ic_category,
        R.drawable.ic_page,
        R.drawable.ic_event,
        R.drawable.ic_info,
        R.drawable.ic_home,
        R.drawable.ic_notification,
        R.drawable.ic_explore,
        R.drawable.ic_palette
    )

    /** Sample app names for the previews, in the same order as [SAMPLE_ICON_RES_IDS]. */
    private val PLACEHOLDER_LABELS = listOf(
        R.string.preview_app_camera, R.string.preview_app_photos, R.string.preview_app_settings,
        R.string.preview_app_apps, R.string.preview_app_games, R.string.preview_app_browser,
        R.string.preview_app_files, R.string.preview_app_clock, R.string.preview_app_notes,
        R.string.preview_app_mail, R.string.preview_app_calendar, R.string.preview_app_weather,
        R.string.preview_app_home, R.string.preview_app_chat, R.string.preview_app_explore,
        R.string.preview_app_theme,
    )

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun getBitmap(
        context: Context,
        index: Int,
        shapeType: Int,
        isLight: Boolean,
        tokens: NexusColorTokens
    ): Bitmap {
        val iconRes = SAMPLE_ICON_RES_IDS[index % SAMPLE_ICON_RES_IDS.size]
        val cacheKey = "$shapeType-$isLight-$iconRes-${tokens.textPrimary}"
        return bitmapCache.getOrPut(cacheKey) {
            createBitmap(context, shapeType, isLight, iconRes, tokens)
        }
    }

    fun getLabel(context: Context, index: Int): String =
        context.getString(PLACEHOLDER_LABELS[index % PLACEHOLDER_LABELS.size])

    fun clearCache() {
        bitmapCache.clear()
    }

    private fun createBitmap(
        context: Context,
        shapeType: Int,
        isLight: Boolean,
        iconRes: Int,
        tokens: NexusColorTokens
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val size = (80 * density).toInt().coerceAtLeast(128)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val rectF = RectF(2f * density, 2f * density, size - 2f * density, size - 2f * density)

        // 1. Background Fill: Dock-style subtle frosted glass
        bgPaint.color = if (isLight) {
            Color.argb(30, 0, 0, 0)
        } else {
            Color.argb(40, 255, 255, 255)
        }
        val path = IconShapePaths.build(shapeType, rectF)
        canvas.drawPath(path, bgPaint)

        // 2. Subtle Border Stroke
        strokePaint.strokeWidth = 1.5f * density
        strokePaint.color = if (isLight) {
            Color.argb(0x25, 0x00, 0x00, 0x00)
        } else {
            Color.argb(0x35, 0xFF, 0xFF, 0xFF)
        }
        canvas.drawPath(path, strokePaint)

        // 3. Vector Drawable Glyph centered with ~22% inset (matching DockSettingsPreviewView)
        val drawable = ContextCompat.getDrawable(context, iconRes)?.mutate()
        if (drawable != null) {
            DrawableCompat.setTint(drawable, tokens.textPrimary)
            val inset = size * 0.22f
            drawable.setBounds(
                inset.toInt(),
                inset.toInt(),
                (size - inset).toInt(),
                (size - inset).toInt()
            )
            drawable.draw(canvas)
        }

        return bmp
    }
}
