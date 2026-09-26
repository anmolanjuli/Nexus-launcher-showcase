package com.nexus.launcher.ui.widgets.performance

/** Snapshot of real-time device performance and hardware metrics. */
data class PerformanceSnapshot(
    val cpuUsagePercent: Int = 0,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val ramPercent: Int = 0,
    val storageFreeBytes: Long = 0L,
    val storageTotalBytes: Long = 0L,
    val storageUsedPercent: Int = 0,
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val batteryTempC: Float? = null,
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL,
    val networkSpeedBytesPerSec: Float = 0f
)

/** Normalized thermal throttling status. */
enum class ThermalLevel {
    NORMAL,
    WARM,
    HOT,
    SEVERE,
    UNKNOWN
}
