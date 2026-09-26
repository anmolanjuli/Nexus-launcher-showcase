package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.WidgetSize

/**
 * Adaptive canvas renderer for the Device Performance First-Party Widget.
 * Supports:
 * - Style 0: Rectangular LCD Cockpit Panels (100% space utilization).
 * - Style 1: Real-time Multi-Metric Telemetry Line Graphs (Nothing style).
 */
class NexusPerformanceRenderer : NexusWidgetRenderer() {

    private val lcdPanels = PerformanceDrawLcdPanels()
    private val telemetry = PerformanceDrawTelemetry()

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

        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)
        val accentColor = try {
            Color.parseColor(config.accentColor)
        } catch (_: Exception) {
            tokens.accent
        }

        val snapshot = DevicePerformanceSampler.sample(context)
        val pad = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val contentW = width - pad * 2f
        val contentH = height - pad * 2f

        // Style 1: Telemetry Line Graph Matrix (Nothing style)
        if (config.clockStyle == 1) {
            val telePad = (10f * dp).coerceIn(8f * dp, 14f * dp)
            telemetry.drawTelemetry(
                context, canvas, telePad, telePad, width - telePad * 2f, height - telePad * 2f,
                snapshot, tokens, palette, isNeumorphic, dp, config
            )
            return
        }

        // Style 0: Rectangular LCD Cockpit Grid
        val isSlimRow = height < (80 * dp) || (width / height.toFloat()) >= 3.2f
        val isLargeGrid = width >= (160 * dp) && height >= (90 * dp)

        when {
            isSlimRow -> drawHorizontalStripLayout(
                context, canvas, pad, pad, contentW, contentH,
                snapshot, accentColor, tokens, palette, isNeumorphic, dp, config
            )
            isLargeGrid -> drawLcd2x2Grid(
                context, canvas, pad, pad, contentW, contentH,
                snapshot, accentColor, tokens, palette, isNeumorphic, dp, config
            )
            else -> drawCompactDualLayout(
                context, canvas, pad, pad, contentW, contentH,
                snapshot, accentColor, tokens, palette, isNeumorphic, dp, config
            )
        }
    }

    /** Strict 2x2 Rectangular LCD Grid utilizing 100% of quadrant space. */
    private fun drawLcd2x2Grid(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        snap: PerformanceSnapshot,
        accent: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val gapX = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val gapY = (6f * dp).coerceIn(4f * dp, 10f * dp)
        val colW = (w - gapX) / 2f
        val rowH = (h - gapY) / 2f

        // Quadrant 1 (Top-Left): CPU LCD Panel
        lcdPanels.drawCpuPanel(
            context, canvas, x, y, colW, rowH,
            snap.cpuUsagePercent, accent, tokens, palette, isNeumorphic, dp, config
        )

        // Quadrant 2 (Top-Right): RAM LCD Panel
        val q2X = x + colW + gapX
        lcdPanels.drawRamPanel(
            context, canvas, q2X, y, colW, rowH,
            snap.ramUsedBytes, snap.ramTotalBytes, snap.ramPercent,
            accent, tokens, palette, isNeumorphic, dp, config
        )

        // Quadrant 3 (Bottom-Left): Storage LCD Panel
        val q3Y = y + rowH + gapY
        lcdPanels.drawStoragePanel(
            context, canvas, x, q3Y, colW, rowH,
            snap.storageUsedPercent, snap.storageFreeBytes, snap.storageTotalBytes,
            accent, tokens, palette, isNeumorphic, dp, config
        )

        // Quadrant 4 (Bottom-Right): Battery & Thermal LCD Panel
        lcdPanels.drawBatteryThermalPanel(
            context, canvas, q2X, q3Y, colW, rowH,
            snap.batteryPercent, snap.isCharging, snap.batteryTempC, snap.thermalLevel,
            tokens, palette, isNeumorphic, dp, config
        )
    }

    /** 1-Row slim strip with 4 horizontal LCD pods. */
    private fun drawHorizontalStripLayout(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        snap: PerformanceSnapshot,
        accent: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val gap = (4f * dp).coerceIn(3f * dp, 8f * dp)
        val podW = (w - gap * 3f) / 4f

        lcdPanels.drawCpuPanel(
            context, canvas, x, y, podW, h,
            snap.cpuUsagePercent, accent, tokens, palette, isNeumorphic, dp, config
        )

        val x2 = x + podW + gap
        lcdPanels.drawRamPanel(
            context, canvas, x2, y, podW, h,
            snap.ramUsedBytes, snap.ramTotalBytes, snap.ramPercent,
            accent, tokens, palette, isNeumorphic, dp, config
        )

        val x3 = x2 + podW + gap
        lcdPanels.drawStoragePanel(
            context, canvas, x3, y, podW, h,
            snap.storageUsedPercent, snap.storageFreeBytes, snap.storageTotalBytes,
            accent, tokens, palette, isNeumorphic, dp, config
        )

        val x4 = x3 + podW + gap
        lcdPanels.drawBatteryThermalPanel(
            context, canvas, x4, y, podW, h,
            snap.batteryPercent, snap.isCharging, snap.batteryTempC, snap.thermalLevel,
            tokens, palette, isNeumorphic, dp, config
        )
    }

    /** Compact dual layout for smaller square widgets. */
    private fun drawCompactDualLayout(
        context: Context,
        canvas: Canvas,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        snap: PerformanceSnapshot,
        accent: Int,
        tokens: NexusColorTokens,
        palette: NexusNeumorphicDraw.SoftPalette?,
        isNeumorphic: Boolean,
        dp: Float,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val gap = 4f * dp
        val topH = (h - gap) * 0.58f
        val botH = (h - gap) * 0.42f
        val colW = (w - gap) / 2f

        lcdPanels.drawCpuPanel(
            context, canvas, x, y, colW, topH,
            snap.cpuUsagePercent, accent, tokens, palette, isNeumorphic, dp, config
        )

        val x2 = x + colW + gap
        lcdPanels.drawRamPanel(
            context, canvas, x2, y, colW, topH,
            snap.ramUsedBytes, snap.ramTotalBytes, snap.ramPercent,
            accent, tokens, palette, isNeumorphic, dp, config
        )

        val botY = y + topH + gap
        lcdPanels.drawBatteryThermalPanel(
            context, canvas, x, botY, w, botH,
            snap.batteryPercent, snap.isCharging, snap.batteryTempC, snap.thermalLevel,
            tokens, palette, isNeumorphic, dp, config
        )
    }
}
