package com.nexus.launcher.ui.widgets.mosaic

import android.content.Context
import com.nexus.launcher.ui.widgets.NexusWidgetKinds
import com.nexus.launcher.ui.widgets.WidgetAppGroup
import com.nexus.launcher.ui.widgets.WidgetProviderEntry

/** Synthetic Nexus widget group for the picker (Living Mosaic, Shortcut Box, App Box, Performance). */
object NexusWidgetCatalog {

    fun buildGroup(context: Context, realNexusWidgets: List<WidgetProviderEntry>? = null): WidgetAppGroup {
        val dp = context.resources.displayMetrics.density
        val icon = context.packageManager.getApplicationIcon(context.packageName)
        val entries = mutableListOf(
            mosaicEntry(context.getString(com.nexus.launcher.R.string.widget_name_living_mosaic), NexusWidgetKinds.LIVING_MOSAIC_2X2, 2, 2, dp),
            mosaicEntry(context.getString(com.nexus.launcher.R.string.widget_name_living_mosaic), NexusWidgetKinds.LIVING_MOSAIC_3X3, 3, 3, dp),
            boxEntry(context.getString(com.nexus.launcher.R.string.widget_name_shortcut_box), NexusWidgetKinds.SHORTCUT_BOX_2X2, 2, 2, dp),
            boxEntry(context.getString(com.nexus.launcher.R.string.widget_name_app_box), NexusWidgetKinds.APP_BOX_2X2, 2, 2, dp),
            liveAppEntry(context, context.getString(com.nexus.launcher.R.string.widget_name_live_apps), NexusWidgetKinds.LIVE_APPS_3X3, 3, 3, dp)
        )

        val hasRealPerformance = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusPerformanceWidgetProvider") == true } == true
        if (!hasRealPerformance) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_performance),
                    previewBitmap = com.nexus.launcher.ui.widgets.performance.PerformancePreviewBuilder.buildPreview(context, dp),
                    minWidthDp = 180,
                    minHeightDp = 110,
                    nexusKind = "performance"
                )
            )
        }

        val hasRealMusic = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusMusicWidgetProvider") == true } == true
        if (!hasRealMusic) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_music),
                    previewBitmap = NexusCatalogPreviewDrawers.buildMusicPreview(context, dp),
                    minWidthDp = 100,
                    minHeightDp = 80,
                    nexusKind = "music"
                )
            )
        }

        val hasRealWeather = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusWeatherWidgetProvider") == true } == true
        if (!hasRealWeather) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_weather),
                    previewBitmap = NexusCatalogPreviewDrawers.buildWeatherPreview(context, dp),
                    minWidthDp = 100,
                    minHeightDp = 80,
                    nexusKind = "weather"
                )
            )
        }

        val hasRealCalendar = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusCalendarWidgetProvider") == true } == true
        if (!hasRealCalendar) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_calendar),
                    previewBitmap = NexusCatalogPreviewDrawers.buildCalendarPreview(context, dp),
                    minWidthDp = 100,
                    minHeightDp = 80,
                    nexusKind = "calendar"
                )
            )
        }

        val hasRealClock = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusClockWidgetProvider") == true } == true
        if (!hasRealClock) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_clock),
                    previewBitmap = null,
                    minWidthDp = 100,
                    minHeightDp = 80,
                    nexusKind = "clock"
                )
            )
        }

        val hasRealGlance = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusGlanceWidgetProvider") == true } == true
        if (!hasRealGlance) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_glance),
                    previewBitmap = null,
                    minWidthDp = 180,
                    minHeightDp = 90,
                    nexusKind = "glance"
                )
            )
        }

        val hasRealAgenda = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusAgendaWidgetProvider") == true } == true
        if (!hasRealAgenda) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_agenda),
                    previewBitmap = null,
                    minWidthDp = 140,
                    minHeightDp = 110,
                    nexusKind = "agenda"
                )
            )
        }

        val hasRealNotes = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusNotesWidgetProvider") == true } == true
        if (!hasRealNotes) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_notes),
                    previewBitmap = null,
                    minWidthDp = 140,
                    minHeightDp = 110,
                    nexusKind = "notes"
                )
            )
        }

        val hasRealBattery = realNexusWidgets?.any { it.info?.provider?.className?.contains("NexusBatteryWidgetProvider") == true } == true
        if (!hasRealBattery) {
            entries.add(
                WidgetProviderEntry(
                    info = null,
                    label = context.getString(com.nexus.launcher.R.string.widget_name_battery),
                    previewBitmap = null,
                    minWidthDp = 100,
                    minHeightDp = 80,
                    nexusKind = "battery"
                )
            )
        }

        if (realNexusWidgets != null) {
            val visibleWidgets = realNexusWidgets.filter {
                it.info?.provider?.className?.contains("NexusProgressWidgetProvider") != true
            }
            entries.addAll(visibleWidgets)
        }
        return WidgetAppGroup(
            packageName = context.packageName,
            appLabel = "Nexus",
            appIcon = icon,
            widgets = entries
        )
    }

    private fun mosaicEntry(
        label: String,
        kind: String,
        spanX: Int,
        spanY: Int,
        dp: Float
    ): WidgetProviderEntry {
        val minW = spanX * 70
        val minH = spanY * 70
        return WidgetProviderEntry(
            info = null,
            label = "$label · ${spanX}×${spanY}",
            previewBitmap = NexusCatalogPreviewDrawers.buildMosaicPreview(spanX, spanY, dp),
            minWidthDp = minW,
            minHeightDp = minH,
            nexusKind = kind
        )
    }

    private fun boxEntry(
        label: String,
        kind: String,
        spanX: Int,
        spanY: Int,
        dp: Float
    ): WidgetProviderEntry {
        val minW = spanX * 70
        val minH = spanY * 70
        return WidgetProviderEntry(
            info = null,
            label = "$label · ${spanX}×${spanY}",
            previewBitmap = NexusCatalogPreviewDrawers.buildShortcutBoxPreview(spanX, spanY, dp),
            minWidthDp = minW,
            minHeightDp = minH,
            nexusKind = kind
        )
    }

    private fun liveAppEntry(
        context: Context,
        label: String,
        kind: String,
        spanX: Int,
        spanY: Int,
        dp: Float
    ): WidgetProviderEntry {
        val minW = spanX * 70
        val minH = spanY * 70
        return WidgetProviderEntry(
            info = null,
            label = "$label · ${spanX}×${spanY}",
            previewBitmap = NexusCatalogPreviewDrawers.buildLiveAppBoxPreview(context, spanX, spanY, dp),
            minWidthDp = minW,
            minHeightDp = minH,
            nexusKind = kind
        )
    }
}
