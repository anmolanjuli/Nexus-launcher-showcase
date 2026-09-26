package com.nexus.launcher.ui.widgets.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Pre-allocated visualizer and physics data engine for Retro Music Player widgets.
 * Moves spectrum analyzer, VU meter, and cassette reel physics off the main UI thread.
 *
 * Computation runs on [Dispatchers.Default] at 15 FPS (66ms intervals) during active playback.
 * The UI thread reads from pre-allocated arrays and volatile fields with zero allocations.
 */
object RetroMusicVisualizerData {

    const val SPECTRUM_BAR_COUNT = 12

    // Pre-allocated buffers
    private val bgSpectrumBars = FloatArray(SPECTRUM_BAR_COUNT)
    private val uiSpectrumBars = FloatArray(SPECTRUM_BAR_COUNT)

    @Volatile
    var vuLeft: Float = 0.25f
        private set

    @Volatile
    var vuRight: Float = 0.25f
        private set

    @Volatile
    var reelAngle: Float = 0f
        private set

    @Volatile
    var hissLevel: Float = 0.2f
        private set

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var computeJob: Job? = null
    @Volatile
    private var isPlaying = false

    fun onPlaybackStateChanged(playing: Boolean) {
        isPlaying = playing
        if (playing) {
            startComputation()
        } else {
            stopComputation()
        }
    }

    @Synchronized
    fun startComputation() {
        if (computeJob?.isActive == true) return
        computeJob = scope.launch {
            while (isActive && isPlaying) {
                val now = System.currentTimeMillis()
                computePhysics(now)
                delay(66L) // 15 FPS cap (66ms)
            }
        }
    }

    @Synchronized
    fun stopComputation() {
        computeJob?.cancel()
        computeJob = null
        // Reset to static resting positions
        for (i in 0 until SPECTRUM_BAR_COUNT) {
            bgSpectrumBars[i] = 0.15f
        }
        System.arraycopy(bgSpectrumBars, 0, uiSpectrumBars, 0, SPECTRUM_BAR_COUNT)
        vuLeft = 0.25f
        vuRight = 0.25f
        hissLevel = 0.2f
    }

    private fun computePhysics(now: Long) {
        // 1. Spectrum Analyzer Bars (Winamp / Terminal)
        for (i in 0 until SPECTRUM_BAR_COUNT) {
            val wave = ((sin(now / 150.0 + i * 0.85) + 1.0) * 0.45 +
                    (sin(now / 80.0 + i * 1.6) + 1.0) * 0.15).toFloat()
            bgSpectrumBars[i] = wave.coerceIn(0.08f, 1f)
        }
        System.arraycopy(bgSpectrumBars, 0, uiSpectrumBars, 0, SPECTRUM_BAR_COUNT)

        // 2. Dual VU Meter Needle Fluctuation (Amplifier)
        val bounceL = (sin(now / 140.0) * 0.08f + sin(now / 70.0) * 0.04f).toFloat()
        val bounceR = (sin(now / 140.0 + 0.8) * 0.08f + sin(now / 70.0 + 1.2) * 0.04f).toFloat()
        vuLeft = (0.35f + bounceL).coerceIn(0.1f, 0.95f)
        vuRight = (0.35f + bounceR).coerceIn(0.1f, 0.95f)

        // 3. Cassette Spool Rotation Angle (Cassette)
        reelAngle = ((now % 30000L) / 30000f) * 360f

        // 4. Tape Hiss / Saturation LED level (Cassette)
        hissLevel = (0.4f + (sin(now / 90.0) * 0.2f).toFloat()).coerceIn(0.1f, 0.9f)
    }

    /**
     * Reads pre-computed spectrum bar values into [target] array.
     * Zero allocations.
     */
    fun copySpectrumBars(target: FloatArray) {
        val count = minOf(target.size, SPECTRUM_BAR_COUNT)
        System.arraycopy(uiSpectrumBars, 0, target, 0, count)
    }

    fun getSpectrumBar(index: Int): Float {
        return if (index in 0 until SPECTRUM_BAR_COUNT) uiSpectrumBars[index] else 0.15f
    }
}
