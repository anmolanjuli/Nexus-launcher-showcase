package com.nexus.launcher.ui.widgets.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize

/**
 * Bitmap rendering engine for the First-Party Progress Bar Widget.
 * Adapts dynamically to 2x1, 2x2, and 4x2 grid sizes.
 */
class NexusProgressRenderer : NexusWidgetRenderer() {

    private val drawBars = ProgressDrawBars()

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)

        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val palette = if (isNeumorphic) NexusNeumorphicDraw.resolvePalette(tokens) else null
        val accentColor = try {
            Color.parseColor(config.accentColor)
        } catch (_: Exception) {
            tokens.accent
        }

        val rawTracks = ProgressDataStore.loadTracks(context, config.appWidgetId)
        val now = System.currentTimeMillis()
        val calculated = rawTracks.map { ProgressPresets.calculate(context, it, now) }

        val pad = (8f * dp).coerceIn(6f * dp, 12f * dp)
        val contentW = width - pad * 2f
        val contentH = height - pad * 2f

        val isSmall1Track = height < (90 * dp) || calculated.size == 1
        val slotCount = if (isSmall1Track) 1 else if (height < (160 * dp)) calculated.size.coerceAtMost(2) else calculated.size.coerceAtMost(3)

        if (slotCount == 1) {
            val track = calculated.firstOrNull() ?: return
            drawBars.drawTrackSlot(
                context, canvas, pad, pad, contentW, contentH,
                track, tokens, palette, isNeumorphic, dp,
                showSubtitle = true,
                isHeroLayout = true
            )
            return
        }

        val gap = (6f * dp).coerceAtMost(contentH * 0.04f)
        val slotH = (contentH - gap * (slotCount - 1)) / slotCount
        val showSubtitle = height >= (180 * dp)

        var curY = pad
        for (i in 0 until slotCount) {
            val track = calculated.getOrNull(i) ?: break
            drawBars.drawTrackSlot(
                context, canvas, pad, curY, contentW, slotH,
                track, tokens, palette, isNeumorphic, dp,
                showSubtitle = showSubtitle,
                isHeroLayout = false
            )
            curY += slotH + gap
        }
    }
}
