package com.nexus.launcher.ui.widgets.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import java.io.RandomAccessFile

/**
 * Lightweight hardware and system metrics sampler.
 * Zero new permissions required (uses standard public APIs and /proc/stat).
 */
object DevicePerformanceSampler {

    private var lastCpuTotal: Long = 0L
    private var lastCpuIdle: Long = 0L
    private var lastCpuPercent: Int = 12 // Safe initial baseline
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastNetworkTime = 0L

    fun sample(context: Context): PerformanceSnapshot {
        val cpu = sampleCpuLoad()
        val (ramUsed, ramTotal, ramPercent) = sampleRam(context)
        val (storageFree, storageTotal, storageUsedPercent) = sampleStorage()
        val (batteryPercent, isCharging, batteryTemp) = sampleBattery(context)
        val thermal = sampleThermal(context)
        val netSpeed = sampleNetworkSpeed()

        val snap = PerformanceSnapshot(
            cpuUsagePercent = cpu,
            ramUsedBytes = ramUsed,
            ramTotalBytes = ramTotal,
            ramPercent = ramPercent,
            storageFreeBytes = storageFree,
            storageTotalBytes = storageTotal,
            storageUsedPercent = storageUsedPercent,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            batteryTempC = batteryTemp,
            thermalLevel = thermal,
            networkSpeedBytesPerSec = netSpeed
        )
        PerformanceHistoryBuffer.recordSnapshot(context, snap)
        return snap
    }

    private fun sampleNetworkSpeed(): Float {
        val currentTime = System.currentTimeMillis()
        val rxBytes = android.net.TrafficStats.getTotalRxBytes()
        val txBytes = android.net.TrafficStats.getTotalTxBytes()

        if (lastNetworkTime == 0L || rxBytes == android.net.TrafficStats.UNSUPPORTED.toLong()) {
            lastRxBytes = rxBytes
            lastTxBytes = txBytes
            lastNetworkTime = currentTime
            return 0f
        }

        val timeDelta = currentTime - lastNetworkTime
        if (timeDelta <= 0) return 0f

        val rxDelta = rxBytes - lastRxBytes
        val txDelta = txBytes - lastTxBytes
        val totalBytes = rxDelta + txDelta

        lastRxBytes = rxBytes
        lastTxBytes = txBytes
        lastNetworkTime = currentTime

        val speedBytesPerSec = (totalBytes.toFloat() / (timeDelta / 1000f))
        return speedBytesPerSec.coerceAtLeast(0f)
    }

    private fun sampleCpuLoad(): Int {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()

            if (line != null && line.startsWith("cpu")) {
                val toks = line.trim().split("\\s+".toRegex())
                if (toks.size >= 8) {
                    val user = toks[1].toLongOrNull() ?: 0L
                    val nice = toks[2].toLongOrNull() ?: 0L
                    val system = toks[3].toLongOrNull() ?: 0L
                    val idle = toks[4].toLongOrNull() ?: 0L
                    val iowait = toks[5].toLongOrNull() ?: 0L
                    val irq = toks[6].toLongOrNull() ?: 0L
                    val softirq = toks[7].toLongOrNull() ?: 0L
                    val steal = if (toks.size > 8) toks[8].toLongOrNull() ?: 0L else 0L

                    val total = user + nice + system + idle + iowait + irq + softirq + steal
                    val totalIdle = idle + iowait

                    if (lastCpuTotal > 0L && total > lastCpuTotal) {
                        val diffTotal = (total - lastCpuTotal).toDouble()
                        val diffIdle = (totalIdle - lastCpuIdle).toDouble()
                        val usage = ((diffTotal - diffIdle) / diffTotal * 100.0).toInt().coerceIn(0, 100)
                        lastCpuPercent = usage
                    }
                    lastCpuTotal = total
                    lastCpuIdle = totalIdle
                }
            }
            lastCpuPercent
        } catch (_: Exception) {
            lastCpuPercent
        }
    }

    private fun sampleRam(context: Context): Triple<Long, Long, Int> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val total = memInfo.totalMem.coerceAtLeast(1L)
        val avail = memInfo.availMem
        val used = (total - avail).coerceAtLeast(0L)
        val percent = ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)

        return Triple(used, total, percent)
    }

    private fun sampleStorage(): Triple<Long, Long, Int> {
        return try {
            val path = Environment.getDataDirectory().path
            val stat = StatFs(path)
            val total = stat.totalBytes.coerceAtLeast(1L)
            val free = stat.availableBytes.coerceAtLeast(0L)
            val used = (total - free).coerceAtLeast(0L)
            val usedPercent = ((used.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            Triple(free, total, usedPercent)
        } catch (_: Exception) {
            Triple(0L, 1L, 0)
        }
    }

    private fun sampleBattery(context: Context): Triple<Int, Boolean, Float?> {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, ifilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 50
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val percent = if (scale > 0) ((level / scale.toFloat()) * 100f).toInt().coerceIn(0, 100) else 50

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawTemp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (rawTemp > 0) rawTemp / 10f else null

        return Triple(percent, isCharging, tempC)
    }

    private fun sampleThermal(context: Context): ThermalLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ThermalLevel.NORMAL
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return when (pm?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NORMAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.WARM
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.HOT
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.SEVERE
            else -> ThermalLevel.NORMAL
        }
    }
}
