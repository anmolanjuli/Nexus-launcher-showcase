package com.nexus.launcher.ui.widgets.battery

import android.graphics.drawable.Drawable

/**
 * Data models for battery snapshot and top-consuming foreground applications.
 */
data class BatteryConsumerApp(
    val packageName: String,
    val appLabel: String,
    val percentOfDrain: Int,
    val iconDrawable: Drawable? = null
)

data class BatterySnapshot(
    val percent: Int = 85,
    val isCharging: Boolean = false,
    val statusText: String = "",
    val topConsumers: List<BatteryConsumerApp> = emptyList()
)
