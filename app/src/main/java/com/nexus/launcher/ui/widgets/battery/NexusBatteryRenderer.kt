package com.nexus.launcher.ui.widgets.battery

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.os.BatteryManager
import com.nexus.launcher.R
import com.nexus.launcher.locale.LocaleObserver
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetShapeInset
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize

/**
 * Renderer for the Nexus Battery Widget.
 * Dispatches to the 5 signature Nexus battery styles:
 * 0 - Fluid Level
 * 1 - Capsule Slider
 * 2 - Radial Gauge
 * 3 - Tech Cell
 * 4 - Power Monitor
 */
class NexusBatteryRenderer(
    private val overrideSnapshot: BatterySnapshot? = null
) : NexusWidgetRenderer() {

    override fun shouldDrawOuterProgressRing(
        config: NexusWidgetConfig.InstanceConfig,
        w: Int,
        h: Int,
        dp: Float
    ): Boolean = false

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        val insets = NexusWidgetShapeInset.getInsets(config.shapeStyle, width.toFloat(), height.toFloat(), dp)
        val contentLeft = insets.left
        val contentTop = insets.top
        val contentW = (width - insets.left - insets.right).coerceAtLeast(10f * dp)
        val contentH = (height - insets.top - insets.bottom).coerceAtLeast(10f * dp)

        val wDp = width / dp
        val hDp = height / dp

        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isGlass = NexusWidgetConfig.isGlassSurface(config.backgroundMode, config.isExpressive)
        val isLight = config.themeMode == NexusWidgetConfig.THEME_LIGHT ||
            (config.themeMode == NexusWidgetConfig.THEME_FOLLOW && tokens.bg == NexusColorTokens.Light.bg)

        // The theme's own ink, like Clock, Calendar and Music — never the accent. Only low battery
        // changes colour, and each style takes that from tokens.danger itself.
        val markColor = tokens.textPrimary

        val snapshot = overrideSnapshot ?: sampleBattery(context, config.clockStyle)
        val locale = LocaleObserver.getEffectiveLocale(context)
        val effectiveStyle = resolveEffectiveStyle(config.clockStyle, wDp, hDp, size)

        when (effectiveStyle) {
            NexusBatteryStyleDrawers.STYLE_FLUID -> {
                NexusBatteryStyleDrawers.drawFluidLevel(
                    context = context,
                    canvas = canvas,
                    left = contentLeft,
                    top = contentTop,
                    width = contentW,
                    height = contentH,
                    dp = dp,
                    percent = snapshot.percent,
                    isCharging = snapshot.isCharging,
                    statusText = snapshot.statusText,
                    tokens = tokens,
                    config = config,
                    locale = locale,
                    isGlass = isGlass,
                    isLight = isLight,
                    markColor = markColor
                )
            }
            NexusBatteryStyleDrawers.STYLE_CAPSULE -> {
                NexusBatteryStyleDrawers.drawCapsuleSlider(
                    context = context,
                    canvas = canvas,
                    left = contentLeft,
                    top = contentTop,
                    width = contentW,
                    height = contentH,
                    dp = dp,
                    percent = snapshot.percent,
                    isCharging = snapshot.isCharging,
                    statusText = snapshot.statusText,
                    tokens = tokens,
                    config = config,
                    locale = locale,
                    isGlass = isGlass,
                    isLight = isLight,
                    markColor = markColor
                )
            }
            NexusBatteryStyleDrawers.STYLE_RADIAL -> {
                NexusBatteryGaugeDrawers.drawRadialGauge(
                    context = context,
                    canvas = canvas,
                    left = contentLeft,
                    top = contentTop,
                    width = contentW,
                    height = contentH,
                    dp = dp,
                    percent = snapshot.percent,
                    isCharging = snapshot.isCharging,
                    statusText = snapshot.statusText,
                    tokens = tokens,
                    config = config,
                    locale = locale,
                    isGlass = isGlass,
                    isLight = isLight,
                    markColor = markColor
                )
            }
            NexusBatteryStyleDrawers.STYLE_TECH_CELL -> {
                NexusBatteryGaugeDrawers.drawTechCell(
                    context = context,
                    canvas = canvas,
                    left = contentLeft,
                    top = contentTop,
                    width = contentW,
                    height = contentH,
                    dp = dp,
                    percent = snapshot.percent,
                    isCharging = snapshot.isCharging,
                    statusText = snapshot.statusText,
                    tokens = tokens,
                    config = config,
                    locale = locale,
                    isGlass = isGlass,
                    isLight = isLight,
                    markColor = markColor
                )
            }
            NexusBatteryStyleDrawers.STYLE_DASHBOARD -> {
                NexusBatteryHogsDrawer.drawPowerMonitor(
                    context = context,
                    canvas = canvas,
                    left = contentLeft,
                    top = contentTop,
                    width = contentW,
                    height = contentH,
                    dp = dp,
                    percent = snapshot.percent,
                    consumers = snapshot.topConsumers,
                    hasUsagePermission = NexusBatteryUsageHelper.hasUsagePermission(context),
                    tokens = tokens,
                    config = config,
                    locale = locale,
                    isGlass = isGlass,
                    isLight = isLight
                )
            }
            else -> {
                NexusBatteryStyleDrawers.drawFluidLevel(
                    context = context,
                    canvas = canvas,
                    left = contentLeft,
                    top = contentTop,
                    width = contentW,
                    height = contentH,
                    dp = dp,
                    percent = snapshot.percent,
                    isCharging = snapshot.isCharging,
                    statusText = snapshot.statusText,
                    tokens = tokens,
                    config = config,
                    locale = locale,
                    isGlass = isGlass,
                    isLight = isLight,
                    markColor = markColor
                )
            }
        }
    }

    private fun resolveEffectiveStyle(
        requestedStyle: Int,
        wDp: Float,
        hDp: Float,
        @Suppress("UNUSED_PARAMETER") size: WidgetSize
    ): Int {
        if (minOf(wDp, hDp) < 50f) return NexusBatteryStyleDrawers.STYLE_FLUID
        if (requestedStyle == NexusBatteryStyleDrawers.STYLE_DASHBOARD && (hDp < 100f || wDp < 140f)) {
            return NexusBatteryStyleDrawers.STYLE_CAPSULE
        }
        return requestedStyle
    }

    private fun sampleBattery(context: Context, style: Int): BatterySnapshot {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.registerReceiver(null, filter)
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val percent = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else 100

            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val isFull = status == BatteryManager.BATTERY_STATUS_FULL

            val statusText = when {
                isFull -> context.getString(R.string.battery_status_full)
                isCharging -> context.getString(R.string.battery_status_charging)
                percent < 20 -> context.getString(R.string.battery_status_low)
                else -> context.getString(R.string.battery_status_discharging)
            }

            val topConsumers = if (style == NexusBatteryStyleDrawers.STYLE_DASHBOARD) {
                NexusBatteryUsageHelper.getTopConsumers(context)
            } else {
                emptyList()
            }

            BatterySnapshot(
                percent = percent,
                isCharging = isCharging,
                statusText = statusText,
                topConsumers = topConsumers
            )
        } catch (_: Exception) {
            BatterySnapshot()
        }
    }
}
