package com.nexus.launcher.ui.widgets.mosaic

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.PathInterpolator
import com.nexus.launcher.ui.NexusDesignSystem

/** Mist-blue border breathe pulse — same feel as the Living Mosaic prototype. */
object LivingMosaicBreathPulse {

    private val interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    fun pulse(view: View, borderDrawable: GradientDrawable, density: Float) {
        val base = android.graphics.Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER)
        val hi = android.graphics.Color.parseColor("#8C7EB8D4") // mist @ ~55%
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000L
            this.interpolator = LivingMosaicBreathPulse.interpolator
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                // 0 → 0.35 peak → 1 settle
                val peak = when {
                    t < 0.35f -> t / 0.35f
                    else -> 1f - ((t - 0.35f) / 0.65f)
                }
                val color = evaluateArgb(peak, base, hi)
                borderDrawable.setStroke(
                    (1.5f * density).toInt().coerceAtLeast(1),
                    color
                )
                val glow = (peak * 0.28f * 255).toInt().coerceIn(0, 255)
                view.elevation = peak * 8f * density
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    view.outlineAmbientShadowColor =
                        android.graphics.Color.argb(glow, 0x7E, 0xB8, 0xD4)
                    view.outlineSpotShadowColor =
                        android.graphics.Color.argb(glow, 0x7E, 0xB8, 0xD4)
                }
            }
        }
        anim.start()
    }

    private fun evaluateArgb(fraction: Float, start: Int, end: Int): Int {
        val sa = android.graphics.Color.alpha(start)
        val sr = android.graphics.Color.red(start)
        val sg = android.graphics.Color.green(start)
        val sb = android.graphics.Color.blue(start)
        val ea = android.graphics.Color.alpha(end)
        val er = android.graphics.Color.red(end)
        val eg = android.graphics.Color.green(end)
        val eb = android.graphics.Color.blue(end)
        return android.graphics.Color.argb(
            (sa + (ea - sa) * fraction).toInt(),
            (sr + (er - sr) * fraction).toInt(),
            (sg + (eg - sg) * fraction).toInt(),
            (sb + (eb - sb) * fraction).toInt()
        )
    }
}
