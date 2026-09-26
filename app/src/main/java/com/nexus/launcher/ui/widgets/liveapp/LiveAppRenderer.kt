package com.nexus.launcher.ui.widgets.liveapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver

/**
 * High performance Canvas renderer for the Live App Widget container.
 * Zero Paint/Path allocation inside onDraw().
 */
class LiveAppRenderer(private val context: Context) {

    private val dp = context.resources.displayMetrics.density

    // Pre-allocated Paints
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1f * dp).coerceAtLeast(1f)
    }
    private val wellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val wellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1f * dp).coerceAtLeast(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * dp
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * dp
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
    }
    private val liveDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Pre-allocated geometry
    private val boundsRect = RectF()
    private val slotRect = RectF()
    private val discBounds = RectF()
    private val iconBounds = Rect()
    private val drawGrid = com.nexus.launcher.ui.widgets.BoxSlotGrid()
    private val hitGrid = com.nexus.launcher.ui.widgets.BoxSlotGrid()
    private val clipPath = Path()

    // Wallpaper blur cache
    private var cachedBlurredBmp: Bitmap? = null
    private var cachedScreenX = Int.MIN_VALUE
    private var cachedScreenY = Int.MIN_VALUE
    private var cachedW = -1
    private var cachedH = -1
    private var cachedRefraction = -1f
    private val screenLoc = IntArray(2)

    private var blurRenderNode: android.graphics.RenderNode? = null
    private var blurRadiusPx = 60f
    private var lastRecordedW = -1
    private var lastRecordedH = -1
    private var lastScreenX = Int.MIN_VALUE
    private var lastScreenY = Int.MIN_VALUE
    private var lastBackdropVersion = -1L

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode = android.graphics.RenderNode("LiveAppBlurNode").apply {
                setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        blurRadiusPx, blurRadiusPx, android.graphics.Shader.TileMode.CLAMP
                    )
                )
            }
        }
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        config: LiveAppConfig,
        apps: List<LiveAppEntry>,
        hasUsageAccess: Boolean = true,
        isHoverGlow: Boolean = false,
        hostView: android.view.View? = null
    ) {
        if (width <= 0f || height <= 0f) return
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)

        val cornerRadius = 24f * dp

        boundsRect.set(0f, 0f, width, height)

        // 1. Wallpaper blur behind plate
        if (isGlass && hostView != null) {
            hostView.getLocationOnScreen(screenLoc)
            val w = width.toInt().coerceAtLeast(1)
            val h = height.toInt().coerceAtLeast(1)
            val node = blurRenderNode
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
                val radius = FrostedGlassEngine.glassBlurRadius(config.surfaceOpacity, config.glassRefraction)
                if (radius != blurRadiusPx) {
                    blurRadiusPx = radius
                    node.setRenderEffect(
                        android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.CLAMP)
                    )
                }
                val currentVersion = com.nexus.launcher.ui.folder.HomeScreenFrameCache.getBackdropVersion()
                if (w != lastRecordedW || h != lastRecordedH || screenLoc[0] != lastScreenX || screenLoc[1] != lastScreenY ||
                    currentVersion != lastBackdropVersion || !node.hasDisplayList()
                ) {
                    lastRecordedW = w
                    lastRecordedH = h
                    lastScreenX = screenLoc[0]
                    lastScreenY = screenLoc[1]
                    lastBackdropVersion = currentVersion
                    node.setPosition(0, 0, w, h)
                    val nodeCanvas = node.beginRecording()
                    com.nexus.launcher.ui.folder.HomeScreenFrameCache.drawWallpaperOnly(nodeCanvas, context, screenLoc[0], screenLoc[1], w, h)
                    node.endRecording()
                }
                canvas.save()
                clipPath.rewind()
                clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW)
                canvas.clipPath(clipPath)
                canvas.drawRenderNode(node)
                canvas.restore()
            } else {
                if (screenLoc[0] != cachedScreenX || screenLoc[1] != cachedScreenY || w != cachedW || h != cachedH ||
                    config.glassRefraction != cachedRefraction || cachedBlurredBmp == null
                ) {
                    val bmp = try {
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    } catch (_: OutOfMemoryError) {
                        null
                    }
                    if (bmp != null) {
                        val divisor = (16 - (9 * config.glassRefraction.coerceIn(0f, 1f))).toInt().coerceAtLeast(5)
                        val ok = FrostedGlassEngine.drawBlurredWallpaper(Canvas(bmp), context, screenLoc[0], screenLoc[1], w, h, divisor)
                        if (ok) {
                            cachedBlurredBmp?.recycle()
                            cachedBlurredBmp = bmp
                            cachedScreenX = screenLoc[0]
                            cachedScreenY = screenLoc[1]
                            cachedW = w
                            cachedH = h
                            cachedRefraction = config.glassRefraction
                        }
                    }
                }
                cachedBlurredBmp?.let { bmp ->
                    canvas.save()
                    clipPath.rewind()
                    clipPath.addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW)
                    canvas.clipPath(clipPath)
                    canvas.drawBitmap(bmp, 0f, 0f, null)
                    canvas.restore()
                }
            }
        }

        // 2. Draw outer plate background
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive) && !config.isFlushBorder
        val margin = if (isNeumorphic) 6f * dp else 0f
        val cardBounds = if (isNeumorphic) RectF(margin, margin, width - margin, height - margin) else boundsRect
        val cardR = if (isNeumorphic) (cornerRadius - margin).coerceAtLeast(4f * dp) else cornerRadius

        val alphaInt = (config.surfaceOpacity * 255).toInt().coerceIn(0, 255)
        if (config.isExpressive) {
            val preset = com.nexus.launcher.ui.dock.DockFrostedGradients.presets[
                com.nexus.launcher.ui.dock.DockFrostedGradients.clampIndex(config.frostedGradientIndex)
            ]
            val sColor = (preset.startRgb and 0x00FFFFFF) or (alphaInt shl 24)
            val eColor = (preset.endRgb and 0x00FFFFFF) or (alphaInt shl 24)
            platePaint.shader = android.graphics.LinearGradient(
                0f, 0f, width, height, sColor, eColor, android.graphics.Shader.TileMode.CLAMP
            )
        } else {
            val surfaceBase = when (config.backgroundMode) {
                NexusWidgetConfig.BG_SOLID -> (tokens.surface or 0xFF000000.toInt())
                else -> (frostedTokens.surface or 0xFF000000.toInt())
            }
            val tintAlpha = if (isGlass) {
                (FrostedGlassEngine.frostFillAlpha(config.surfaceOpacity, config.glassRefraction) * 255f).toInt()
            } else {
                alphaInt
            }
            platePaint.shader = null
            platePaint.color = (surfaceBase and 0x00FFFFFF) or (tintAlpha shl 24)
        }
        strokePaint.color = if (isGlass) frostedTokens.border else tokens.divider

        val palette = if (isNeumorphic) com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(tokens) else null
        if (isNeumorphic && palette != null) {
            if (FrostedGlassEngine.isDefaultFlatStyleEnabled) {
                com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawFlatSurface(canvas, cardBounds, cardR, 1, palette, dp)
            } else {
                com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawRaisedSurface(canvas, cardBounds, cardR, 1, palette, dp)
            }
        } else {
            canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, platePaint)
            if (!config.isFlushBorder) {
                canvas.drawRoundRect(boundsRect, cornerRadius, cornerRadius, strokePaint)
            }
        }

        if (isHoverGlow) {
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 3.5f * dp
            glowPaint.color = tokens.accent
            canvas.drawRoundRect(cardBounds, cardR, cardR, glowPaint)

            glowPaint.style = Paint.Style.FILL
            glowPaint.color = ColorUtils.setAlphaComponent(tokens.accent, 0x22)
            canvas.drawRoundRect(cardBounds, cardR, cardR, glowPaint)
        }

        // 3. Draw slots
        val cols = config.gridCols.coerceIn(1, 9)
        val rows = config.gridRows.coerceIn(1, 9)
        val grid = drawGrid.layout(width, height, cols, rows, config.iconSpacing, dp)
        val slotH = grid.slotH
        // Neumorphic lifts each app on a raised disc; Default and Frosted show the icon alone.
        val raisedDisc = isNeumorphic && palette != null && !FrostedGlassEngine.isDefaultFlatStyleEnabled

        wellBgPaint.color = if (config.backgroundMode == NexusWidgetConfig.BG_SOLID) {
            tokens.surfaceRaised
        } else {
            ColorUtils.setAlphaComponent(frostedTokens.surfaceRaised, 0x4D)
        }
        wellStrokePaint.color = if (isGlass) frostedTokens.border else tokens.divider
        textPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
        hintPaint.color = tokens.textSecondary
        liveDotPaint.color = tokens.accent

        // If no apps and permission not granted, show permission prompt in center
        if (apps.isEmpty() && !hasUsageAccess) {
            val hintText = context.getString(R.string.live_apps_permission_hint)
            canvas.drawText(hintText, width / 2f, height / 2f + 4f * dp, hintPaint)
            return
        }

        val maxSlots = minOf(cols * rows, LiveAppConfig.MAX_ICONS)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val slotIndex = r * cols + c
                grid.slotRect(c, r, slotRect)

                val showLabelsEffective = config.showLabels && slotH >= 36f * dp
                val discDiameter = grid.discDiameter(showLabelsEffective)
                val discRadius = discDiameter / 2f
                val cx = slotRect.centerX()
                val cy = if (showLabelsEffective) (slotRect.top + (slotH - 12f * dp) / 2f) else slotRect.centerY()
                discBounds.set(cx - discRadius, cy - discRadius, cx + discRadius, cy + discRadius)

                val slotPath = com.nexus.launcher.ui.folder.FolderIconShapeDraw.getShapePath(discBounds, discRadius, 1)

                val app = if (slotIndex < maxSlots && slotIndex < apps.size) apps[slotIndex] else null
                if (app == null) {
                    // Empty slot: Tactile debossed squircle well (no plus icon)
                    if (isNeumorphic && palette != null) {
                        com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawDebossedWell(canvas, discBounds, discRadius, palette, dp)
                    } else {
                        canvas.drawPath(slotPath, wellBgPaint)
                        if (!config.isFlushBorder) {
                            canvas.drawPath(slotPath, wellStrokePaint)
                        }
                    }
                } else {
                    if (raisedDisc && palette != null) {
                        com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawRaisedSurface(canvas, discBounds, discRadius, 1, palette, dp)
                    }

                    val icon = app.icon
                    if (icon != null) {
                        val iconScale = if (config.showLabels) 0.74f else (if (raisedDisc) 0.80f else 0.88f)
                        val iconSize = (discDiameter * iconScale).toInt().coerceAtLeast(1)
                        val iconLeft = (cx - iconSize / 2f).toInt()
                        val iconTop = (cy - iconSize / 2f).toInt()
                        iconBounds.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                        icon.bounds = iconBounds
                        icon.draw(canvas)
                    }

                    if (app.isForegroundService) {
                        val dotRadius = 3f * dp
                        val dotCx = discBounds.right - 5f * dp
                        val dotCy = discBounds.top + 5f * dp
                        canvas.drawCircle(dotCx, dotCy, dotRadius, liveDotPaint)
                    }

                    if (showLabelsEffective && app.label.isNotEmpty()) {
                        textPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
                        val textY = slotRect.bottom - 2f * dp
                        canvas.drawText(app.label, slotRect.centerX(), textY, textPaint)
                    }
                }
            }
        }
    }

    fun getSlotAt(
        touchX: Float,
        touchY: Float,
        width: Float,
        height: Float,
        config: LiveAppConfig
    ): Int? {
        val cols = config.gridCols.coerceIn(1, 9)
        val rows = config.gridRows.coerceIn(1, 9)
        return hitGrid.layout(width, height, cols, rows, config.iconSpacing, dp).slotAt(touchX, touchY)
    }
}
