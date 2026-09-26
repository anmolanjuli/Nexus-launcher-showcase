package com.nexus.launcher.ui.icons

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.theme.ThemeController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Material themed icons: widget-matched [tokens.surface] plate + flood-stripped Tier 2 glyphs. */
@Singleton
class ThemedIconFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val themeController: ThemeController,
    private val iconCache: ThemedIconCache,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * True when the user has enabled themed icons in settings. Safe to read from any thread.
     *
     * The collector in [init] mirrors the setting into this, but callers that resolve icons in
     * response to the *same* settings change must set it themselves first — see
     * `MainViewModel`'s icon collector. Two collectors on one flow have no ordering between them,
     * and resolving with a stale `false` here caches un-themed icons that nothing invalidates.
     */
    @Volatile var enabled: Boolean = false
        internal set

    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glyphMaskCache = ConcurrentHashMap<String, Bitmap>()

    init {
        // Mirror the settings toggle into [enabled] so callers can check it synchronously.
        settingsRepository.settingsFlow
            .map { it.iconTheming }
            .distinctUntilChanged()
            .onEach { enabled = it }
            .launchIn(scope)

        // Evict cache whenever the resolved plate/foreground colors change.
        themeController.currentTokens
            .distinctUntilChanged { a, b ->
                val ca = ThemedIconColors.resolve(a, themeController.themeMode.value)
                val cb = ThemedIconColors.resolve(b, themeController.themeMode.value)
                ca == cb
            }
            .onEach { iconCache.clear() }
            .launchIn(scope)
    }

    fun createThemedDrawable(
        packageName: String,
        sizePx: Int = targetBitmapPx(context.resources.displayMetrics.density)
    ): Drawable {
        val tokens = themeController.currentTokens.value
        val themeMode = themeController.themeMode.value
        val themeKey = ThemedIconColors.cacheKey(tokens, themeMode) + CACHE_ALGO_VERSION
        val cacheKey = iconCache.buildKey(packageName, themeKey, sizePx)
        iconCache.get(cacheKey)?.let { cached ->
            return BitmapDrawable(context.resources, cached)
        }

        val colors = ThemedIconColors.resolve(tokens, themeMode)
        val isDarkTheme = ThemedIconColors.isDarkTheme(themeMode, tokens)
        val density = context.resources.displayMetrics.density
        val bitmap = createThemedIcon(packageName, sizePx, colors, isDarkTheme, density)

        iconCache.put(cacheKey, bitmap)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun createThemedIcon(
        packageName: String,
        sizePx: Int,
        colors: ThemedIconColors.PlateColors,
        isDarkTheme: Boolean,
        density: Float
    ): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawContainerPlate(canvas, sizePx, colors, density)

        val glyphMask = getOrCreateGlyphMask(packageName, sizePx, isDarkTheme)
        glyphPaint.colorFilter = PorterDuffColorFilter(colors.foreground, PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(glyphMask, 0f, 0f, glyphPaint)
        return out
    }

    private fun getOrCreateGlyphMask(
        packageName: String,
        sizePx: Int,
        isDarkTheme: Boolean
    ): Bitmap {
        val cached = glyphMaskCache[packageName]
        if (cached != null && !cached.isRecycled && cached.width == sizePx && cached.height == sizePx) {
            return cached
        }
        val original = loadOriginalIcon(packageName)
        val mask = renderGlyphLayer(original, sizePx, Color.WHITE, isDarkTheme)
        glyphMaskCache[packageName] = mask
        return mask
    }

    fun clearGlyphCache(packageName: String? = null) {
        if (packageName != null) {
            glyphMaskCache.remove(packageName)?.let { if (!it.isRecycled) it.recycle() }
        } else {
            glyphMaskCache.values.forEach { if (!it.isRecycled) it.recycle() }
            glyphMaskCache.clear()
        }
    }

    private fun renderGlyphLayer(
        original: Drawable,
        sizePx: Int,
        fgColor: Int,
        isDarkTheme: Boolean
    ): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            original is AdaptiveIconDrawable &&
            original.monochrome != null
        ) {
            return ThemedIconSilhouette.renderNativeMonochromeLayer(
                original.monochrome!!,
                sizePx,
                fgColor
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && original is AdaptiveIconDrawable) {
            val foreground = original.foreground
            if (foreground != null) {
                val glyph = ThemedIconSilhouette.buildTonalGlyphOnPlate(
                    drawable = foreground,
                    fgColor = fgColor,
                    isDarkTheme = isDarkTheme,
                    sizePx = sizePx,
                    adaptiveViewport = true
                )
                if (!ThemedIconSilhouette.isGlyphEmpty(glyph)) return glyph
                glyph.recycle()
            }
            return ThemedIconSilhouette.buildTonalGlyphOnPlate(
                drawable = original,
                fgColor = fgColor,
                isDarkTheme = isDarkTheme,
                sizePx = sizePx,
                adaptiveViewport = true
            )
        }

        return ThemedIconSilhouette.buildTonalGlyphOnPlate(
            drawable = original,
            fgColor = fgColor,
            isDarkTheme = isDarkTheme,
            sizePx = sizePx,
            adaptiveViewport = false
        )
    }

    private fun drawContainerPlate(
        canvas: Canvas,
        sizePx: Int,
        colors: ThemedIconColors.PlateColors,
        density: Float
    ) {
        val corner = sizePx * PLATE_CORNER_RADIUS_RATIO
        val edge = sizePx.toFloat()
        platePaint.color = colors.background
        canvas.drawRoundRect(0f, 0f, edge, edge, corner, corner, platePaint)

        // Same hairline the widget container draws — this is what separates the plate from
        // the drawer scrim when surface and scrim share a color (Light, AMOLED).
        val stroke = PLATE_BORDER_DP * density
        val inset = stroke * 0.5f
        borderPaint.color = colors.borderColor
        borderPaint.strokeWidth = stroke
        canvas.drawRoundRect(inset, inset, edge - inset, edge - inset, corner - inset, corner - inset, borderPaint)
    }

    private fun loadOriginalIcon(packageName: String): Drawable {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            context.packageManager.defaultActivityIcon
        }
    }

    companion object {
        const val THEMED_ICON_DP = 85f
        const val THEMED_DEFAULT_SHAPE = 11
        const val CACHE_ALGO_VERSION = ":themed_final_v4"

        fun targetBitmapPx(density: Float): Int =
            (THEMED_ICON_DP * density).toInt().coerceIn(96, 256)

        private const val PLATE_CORNER_RADIUS_RATIO = 0.28f
        /** Matches NexusWidgetRenderer's container stroke. */
        private const val PLATE_BORDER_DP = 1.25f
    }
}
