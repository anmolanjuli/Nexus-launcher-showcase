package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

data class WidgetProviderEntry(
    /** Null for synthetic Nexus widgets (e.g. Living Mosaic). */
    val info: AppWidgetProviderInfo?,
    val label: String,
    val previewBitmap: Bitmap?,
    val minWidthDp: Int,
    val minHeightDp: Int,
    /** Non-null for Nexus synthetic entries — see [NexusWidgetKinds]. */
    val nexusKind: String? = null
) {
    val isNexusWidget: Boolean get() = nexusKind != null
    val isLivingMosaic: Boolean get() = NexusWidgetKinds.isLivingMosaic(nexusKind)
    val isShortcutBox: Boolean get() = NexusWidgetKinds.isShortcutBox(nexusKind)
    val isAppBox: Boolean get() = NexusWidgetKinds.isAppBox(nexusKind)
    val isLiveApps: Boolean get() = NexusWidgetKinds.isLiveApps(nexusKind)
}

data class WidgetAppGroup(
    val packageName: String,
    val appLabel: String,
    val appIcon: Drawable,
    val widgets: List<WidgetProviderEntry>
)

enum class RebindOutcome {
    BOUND,
    PROVIDER_NOT_INSTALLED,
    NEEDS_USER_CONFIRMATION
}
