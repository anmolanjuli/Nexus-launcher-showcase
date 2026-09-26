package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver

/** Wallpaper snapshot helpers for folder UI surfaces. */
object FolderWallpaperBackdrop {

  private const val SHEET_SCRIM_ALPHA = 0x88
  private const val CARD_SCRIM_ALPHA = 0x48
  private const val EDGE_SCRIM_ALPHA = 0x22

  private fun windowOpacity(config: FolderConfig): Float =
    config.windowBackgroundOpacity.coerceIn(0f, 1f)

  private fun scaledAlpha(base: Int, opacity: Float): Int =
    (base * opacity).toInt().coerceIn(0, 255)

  fun buildEdgeToEdgeStack(context: Context, width: Int, height: Int): LayerDrawable {
    val layers = mutableListOf<Drawable>()
    layers += BitmapDrawable(context.resources, captureWallpaper(context, width, height))
    layers += ColorDrawable(Color.argb(EDGE_SCRIM_ALPHA, 0, 0, 0))
    return LayerDrawable(layers.toTypedArray())
  }

  /**
   * Whether the *open folder window* draws as glass.
   *
   * This used to ask [FolderIconPlateDraw.isGlass], which describes the **closed icon's** plate
   * and answers false for an expressive folder - reasonably so, since an expressive icon draws
   * its gradient instead of a glass plate. Applied to the window that meant turning on Expressive
   * Gradient silently dropped the blurred-wallpaper layer below, leaving a flat fill: opaque and
   * dark at full opacity, and see-through rather than frosted as opacity came down.
   *
   * For the window, expressive means "tint the glass with my gradient", not "stop being glass" -
   * the plate colour below already resolves the gradient through
   * [FolderIconSurfaceColor.windowPlateRgb]. Only an explicitly solid window, or Frosted Glass
   * being off globally, should drop the blur.
   */
  fun isWindowGlass(config: FolderConfig): Boolean {
    if (config.windowBackgroundMode.equals("SOLID", ignoreCase = true)) return false
    return FrostedGlassEngine.isGlobalFrostedGlassEnabled
  }

