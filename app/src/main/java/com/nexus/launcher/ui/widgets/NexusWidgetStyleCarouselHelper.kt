package com.nexus.launcher.ui.widgets

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.battery.NexusBatteryStyleCarouselView
import com.nexus.launcher.ui.widgets.clock.NexusClockStyleCarouselView
import com.nexus.launcher.ui.widgets.glance.NexusGlanceStyleCarouselView
import com.nexus.launcher.ui.widgets.music.NexusMusicStyleCarouselView
import com.nexus.launcher.ui.widgets.performance.NexusPerformanceStyleCarouselView
import com.nexus.launcher.ui.widgets.weather.NexusWeatherStyleCarouselView

/**
 * Encapsulates style carousels and static preview card creation for [NexusWidgetSettingsSheet].
 * Dispatches to specialized carousels for Clock, Performance, Glance, Weather, Battery, and Music widgets.
 */
class NexusWidgetStyleCarouselHelper(
    private val context: Context,
    private val tokens: NexusColorTokens,
    private val dp: Float,
    private val providerClassName: String?,
    private val getConfig: () -> NexusWidgetConfig.InstanceConfig,
    private val onStyleChanged: (Int) -> Unit,
    private val onClockMessageVisibilityChanged: (Boolean) -> Unit
) {
    private var clockCarousel: NexusClockStyleCarouselView? = null
    private var perfCarousel: NexusPerformanceStyleCarouselView? = null
    private var glanceCarousel: NexusGlanceStyleCarouselView? = null
    private var weatherCarousel: NexusWeatherStyleCarouselView? = null
    private var batteryCarousel: NexusBatteryStyleCarouselView? = null
    private var musicCarousel: NexusMusicStyleCarouselView? = null
    private var genericCarousel: NexusGenericStyleCarouselView? = null

    val isClockWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.clock.NexusClockWidgetProvider"
    val isPerformanceWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.performance.NexusPerformanceWidgetProvider"
    val isGlanceWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.glance.NexusGlanceWidgetProvider"
    val isWeatherWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.weather.NexusWeatherWidgetProvider"
    val isBatteryWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.battery.NexusBatteryWidgetProvider"
    val isMusicWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.music.NexusMusicWidgetProvider"
    val isCalendarWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.calendar.NexusCalendarWidgetProvider"
    val isAgendaWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.agenda.NexusAgendaWidgetProvider"
    val isNotesWidget: Boolean = providerClassName == "com.nexus.launcher.ui.widgets.notes.NexusNotesWidgetProvider"

    fun attachTo(card: LinearLayout): ImageView? {
        val config = getConfig()
        when {
            isClockWidget -> {
                val carousel = NexusClockStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    currentStyle = config.clockStyle,
                    latestConfig = config,
                    onStyleSelected = { newStyle ->
                        onStyleChanged(newStyle)
                        onClockMessageVisibilityChanged(newStyle == 0 || newStyle == 3)
                    }
                )
                clockCarousel = carousel
                card.addView(carousel)
                return null
            }
            isPerformanceWidget -> {
                val carousel = NexusPerformanceStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    currentStyle = config.clockStyle,
                    latestConfig = config,
                    onStyleSelected = { newStyle ->
                        onStyleChanged(newStyle)
                    }
                )
                perfCarousel = carousel
                card.addView(carousel)
                return null
            }
            isGlanceWidget -> {
                val carousel = NexusGlanceStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    currentStyle = config.clockStyle,
                    latestConfig = config,
                    onStyleSelected = { newStyle ->
                        onStyleChanged(newStyle)
                    }
                )
                glanceCarousel = carousel
                card.addView(carousel)
                return null
            }
            isWeatherWidget -> {
                val carousel = NexusWeatherStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    currentStyle = config.clockStyle,
                    latestConfig = config,
                    onStyleSelected = { newStyle ->
                        onStyleChanged(newStyle)
                    }
                )
                weatherCarousel = carousel
                card.addView(carousel)
                return null
            }
            isBatteryWidget -> {
                val carousel = NexusBatteryStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    currentStyle = config.clockStyle,
                    latestConfig = config,
                    onStyleSelected = { newStyle ->
                        onStyleChanged(newStyle)
                    }
                )
                batteryCarousel = carousel
                card.addView(carousel)
                return null
            }
            isMusicWidget -> {
                val carousel = NexusMusicStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    currentStyle = config.clockStyle,
                    latestConfig = config,
                    onStyleSelected = { newStyle ->
                        onStyleChanged(newStyle)
                    }
                )
                musicCarousel = carousel
                card.addView(carousel)
                return null
            }
            else -> {
                val generic = NexusGenericStyleCarouselView(
                    context = context,
                    tokens = tokens,
                    latestConfig = config,
                    providerClassName = providerClassName
                )
                genericCarousel = generic
                card.addView(generic)
                return generic.previewView
            }
        }
    }

    fun updateLiveConfig(config: NexusWidgetConfig.InstanceConfig) {
        clockCarousel?.updateLiveConfig(config)
        perfCarousel?.updateLiveConfig(config)
        glanceCarousel?.updateLiveConfig(config)
        weatherCarousel?.updateLiveConfig(config)
        batteryCarousel?.updateLiveConfig(config)
        musicCarousel?.updateLiveConfig(config)
        genericCarousel?.updateLiveConfig(config)
    }

    fun resetToStyle(styleId: Int, config: NexusWidgetConfig.InstanceConfig) {
        clockCarousel?.selectStyle(styleId)
        clockCarousel?.updateLiveConfig(config)
        perfCarousel?.selectStyle(styleId)
        perfCarousel?.updateLiveConfig(config)
        glanceCarousel?.selectStyle(styleId)
        glanceCarousel?.updateLiveConfig(config)
        weatherCarousel?.selectStyle(styleId)
        weatherCarousel?.updateLiveConfig(config)
        batteryCarousel?.selectStyle(styleId)
        batteryCarousel?.updateLiveConfig(config)
        musicCarousel?.selectStyle(styleId)
        musicCarousel?.updateLiveConfig(config)
        genericCarousel?.updateLiveConfig(config)
        onClockMessageVisibilityChanged(true)
    }
}
