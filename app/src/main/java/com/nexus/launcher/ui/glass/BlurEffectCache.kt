package com.nexus.launcher.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.SparseArray
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * One [RenderEffect] per blur radius, made once and reused.
 *
 * The drawer transition steps the home, drawer, dock and widget blurs through ~20 radii each,
 * every open and every close; building a fresh effect at each step was steady garbage for the
 * whole motion. A RenderEffect is an immutable description, so one instance can sit on several
 * nodes and views at once. Radii are keyed to the half pixel, which also bounds the cache for
 * callers that animate the radius continuously.
 */
@RequiresApi(Build.VERSION_CODES.S)
object BlurEffectCache {

    private val effects = SparseArray<RenderEffect>()

    fun get(radius: Float, tileMode: Shader.TileMode): RenderEffect {
        val key = tileMode.ordinal * 100_000 + (radius * 2f).roundToInt()
        return effects.get(key) ?: RenderEffect.createBlurEffect(radius, radius, tileMode)
            .also { effects.put(key, it) }
    }
}
