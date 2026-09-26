package com.nexus.launcher.ui.canvas

import android.content.Context

/**
 * The user's animation-speed preference, read from one place.
 *
 * Every settle path used to inline its own `getSharedPreferences("nexus_prefs", ...)
 * .getFloat("anim_speed_multiplier", 1f)` at animation-start time. Centralising it keeps the
 * surfaces in step when the slider moves and takes the lookup off the release path.
 */
internal object MotionSpeed {

    private const val PREFS = "nexus_prefs"
    private const val KEY = "anim_speed_multiplier"

    @Volatile
    private var cached: Float = Float.NaN

    fun multiplier(context: Context): Float {
        val current = cached
        if (!current.isNaN()) return current
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY, 1.0f)
            .coerceIn(0.25f, 3.0f)
        cached = value
        return value
    }

    /** Call when the settings slider writes a new value. */
    fun invalidate() {
        cached = Float.NaN
    }
}
