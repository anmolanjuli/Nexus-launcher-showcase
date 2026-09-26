package com.nexus.launcher.ui.dock.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.dock.DockFrostedGradients
import com.nexus.launcher.ui.dock.DockSlotLayout
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw

/** Live interactive preview of the dock rendered inside the Dock Settings Sheet. */
class DockSettingsPreviewView(context: Context) : View(context) {

    var pendingSettings: PendingDockSettings? = null
        set(value) {
            field = value?.copy()
            invalidate()
        }

    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private val pillRect = RectF()
    private val iconRect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val sampleIconDrawables = mutableListOf<Drawable>()

    init {
        val iconResIds = listOf(
            R.drawable.ic_search,
            R.drawable.ic_apps,
            R.drawable.ic_camera,
            R.drawable.ic_settings,
            R.drawable.ic_grid,
            R.drawable.ic_category,
            R.drawable.ic_folder_solid,
            R.drawable.ic_bell,
            R.drawable.ic_info,
            R.drawable.ic_page
        )
        for (res in iconResIds) {
            ContextCompat.getDrawable(context, res)?.mutate()?.let {
                sampleIconDrawables.add(it)
            }
        }
    }

    fun updateTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        invalidate()
    }

    fun update(settings: PendingDockSettings) {
        pendingSettings = settings
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val dp = resources.displayMetrics.density
        currentTokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        // 1. Draw outer preview card container
        val isLight = currentTokens === NexusColorTokens.Light ||
            (Color.red(currentTokens.surface) > 200 && Color.green(currentTokens.surface) > 200)
        bgPaint.shader = null
        bgPaint.color = if (isLight) currentTokens.surfaceRaised else currentTokens.surface
        val cardRadius = 16f * dp
        pillRect.set(0f, 0f, w, h)
        canvas.drawRoundRect(pillRect, cardRadius, cardRadius, bgPaint)

        borderPaint.color = currentTokens.divider
        borderPaint.strokeWidth = 1f * dp
        canvas.drawRoundRect(pillRect, cardRadius, cardRadius, borderPaint)

        val settings = pendingSettings ?: return
        val maxIcons = settings.maxIcons.coerceIn(1, 10)
        val showLabels = settings.showLabels
        val searchEnabled = settings.searchInDock

        // 2. Compute miniature dock dimensions at 1:1 actual screen scale
        val maxPreviewPillW = w - (24f * dp)
        val maxPreviewPillH = h - (16f * dp)

        val targetPillH = (settings.dockHeightDp * dp).coerceAtMost(maxPreviewPillH)
        val naturalSlotW = (settings.iconSizeDp + 16f) * dp
        val slotWidth = (maxPreviewPillW / maxIcons).coerceAtMost(naturalSlotW)
        val totalOccupiedW = slotWidth * maxIcons
        val targetIconSize = DockSlotLayout.dynamicIconSizePx(
            slotWidth, dp, targetPillH, showLabels, settings.labelFontSizeSp
        )
        val pillRadius = (settings.cornerRadiusDp * dp).coerceAtMost(targetPillH / 2f)

        val pillLeft = (w - totalOccupiedW) / 2f
        val pillTop = (h - targetPillH) / 2f
        val pillRight = pillLeft + totalOccupiedW
        val pillBottom = pillTop + targetPillH
        pillRect.set(pillLeft, pillTop, pillRight, pillBottom)

        // 3. Draw Preview Dock Pill
        drawPreviewPill(canvas, settings, pillRadius, dp)

        // 4. Draw Preview Icons
        labelPaint.color = currentTokens.textSecondary
        labelPaint.textSize = settings.labelFontSizeSp.toFloat() * dp

        val isNeumorphic = settings.backgroundMode == DockBackgroundMode.NEUMORPHIC
        val palette = if (isNeumorphic) NexusNeumorphicDraw.resolvePalette(currentTokens) else null

        val sampleLabels = listOf(
            com.nexus.launcher.R.string.preview_app_search, com.nexus.launcher.R.string.preview_app_phone, com.nexus.launcher.R.string.preview_app_messages,
            com.nexus.launcher.R.string.preview_app_camera, com.nexus.launcher.R.string.preview_app_photos, com.nexus.launcher.R.string.preview_app_browser,
            com.nexus.launcher.R.string.preview_app_music, com.nexus.launcher.R.string.preview_app_files, com.nexus.launcher.R.string.preview_app_clock,
            com.nexus.launcher.R.string.preview_app_settings,
        ).map { context.getString(it) }

        val labelAreaH = if (showLabels) (settings.labelFontSizeSp + 4f) * dp else 0f
        val availableIconAreaH = targetPillH - labelAreaH

        for (i in 0 until maxIcons) {
            val cx = pillLeft + (i * slotWidth) + (slotWidth / 2f)
            val cy = pillTop + availableIconAreaH / 2f

            val iconRadius = targetIconSize / 2f
            iconRect.set(cx - iconRadius, cy - iconRadius, cx + iconRadius, cy + iconRadius)

            if (isNeumorphic && palette != null) {
                NexusNeumorphicDraw.drawRaisedSurface(
                    canvas = canvas,
                    bounds = iconRect,
                    radius = iconRadius,
                    shapeStyle = 0,
                    palette = palette,
                    dp = dp
                )
            } else {
                bgPaint.shader = null
                bgPaint.color = Color.argb(40, 255, 255, 255)
                canvas.drawCircle(cx, cy, iconRadius, bgPaint)
            }

            val drawable = if (i == 0 && searchEnabled) {
                sampleIconDrawables.getOrNull(0)
            } else {
                val idx = if (searchEnabled) i else (i + 1)
                sampleIconDrawables.getOrNull(idx % sampleIconDrawables.size)
            }

            drawable?.let { d ->
                DrawableCompat.setTint(d, currentTokens.textPrimary)
                val inset = targetIconSize * 0.22f
                d.setBounds(
                    (cx - iconRadius + inset).toInt(),
                    (cy - iconRadius + inset).toInt(),
                    (cx + iconRadius - inset).toInt(),
                    (cy + iconRadius - inset).toInt()
                )
                d.draw(canvas)
            }

            if (showLabels) {
                val labelText = if (i == 0 && searchEnabled) sampleLabels[0] else sampleLabels.getOrElse(i) { context.getString(com.nexus.launcher.R.string.preview_app_app) }
                val labelY = cy + iconRadius + (6f * dp)
                canvas.drawText(labelText, cx, labelY, labelPaint)
            }
        }
    }

    private fun drawPreviewPill(canvas: Canvas, settings: PendingDockSettings, radius: Float, dp: Float) {
        val opacity = settings.dockBackgroundOpacity
        val alphaInt = (opacity * 255).toInt().coerceIn(0, 255)

        when (settings.backgroundMode) {
            DockBackgroundMode.NEUMORPHIC -> {
                val palette = NexusNeumorphicDraw.resolvePalette(currentTokens)
                NexusNeumorphicDraw.drawRaisedSurface(
                    canvas = canvas,
                    bounds = pillRect,
                    radius = radius,
                    shapeStyle = 1,
                    palette = palette,
                    dp = dp * 0.7f
                )
            }
            DockBackgroundMode.FROSTED -> {
                val preset = DockFrostedGradients.presets[
                    DockFrostedGradients.clampIndex(settings.frostedGradientIndex)
                ]
                val sColor = (preset.startRgb and 0x00FFFFFF) or (alphaInt shl 24)
                val eColor = (preset.endRgb and 0x00FFFFFF) or (alphaInt shl 24)
                bgPaint.shader = LinearGradient(
                    pillRect.left, pillRect.top, pillRect.right, pillRect.top,
                    sColor, eColor, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(pillRect, radius, radius, bgPaint)
            }
            DockBackgroundMode.SOLID -> {
                val base = if (settings.solidColorArgb != DockSettingsRepository.DEFAULT_SOLID_COLOR_ARGB) {
                    settings.solidColorArgb
                } else {
                    currentTokens.surface
                }
                bgPaint.shader = null
                bgPaint.color = (base and 0x00FFFFFF) or (alphaInt shl 24)
                canvas.drawRoundRect(pillRect, radius, radius, bgPaint)
            }
            DockBackgroundMode.TRANSPARENT -> {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(currentTokens)
                val tintAlpha = (opacity * 200).toInt().coerceIn(0, 255)
                val c1 = (tintAlpha shl 24) or (frostedTokens.surface and 0x00FFFFFF)
                val c2 = (tintAlpha shl 24) or (frostedTokens.surfaceRaised and 0x00FFFFFF)
                bgPaint.shader = LinearGradient(
                    pillRect.left, pillRect.top, pillRect.left, pillRect.bottom,
                    c1, c2, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(pillRect, radius, radius, bgPaint)

                borderPaint.color = frostedTokens.border
                borderPaint.strokeWidth = 1f * dp
                canvas.drawRoundRect(pillRect, radius, radius, borderPaint)
            }
        }
    }
}
