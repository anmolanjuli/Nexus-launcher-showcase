package com.nexus.launcher.search.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.WidgetSize

class NexusSearchWidgetRenderer : NexusWidgetRenderer() {

    private val nPath = PathParser.createPathFromPathData("M 5,19 L 5,5 L 19,19 L 19,5")
    private val micPath = PathParser.createPathFromPathData("M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92H17z")
    private val nPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
    }
    private val micPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val searchTrack = android.graphics.RectF()

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isNeumorphic = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val isGlass = com.nexus.launcher.ui.widgets.NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val palette = com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.resolvePalette(tokens)
        val isLight = palette.isLight
        val highContrastSecondary = if (isLight) Color.parseColor("#222A35") else if (isGlass) Color.argb(225, 245, 245, 245) else palette.textSecondary

        val iconSize = 24f * dp
        val paddingHorizontal = 16f * dp
        val cy = height / 2f

        if (isNeumorphic) {
            searchTrack.set(paddingHorizontal - 4f * dp, cy - 18f * dp, width - paddingHorizontal + 4f * dp, cy + 18f * dp)
            com.nexus.launcher.ui.widgets.NexusNeumorphicDraw.drawDebossedWell(canvas, searchTrack, 10f * dp, palette, dp)
        }

        val shadowColor = if (isLight) Color.argb(45, 255, 255, 255) else Color.argb(120, 0, 0, 0)

        // Draw Nexus "N" Logomark (Left)
        nPaint.color = palette.textPrimary
        nPaint.strokeWidth = 3f * dp
        if (isGlass) nPaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor) else nPaint.clearShadowLayer()

        canvas.save()
        canvas.translate(paddingHorizontal, cy - (12f * dp))
        canvas.scale(dp, dp)
        canvas.drawPath(nPath, nPaint)
        canvas.restore()

        // Draw "Search Nexus" placeholder (Center)
        textPaint.color = highContrastSecondary
        textPaint.textSize = 16f * dp
        if (isGlass) textPaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor) else textPaint.clearShadowLayer()

        val textX = paddingHorizontal + iconSize + (12f * dp)
        val fontMetrics = textPaint.fontMetrics
        val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        val hintText = context.getString(com.nexus.launcher.R.string.home_search_hint)
        canvas.drawText(hintText, textX, textY, textPaint)

        // Draw Mic icon (Right)
        micPaint.color = highContrastSecondary
        if (isGlass) micPaint.setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor) else micPaint.clearShadowLayer()

        canvas.save()
        canvas.translate(width - paddingHorizontal - iconSize, cy - (12f * dp))
        canvas.scale(dp, dp)
        canvas.drawPath(micPath, micPaint)
        canvas.restore()
    }
}
