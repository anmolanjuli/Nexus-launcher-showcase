package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.ImageView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetPreviewCache
import com.nexus.launcher.ui.widgets.WidgetGlassPreviewDrawable
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarRenderer
import com.nexus.launcher.ui.widgets.clock.NexusClockRenderer
import com.nexus.launcher.ui.widgets.music.NexusMusicRenderer
import com.nexus.launcher.ui.widgets.weather.NexusWeatherRenderer

/**
 * Real Nexus widgets, laid out like a corner of a home screen.
 *
 * Every widget is drawn by its own renderer with the sample data the widget picker uses, in the
 * current UI style and the theme's accent, so the picture is exactly what the user would place. A
 * tile has room for two (Weather over Music); the full-screen preview gets four (Weather, Clock
 * beside Calendar, Music). Rendered once the frame knows its size, so each copy is sharp.
 */
internal class PremiumShowcaseWidgets(context: Context, private val tokens: NexusColorTokens) : FrameLayout(context) {

    private enum class Kind { WEATHER, CLOCK, CALENDAR, MUSIC }

    /** Position and size in fractions of the layout's width, so one layout scales to any frame. */
    private class Slot(val kind: Kind, val x: Float, val y: Float, val w: Float, val h: Float)

    private val compact = listOf(
        Slot(Kind.WEATHER, 0f, 0f, 1f, 0.46f),
        Slot(Kind.MUSIC, 0f, 0.5f, 1f, 0.46f),
    )

    private val full = listOf(
        Slot(Kind.WEATHER, 0f, 0f, 1f, 0.46f),
        Slot(Kind.CLOCK, 0f, 0.5f, 0.48f, 0.48f),
        Slot(Kind.CALENDAR, 0.52f, 0.5f, 0.48f, 0.48f),
        Slot(Kind.MUSIC, 0f, 1.02f, 1f, 0.46f),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        // Adding views from inside a size change has to wait for the layout pass to finish.
        post { layoutWidgets(w, h) }
    }

    private fun layoutWidgets(w: Int, h: Int) {
        removeAllViews()
        val dp = resources.displayMetrics.density
        val pad = 8 * dp
        val availW = w - pad * 2
        val availH = h - pad * 2
        val slots = if (availH < 220 * dp) compact else full
        val span = slots.maxOf { it.y + it.h }
        val unit = minOf(availW, availH / span)
        val originX = (w - unit) / 2f
        val originY = (h - unit * span) / 2f
        val config = config()

        slots.forEach { slot ->
            val sw = (slot.w * unit).toInt().coerceAtLeast(1)
            val sh = (slot.h * unit).toInt().coerceAtLeast(1)
            val bitmap = runCatching { render(slot.kind, sw, sh, config, dp) }.getOrNull() ?: return@forEach
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(bitmap)
            }
            WidgetGlassPreviewDrawable.bind(image, config, bitmap)
            addView(image, LayoutParams(sw, sh).apply {
                leftMargin = (originX + slot.x * unit).toInt()
                topMargin = (originY + slot.y * unit).toInt()
            })
        }
    }

    /** The configuration the widget picker previews with: the current style, the theme's accent. */
    private fun config(): NexusWidgetConfig.InstanceConfig {
        val glass = FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val mode = when {
            glass -> NexusWidgetConfig.BG_GLASS
            FrostedGlassEngine.isDefaultFlatStyleEnabled -> NexusWidgetConfig.BG_SOLID
            else -> NexusWidgetConfig.BG_NEUMORPHIC
        }
        return NexusWidgetConfig.InstanceConfig(
            appWidgetId = -1,
            backgroundMode = mode,
            backgroundOpacity = if (glass) 0.65f else 1f,
            accentColor = String.format("#%06X", 0xFFFFFF and tokens.accent),
            cornerRadius = 16,
        )
    }

    private fun render(kind: Kind, w: Int, h: Int, config: NexusWidgetConfig.InstanceConfig, dp: Float): Bitmap? {
        val wDp = (w / dp).toInt().coerceAtLeast(1)
        val hDp = (h / dp).toInt().coerceAtLeast(1)
        return when (kind) {
            Kind.WEATHER -> NexusWeatherRenderer(NexusWidgetPreviewCache.createMockWeatherData(context))
                .render(context, w, h, config, wDp, hDp, -1f)
            Kind.CLOCK -> NexusClockRenderer().render(context, w, h, config, wDp, hDp, -1f)
            Kind.CALENDAR -> NexusCalendarRenderer(NexusWidgetPreviewCache.createMockCalendarEvents(context))
                .render(context, w, h, config, wDp, hDp, -1f)
            Kind.MUSIC -> NexusMusicRenderer().render(context, w, h, config, wDp, hDp, 0.42f)
        }
    }
}
