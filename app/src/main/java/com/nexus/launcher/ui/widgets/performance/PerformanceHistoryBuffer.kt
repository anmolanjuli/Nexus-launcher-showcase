package com.nexus.launcher.ui.widgets.performance

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages fixed-size rolling history buffers for performance graphs.
 * Zero-allocation access during rendering.
 */
object PerformanceHistoryBuffer {

    const val MAX_POINTS = 20
    private const val PREFS_NAME = "nexus_perf_history"

    private val cpuPoints = FloatArray(MAX_POINTS)
    private val ramPoints = FloatArray(MAX_POINTS)
    private val batPoints = FloatArray(MAX_POINTS)
    private val netPoints = FloatArray(MAX_POINTS)
    private var isLoaded = false

    fun recordSnapshot(context: Context, snap: PerformanceSnapshot) {
        ensureLoaded(context)
        pushValue(cpuPoints, snap.cpuUsagePercent.toFloat())
        pushValue(ramPoints, snap.ramPercent.toFloat())
        pushValue(batPoints, snap.batteryPercent.toFloat())

        // Normalized net speed (0..100) based on dynamic scale
        val maxSpeed = 2f * 1024f * 1024f // 2MB/s baseline scale
        val normNet = ((snap.networkSpeedBytesPerSec / maxSpeed) * 100f).coerceIn(0f, 100f)
        pushValue(netPoints, normNet)

        saveToPrefs(context)
    }

    fun getCpuHistory(context: Context): FloatArray {
        ensureLoaded(context)
        return cpuPoints
    }

    fun getRamHistory(context: Context): FloatArray {
        ensureLoaded(context)
        return ramPoints
    }

    fun getBatHistory(context: Context): FloatArray {
        ensureLoaded(context)
        return batPoints
    }

    fun getNetHistory(context: Context): FloatArray {
        ensureLoaded(context)
        return netPoints
    }

    private fun pushValue(array: FloatArray, value: Float) {
        System.arraycopy(array, 1, array, 0, array.size - 1)
        array[array.size - 1] = value
    }

    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadArray(prefs, "cpu", cpuPoints, 15f)
        loadArray(prefs, "ram", ramPoints, 45f)
        loadArray(prefs, "bat", batPoints, 80f)
        loadArray(prefs, "net", netPoints, 10f)
        isLoaded = true
    }

    private fun loadArray(prefs: SharedPreferences, key: String, target: FloatArray, defaultVal: Float) {
        val raw = prefs.getString(key, null)
        if (raw.isNullOrEmpty()) {
            target.fill(defaultVal)
            return
        }
        val parts = raw.split(",")
        for (i in target.indices) {
            target[i] = parts.getOrNull(i)?.toFloatOrNull() ?: defaultVal
        }
    }

    private fun saveToPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString("cpu", cpuPoints.joinToString(","))
                .putString("ram", ramPoints.joinToString(","))
                .putString("bat", batPoints.joinToString(","))
                .putString("net", netPoints.joinToString(","))
                .apply()
        } catch (_: Exception) {}
    }
}
