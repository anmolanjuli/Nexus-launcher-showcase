package com.nexus.launcher.ui.widgets.weather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize

class NexusWeatherRenderer(private val weatherData: WeatherData?) : NexusWidgetRenderer() {

    companion object {
        const val STYLE_CLASSIC = 0
        const val STYLE_STATION = 1
    }

    private val stationRenderer by lazy { NexusWeatherStationRenderer(weatherData) }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        if (config.clockStyle == STYLE_STATION) {
            stationRenderer.drawContent(context, canvas, width, height, size, config)
            return
        }
        val dp = context.resources.displayMetrics.density
        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)

        val primaryColor = palette.textPrimary
        val secondaryColor = if (palette.isLight) {
            Color.parseColor("#222A35")
        } else if (isGlass) {
            Color.argb(225, 245, 245, 245)
        } else {
            palette.textSecondary
        }

        if (weatherData == null) {
            placeholderPaint.apply {
                color = primaryColor
                textSize = 14f * dp
                typeface = getTypeface(context, config, Typeface.NORMAL)
                textAlign = Paint.Align.CENTER
                if (isGlass) {
                    val shadowColor = if (palette.isLight) Color.argb(45, 255, 255, 255) else Color.argb(160, 0, 0, 0)
                    setShadowLayer(1.5f * dp, 0f, 1f * dp, shadowColor)
                }
            }
            canvas.drawText(context.getString(R.string.weather_no_data), width / 2f, height / 2f, placeholderPaint)
            return
        }

        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, width.toFloat(), height.toFloat(), dp)
        val availH = height - insets.top - insets.bottom
        val wDp = width / dp
        val hDp = height / dp

        when {
            wDp < 120f || hDp < 85f -> NexusWeatherClassicDrawers.drawIconOnly(
                context, canvas, width, height, dp, config, palette, isGlass, primaryColor, weatherData
            )
            hDp < 130f || wDp < 155f -> NexusWeatherClassicDrawers.drawCompact(
                context, canvas, width, height, dp, config, palette, isGlass, primaryColor, secondaryColor, weatherData
            )
            availH >= 128f * dp && wDp >= 170f && weatherData.daily.size >= 3 -> NexusWeatherClassicDrawers.drawExpanded(
                context, canvas, width, height, dp, config, palette, isGlass, primaryColor, secondaryColor, weatherData
            )
            else -> NexusWeatherClassicDrawers.drawStandard(
                context, canvas, width, height, dp, config, palette, isGlass, primaryColor, secondaryColor, weatherData
            )
        }
    }
}
