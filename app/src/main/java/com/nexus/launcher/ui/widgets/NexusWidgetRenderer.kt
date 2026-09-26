package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.folder.FolderIconShapeDraw
import com.nexus.launcher.ui.glass.FrostedGlassEngine

abstract class NexusWidgetRenderer {

    /** Subclasses implement this to draw their specific content inside the widget bounds. */
    abstract fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    )

    fun getTypeface(context: Context, weight: Int = android.graphics.Typeface.NORMAL): android.graphics.Typeface {
        return com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, weight)
    }

    fun getTypeface(
        context: Context,
        config: NexusWidgetConfig.InstanceConfig?,
        weight: Int = android.graphics.Typeface.NORMAL
    ): android.graphics.Typeface {
        val fontKey = config?.fontFamily
        if (!fontKey.isNullOrEmpty() && fontKey != "default") {
            try {
                val family = com.nexus.launcher.typography.AppFontFamily.fromKey(fontKey)
                if (family.fontResId != null) {
                    val tf = androidx.core.content.res.ResourcesCompat.getFont(context, family.fontResId)
                    if (tf != null) return tf
                } else if (family.familyName != null) {
                    return android.graphics.Typeface.create(family.familyName, weight)
                }
            } catch (_: Exception) {}
        }
        return com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(context, weight)
    }

    fun getPaint(context: Context, slot: com.nexus.launcher.typography.NexusTypeSlot): android.text.TextPaint {
        return com.nexus.launcher.typography.CanvasTypographyHelper.getPaint(context, slot)
    }

    fun render(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        config: NexusWidgetConfig.InstanceConfig,
        minWidthDp: Int,
        minHeightDp: Int,
        progress: Float = -1f,
        targetBitmap: Bitmap? = null
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = if (targetBitmap != null && !targetBitmap.isRecycled && targetBitmap.width == w && targetBitmap.height == h) {
            targetBitmap.eraseColor(0)
            targetBitmap
        } else {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bmp)
        val dp = context.resources.displayMetrics.density
        // Folder squircle path uses r*0.4 because folder `r` is half the icon.
        // Widget `cornerRadius` is already the desired corner in dp — compensate so
        // the slider maps 1:1. Pill (11) ignores r and uses min(w,h)/2.
        val visualR = config.cornerRadius * dp
        val r = if (config.shapeStyle == 1) visualR / 0.4f else visualR

        val opacity = config.backgroundOpacity.coerceIn(0f, 1f)
        val alpha = (opacity * 255f).toInt().coerceIn(0, 255)
        // Embedded Mosaic children never draw their own independent plate — the Mosaic tile's
        // own cell backdrop (LivingMosaicGlassBackdropView / LivingMosaicGlass) is the ONLY
        // background layer for them, so every child visually matches the tile instead of each
        // carrying its own separate Glass/Neumorphic choice (see isMosaicEmbedded's doc).
        val isRetro = isRetroStyle(config)
        val retroSurface = if (isRetro && config.clockStyle > 0) {
            com.nexus.launcher.ui.widgets.music.RetroMusicConfig.read(context, config.appWidgetId).resolveEffectiveSurface()
        } else {
            com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.DEFAULT
        }
        val isRetroDefault = isRetro && retroSurface == com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.DEFAULT
        // Retro styles with DEFAULT surface render their own authentic chassis/palette.
        // Retro styles with FROSTED or NEUMORPHIC reuse the canonical Nexus surface plate.
        val showBackground = !isRetroDefault && opacity > 0.01f && !config.isMosaicEmbedded
        val showBorder = showBackground && !config.isBorderless

        // A circle's plate is a centred square, not the whole widget — see NexusWidgetShapeGeometry.
        val bounds = NexusWidgetShapeGeometry.plate(config.shapeStyle, w.toFloat(), h.toFloat())
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        // Standard widgets follow the canonical 3-way UI Style system (Frosted Glass /
        // Neumorphism / Default) via NexusWidgetConfig. Expressive gradient presets and Solid
        // color backgrounds remain dedicated overrides.
        val isNeumorphic = if (isRetro) {
            retroSurface == com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.NEUMORPHIC && !config.isBorderless
        } else {
            NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive) && !config.isBorderless
        }
        val margin = if (isNeumorphic) 6f * dp else 0f
        val cardBounds = if (isNeumorphic) {
            RectF(bounds.left + margin, bounds.top + margin, bounds.right - margin, bounds.bottom - margin)
        } else {
            bounds
        }
        val cardR = if (isNeumorphic) (r - margin).coerceAtLeast(4f * dp) else r

        val borderWidth = 1.25f * dp
        val inset = borderWidth / 2f
        val strokeBounds = RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (showBackground) {
            val isGlass = if (isRetro) {
                retroSurface == com.nexus.launcher.ui.widgets.music.RetroMusicConfig.SurfaceMode.FROSTED
            } else {
                NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
            }
            val isSolid = !isRetro && config.backgroundMode == NexusWidgetConfig.BG_SOLID
            val refr = config.glassRefraction.coerceIn(0f, 1f)
            val op = opacity.coerceIn(0f, 1f)

            val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)

            // Opacity slider controls frost density/plate substance; Glass Refraction trades
            // it for background bleed (transmittance) — see FrostedGlassEngine.frostFillAlpha,
            // the same formula folders and boxes use so all three respond identically.
            //
            // The coerceAtLeast floor below matters specifically for plain widgets (weather,
            // clock, agenda, ...): removing the corner-sheen/border-rim highlight (per explicit
            // request — it read as a distracting directional "glass shadow") also removed most
            // of what made the raw frostFillAlpha value read as "frosted" rather than "just
            // transparent." AppBox/ShortcutBox don't need this floor — their default opacity is
            // already higher (0.82 vs a plain widget's 0.65 reset default) and their slot-well
            // chrome adds its own visual substance on top of the plain tint. A large, mostly
            // empty widget card has nothing else to lean on, so it needs the tint itself to
            // carry more weight.
            // No floor. A 0.55 minimum was added so a plain widget would still read as frosted
            // once its rim highlight was removed — but it pinned the tint across most of the
            // slider, so dragging Opacity down barely changed anything, and the widget never got
            // lighter than frosted. Mosaics and folders use frostFillAlpha as-is; widgets now
            // match them, so one slider behaves the same everywhere.
            val effectiveAlpha = if (isGlass) FrostedGlassEngine.frostFillAlpha(op, refr) else op
            val fillAlpha = (effectiveAlpha * 255f).toInt().coerceIn(0, 255)

            if (config.isExpressive) {
                if (config.backgroundMode == NexusWidgetConfig.BG_FROSTED) {
                    val presetIndex = config.frostedGradientIndex.coerceIn(0, DockFrostedGradients.presets.size - 1)
                    val preset = DockFrostedGradients.presets[presetIndex]
                    val shader = android.graphics.LinearGradient(
                        0f, 0f, w.toFloat(), 0f,
                        Color.argb(fillAlpha, Color.red(preset.startRgb), Color.green(preset.startRgb), Color.blue(preset.startRgb)),
                        Color.argb(fillAlpha, Color.red(preset.endRgb), Color.green(preset.endRgb), Color.blue(preset.endRgb)),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    bgPaint.shader = shader
                } else {
                    val base = Color.parseColor(NexusDesignSystem.COLOR_BASE)
                    bgPaint.shader = null
                    bgPaint.color = Color.argb(fillAlpha, Color.red(base), Color.green(base), Color.blue(base))
                }
            } else {
                // Glass and Solid modes use uniform theme surface token (matching Soft UI)
                val base = if (isGlass || isSolid) frostedTokens.surface else tokens.surface
                bgPaint.shader = null
                bgPaint.color = Color.argb(fillAlpha, Color.red(base), Color.green(base), Color.blue(base))
            }

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = borderWidth
                if (config.isExpressive) {
                    color = Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
                } else if (isGlass) {
                    val borderAlpha = (Color.alpha(frostedTokens.border) * (0.5f + 0.5f * op)).toInt().coerceIn(16, 255)
                    color = Color.argb(borderAlpha, Color.red(frostedTokens.border), Color.green(frostedTokens.border), Color.blue(frostedTokens.border))
                } else {
                    color = tokens.divider
                }
            }

            if (isNeumorphic) {
                val palette = NexusNeumorphicDraw.resolvePalette(context, config.themeMode)
                if (FrostedGlassEngine.isDefaultFlatStyleEnabled) {
                    NexusNeumorphicDraw.drawFlatSurface(canvas, cardBounds, cardR, config.shapeStyle, palette, dp)
                } else {
                    NexusNeumorphicDraw.drawRaisedSurface(canvas, cardBounds, cardR, config.shapeStyle, palette, dp)
                }
            } else {
                val bgPath = FolderIconShapeDraw.getShapePath(bounds, r, config.shapeStyle)

                // Glass mode's wallpaper blur is drawn live by WidgetGlassLiveBackdropView, a
                // sibling behind this widget's host view (see AppWidgetOverlayBinder /
                // WidgetOverlayLayoutParams) — not baked in here. A widget's own bitmap is
                // static (an AppWidgetProvider can't host a live View), so it can't itself track
                // the wallpaper as the widget's screen position moves during a page swipe; only
                // a live sibling redrawn on scroll can. This bitmap draws only the tint/sheen/
                // border on top of whatever the live sibling shows through.
                canvas.drawPath(bgPath, bgPaint)

                if (showBorder) {
                    val strokePath = FolderIconShapeDraw.getShapePath(strokeBounds, (r - inset).coerceAtLeast(0f), config.shapeStyle)
                    canvas.drawPath(strokePath, strokePaint)
                }
            }
        }

        if (progress >= 0f && !isRetro && shouldDrawOuterProgressRing(config, w, h, dp)) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * dp
                color = if (isNeumorphic) {
                    NexusNeumorphicDraw.resolvePalette(context, config.themeMode).textPrimary
                } else {
                    val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
                    tokens.textPrimary
                }
                strokeCap = Paint.Cap.ROUND
            }

            val sweepAngle = 360f * progress.coerceIn(0f, 1f)
            val path = FolderIconShapeDraw.getShapePath(strokeBounds, r - inset, config.shapeStyle)
            
            val pathMeasure = android.graphics.PathMeasure(path, false)
            val pathLength = pathMeasure.length
            val drawLength = pathLength * (sweepAngle / 360f)
            
            val progressPath = android.graphics.Path()
            pathMeasure.getSegment(0f, drawLength, progressPath, true)
            canvas.drawPath(progressPath, progressPaint)
        }

        val size = NexusWidgetSizeHelper.resolve(minWidthDp, minHeightDp)
        
        // Clip content to rounded corners so drawing doesn't spill over
        canvas.save()
        val clipPath = FolderIconShapeDraw.getShapePath(cardBounds, cardR, config.shapeStyle)
        canvas.clipPath(clipPath)
        
        drawContent(context, canvas, w, h, size, config)
        
        canvas.restore()
        return bmp
    }

    open fun isRetroStyle(config: NexusWidgetConfig.InstanceConfig): Boolean = false

    open fun shouldDrawOuterProgressRing(config: NexusWidgetConfig.InstanceConfig, w: Int, h: Int, dp: Float): Boolean = true
}

