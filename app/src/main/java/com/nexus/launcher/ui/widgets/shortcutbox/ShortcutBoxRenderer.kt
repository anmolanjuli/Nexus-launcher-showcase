package com.nexus.launcher.ui.widgets.shortcutbox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.withClip
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * High performance Canvas renderer for Shortcut / App Box container.
 * Zero Paint/Path allocation inside onDraw().
 */
class ShortcutBoxRenderer(private val context: Context) {

    private val dp = context.resources.displayMetrics.density

    // Pre-allocated Paints
    private val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1f * dp).coerceAtLeast(1f)
    }
    private val wellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wellStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = (1f * dp).coerceAtLeast(1f)
    }
    private val plusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * dp
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 11f * dp
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dp
    }
    private val buttonDraw = ShortcutBoxButtonDraw(dp)

    // Pre-allocated geometry structures
    private val boundsRect = RectF()
    private val slotRect = RectF()
    private val discBounds = RectF()
    private val iconBounds = Rect()
    private val drawGrid = com.nexus.launcher.ui.widgets.BoxSlotGrid()
    private val hitGrid = com.nexus.launcher.ui.widgets.BoxSlotGrid()
    private val path = Path()

    // Blurred-wallpaper cache — ShortcutBoxView redraws on every onDraw, so this must not
    // allocate a fresh downscale/blur bitmap per frame; only recompute when position/size move.
    // Bitmap downscale-blur is the pre-API31 fallback only; the primary path below is a real
    // RenderNode GPU Gaussian blur (same technique as WidgetGlassLiveBackdropView) so box
    // widgets match real widgets' blur strength exactly instead of approximating it.
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            blurRenderNode = android.graphics.RenderNode("ShortcutBoxBlurNode").apply {
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
        spanX: Int,
        spanY: Int,
        config: ShortcutBoxConfig,
        membersBySlot: Map<Int, HomeScreenItem>,
        iconsBySlot: Map<Int, Drawable?>,
        labelsBySlot: Map<Int, String>,
        hiddenSlot: Int? = null,
        isHoverGlow: Boolean = false,
        hostView: android.view.View? = null
    ) {
        if (width <= 0f || height <= 0f) return
        val tokens = com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val isGlass = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)

        val cornerRadius = 24f * dp

        // 1. Draw outer plate background
        boundsRect.set(0f, 0f, width, height)

        // Blurred wallpaper behind the tint, matching widgets/folders — this is a live View
        // (unlike a widget's baked bitmap) so its screen position is always known directly.
        if (isGlass && hostView != null) {
            hostView.getLocationOnScreen(screenLoc)
            val w = width.toInt().coerceAtLeast(1)
            val h = height.toInt().coerceAtLeast(1)
            val node = blurRenderNode
            if (node != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && canvas.isHardwareAccelerated) {
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
                canvas.clipPath(
                    Path().apply {
                        addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW)
                    }
                )
                canvas.drawRenderNode(node)
                canvas.restore()
            } else {
                // Pre-API31 fallback: no RenderNode/RenderEffect available.
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
                    canvas.clipPath(
                        Path().apply {
                            addRoundRect(boundsRect, cornerRadius, cornerRadius, Path.Direction.CW)
                        }
                    )
                    canvas.drawBitmap(bmp, 0f, 0f, null)
                    canvas.restore()
                }
            }
        }
        val isNeumorphic = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive) &&
            !config.isFlushBorder
        val margin = if (isNeumorphic) 6f * dp else 0f
        val cardBounds = if (isNeumorphic) android.graphics.RectF(margin, margin, width - margin, height - margin) else boundsRect
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
                com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_SOLID -> (tokens.surface or 0xFF000000.toInt())
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
            glowPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.accent, 0x22)
            canvas.drawRoundRect(cardBounds, cardR, cardR, glowPaint)
        }

        // 2. Draw slots
        val cols = config.gridCols.coerceIn(1, 6)
        val rows = config.gridRows.coerceIn(1, 6)
        val grid = drawGrid.layout(width, height, cols, rows, config.iconSpacing, dp)
        val slotH = grid.slotH
        // Neumorphic lifts each shortcut on a raised disc; Default and Frosted do not.
        val raisedDisc = isNeumorphic && palette != null && !FrostedGlassEngine.isDefaultFlatStyleEnabled

        wellBgPaint.color = if (config.backgroundMode == com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_SOLID) {
            tokens.surfaceRaised
        } else {
            androidx.core.graphics.ColorUtils.setAlphaComponent(frostedTokens.surfaceRaised, 0x4D)
        }
        wellStrokePaint.color = if (isGlass) frostedTokens.border else tokens.divider
        plusPaint.color = if (isNeumorphic && palette != null) palette.textSecondary else tokens.textSecondary
        textPaint.color = if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val slotIndex = r * cols + c
                grid.slotRect(c, r, slotRect)

                val discDiameter = grid.discDiameter(config.showLabels)
                val discRadius = discDiameter / 2f
                val cx = slotRect.centerX()
                val cy = if (config.showLabels) (slotRect.top + (slotH - 12f * dp) / 2f) else slotRect.centerY()
                discBounds.set(cx - discRadius, cy - discRadius, cx + discRadius, cy + discRadius)

                val slotPath = com.nexus.launcher.ui.folder.FolderIconShapeDraw.getShapePath(discBounds, discRadius, 1)
                val member = membersBySlot[slotIndex]
                if (member == null || slotIndex == hiddenSlot) {
                    // Empty or ghosted slot: Draw tactile debossed squircle well
                    if (isNeumorphic && palette != null) {
                        com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawDebossedWell(canvas, discBounds, discRadius, palette, dp)
                    } else {
                        canvas.drawPath(slotPath, wellBgPaint)
                        if (!config.isFlushBorder) {
                            canvas.drawPath(slotPath, wellStrokePaint)
                        }
                    }

                    if (member == null) {
                        // Plus icon in the center of the well
                        val plusHalf = (discRadius * 0.32f).coerceIn(4f * dp, 8f * dp)
                        canvas.drawLine(cx - plusHalf, cy, cx + plusHalf, cy, plusPaint)
                        canvas.drawLine(cx, cy - plusHalf, cx, cy + plusHalf, plusPaint)
                    }
                } else {
                    // Filled slot: Draw raised squircle button
                    val shortcutId = try { org.json.JSONObject(member.folderConfigJson).optString("shortcutId") } catch (_: Exception) { null }
                    val isBuiltin = member.packageName == context.packageName || NexusBuiltinShortcuts.isBuiltin(shortcutId)
                    val isActive = isBuiltin && NexusShortcutActions.isShortcutActive(context, shortcutId)
                    val activeAccent = if (isActive) NexusShortcutActions.getActiveAccentColor(context, shortcutId) else null

                    if (raisedDisc && palette != null) {
                        com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawRaisedSurface(canvas, discBounds, discRadius, 1, palette, dp)
                    } else if (isBuiltin) {
                        // App shortcuts bring their own icon background; a built-in glyph does not.
                        // Default draws that plate without an outline, like its app slots.
                        buttonDraw.drawPlate(canvas, slotPath, isGlass, config.isFlushBorder || isNeumorphic, tokens, frostedTokens)
                    }

                    if (isActive && activeAccent != null) {
                        buttonDraw.drawActiveGlow(canvas, slotPath, activeAccent)
                    }

                    val icon = iconsBySlot[slotIndex]
                    if (icon != null) {
                        val iconScale = if (raisedDisc) 0.54f else 0.62f
                        val iconSize = (discDiameter * iconScale).toInt()
                        val iconLeft = (cx - iconSize / 2f).toInt()
                        val iconTop = (cy - iconSize / 2f).toInt()
                        iconBounds.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                        icon.bounds = iconBounds

                        if (isBuiltin) {
                            icon.setTint(
                                buttonDraw.glyphTint(
                                    activeAccent = if (isActive) activeAccent else null,
                                    neumorphicSecondary = if (isNeumorphic) palette?.textSecondary else null,
                                    tokens = tokens,
                                ),
                            )
                        }
                        icon.draw(canvas)
                    }

                    // Optional label
                    if (config.showLabels) {
                        val label = labelsBySlot[slotIndex].orEmpty()
                        if (label.isNotEmpty()) {
                            textPaint.color = if (isActive && activeAccent != null) activeAccent else if (isNeumorphic && palette != null) palette.textPrimary else tokens.textPrimary
                            val textY = slotRect.bottom - 2f * dp
                            canvas.drawText(label, slotRect.centerX(), textY, textPaint)
                        }
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
        config: ShortcutBoxConfig
    ): Int? {
        val cols = config.gridCols.coerceIn(1, 6)
        val rows = config.gridRows.coerceIn(1, 6)
        return hitGrid.layout(width, height, cols, rows, config.iconSpacing, dp).slotAt(touchX, touchY)
    }
}