  fun buildCardStack(
    context: Context,
    config: FolderConfig,
    width: Int,
    height: Int,
    screenX: Int = 0,
    screenY: Int = 0,
    // False for a preview that draws the blurred wallpaper itself (GlassPreviewComposer); this
    // layer is the unblurred slice the real window blurs as a whole.
    includeWallpaper: Boolean = true
  ): LayerDrawable {
    val op = windowOpacity(config).coerceIn(0f, 1f)
    val opInt = (op * 255).toInt()
    if (op <= 0.001f) {
      return LayerDrawable(emptyArray())
    }

    val layers = mutableListOf<Drawable>()
    val isGlass = isWindowGlass(config)

    if (isGlass && includeWallpaper) {
      try {
        val wallpaperBmp = captureWallpaper(context, width, height, screenX, screenY)
        layers += BitmapDrawable(context.resources, wallpaperBmp)
      } catch (_: Exception) {}
    }
    if (isGlass) {
      val plateRgb = if (config.isExpressive) {
        FolderIconSurfaceColor.windowPlateRgb(context, config)
      } else {
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        FrostedGlassEngine.resolveFrostedTokens(tokens).surface
      }
      val refr = config.glassRefraction.coerceIn(0f, 1f)
      // Refraction trades surface substance for wallpaper transmittance ("bg bleed"): higher
      // refraction thins the tint so more of the (blurred) wallpaper layer beneath shows
      // through. Previously hardcoded to a flat 40% of opacity regardless of refraction, which
      // both muted the opacity slider's range and made refraction purely cosmetic (it only fed
      // the sheen highlight below, never the actual bleed-through amount).
      val glassAlpha = (FrostedGlassEngine.frostFillAlpha(op, refr) * 255f).toInt()
      layers += ColorDrawable(Color.argb(glassAlpha, Color.red(plateRgb), Color.green(plateRgb), Color.blue(plateRgb)))
      // No sheen/glass-shadow layer here, matching widgets (removed per explicit request).
    } else if (config.isExpressive && config.windowBackgroundMode.uppercase() == "SOLID") {
      val hex = config.solidBackgroundColor ?: config.backgroundColor ?: com.nexus.launcher.ui.folder.FolderAuroraTheme.BASE_BG
      val argb = try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor(com.nexus.launcher.ui.folder.FolderAuroraTheme.BASE_BG) }
      val solidWithAlpha = Color.argb(opInt, Color.red(argb), Color.green(argb), Color.blue(argb))
      layers += ColorDrawable(solidWithAlpha)
    } else if (!config.isExpressive) {
      val plateRgb = FolderIconSurfaceColor.windowPlateRgb(context, config)
      val surfaceAlpha = Color.argb(opInt, Color.red(plateRgb), Color.green(plateRgb), Color.blue(plateRgb))
      layers += ColorDrawable(surfaceAlpha)
    } else {
      if (config.windowBackgroundMode.uppercase() == "FROSTED") {
        configTintDrawable(context, config, forCard = true, opacity = op)?.let { layers += it }
      } else {
        layers += ColorDrawable(Color.argb((255 * op).toInt(), 0, 0, 0))
      }
    }
    return LayerDrawable(layers.toTypedArray())
  }

  fun buildGlassStack(
    context: Context,
    config: FolderConfig,
    width: Int,
    height: Int,
    screenX: Int = 0,
    screenY: Int = 0
  ): LayerDrawable = buildCardStack(context, config, width, height, screenX, screenY)

  fun buildSheetStack(
    context: Context,
    config: FolderConfig,
    width: Int,
    height: Int,
    screenX: Int = 0,
    screenY: Int = 0
  ): LayerDrawable {
    return buildGlassStack(context, config, width, height, screenX, screenY)
  }

  private fun captureWallpaper(
    context: Context,
    width: Int,
    height: Int,
    screenX: Int = 0,
    screenY: Int = 0
  ): Bitmap {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    HomeScreenFrameCache.drawWallpaperOnly(canvas, context, screenX, screenY, w, h)
    return bitmap
  }

  private fun configTintDrawable(
    context: Context,
    config: FolderConfig,
    forCard: Boolean = false,
    opacity: Float = 1f
  ): Drawable? {
    if (!config.isExpressive) return null
    val frostedAlpha = (255 * opacity).toInt().coerceIn(0, 255)
    val solidAlpha = (255 * opacity).toInt().coerceIn(0, 255)
    return when (config.windowBackgroundMode.uppercase()) {
      "FROSTED" -> {
        val idx = com.nexus.launcher.ui.dock.DockFrostedGradients.clampIndex(config.frostedGradientIndex)
        val preset = com.nexus.launcher.ui.dock.DockFrostedGradients.presets[idx]
        val density = context.resources.displayMetrics.density
        val cornerPx = 20f * density
        val start = Color.argb(
          frostedAlpha,
          Color.red(preset.startRgb),
          Color.green(preset.startRgb),
          Color.blue(preset.startRgb)
        )
        val end = Color.argb(
          frostedAlpha,
          Color.red(preset.endRgb),
          Color.green(preset.endRgb),
          Color.blue(preset.endRgb)
        )
        android.graphics.drawable.GradientDrawable(
          android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
          intArrayOf(start, end)
        ).apply {
          cornerRadius = cornerPx
          if (!forCard) {
            setStroke(
              (1f * density).toInt().coerceAtLeast(1),
              Color.parseColor(FolderAuroraTheme.GLASS_BORDER)
            )
          }
        }
      }
      "SOLID" -> {
        val hex = config.solidBackgroundColor ?: config.backgroundColor ?: FolderAuroraTheme.BASE_BG
        val argb = try {
          Color.parseColor(hex)
        } catch (_: Exception) {
          Color.parseColor(FolderAuroraTheme.BASE_BG)
        }
        ColorDrawable(Color.argb(solidAlpha, Color.red(argb), Color.green(argb), Color.blue(argb)))
      }
      else -> null
    }
  }
}
